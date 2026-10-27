package app.calstats.work

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitMathTest {
    private fun exp(amount: Long, payer: String, mode: SplitMode, shares: Map<String, Long>, items: List<ReceiptItem> = emptyList()) =
        Expense("x", "b", "t", amount, payer, "2026-10-27", mode, shares, items)

    @Test fun equalSplitSumsExactly() {
        val o = SplitMath.owed(exp(1000, "a", SplitMode.EQUAL, mapOf("a" to 1L, "b" to 1L, "c" to 1L)))
        assertEquals(1000L, o.values.sum())
        assertEquals(setOf(333L, 334L), o.values.toSet())
    }

    @Test fun restaurantScenario() {
        // A платит 3000 за троих, B платит 900 за A и B
        val e1 = exp(3000, "A", SplitMode.EQUAL, mapOf("A" to 1L, "B" to 1L, "C" to 1L))
        val e2 = exp(900, "B", SplitMode.EQUAL, mapOf("A" to 1L, "B" to 1L))
        val net = SplitMath.balances(listOf(e1, e2))
        assertEquals(1550L, net["A"])
        assertEquals(-550L, net["B"])
        assertEquals(-1000L, net["C"])
        val t = SplitMath.settle(net)
        assertEquals(2, t.size)
        assertEquals(1550L, t.sumOf { it.amount })
        // после переводов все в нуле
        val after = SplitMath.balances(listOf(e1, e2) + t.map { exp(it.amount, it.from, SplitMode.EXACT, mapOf(it.to to it.amount)) })
        after.values.forEach { assertEquals(0L, it) }
    }

    @Test fun itemsWithService() {
        val items = listOf(
            ReceiptItem("1", "Пицца", 1000, listOf("A", "B")),
            ReceiptItem("2", "Вино", 600, listOf("B")),
        )
        val total = SplitMath.itemsTotal(items, 10)
        assertEquals(1760L, total)
        val o = SplitMath.owed(exp(total, "A", SplitMode.ITEMS, emptyMap(), items))
        assertEquals(1760L, o.values.sum())
        assertEquals(550L, o["A"])
        assertEquals(1210L, o["B"])
    }

    @Test fun moneyFormat() {
        assertEquals("1 234,50 ₽", SplitMath.money(123450))
        assertEquals(123450L, SplitMath.parse("1 234,5"))
    }
}
