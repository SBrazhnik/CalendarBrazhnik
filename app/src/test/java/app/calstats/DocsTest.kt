package app.calstats

import app.calstats.work.DocKind
import app.calstats.work.DocState
import app.calstats.work.DocsMath
import app.calstats.work.Document
import app.calstats.work.Recurring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocsTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun doc(exp: LocalDate?, remind: Int = 30) = Document("1", DocKind.OSAGO, expires = exp?.toString(), remindDays = remind)

    @Test fun states() {
        assertEquals(DocState.NONE, DocsMath.state(doc(null), today))
        assertEquals(DocState.OK, DocsMath.state(doc(today.plusDays(31)), today))
        assertEquals(DocState.SOON, DocsMath.state(doc(today.plusDays(30)), today))
        assertEquals(DocState.SOON, DocsMath.state(doc(today), today))
        assertEquals(DocState.EXPIRED, DocsMath.state(doc(today.minusDays(1)), today))
        val list = listOf(doc(today.plusDays(20)).copy(id = "a"), doc(today.minusDays(3)).copy(id = "b"), doc(today.plusDays(300)).copy(id = "c"))
        assertEquals(listOf("b", "a"), DocsMath.attention(list, today).map { it.id })
    }

    @Test fun remindDays() {
        assertTrue(DocsMath.remindToday(doc(today.plusDays(30)), today))
        assertTrue(DocsMath.remindToday(doc(today.plusDays(7)), today))
        assertTrue(DocsMath.remindToday(doc(today.plusDays(1)), today))
        assertTrue(DocsMath.remindToday(doc(today), today))
        assertFalse(DocsMath.remindToday(doc(today.plusDays(10)), today))
        assertFalse(DocsMath.remindToday(doc(today.plusDays(7)).copy(archived = true), today))
    }

    @Test fun passport() {
        assertEquals(LocalDate.of(2026, 3, 1), DocsMath.passportExpiry(LocalDate.of(2006, 3, 1), today))
        assertEquals(LocalDate.of(2031, 5, 2), DocsMath.passportExpiry(LocalDate.of(1986, 5, 2), today))
        assertNull(DocsMath.passportExpiry(LocalDate.of(1970, 1, 1), today))
    }

    @Test fun maskAndTrial() {
        assertEquals("•••• 6789", DocsMath.masked("45 12 456789"))
        assertEquals("123", DocsMath.masked("123"))
        val r = Recurring("r", "Кинопоиск", 29900, nextDate = today.plusDays(2).toString(), trialUntil = today.plusDays(2).toString())
        assertEquals(2L, DocsMath.trialLeft(r, today))
        assertNull(DocsMath.trialLeft(r.copy(trialUntil = today.minusDays(1).toString()), today))
    }
}
