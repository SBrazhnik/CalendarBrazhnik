package app.calstats

import app.calstats.work.Habit
import app.calstats.work.SmartInput
import app.calstats.work.SmartInput.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SmartInputTest {
    private val now = LocalDateTime.of(2026, 11, 23, 12, 0)
    private fun p(s: String, h: List<Habit> = emptyList()) = SmartInput.parse(s, now, h)!!

    @Test fun expense() {
        val r = p("потратил 450 на такси")
        assertEquals(Kind.EXPENSE, r.kind); assertEquals(45000L, r.amount); assertEquals("Такси", r.text); assertEquals("Транспорт", r.category)
        val r2 = p("кофе 250 руб")
        assertEquals(Kind.EXPENSE, r2.kind); assertEquals(25000L, r2.amount); assertEquals("Кафе и рестораны", r2.category)
        assertEquals(150000L, p("заплатил 1,5к за интернет").amount)
    }
    @Test fun income() {
        val r = p("получил зарплату 85000")
        assertEquals(Kind.INCOME, r.kind); assertEquals(8500000L, r.amount)
    }
    @Test fun water() {
        assertEquals(500, p("выпил 2 стакана воды").ml)
        assertEquals(300, p("вода 300 мл").ml)
    }
    @Test fun weightMood() {
        assertEquals(82.5, p("вес 82,5").kg, 0.01)
        assertEquals(5, p("настроение отличное").mood)
    }
    @Test fun shop() {
        val r = p("купить молоко, хлеб и яйца 10 шт")
        assertEquals(Kind.SHOP, r.kind); assertEquals(3, r.items.size)
    }
    @Test fun eventTask() {
        val e = p("завтра в 15 встреча с Иваном")
        assertEquals(Kind.EVENT, e.kind); assertEquals(now.toLocalDate().plusDays(1), e.date)
        val t = p("напомни позвонить в банк")
        assertEquals(Kind.TASK, t.kind); assertEquals("Позвонить в банк", t.text)
    }
    @Test fun habit() {
        val h = Habit("h1", "Зарядка")
        val r = p("сделал зарядку", listOf(h))
        assertEquals(Kind.HABIT, r.kind); assertEquals("h1", r.habitId)
    }
}
