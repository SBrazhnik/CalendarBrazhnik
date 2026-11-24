package app.calstats

import app.calstats.work.Account
import app.calstats.work.HabitOps
import app.calstats.work.HealthMore
import app.calstats.work.HealthOps
import app.calstats.work.Issue
import app.calstats.work.JournalEntry
import app.calstats.work.ME
import app.calstats.work.Mood
import app.calstats.work.Note
import app.calstats.work.PALETTE
import app.calstats.work.Project
import app.calstats.work.ShopItem
import app.calstats.work.ShopList
import app.calstats.work.SmartInput
import app.calstats.work.SmartInput.Kind
import app.calstats.work.Transaction
import app.calstats.work.TxType
import app.calstats.work.WorkOps
import app.calstats.work.FinanceOps
import app.calstats.work.newId

/** Модуль, в который уйдёт запись быстрого ввода. */
fun SmartInput.Kind.tab(): Tab = when (this) {
    Kind.EXPENSE, Kind.INCOME -> Tab.FINANCE
    Kind.TASK -> Tab.TASKS
    Kind.EVENT -> Tab.CALENDAR
    Kind.WATER, Kind.WEIGHT, Kind.MOOD -> Tab.HEALTH
    Kind.SHOP -> Tab.SHOPPING
    Kind.NOTE -> Tab.NOTES
    Kind.HABIT -> Tab.HABITS
    Kind.JOURNAL -> Tab.JOURNAL
}

/** Чистое применение записи к данным (для приложения и для Telegram-бота). null — событие календаря (нужен экран). */
object SmartOps {
    fun apply(w0: app.calstats.work.Workspace, r: SmartInput.Result, today: java.time.LocalDate, source: String = "Быстрый ввод"): Pair<app.calstats.work.Workspace, String>? = when (r.kind) {
        Kind.EXPENSE, Kind.INCOME -> {
            val income = r.kind == Kind.INCOME
            var w = FinanceOps.ensureDefaults(w0)
            val acc = w.accounts.firstOrNull { !it.archived } ?: Account(newId(), "Карта").also { w = FinanceOps.upsertAccount(w, it) }
            val cats = w.finCategories.filter { !it.archived && it.income == income }
            val cat = cats.firstOrNull { it.name == r.category }
                ?: (if (income) cats.firstOrNull { r.text.contains(it.name.take(5), true) } ?: cats.firstOrNull()
                else cats.firstOrNull { it.name == "Другое" } ?: cats.lastOrNull())
            FinanceOps.upsertTx(w, Transaction(newId(), if (income) TxType.INCOME else TxType.EXPENSE, r.amount, acc.id,
                categoryId = cat?.id, date = today.toString(), note = r.text, source = source)) to
                ("${r.kind.title}: ${app.calstats.work.SplitMath.money(r.amount)}" + (cat?.name?.let { " · $it" } ?: "") + (if (r.text.isNotBlank()) " · ${r.text}" else ""))
        }
        Kind.TASK -> {
            var w = w0
            if (w.projects.none { !it.archived }) w = WorkOps.upsertProject(w, Project(newId(), "ЛИЧ", "Личное", PALETTE[0]))
            val project = w.projects.first { !it.archived }
            WorkOps.createIssue(w, Issue(newId(), project.id, 0, r.text.take(120), assigneeId = ME, due = r.date?.toString())).first to
                ("Задача: ${r.text}" + (r.date?.let { " · до ${Fmt.dayTitle(it, today)}" } ?: ""))
        }
        Kind.EVENT -> null
        Kind.WATER -> HealthOps.addWater(w0, today, r.ml) to "Вода: +${r.ml} мл, всего ${HealthOps.water(HealthOps.addWater(w0, today, r.ml), today)} мл"
        Kind.WEIGHT -> HealthOps.addWeight(w0, today, r.kg) to "Вес: ${r.kg} кг"
        Kind.MOOD -> HealthMore.setMood(w0, (w0.moods.firstOrNull { it.date == today.toString() } ?: Mood(today.toString(), r.mood)).copy(mood = r.mood)) to "Настроение: ${r.mood} из 5"
        Kind.SHOP -> {
            var w = w0
            val list = w.shopLists.firstOrNull { !it.archived } ?: ShopList(newId(), "Продукты", "🛒").also { w = w.copy(shopLists = w.shopLists + it) }
            w.copy(shopItems = w.shopItems + r.items.map { (n, q) -> ShopItem(newId(), list.id, n, q) }) to "В покупки: ${r.items.joinToString(", ") { it.first }}"
        }
        Kind.NOTE -> WorkOps.upsertNote(w0, Note(newId(), body = r.text, source = source)) to "Заметка сохранена"
        Kind.HABIT -> {
            val h = w0.habits.firstOrNull { it.id == r.habitId }
            (if (h != null) HabitOps.set(w0, h.id, today, h.target) else w0) to "Привычка отмечена: ${r.text}"
        }
        Kind.JOURNAL -> {
            val cur = w0.journal.firstOrNull { it.date == today.toString() } ?: JournalEntry(today.toString())
            val e = cur.copy(text = listOf(cur.text, r.text).filter { it.isNotBlank() }.joinToString("\n"), updated = System.currentTimeMillis())
            w0.copy(journal = w0.journal.filter { it.date != e.date } + e) to "Записано в дневник"
        }
    }
}

/** Сохраняет разобранную запись. Возвращает текст подтверждения. */
fun MainViewModel.applySmart(r: SmartInput.Result): String {
    val today = this.today
    if (!isEnabled(r.kind.tab())) setModuleEnabled(r.kind.tab(), true)
    if (r.kind == Kind.EVENT) {
        push(Screen.Edit(presetDate = r.date ?: today, presetStart = r.time?.let { (r.date ?: today).atTime(it) }, presetAllDay = r.time == null, presetTitle = r.text))
        return "Проверьте событие и сохраните"
    }
    var msg = ""
    updateWork { w -> val res = SmartOps.apply(w, r, today); msg = res?.second.orEmpty(); res?.first ?: w }
    return msg
}
