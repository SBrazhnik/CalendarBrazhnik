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
    /** Напоминание «ЧЧ:ММ», если привычка ещё не отмечена. */
    val remind: String? = null,
    /** Цель «N раз в неделю» вместо «каждый выбранный день». */
    val perWeek: Int? = null,
    /** Отмечать автоматически по Samsung Health: STEPS (шагов ≥ autoMin) или SLEEP (минут сна ≥ autoMin). */
    val auto: String? = null,
    val autoMin: Int = 0,
    /** «Заморозка»: дни болезни/отпуска — не нужны и серию не рвут. */
    val frozen: List<String> = emptyList(),
) {
    val createdDate: LocalDate get() = runCatching { LocalDate.parse(created) }.getOrDefault(LocalDate.now())
    fun scheduled(d: LocalDate) = d.dayOfWeek.value in days && d.toString() !in frozen
    fun isFrozen(d: LocalDate) = d.toString() in frozen
}

/** Награды за серии. */
object HabitRewards {
    val LEVELS = listOf(3 to "🌱", 7 to "🥉", 14 to "🥈", 30 to "🥇", 60 to "🏅", 100 to "🏆", 200 to "💎", 365 to "👑")
    fun badge(streak: Int): Pair<Int, String>? = LEVELS.lastOrNull { streak >= it.first }
    fun next(streak: Int): Pair<Int, String>? = LEVELS.firstOrNull { streak < it.first }
    /** Отметка дала новую награду? */
    fun reached(before: Int, after: Int): Pair<Int, String>? = LEVELS.firstOrNull { before < it.first && after >= it.first }
}

/** Отметка за день. Для «делать» — сколько сделано; для «бросить» — 1 сорвался, 0 удержался. */
@Serializable
data class HabitLog(val habitId: String, val date: String, val value: Int, val note: String = "")

object HabitMath {
    fun values(logs: List<HabitLog>, habitId: String): Map<LocalDate, Int> =
        logs.filter { it.habitId == habitId }.mapNotNull { l -> runCatching { LocalDate.parse(l.date) }.getOrNull()?.let { it to l.value } }.toMap()

    fun done(h: Habit, v: Map<LocalDate, Int>, d: LocalDate) = (v[d] ?: 0) >= h.target

    /** Сколько дней на неделе (пн–вс) с датой [d] привычка выполнена. */
    fun weekCount(h: Habit, v: Map<LocalDate, Int>, d: LocalDate): Int {
        val mon = d.minusDays(d.dayOfWeek.value - 1L)
        return (0..6).count { done(h, v, mon.plusDays(it.toLong())) }
    }

    /** Серия выполнений: не выполненный сегодня день серию не рвёт, пока он не закончился; дни вне графика пропускаются. */
    fun streak(h: Habit, v: Map<LocalDate, Int>, today: LocalDate): Int {
        h.perWeek?.let { goal ->
            // Серия в неделях: текущая неделя считается, если цель уже выполнена.
            var mon = today.minusDays(today.dayOfWeek.value - 1L)
            if (weekCount(h, v, mon) < goal) mon = mon.minusWeeks(1)
            var n = 0
            while (n < 520 && weekCount(h, v, mon) >= goal) { n++; mon = mon.minusWeeks(1) }
            return n
        }
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
        val note = w.habitLogs.firstOrNull { it.habitId == habitId && it.date == date.toString() }?.note.orEmpty()
        return w.copy(habitLogs = if (value == null && note.isEmpty()) rest else rest + HabitLog(habitId, date.toString(), value ?: 0, note))
    }

    /** Нажатие на день у «делать»: +1 до цели, после цели — сброс. */
    fun setNote(w: Workspace, habitId: String, date: LocalDate, note: String): Workspace {
        val cur = w.habitLogs.firstOrNull { it.habitId == habitId && it.date == date.toString() }
        val rest = w.habitLogs.filter { it !== cur }
        return w.copy(habitLogs = rest + (cur?.copy(note = note) ?: HabitLog(habitId, date.toString(), 0, note)))
    }

    /** Автоотметки по шагам и сну из Samsung Health. */
    fun applyAuto(w: Workspace, s: HealthSnapshot): Workspace {
        var out = w
        w.habits.filter { !it.archived && it.auto != null && it.autoMin > 0 }.forEach { h ->
            s.days.forEachIndexed { i, d ->
                val v = when (h.auto) {
                    "STEPS" -> s.steps.getOrNull(i); "SLEEP" -> s.sleepMin.getOrNull(i)
                    "WATER" -> w.waterLog.filter { it.date == d.toString() }.sumOf { it.ml }.toLong()
                    else -> null
                } ?: return@forEachIndexed
                val cur = HabitMath.values(out.habitLogs, h.id)[d] ?: 0
                if (v >= h.autoMin && cur < h.target) out = set(out, h.id, d, h.target)
            }
        }
        return out
    }

    /** Заморозить на n дней начиная с from (или разморозить, если уже заморожено). */
    fun freeze(w: Workspace, id: String, from: LocalDate, n: Int): Workspace = w.copy(habits = w.habits.map { h ->
        if (h.id != id) h else {
            val days = (0 until n).map { from.plusDays(it.toLong()).toString() }
            if (n == 0) h.copy(frozen = h.frozen.filter { LocalDate.parse(it).isBefore(from) }) else h.copy(frozen = (h.frozen + days).distinct())
        }
    })

    fun tap(w: Workspace, h: Habit, date: LocalDate): Workspace {
        val cur = HabitMath.values(w.habitLogs, h.id)[date] ?: 0
        return set(w, h.id, date, if (cur >= h.target) null else cur + 1)
    }
}

/** Готовые привычки для быстрого старта. */
val HABIT_TEMPLATES = listOf(
    Habit("t1", "Вода", "💧", 0xFF3FA3B5.toInt(), HabitKind.DO, 8, "стаканов"),
    Habit("t2", "Зарядка", "🤸", 0xFF1F3A2B.toInt()),
    Habit("t3", "Чтение", "📖", 0xFF7C6BD6.toInt(), target = 1, unit = "20 минут"),
    Habit("t4", "10 000 шагов", "🚶", 0xFF2E8B6B.toInt()),
    Habit("t5", "Сон до 23:00", "😴", 0xFF4F6BD8.toInt()),
    Habit("t6", "Медитация", "🧘", 0xFFC9963A.toInt()),
    Habit("t7", "Без сладкого", "🍬", 0xFFD9667A.toInt(), HabitKind.QUIT),
    Habit("t8", "Не курить", "🚭", 0xFF8D6E63.toInt(), HabitKind.QUIT),
)
