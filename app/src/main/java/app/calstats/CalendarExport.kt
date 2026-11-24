package app.calstats

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.calstats.work.DebtDir
import app.calstats.work.DebtMath
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.Status
import app.calstats.work.Workspace
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Даты из модулей → события календаря: сроки долгов, документов, платежи, прививки питомцев, ТО, цели, задачи.
 * События создаются в выбранном календаре (Google синхронизирует их сам) с выбранным направлением (цветом)
 * и напоминанием; при изменении в приложении — обновляются, при удалении — удаляются.
 */
object CalendarExport {
    enum class Source(val title: String, val hint: String, val defaultOn: Boolean) {
        DEBTS("Долги", "дата возврата", true),
        DOCS("Документы", "окончание срока", true),
        PAYMENTS("Платежи и подписки", "ближайшее списание", true),
        PETS("Питомцы", "прививки, обработки", true),
        CAR("Авто", "следующее ТО", true),
        GOALS("Цели", "срок цели", false),
        TASKS("Задачи", "срок задачи", false),
        NOTES("Заметки-напоминания", "время напоминания (если в заметке включено «В календарь»)", true),
    }

    data class Item(val source: Source, val key: String, val date: LocalDate, val title: String, val desc: String, val remindDays: List<Int> = listOf(1), val at: Long? = null)

    class Settings(ctx: Context) {
        private val p = ctx.getSharedPreferences("cal_export", Context.MODE_PRIVATE)
        var calendarKey: String?
            get() = p.getString("calendar", null)
            set(v) = p.edit().putString("calendar", v).apply()
        fun on(s: Source) = p.getBoolean("on_" + s.name, s.defaultOn)
        fun setOn(s: Source, v: Boolean) = p.edit().putBoolean("on_" + s.name, v).apply()
        fun colorKey(s: Source): String? = p.getString("color_" + s.name, null)
        fun setColorKey(s: Source, k: String?) = p.edit().putString("color_" + s.name, k).apply()
        internal fun mapping(): Map<String, String> = p.all.filterKeys { it.startsWith("m_") }.mapKeys { it.key.removePrefix("m_") }.mapValues { it.value.toString() }
        internal fun put(key: String, v: String?) = p.edit().apply { if (v == null) remove("m_$key") else putString("m_$key", v) }.apply()
    }

    fun items(w: Workspace, today: LocalDate, s: Settings): List<Item> {
        val out = ArrayList<Item>()
        if (s.on(Source.DEBTS)) w.debts.filter { !it.closed && it.dueDate != null }.forEach { d ->
            val who = w.counterparties.firstOrNull { it.id == d.partyId }?.name ?: "?"
            val st = DebtMath.status(d, today)
            val sum = SplitMath.money(st.atDue ?: st.totalDue)
            out += Item(
                Source.DEBTS, "debt:" + d.id, d.dueDate!!,
                if (d.dir == DebtDir.I_OWE) "💸 Вернуть: $who — $sum" else "💸 $who возвращает $sum",
                listOfNotNull(d.title.takeIf { it.isNotBlank() }, d.ratePerMonth.takeIf { it > 0 }?.let { "под $it% в месяц" }).joinToString(", "),
                listOf(1, 3),
            )
        }
        if (s.on(Source.DOCS)) w.documents.filter { !it.archived && it.expiry != null }.forEach { d ->
            out += Item(Source.DOCS, "doc:" + d.id, d.expiry!!, "${d.kind.emoji} Истекает: ${d.name}" + d.owner.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                d.issuer, listOf(1, d.remindDays.coerceAtLeast(2)))
        }
        if (s.on(Source.PAYMENTS)) w.recurrings.filter { it.active }.forEach { r ->
            out += Item(Source.PAYMENTS, "pay:" + r.id, r.next, "🔁 ${r.name} — ${SplitMath.money(r.amount)}", "", listOf(1))
        }
        if (s.on(Source.PETS)) app.calstats.work.Pets.upcoming(w.petEvents, today, 3650).forEach { e ->
            val p = w.pets.firstOrNull { it.id == e.petId }?.name.orEmpty()
            out += Item(Source.PETS, "pet:" + e.id, LocalDate.parse(e.next), "${e.type.emoji} $p: ${e.type.title.lowercase()}", e.note, listOf(1))
        }
        if (s.on(Source.CAR)) w.cars.forEach { c ->
            app.calstats.work.CarMath.nextService(c, w.carEvents, today)?.date?.let { d ->
                out += Item(Source.CAR, "car:" + c.id, d, "🔧 ТО: ${c.name}", "", listOf(7))
            }
        }
        if (s.on(Source.GOALS)) w.lifeGoals.filter { !it.done && it.deadline != null }.forEach { g ->
            out += Item(Source.GOALS, "goal:" + g.id, LocalDate.parse(g.deadline), "🎯 Срок цели: ${g.title}", g.why, listOf(7))
        }
        if (s.on(Source.TASKS)) w.issues.filter { it.assigneeId == ME && it.status != Status.DONE && it.dueDate != null }.forEach { i ->
            out += Item(Source.TASKS, "task:" + i.id, i.dueDate!!, "📌 ${i.title}", i.description, listOf(1))
        }
        if (s.on(Source.NOTES)) w.notes.filter { it.toCalendar && it.remindAt != null && !it.remindDone && !it.archived }.forEach { n ->
            val at = n.remindAt!!
            out += Item(Source.NOTES, "note:" + n.id, java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                "📝 " + n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.take(80), n.body.take(800), listOf(0), at = at)
        }
        return out.filter { !it.date.isBefore(today.minusDays(30)) }
    }

    private fun canWrite(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Календарь для дат: выбранный, иначе основной Google, иначе первый доступный для записи. */
    fun target(ctx: Context, s: Settings = Settings(ctx)): CalendarInfo? {
        val cals = runCatching { CalendarRepository(ctx).calendars() }.getOrDefault(emptyList()).filter { it.writable }
        return cals.firstOrNull { it.key == s.calendarKey }
            ?: cals.firstOrNull { it.accountType == "com.google" && it.primary }
            ?: cals.firstOrNull { it.accountType == "com.google" }
            ?: cals.firstOrNull()
    }

    /** Привести события в календаре к текущим данным. Возвращает число изменений. */
    @Synchronized
    fun sync(ctx: Context, w: Workspace, today: LocalDate = LocalDate.now()): Int {
        if (!canWrite(ctx)) return 0
        val s = Settings(ctx)
        val repo = CalendarRepository(ctx)
        val cal = target(ctx, s) ?: return 0
        val want = items(w, today, s).associateBy { it.key }
        val have = s.mapping()
        var changes = 0
        // Удалить события, которых больше нет (долг закрыт, источник выключен, календарь сменили).
        for ((key, v) in have) {
            val (id, _, calId) = v.split("|").let { Triple(it.getOrNull(0)?.toLongOrNull(), it.getOrNull(1), it.getOrNull(2)?.toLongOrNull()) }
            if (key !in want || calId != cal.id) {
                if (id != null) runCatching { repo.delete(id) }
                s.put(key, null)
                changes++
            }
        }
        val mapping = s.mapping()
        for (it in want.values) {
            val timed = it.at != null
            val begin = it.at ?: it.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val end = it.at?.plus(30 * 60_000L) ?: it.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            // Напоминание за N дней в 9:00 (для событий на весь день минуты считаются от полуночи); для заметок — в момент события.
            val rem = if (timed) listOf(0) else it.remindDays.map { d -> d * 1440 - 540 }
            val color = s.colorKey(it.source)
            val desc = (it.desc.takeIf { d -> d.isNotBlank() }?.let { d -> "$d\n\n" } ?: "") + "Из «Зелёного ритма»: ${it.source.title}"
            val hash = listOf(it.title, it.date, it.at, desc, color, rem).hashCode().toString()
            val cur = mapping[it.key]?.split("|")
            val id = cur?.getOrNull(0)?.toLongOrNull()
            if (id != null && cur.getOrNull(1) == hash && repo.event(id) != null) continue
            val ok = id != null && repo.event(id) != null && repo.update(id, it.title, desc, begin, end, !timed, color, null, rem)
            val newId = if (ok) id else repo.insert(cal.id, it.title, desc, begin, end, !timed, color, null, rem)
            if (newId != null) {
                s.put(it.key, "$newId|$hash|${cal.id}")
                changes++
            }
        }
        if (changes > 0) runCatching { repo.requestSync(listOf(cal)) }
        return changes
    }

    /** Удалить из календаря всё, что туда добавило приложение. */
    fun removeAll(ctx: Context) {
        if (!canWrite(ctx)) return
        val s = Settings(ctx)
        val repo = CalendarRepository(ctx)
        s.mapping().forEach { (k, v) -> v.substringBefore('|').toLongOrNull()?.let { runCatching { repo.delete(it) } }; s.put(k, null) }
    }
}
