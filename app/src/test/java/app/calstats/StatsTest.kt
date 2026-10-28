package app.calstats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = LocalDateTime.of(2026, 10, 28, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun ev(from: LocalDateTime, to: LocalDateTime, title: String = "Тренировка", cal: Long = 1) = EventInstance(
        1, title, from.atZone(zone).toInstant().toEpochMilli(), to.atZone(zone).toInstant().toEpochMilli(), false, 0xFF0000, cal,
    )

    private fun allDay(from: LocalDate, toIncl: LocalDate) = EventInstance(
        2, "Отпуск", from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        toIncl.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, 0xFF0000, 1,
    )

    private fun month(y: Int, m: Int) = Stats.window(LocalDate.of(y, m, 1), LocalDate.of(y, m, 1).plusMonths(1), now, false, zone)

    @Test fun monthsAddUpToYear() {
        val list = listOf(
            ev(LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 12, 0)),
            ev(LocalDateTime.of(2026, 1, 31, 23, 0), LocalDateTime.of(2026, 2, 1, 1, 0)), // через границу месяцев
            ev(LocalDateTime.of(2026, 3, 5, 9, 0), LocalDateTime.of(2026, 3, 5, 10, 30)),
            allDay(LocalDate.of(2026, 4, 29), LocalDate.of(2026, 5, 2)),
        )
        val months = (1..12).map { Stats.aggregate(list, month(2026, it)) }
        assertEquals(180L, months[0].minutes) // 2ч + 1ч до полуночи
        assertEquals(2, months[0].days)
        assertEquals(60L, months[1].minutes)
        assertEquals(90L, months[2].minutes)
        assertEquals(2, months[3].days) // 29–30 апреля
        assertEquals(2, months[4].days) // 1–2 мая
        val year = Stats.aggregate(list, Stats.window(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), now, false, zone))
        assertEquals(year.minutes, months.sumOf { it.minutes })
        assertEquals(year.days, months.sumOf { it.days })
    }

    @Test fun overlapsNotDoubled() {
        val list = listOf(
            ev(LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 12, 0)),
            ev(LocalDateTime.of(2026, 6, 1, 11, 0), LocalDateTime.of(2026, 6, 1, 13, 0)),
        )
        assertEquals(180L, Stats.aggregate(list, month(2026, 6)).minutes)
    }

    @Test fun duplicatesAcrossCalendarsCountedOnce() {
        val a = ev(LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 12, 0), cal = 1)
        val b = a.copy(calendarId = 2, eventId = 99)
        val groups = Stats.buildGroups(listOf(a, b), AppData(), setOf(1L, 2L))
        assertEquals(1, groups.single().instances.size)
    }

    @Test fun futureNotCounted() {
        val list = listOf(ev(LocalDateTime.of(2026, 11, 3, 10, 0), LocalDateTime.of(2026, 11, 3, 12, 0)))
        assertEquals(0, Stats.aggregate(list, month(2026, 11)).events)
    }
}
