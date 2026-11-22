package app.calstats

import app.calstats.work.QuickParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickParseTest {
    // воскресенье, 22 ноября 2026, 12:00
    private val now = LocalDateTime.of(2026, 11, 22, 12, 0)

    @Test fun tomorrow() {
        val r = QuickParse.parse("завтра в 15 к врачу", now)!!
        assertEquals(LocalDate.of(2026, 11, 23), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
        assertEquals("К врачу", r.title)
    }

    @Test fun weekdayAndMinutes() {
        val r = QuickParse.parse("в пятницу в 19:30 кино", now)!!
        assertEquals(LocalDate.of(2026, 11, 27), r.date)
        assertEquals(LocalTime.of(19, 30), r.time)
        assertEquals("Кино", r.title)
    }

    @Test fun monthName() {
        val r = QuickParse.parse("ужин 25 декабря в 18", now)!!
        assertEquals(LocalDate.of(2026, 12, 25), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
        assertEquals("Ужин", r.title)
    }

    @Test fun eveningAndAllDay() {
        assertEquals(LocalTime.of(21, 0), QuickParse.parse("сегодня в 9 вечера тренировка", now)!!.time)
        val r = QuickParse.parse("послезавтра стрижка", now)!!
        assertEquals(LocalDate.of(2026, 11, 24), r.date)
        assertNull(r.time)
        assertEquals(LocalDateTime.of(2026, 11, 22, 14, 0), QuickParse.parse("через 2 часа созвон", now)!!.start)
        assertNull(QuickParse.parse("просто текст", now))
    }
}
