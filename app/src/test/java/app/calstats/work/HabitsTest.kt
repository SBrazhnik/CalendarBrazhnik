package app.calstats.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val today = LocalDate.of(2026, 11, 16) // понедельник

    private fun w(h: Habit, vararg done: LocalDate) =
        Workspace(habits = listOf(h), habitLogs = done.map { HabitLog(h.id, it.toString(), h.target) })

    @Test fun streakNotBrokenByTodayNotYetDone() {
        val h = Habit("h", "Зарядка", created = "2026-11-01")
        val ws = w(h, today.minusDays(1), today.minusDays(2), today.minusDays(3))
        val v = HabitMath.values(ws.habitLogs, "h")
        assertEquals(3, HabitMath.streak(h, v, today))
        val ws2 = HabitOps.tap(ws, h, today)
        assertEquals(4, HabitMath.streak(h, HabitMath.values(ws2.habitLogs, "h"), today))
    }

    @Test fun unscheduledDaysSkipped() {
        // только будни
        val h = Habit("h", "Работа", days = listOf(1, 2, 3, 4, 5), created = "2026-11-01")
        val ws = w(h, LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12)) // пт, чт; сб/вс пропущены
        assertEquals(2, HabitMath.streak(h, HabitMath.values(ws.habitLogs, "h"), today))
    }

    @Test fun countHabitTapCycles() {
        val h = Habit("h", "Вода", target = 3)
        var ws = Workspace(habits = listOf(h))
        repeat(3) { ws = HabitOps.tap(ws, h, today) }
        assertEquals(true, HabitMath.done(h, HabitMath.values(ws.habitLogs, "h"), today))
        ws = HabitOps.tap(ws, h, today)
        assertEquals(0, HabitMath.values(ws.habitLogs, "h")[today] ?: 0)
    }

    @Test fun bestAndRate() {
        val h = Habit("h", "Чтение", created = "2026-11-01")
        val days = listOf(1, 2, 3, 5, 6).map { LocalDate.of(2026, 11, it) }.toTypedArray()
        val v = HabitMath.values(w(h, *days).habitLogs, "h")
        assertEquals(3, HabitMath.best(h, v, today))
        // с 1 по 15 ноября: 15 дней, выполнено 5
        assertEquals(33, HabitMath.rate(h, v, today))
    }
}
