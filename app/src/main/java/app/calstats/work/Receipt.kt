package app.calstats.work

import java.time.LocalDate

/** QR-код кассового чека (ФНС): t=20261124T1530&s=1250.00&fn=…&i=…&fp=…&n=1 */
object Receipt {
    data class Info(val date: LocalDate, val amount: Long, val refund: Boolean, val key: String)

    fun parse(raw: String): Info? {
        if (!raw.contains("t=") || !raw.contains("s=")) return null
        val m = raw.trim().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to it[1] } }.toMap()
        val t = m["t"] ?: return null
        val date = runCatching { LocalDate.of(t.take(4).toInt(), t.substring(4, 6).toInt(), t.substring(6, 8).toInt()) }.getOrNull() ?: return null
        val s = m["s"]?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val amount = Math.round(s * 100)
        if (amount <= 0) return null
        val key = "qr:" + listOf(m["fn"], m["i"], m["fp"]).joinToString(":") { it.orEmpty() }
        return Info(date, amount, m["n"] == "2" || m["n"] == "4", key)
    }
}
