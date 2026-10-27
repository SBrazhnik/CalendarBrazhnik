package app.calstats.work

import kotlin.math.abs
import kotlin.math.min

/** Расчёты для общих расходов: доли, балансы и минимальный список переводов. */
object SplitMath {

    /** Делит total копеек пропорционально весам; остаток раздаётся по наибольшим дробным частям. */
    fun distribute(total: Long, weights: Map<String, Long>): Map<String, Long> {
        val positive = weights.filterValues { it > 0 }
        val sum = positive.values.sum()
        if (positive.isEmpty() || sum <= 0L) return emptyMap()
        val base = LinkedHashMap<String, Long>()
        val rema = ArrayList<Pair<String, Long>>()
        var used = 0L
        for ((k, w) in positive) {
            val exact = total * w
            val part = Math.floorDiv(exact, sum)
            base[k] = part
            used += part
            rema += k to Math.floorMod(exact, sum)
        }
        var left = total - used
        for ((k, _) in rema.sortedByDescending { it.second }) {
            if (left <= 0) break
            base[k] = base.getValue(k) + 1
            left--
        }
        return base
    }

    /** Сколько каждый участник должен по этому расходу (сумма = amount). */
    fun owed(e: Expense): Map<String, Long> = when (e.mode) {
        SplitMode.EQUAL -> distribute(e.amount, e.shares.keys.associateWith { 1L })
        SplitMode.SHARES -> distribute(e.amount, e.shares)
        SplitMode.EXACT -> e.shares.filterValues { it != 0L }
        SplitMode.ITEMS -> {
            val sub = HashMap<String, Long>()
            for (item in e.items) {
                distribute(item.amount, item.personIds.associateWith { 1L }).forEach { (p, v) ->
                    sub[p] = (sub[p] ?: 0L) + v
                }
            }
            // Обслуживание/чаевые и расхождение с итогом — пропорционально позициям.
            val itemsTotal = sub.values.sum()
            val extra = e.amount - itemsTotal
            if (extra != 0L && itemsTotal > 0L) {
                distribute(extra, sub).forEach { (p, v) -> sub[p] = (sub[p] ?: 0L) + v }
            }
            sub
        }
    }

    fun itemsTotal(items: List<ReceiptItem>, servicePercent: Int): Long {
        val base = items.sumOf { it.amount }
        return base + Math.round(base * servicePercent / 100.0)
    }

    /** Баланс: заплатил − должен. Плюс — ему должны, минус — он должен. */
    fun balances(expenses: List<Expense>): Map<String, Long> {
        val net = HashMap<String, Long>()
        for (e in expenses) {
            net[e.payerId] = (net[e.payerId] ?: 0L) + e.amount
            owed(e).forEach { (p, v) -> net[p] = (net[p] ?: 0L) - v }
        }
        return net
    }

    data class Transfer(val from: String, val to: String, val amount: Long)

    /** Минимум переводов: самый большой должник платит самому большому кредитору. */
    fun settle(net: Map<String, Long>): List<Transfer> {
        val debtors = net.filterValues { it < 0 }.map { it.key to -it.value }.toMutableList()
        val creditors = net.filterValues { it > 0 }.map { it.key to it.value }.toMutableList()
        val out = ArrayList<Transfer>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.second }
            creditors.sortByDescending { it.second }
            val (d, da) = debtors[0]
            val (c, ca) = creditors[0]
            val x = min(da, ca)
            if (x > 0) out += Transfer(d, c, x)
            if (da - x <= 0) debtors.removeAt(0) else debtors[0] = d to (da - x)
            if (ca - x <= 0) creditors.removeAt(0) else creditors[0] = c to (ca - x)
        }
        return out
    }

    /** «1 234,50 ₽»; копейки скрываются, если их нет. */
    fun money(kop: Long, sign: Boolean = false): String {
        val neg = kop < 0
        val a = abs(kop)
        val rub = a / 100
        val k = a % 100
        val rubStr = rub.toString().reversed().chunked(3).joinToString(" ").reversed()
        val body = if (k == 0L) rubStr else "$rubStr,${k.toString().padStart(2, '0')}"
        val prefix = when {
            neg -> "−"
            sign && kop > 0 -> "+"
            else -> ""
        }
        return "$prefix$body ₽"
    }

    /** «1234,5» / «1 234.50» → копейки. */
    fun parse(text: String): Long? {
        val t = text.replace(" ", "").replace(" ", "").replace("₽", "").replace(',', '.')
        if (t.isEmpty()) return null
        val v = t.toBigDecimalOrNull() ?: return null
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun plain(kop: Long): String {
        val r = kop / 100
        val k = abs(kop % 100)
        return if (k == 0L) r.toString() else "$r,${k.toString().padStart(2, '0')}"
    }
}
