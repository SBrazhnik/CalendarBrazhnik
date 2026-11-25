package app.calstats.work

import kotlinx.serialization.Serializable

/** Погода и курс доллара за день — копятся в истории. */
@Serializable
data class DayInfo(
    val date: String,
    val tMax: Double? = null,
    val tMin: Double? = null,
    val code: Int? = null,
    val usd: Double? = null,
)

object Weather {
    /** Код погоды WMO → значок и подпись. */
    fun describe(code: Int?): Pair<String, String> = when (code) {
        null -> "·" to ""
        0 -> "☀️" to "Ясно"
        1 -> "🌤" to "Преимущественно ясно"
        2 -> "⛅" to "Переменная облачность"
        3 -> "☁️" to "Пасмурно"
        45, 48 -> "🌫" to "Туман"
        51, 53, 55, 56, 57 -> "🌦" to "Морось"
        61, 63, 65, 66, 67 -> "🌧" to "Дождь"
        71, 73, 75, 77 -> "🌨" to "Снег"
        80, 81, 82 -> "🌦" to "Ливень"
        85, 86 -> "🌨" to "Снегопад"
        95, 96, 99 -> "⛈" to "Гроза"
        else -> "🌡" to ""
    }

    fun temp(t: Double?) = t?.let { val r = Math.round(it).toInt(); (if (r > 0) "+" else if (r < 0) "−" else "") + kotlin.math.abs(r) + "°" } ?: "—"

    /** Объединить свежие данные с историей: новые значения заменяют старые, пустые — не затирают. */
    fun merge(old: List<DayInfo>, fresh: List<DayInfo>): List<DayInfo> {
        val m = old.associateBy { it.date }.toMutableMap()
        for (f in fresh) {
            val o = m[f.date]
            m[f.date] = if (o == null) f else DayInfo(f.date, f.tMax ?: o.tMax, f.tMin ?: o.tMin, f.code ?: o.code, f.usd ?: o.usd)
        }
        return m.values.sortedBy { it.date }
    }

    /** Курс на каждый день: действует последнее установленное значение (выходные — курс пятницы/субботы). */
    fun fillRates(records: List<Pair<java.time.LocalDate, Double>>, from: java.time.LocalDate, to: java.time.LocalDate): Map<java.time.LocalDate, Double> {
        val sorted = records.sortedBy { it.first }
        val out = LinkedHashMap<java.time.LocalDate, Double>()
        var i = 0; var cur: Double? = null
        var d = from
        while (!d.isAfter(to)) {
            while (i < sorted.size && !sorted[i].first.isAfter(d)) { cur = sorted[i].second; i++ }
            cur?.let { out[d] = it }
            d = d.plusDays(1)
        }
        return out
    }
}
