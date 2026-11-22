package app.calstats

import app.calstats.work.BankNotify
import app.calstats.work.LoanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankNotifyTest {
    private fun p(text: String, title: String? = null) = BankNotify.parse("com.idamob.tinkoff.android", title, text, "2026-11-22", "x")

    @Test fun purchase() {
        val t = p("Покупка 1 250,50 ₽. Пятёрочка. Доступно 34 000 ₽")!!
        assertEquals(125050L, t.amount)
        assertEquals(false, t.income)
        assertEquals("Пятёрочка", t.merchant)
    }

    @Test fun income() {
        val t = p("Пополнение +5 000 ₽ от Ивана И. Баланс 40 000 ₽")!!
        assertEquals(500000L, t.amount)
        assertTrue(t.income)
    }

    @Test fun ignoreCodes() {
        assertNull(p("Код для входа: 1234. Никому не сообщайте"))
        assertNull(BankNotify.parse("org.telegram.messenger", null, "Покупка 100 ₽", "2026-11-22", "y"))
    }

    @Test fun loan() {
        // 1 000 000 ₽ на 12 мес под 12% годовых: платёж ≈ 88 849 ₽
        val pay = LoanMath.payment(100_000_000, 12.0, 12)
        assertEquals(8_884_879.0, pay.toDouble(), 100.0)
        val s = LoanMath.schedule(100_000_000, 12.0, 12)
        assertEquals(0L, s.last().left)
        assertEquals(1_000_000L, s.first().interest)
    }
}
