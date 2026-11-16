package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/** Привычка: «делать» (зарядка, вода) или «не делать» (сладкое, курение). */
enum class HabitKind(val title: String) { DO("Делать"), QUIT("Бросить") }

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val emoji: String = "✅",
    val color: Int = PALETTE[1],
    val kind: HabitKind = HabitKind.DO,
    /** Сколько раз/единиц в день, например 8 стаканов воды. */
    val target: Int = 1,
    val unit: String = "",
    /** Дни недели 1..7 (пн..вс), в которые привычка нужна. */
    val days: List<Int> = (1..7).toList(),
    val archived: Boolean = false,
    val created: String = LocalDate.now().toString(),
) {
    val createdDate: LocalDate get() = runCatching { LocalDate.parse(created) }.getOrDefault(LocalDate.now())
    fun scheduled(d: LocalDate) = d.dayOfWeek.value in days
}

/** Отметка за день. Для «делать» — сколько сделано; для «бросить» — 1 сорвался, 0 удержался. */
@Serializable
data class HabitLog(val habitId: String, val date: String, val value: Int)

object HabitMath {
    fun values(logs: List<HabitLog>, habitId: String): Map<LocalDate, Int> =
        logs.filter { it.habitId == habitId }.mapNotNull { l -> runCatching { LocalDate.parse(l.date) }.getOrNull()?.let { it to l.value } }.toMap()

    fun done(h: Habit, v: Map<LocalDate, Int>, d: LocalDate) = (v[d] ?: 0) >= h.target

    /** Серия выполнений: не выполненный сегодня день серию не рвёт, пока он не закончился; дни вне графика пропускаются. */
    fun streak(h: Habit, v: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = if (done(h, v, today) || !h.scheduled(today)) today else today.minusDays(1)
        var n = 0
        while (!d.isBefore(h.createdDate.minusDays(400))) {
            if (h.scheduled(d)) {
                if (done(h, v, d)) n++ else break
            }
            d = d.minusDays(1)
            if (d.isBefore(v.keys.minOrNull() ?: today)) break
        }
        return n
    }

    fun best(h: Habit, v: Map<LocalDate, Int>, today: LocalDate): Int {
        val first = v.keys.minOrNull() ?: return 0
        var best = 0
        var cur = 0
        var d = first
        while (!d.isAfter(today)) {
            if (h.scheduled(d)) {
                if (done(h, v, d)) {
                    cur++
                    if (cur > best) best = cur
                } else if (d != today) cur = 0
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** Доля выполненных дней по графику за последние n дней (сегодня учитывается, только если уже выполнено). */
    fun rate(h: Habit, v: Map<LocalDate, Int>, today: LocalDate, n: Int = 30): Int {
        val start = maxOf(today.minusDays(n.toLong() - 1), h.createdDate)
        var planned = 0
        var ok = 0
        var d = start
        while (!d.isAfter(today)) {
            if (h.scheduled(d) && (d != today || done(h, v, d))) {
                planned++
                if (done(h, v, d)) ok++
            }
            d = d.plusDays(1)
        }
        return if (planned == 0) 0 else ok * 100 / planned
    }

    /** «Бросить» → карта «сорвался/удержался» в формате учёта алкоголя. */
    fun quitMap(v: Map<LocalDate, Int>): Map<LocalDate, Boolean> = v.mapValues { it.value > 0 }

    val weekdayAll = DayOfWeek.entries.map { it.value }
}

object HabitOps {
    fun upsert(w: Workspace, h: Habit) =
        if (w.habits.any { it.id == h.id }) w.copy(habits = w.habits.map { if (it.id == h.id) h else it }) else w.copy(habits = w.habits + h)

    fun delete(w: Workspace, id: String) = w.copy(habits = w.habits.filter { it.id != id }, habitLogs = w.habitLogs.filter { it.habitId != id })

    /** Записать значение за день (null — снять отметку). */
    fun set(w: Workspace, habitId: String, date: LocalDate, value: Int?): Workspace {
        val rest = w.habitLogs.filter { !(it.habitId == habitId && it.date == date.toString()) }
        return w.copy(habitLogs = if (value == null) rest else rest + HabitLog(habitId, date.toString(), value))
    }

    /** Нажатие на день у «делать»: +1 до цели, после цели — сброс. */
    fun tap(w: Workspace, h: Habit, date: LocalDate): Workspace {
        val cur = HabitMath.values(w.habitLogs, h.id)[date] ?: 0
        return set(w, h.id, date, if (cur >= h.target) null else cur + 1)
    }
}

/** Готовые привычки для быстрого старта. */
val HABIT_TEMPLATES = listOf(
    Habit("t1", "Вода", "💧", 0xFF3FA3B5.toInt(), HabitKind.DO, 8, "стаканов"),
    Habit("t2", "Зарядка", "🤸", 0xFF34553F.toInt()),
    Habit("t3", "Чтение", "📖", 0xFF7C6BD6.toInt(), target = 1, unit = "20 минут"),
    Habit("t4", "10 000 шагов", "🚶", 0xFF2E8B6B.toInt()),
    Habit("t5", "Сон до 23:00", "😴", 0xFF4F6BD8.toInt()),
    Habit("t6", "Медитация", "🧘", 0xFFC9963A.toInt()),
    Habit("t7", "Без сладкого", "🍬", 0xFFD9667A.toInt(), HabitKind.QUIT),
    Habit("t8", "Не курить", "🚭", 0xFF8D6E63.toInt(), HabitKind.QUIT),
)
