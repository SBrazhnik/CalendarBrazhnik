package app.calstats

import app.calstats.work.Debt
import app.calstats.work.DebtDir
import app.calstats.work.DebtMath
import app.calstats.work.DebtOps
import app.calstats.work.DebtPayment
import app.calstats.work.Counterparty
import app.calstats.work.HealthStats
import app.calstats.work.FoodEntry
import app.calstats.work.Meal
import app.calstats.work.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtsTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test fun noInterest() {
        val d = Debt("1", "p", DebtDir.OWED_TO_ME, principal = 100_000, date = start.toString())
        val s = DebtMath.status(d, start.plusDays(100))
        assertEquals(100_000L, s.totalDue)
        assertEquals(0L, s.interestTotal)
    }

    @Test fun monthlyInterest() {
        // 100 000 ₽ под 3% в месяц: за месяц ≈ 3 000 ₽
        val d = Debt("1", "p", DebtDir.OWED_TO_ME, principal = 10_000_000, date = start.toString(), ratePerMonth = 3.0)
        val s = DebtMath.status(d, start.plusDays(365))
        // за год простыми процентами ≈ 36% = 36 000 ₽
        assertEquals(13_597_500.0, s.totalDue.toDouble(), 5_000.0)
        assertEquals(300_000.0, s.monthlyInterest.toDouble(), 100.0)
    }

    @Test fun paymentsCoverInterestFirst() {
        val d = Debt("1", "p", DebtDir.I_OWE, principal = 10_000_000, date = start.toString(), ratePerMonth = 3.0,
            payments = listOf(DebtPayment("a", start.plusDays(61).toString(), 1_000_000)))
        val s = DebtMath.status(d, start.plusDays(61))
        // за 61 день набежало ≈ 6 012 ₽; платёж 10 000 ₽ гасит проценты и ≈ 3 988 ₽ долга
        assertEquals(0L, s.interestUnpaid)
        assertEquals(9_601_000.0, s.principalLeft.toDouble(), 1_000.0)
    }

    @Test fun summaryAndClose() {
        var w = Workspace()
        w = DebtOps.upsertParty(w, Counterparty("p", "Иван"))
        w = DebtOps.upsert(w, Debt("a", "p", DebtDir.OWED_TO_ME, principal = 500_000, date = start.toString()))
        w = DebtOps.upsert(w, Debt("b", "p", DebtDir.I_OWE, principal = 200_000, date = start.toString()))
        val sum = DebtMath.summary(w, start)
        assertEquals(300_000L, sum.net)
        assertEquals(300_000L, DebtMath.partyBalance(w, "p", start))
        w = DebtOps.pay(w, "a", DebtPayment("x", start.toString(), 500_000), start)
        assertTrue(w.debts.first { it.id == "a" }.closed)
    }

    @Test fun healthStatsCompare() {
        val today = LocalDate.of(2026, 11, 22)
        val food = (0 until 14).map { i ->
            FoodEntry("f$i", today.minusDays(i.toLong()).toString(), Meal.LUNCH, "x", 100, if (i < 7) 2000.0 else 1600.0, 100.0, 50.0, 200.0)
        }
        val r = HealthStats.compute(Workspace(foodLog = food), null, today, 7)
        val kcal = r.metrics.first { it.key == "kcal" }
        assertEquals(2000.0, kcal.cur!!, 0.01)
        assertEquals(25.0, kcal.deltaPct!!, 0.01)
        assertEquals(7, r.loggedDays)
    }
}
