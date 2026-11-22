package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/*
 * Долги и займы: кто кому должен, за что, сколько, под какой процент в месяц.
 * Суммы — в копейках. Проценты простые, начисляются на остаток долга каждый день
 * (ставка за месяц / средняя длина месяца). Платёж сначала гасит набежавшие проценты, потом сам долг.
 */

@Serializable
data class Counterparty(
    val id: String,
    val name: String,
    val company: Boolean = false,
    val phone: String = "",
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
)

enum class DebtDir(val title: String, val short: String) {
    I_OWE("Я должен", "должен я"),
    OWED_TO_ME("Мне должны", "должны мне"),
}

@Serializable
data class DebtPayment(val id: String, val date: String, val amount: Long, val note: String = "")

@Serializable
data class Debt(
    val id: String,
    val partyId: String,
    val dir: DebtDir = DebtDir.OWED_TO_ME,
    val title: String = "",
    val principal: Long = 0,
    val date: String,
    val due: String? = null,
    /** Процент в месяц, например 3.0 = 3% в месяц. 0 — без процентов. */
    val ratePerMonth: Double = 0.0,
    val payments: List<DebtPayment> = emptyList(),
    val closed: Boolean = false,
    val created: Long = System.currentTimeMillis(),
) {
    val start: LocalDate get() = LocalDate.parse(date)
    val dueDate: LocalDate? get() = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

data class DebtStatus(
    /** Остаток самого долга. */
    val principalLeft: Long,
    /** Набежавшие и ещё не оплаченные проценты. */
    val interestUnpaid: Long,
    /** Всего начислено процентов за всё время. */
    val interestTotal: Long,
    val paid: Long,
    /** Сколько нужно вернуть сегодня, чтобы закрыть долг. */
    val totalDue: Long,
    /** Сколько процентов набегает за месяц на текущий остаток. */
    val monthlyInterest: Long,
    /** Прогноз суммы к сроку возврата (если не платить до срока). */
    val atDue: Long?,
    val overdueDays: Long,
) {
    val settled get() = totalDue <= 0
}

object DebtMath {
    const val DAYS_IN_MONTH = 365.25 / 12

    fun interest(balance: Long, ratePerMonth: Double, days: Long): Double =
        if (balance <= 0 || ratePerMonth <= 0 || days <= 0) 0.0 else balance * ratePerMonth / 100.0 * days / DAYS_IN_MONTH

    fun status(d: Debt, today: LocalDate): DebtStatus {
        var principal = d.principal.toDouble()
        var unpaid = 0.0
        var accrued = 0.0
        var cursor = d.start
        val pays = d.payments.sortedBy { it.date }
        fun accrueTo(day: LocalDate) {
            val days = ChronoUnit.DAYS.between(cursor, day)
            if (days > 0) {
                val i = interest(principal.roundToLong(), d.ratePerMonth, days)
                unpaid += i
                accrued += i
                cursor = day
            }
        }
        for (p in pays) {
            val pd = runCatching { LocalDate.parse(p.date) }.getOrNull() ?: continue
            if (pd.isAfter(today)) continue
            accrueTo(pd)
            var amt = p.amount.toDouble()
            val toInterest = minOf(amt, unpaid)
            unpaid -= toInterest
            amt -= toInterest
            principal -= amt
        }
        accrueTo(today)
        val left = principal.coerceAtLeast(0.0)
        val total = left + unpaid
        val due = d.dueDate
        val atDue = due?.takeIf { it.isAfter(today) }?.let {
            (total + interest(left.roundToLong(), d.ratePerMonth, ChronoUnit.DAYS.between(today, it))).roundToLong()
        }
        val overdue = due?.let { ChronoUnit.DAYS.between(it, today) }?.takeIf { it > 0 && total > 0.5 } ?: 0
        return DebtStatus(
            principalLeft = left.roundToLong(),
            interestUnpaid = unpaid.roundToLong(),
            interestTotal = accrued.roundToLong(),
            paid = pays.filter { runCatching { !LocalDate.parse(it.date).isAfter(today) }.getOrDefault(false) }.sumOf { it.amount },
            totalDue = total.roundToLong().coerceAtLeast(0),
            monthlyInterest = interest(left.roundToLong(), d.ratePerMonth, 1).times(DAYS_IN_MONTH).roundToLong(),
            atDue = atDue,
            overdueDays = overdue,
        )
    }

    data class Summary(val owedToMe: Long, val iOwe: Long, val monthlyIn: Long, val monthlyOut: Long, val overdue: Int) {
        val net get() = owedToMe - iOwe
    }

    fun summary(w: Workspace, today: LocalDate): Summary {
        var a = 0L
        var b = 0L
        var mi = 0L
        var mo = 0L
        var od = 0
        w.debts.filter { !it.closed }.forEach { d ->
            val s = status(d, today)
            if (d.dir == DebtDir.OWED_TO_ME) { a += s.totalDue; mi += s.monthlyInterest } else { b += s.totalDue; mo += s.monthlyInterest }
            if (s.overdueDays > 0) od++
        }
        return Summary(a, b, mi, mo, od)
    }

    /** Сколько у человека/компании: плюс — должны мне, минус — должен я. */
    fun partyBalance(w: Workspace, partyId: String, today: LocalDate): Long =
        w.debts.filter { it.partyId == partyId && !it.closed }.sumOf {
            val s = status(it, today).totalDue
            if (it.dir == DebtDir.OWED_TO_ME) s else -s
        }

    /** Долги со сроком сегодня/завтра или просроченные — для уведомлений. */
    fun reminders(w: Workspace, today: LocalDate): List<String> = w.debts.filter { !it.closed }.mapNotNull { d ->
        val due = d.dueDate ?: return@mapNotNull null
        val s = status(d, today)
        if (s.settled) return@mapNotNull null
        val name = w.counterparties.firstOrNull { it.id == d.partyId }?.name ?: "?"
        val days = ChronoUnit.DAYS.between(today, due)
        val what = if (d.dir == DebtDir.I_OWE) "вернуть $name" else "$name должен вернуть"
        when {
            days == 0L -> "💸 Сегодня $what ${SplitMath.money(s.totalDue)}"
            days == 1L -> "💸 Завтра $what ${SplitMath.money(s.totalDue)}"
            days == 7L -> "💸 Через неделю $what ${SplitMath.money(s.totalDue)}"
            days < 0 && (-days) % 7 == 1L -> "⚠ Просрочено ${-days} дн.: $what ${SplitMath.money(s.totalDue)}"
            else -> null
        }
    }

    /** Накопления: остатки на счетах (кроме кредиток) + отложенное на цели. */
    fun savings(w: Workspace): Long {
        val acc = w.accounts.filter { !it.archived && it.includeInTotal && it.type != AccountType.CREDIT }
            .sumOf { FinanceMath.balance(it, w.transactions) }
        return acc + w.goals.filter { !it.done }.sumOf { it.saved }
    }
}

object DebtOps {
    fun upsertParty(w: Workspace, p: Counterparty) =
        if (w.counterparties.any { it.id == p.id }) w.copy(counterparties = w.counterparties.map { if (it.id == p.id) p else it })
        else w.copy(counterparties = w.counterparties + p)

    fun deleteParty(w: Workspace, id: String) =
        w.copy(counterparties = w.counterparties.filter { it.id != id }, debts = w.debts.filter { it.partyId != id })

    fun upsert(w: Workspace, d: Debt) =
        if (w.debts.any { it.id == d.id }) w.copy(debts = w.debts.map { if (it.id == d.id) d else it })
        else w.copy(debts = w.debts + d)

    fun delete(w: Workspace, id: String) = w.copy(debts = w.debts.filter { it.id != id })

    /** Платёж; если долг погашен полностью — закрываем. */
    fun pay(w: Workspace, debtId: String, p: DebtPayment, today: LocalDate): Workspace {
        val d = w.debts.firstOrNull { it.id == debtId } ?: return w
        val nd = d.copy(payments = d.payments + p)
        val closed = DebtMath.status(nd, today).totalDue <= 0
        return upsert(w, nd.copy(closed = closed))
    }
}
