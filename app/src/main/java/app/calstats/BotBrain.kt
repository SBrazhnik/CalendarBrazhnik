package app.calstats

import android.content.Context
import app.calstats.work.BookStatus
import app.calstats.work.DebtDir
import app.calstats.work.DebtMath
import app.calstats.work.FinanceMath
import app.calstats.work.Forecast
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.HealthOps
import app.calstats.work.ME
import app.calstats.work.SmartInput
import app.calstats.work.SplitMath
import app.calstats.work.Status
import app.calstats.work.TxType
import app.calstats.work.WorkOps
import app.calstats.work.WorkStore
import app.calstats.work.Workspace
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * «Мозг» Telegram-бота: отвечает на кнопки меню и вопросы («сколько потратил?», «что завтра?»)
 * и превращает обычные фразы в записи («кофе 250», «купить хлеб», «завтра в 15 встреча»).
 */
object BotBrain {
    /** Доступ к данным: из открытого приложения (без конфликтов) или напрямую из файла. */
    interface Access {
        suspend fun get(): Workspace
        suspend fun update(f: (Workspace) -> Workspace)
    }

    class StoreAccess(private val ctx: Context) : Access {
        private val store = WorkStore(ctx)
        override suspend fun get() = store.load()
        override suspend fun update(f: (Workspace) -> Workspace) { synchronized(BotBrain) { store.save(f(store.load())) } }
    }

    val COMMANDS = listOf(
        "today" to "План на сегодня", "tomorrow" to "Что завтра", "tasks" to "Мои задачи", "money" to "Финансы и траты",
        "debts" to "Долги", "habits" to "Привычки на сегодня", "health" to "Здоровье за сегодня", "shop" to "Список покупок",
        "reminders" to "Напоминания", "notes" to "Последние заметки", "help" to "Что умеет бот",
    )

    const val KEYBOARD = """{"keyboard":[[{"text":"📅 Сегодня"},{"text":"📆 Завтра"},{"text":"✅ Задачи"}],[{"text":"💸 Финансы"},{"text":"🤝 Долги"},{"text":"🔁 Привычки"}],[{"text":"🛒 Покупки"},{"text":"⏰ Напоминания"},{"text":"❓ Помощь"}]],"resize_keyboard":true,"is_persistent":true}"""

    const val HELP = "Я — ваш помощник «Зелёный ритм» 🦋\n\n" +
        "Кнопки внизу и меню слева — быстрые ответы.\n\n" +
        "Можно спросить:\n" +
        "• что сегодня? что завтра?\n• сколько потратил за неделю?\n• какой баланс? кто мне должен?\n• что купить? какие задачи?\n\n" +
        "Можно записать:\n" +
        "• кофе 250 / потратил 450 на такси — расход\n" +
        "• получил зарплату 85 тыс — доход\n" +
        "• завтра в 15 встреча с Иваном — событие в календарь\n" +
        "• напомни в пятницу позвонить в банк — задача\n" +
        "• купить молоко, хлеб — в покупки\n" +
        "• выпил 2 стакана воды, вес 82,5, настроение 4\n" +
        "• сделал зарядку — отметить привычку\n" +
        "• готово 3 — закрыть задачу №3 из списка\n" +
        "• карта Пятёрочка — номер бонусной карты\n" +
        "• любой другой текст — заметка"

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    suspend fun answer(ctx: Context, raw: String, acc: Access): String {
        val msg = raw.trim()
        val t = msg.lowercase().replace(Regex("^[^\\p{L}\\d/]+"), "").trim()
        val today = LocalDate.now()
        val w = acc.get()
        val cmd = t.removePrefix("/").substringBefore('@').substringBefore(' ')
        return when {
            cmd in setOf("start", "help") || t in setOf("помощь", "help", "меню", "что ты умеешь", "что умеешь") -> HELP
            cmd == "today" || t in setOf("сегодня", "план", "что сегодня", "что сегодня?", "план на сегодня") -> today(ctx, w, today)
            cmd == "tomorrow" || t.startsWith("завтра") && (t.length < 12 || t.endsWith("?")) -> day(ctx, w, today.plusDays(1))
            cmd == "tasks" || t in setOf("задачи", "дела", "мои задачи", "какие задачи", "какие задачи?") -> tasks(ctx, w, today)
            Regex("^(готово|сделал[аи]?|выполнено|закрыть)\\s+(\\d+)$").matches(t) -> {
                val n = Regex("\\d+").find(t)!!.value.toInt()
                val ids = ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE).getString("last_tasks", "").orEmpty().split(',').filter { it.isNotBlank() }
                val id = ids.getOrNull(n - 1) ?: return "Сначала откройте список: «✅ Задачи»"
                val i = w.issues.firstOrNull { it.id == id } ?: return "Задача не найдена"
                acc.update { WorkOps.setStatus(it, id, Status.DONE) }
                "✅ Готово: ${i.title}"
            }
            cmd == "money" || has(t, "финанс", "баланс", "сколько потратил", "сколько потрачено", "траты", "расходы за", "деньги") -> money(w, today, t)
            cmd == "debts" || has(t, "долг", "кто мне должен", "кому я должен") -> debts(w, today)
            cmd == "habits" || t in setOf("привычки", "мои привычки") -> habits(w, today)
            cmd == "health" || t in setOf("здоровье", "как здоровье") -> health(w, today)
            cmd == "shop" || t in setOf("покупки", "что купить", "что купить?", "список покупок") -> shop(w)
            cmd == "reminders" || t in setOf("напоминания", "мои напоминания") -> reminders(w)
            cmd == "notes" || t in setOf("заметки", "мои заметки") -> notes(w)
            t.startsWith("карта ") -> {
                val q = t.removePrefix("карта ").trim()
                val c = w.cards.firstOrNull { it.store.lowercase().contains(q) } ?: return "Карту «$q» не нашёл"
                "💳 ${c.store}\n${c.number}" + if (c.note.isNotBlank()) "\n${c.note}" else ""
            }
            else -> record(ctx, msg, t, w, acc, today)
        }
    }

    // ---------- ответы ----------

    private fun events(ctx: Context, d: LocalDate): List<EventInstance> {
        if (!Notify.hasCalendar(ctx)) return emptyList()
        val z = ZoneId.systemDefault()
        val from = d.atStartOfDay(z).toInstant().toEpochMilli(); val to = d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        return runCatching { CalendarRepository(ctx).instances(from, to) }.getOrDefault(emptyList()).sortedWith(compareBy({ !it.allDay }, { it.begin }))
    }

    private fun hm(ms: Long) = Fmt.time(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime())

    private fun today(ctx: Context, w: Workspace, d: LocalDate): String {
        val lines = Notify.morningLines(ctx, d)
        return "📅 ${Fmt.dayTitle(d, d)}, ${Fmt.date(d)}\n\n" + (if (lines.isEmpty()) "Ничего срочного — хорошего дня 🌿" else lines.joinToString("\n"))
    }

    private fun day(ctx: Context, w: Workspace, d: LocalDate): String {
        val ev = events(ctx, d)
        val tasks = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE && it.dueDate == d }
        val out = StringBuilder("📆 ${Fmt.dayTitle(d, LocalDate.now())}, ${Fmt.date(d)}\n")
        if (ev.isEmpty() && tasks.isEmpty()) out.append("\nСобытий и задач нет")
        ev.forEach { out.append("\n📅 ").append(if (it.allDay) "" else hm(it.begin) + " ").append(it.title) }
        tasks.forEach { out.append("\n📌 ").append(it.title) }
        w.recurrings.filter { it.active && it.next == d }.forEach { out.append("\n🔁 ${it.name} — ${SplitMath.money(it.amount)}") }
        return out.toString()
    }

    private fun tasks(ctx: Context, w: Workspace, today: LocalDate): String {
        val open = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
            .sortedWith(compareBy({ it.dueDate ?: LocalDate.MAX }, { it.priority.ordinal })).take(15)
        ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE).edit().putString("last_tasks", open.joinToString(",") { it.id }).apply()
        if (open.isEmpty()) return "✅ Открытых задач нет"
        return "✅ Задачи (${open.size}):\n\n" + open.mapIndexed { i, x ->
            "${i + 1}. ${x.title}" + (x.dueDate?.let { d -> if (d.isBefore(today)) " ⚠ просрочено" else " · до ${Fmt.dayTitle(d, today).lowercase()}" } ?: "")
        }.joinToString("\n") + "\n\nЗакрыть: «готово 2»"
    }

    private fun money(w: Workspace, today: LocalDate, q: String): String {
        val exp = w.transactions.filter { it.type == TxType.EXPENSE }
        fun sum(from: LocalDate) = exp.filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(today) }.sumOf { it.amount }
        val mon = today.minusDays(today.dayOfWeek.value - 1L)
        val month = today.withDayOfMonth(1)
        val byCat = exp.filter { !it.localDate.isBefore(month) }.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(4).joinToString("\n") { (id, v) -> "  ${w.category(id)?.emoji ?: "•"} ${w.category(id)?.name ?: "Без категории"} — ${SplitMath.money(v)}" }
        val sb = StringBuilder("💸 Финансы\n\n")
        if (w.accounts.any { !it.archived }) sb.append("Баланс: ${SplitMath.money(FinanceMath.total(w))}\n")
        sb.append("Потрачено сегодня: ${SplitMath.money(sum(today))}\nЗа неделю: ${SplitMath.money(sum(mon))}\nЗа месяц: ${SplitMath.money(sum(month))}\n")
        if (byCat.isNotBlank()) sb.append("\nБольше всего в этом месяце:\n$byCat\n")
        if (w.accounts.any { !it.archived }) {
            val f = Forecast.compute(w, today, listOf(5, 20))
            sb.append("\nДо зарплаты ${f.days} дн.: можно тратить ~${SplitMath.money(f.perDay)} в день")
        }
        return sb.toString()
    }

    private fun debts(w: Workspace, today: LocalDate): String {
        val s = DebtMath.summary(w, today)
        val open = w.debts.filter { !it.closed }
        if (open.isEmpty()) return "🤝 Долгов нет"
        val sb = StringBuilder("🤝 Долги\n\nМне должны: ${SplitMath.money(s.owedToMe)}\nЯ должен: ${SplitMath.money(s.iOwe)}\n")
        if (s.overdue > 0) sb.append("⚠ Просрочено: ${s.overdue}\n")
        open.sortedBy { it.due ?: "9999" }.take(10).forEach { d ->
            val who = w.counterparties.firstOrNull { it.id == d.partyId }?.name ?: "?"
            val st = DebtMath.status(d, today)
            sb.append("\n${if (d.dir == DebtDir.I_OWE) "↗ Я → $who" else "↙ $who → мне"}: ${SplitMath.money(st.totalDue)}" + (d.due?.let { " · до ${Fmt.date(LocalDate.parse(it))}" } ?: ""))
        }
        return sb.toString()
    }

    private fun habits(w: Workspace, today: LocalDate): String {
        val hs = w.habits.filter { !it.archived && it.kind == HabitKind.DO && (it.perWeek != null || it.scheduled(today)) }
        if (hs.isEmpty()) return "🔁 Привычек на сегодня нет"
        return "🔁 Привычки сегодня\n\n" + hs.joinToString("\n") { h ->
            val v = HabitMath.values(w.habitLogs, h.id)
            (if (HabitMath.done(h, v, today)) "✅ " else "○ ") + "${h.emoji} ${h.name}" + HabitMath.streak(h, v, today).let { if (it > 1) " · 🔥 $it" else "" }
        } + "\n\nОтметить: «сделал зарядку»"
    }

    private fun health(w: Workspace, today: LocalDate): String {
        val sb = StringBuilder("💚 Здоровье сегодня\n\n")
        sb.append("💧 Вода: ${HealthOps.water(w, today)} мл\n")
        w.weightLog.maxByOrNull { it.date }?.let { sb.append("⚖ Вес: ${it.kg} кг (${Fmt.date(LocalDate.parse(it.date))})\n") }
        w.moods.firstOrNull { it.date == today.toString() }?.let { sb.append("🙂 Настроение: ${it.mood} из 5\n") }
        w.pressure.maxByOrNull { it.at }?.let { sb.append("🩺 Давление: ${it.sys}/${it.dia} (${app.calstats.work.Vitals.category(it.sys, it.dia).first})\n") }
        w.meds.filter { it.active }.takeIf { it.isNotEmpty() }?.let { l -> sb.append("💊 Лекарства: ${l.joinToString { it.name }}\n") }
        return sb.toString()
    }

    private fun shop(w: Workspace): String {
        val open = w.shopItems.filter { !it.done }
        if (open.isEmpty()) return "🛒 Список покупок пуст.\nДобавить: «купить молоко, хлеб»"
        return "🛒 Купить\n" + open.groupBy { it.listId }.entries.joinToString("\n") { (lid, l) ->
            val list = w.shopLists.firstOrNull { it.id == lid }
            "\n${list?.emoji ?: "🛒"} ${list?.name ?: ""}\n" + l.joinToString("\n") { "• ${it.name}" + if (it.qty.isNotBlank()) " — ${it.qty}" else "" }
        }
    }

    private fun reminders(w: Workspace): String {
        val r = w.notes.filter { it.remindAt != null && !it.remindDone && !it.archived }.sortedBy { it.remindAt }.take(12)
        if (r.isEmpty()) return "⏰ Напоминаний нет.\nДобавить: «напомни завтра в 10 позвонить врачу»"
        return "⏰ Напоминания\n\n" + r.joinToString("\n") { n ->
            "• " + app.calstats.ui.remindText(n.remindAt!!) + " — " + n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.take(60)
        }
    }

    private fun notes(w: Workspace): String {
        val l = w.notes.filter { !it.archived }.sortedByDescending { it.updated }.take(6)
        if (l.isEmpty()) return "📝 Заметок нет. Просто напишите текст — сохраню как заметку."
        return "📝 Последние заметки\n\n" + l.joinToString("\n\n") { n -> (if (n.title.isNotBlank()) "${n.title}\n" else "") + n.body.take(150) }
    }

    /** Фраза → запись. */
    private suspend fun record(ctx: Context, msg: String, t: String, w: Workspace, acc: Access, today: LocalDate): String {
        val now = LocalDateTime.now()
        var r = SmartInput.parse(msg, now, w.habits) ?: return HELP
        // Обычный текст без даты и слов «задача/напомни» — заметка, как раньше
        if (r.kind == SmartInput.Kind.TASK && r.date == null && !Regex("^(задач|напомни|надо|нужно|сделать|/task)").containsMatchIn(t)) {
            if (t.endsWith("?")) return "Не понял вопрос 🤔\n\nСпросите: «что сегодня?», «сколько потратил за неделю?», «кто мне должен?», «что купить?» — или нажмите кнопку внизу."
            r = SmartInput.Result(SmartInput.Kind.NOTE, msg)
        }
        if (r.kind == SmartInput.Kind.EVENT) {
            // Событие сразу в календарь
            val cal = CalendarExport.target(ctx) ?: return "Нет доступа к календарю — откройте приложение и разрешите календарь"
            val z = ZoneId.systemDefault()
            val d = r.date ?: today
            val begin = r.time?.let { d.atTime(it).atZone(z).toInstant().toEpochMilli() } ?: d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val end = if (r.time != null) begin + 3600_000 else d.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val id = runCatching { CalendarRepository(ctx).insert(cal.id, r.text, "Из Telegram", begin, end, r.time == null, null, null, listOf(30)) }.getOrNull()
            return if (id != null) "📅 В календарь: ${r.text}\n${Fmt.dayTitle(d, today)}" + (r.time?.let { ", ${Fmt.time(it)}" } ?: ", весь день") else "Не удалось добавить в календарь"
        }
        var reply = ""
        acc.update { cur -> val res = SmartOps.apply(cur, r, today, "Telegram"); reply = res?.second.orEmpty(); res?.first ?: cur }
        return "✓ $reply"
    }
}
