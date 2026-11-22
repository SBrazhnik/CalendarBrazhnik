package app.calstats.work

import java.time.LocalDate
import java.time.LocalDateTime

/** Разбор распознанного текста документа и QR-кода кассового чека. */
object DocScan {
    data class Found(val numbers: List<String>, val dates: List<LocalDate>) {
        /** Дата выдачи — самая ранняя, срок — самая поздняя из будущих. */
        fun issued(today: LocalDate) = dates.filter { !it.isAfter(today) }.maxOrNull()
        fun expires(today: LocalDate) = dates.filter { it.isAfter(today) }.maxOrNull()
    }

    private val DATE = Regex("""\b(\d{2})[.\-/](\d{2})[.\-/](\d{4})\b""")
    private val NUM = Regex("""\b(\d{2}\s?\d{2}\s?№?\s?\d{6}|\d{2}\s?[A-ZА-Я]{2}\s?\d{6}|\d{3}-\d{3}-\d{3}\s?\d{2}|\d{10,12}|[A-ZА-Я]{3}\d{10})\b""")

    fun parse(text: String): Found {
        val dates = DATE.findAll(text).mapNotNull { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }.filter { it.year in 1900..2100 }.distinct().toList()
        val nums = NUM.findAll(text).map { it.value.replace("№", "").replace(Regex("\\s+"), " ").trim() }
            .filter { n -> dates.none { d -> n.replace(" ", "").contains(d.toString().replace("-", "")) } }
            .distinct().take(5).toList()
        return Found(nums, dates)
    }

    data class Receipt(val time: LocalDateTime, val sum: Long, val fn: String?, val raw: String)

    /** QR чека ФНС: t=20261122T1530&s=1250.00&fn=...&i=...&fp=...&n=1 */
    fun receipt(qr: String): Receipt? {
        val p = qr.trim().split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to it[1] } }.toMap()
        val t = p["t"] ?: return null
        val s = p["s"] ?: return null
        val m = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})""").find(t) ?: return null
        val time = runCatching {
            LocalDateTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
        }.getOrNull() ?: return null
        val sum = BankImport.parseAmount(s) ?: return null
        return Receipt(time, sum, p["fn"], qr)
    }
}
