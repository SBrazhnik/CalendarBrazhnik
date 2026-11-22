package app.calstats.work

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Разбор события, написанного обычным текстом:
 * «завтра в 15 к врачу», «в пятницу в 19:30 кино», «25 ноября в 18 ужин», «через 2 часа созвон»,
 * «сегодня в 9 вечера тренировка», «послезавтра стрижка» (весь день).
 */
object QuickParse {
    data class Result(val title: String, val date: LocalDate, val time: LocalTime?) {
        val start: LocalDateTime? get() = time?.let { date.atTime(it) }
    }

    private val MONTHS = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
    private val DOW = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY,
        "пн" to DayOfWeek.MONDAY, "вт" to DayOfWeek.TUESDAY, "ср" to DayOfWeek.WEDNESDAY, "чт" to DayOfWeek.THURSDAY,
        "пт" to DayOfWeek.FRIDAY, "сб" to DayOfWeek.SATURDAY, "вс" to DayOfWeek.SUNDAY,
    )

    fun parse(text: String, now: LocalDateTime): Result? {
        var s = " " + text.trim().replace(Regex("\\s+"), " ") + " "
        if (s.isBlank()) return null
        var date: LocalDate? = null
        var time: LocalTime? = null
        val today = now.toLocalDate()

        fun cut(r: Regex, f: (MatchResult) -> Unit): Boolean {
            val m = r.find(s) ?: return false
            f(m)
            s = s.replaceRange(m.range, " ").let { " " + it.trim() + " " }
            return true
        }

        // через N минут/часов/дней
        cut(Regex("(?iu)\\sчерез\\s(\\d+|полчаса|час)\\s?(минут[уы]?|мин|час(?:а|ов)?|д(?:ень|ня|ней))?\\s")) { m ->
            val raw = m.groupValues[1].lowercase()
            val unit = m.groupValues[2].lowercase()
            when {
                raw == "полчаса" -> { val t = now.plusMinutes(30); date = t.toLocalDate(); time = t.toLocalTime().withSecond(0).withNano(0) }
                raw == "час" -> { val t = now.plusHours(1); date = t.toLocalDate(); time = t.toLocalTime().withSecond(0).withNano(0) }
                unit.startsWith("мин") -> { val t = now.plusMinutes(raw.toLong()); date = t.toLocalDate(); time = t.toLocalTime().withSecond(0).withNano(0) }
                unit.startsWith("час") -> { val t = now.plusHours(raw.toLong()); date = t.toLocalDate(); time = t.toLocalTime().withSecond(0).withNano(0) }
                unit.startsWith("д") -> date = today.plusDays(raw.toLong())
            }
        }
        cut(Regex("(?iu)\\sпослезавтра\\s")) { date = today.plusDays(2) }
        cut(Regex("(?iu)\\sзавтра\\s")) { date = today.plusDays(1) }
        cut(Regex("(?iu)\\sсегодня\\s")) { date = today }
        // 25 ноября / 25.11 / 25.11.2026
        cut(Regex("(?iu)\\s(\\d{1,2})\\s(январ|феврал|март|апрел|ма[яй]|июн|июл|август|сентябр|октябр|ноябр|декабр)[а-яё]*\\s")) { m ->
            val stem = m.groupValues[2].lowercase()
            val mi = MONTHS.indexOfFirst { if (it == "ма") stem.startsWith("ма") && !stem.startsWith("март") else stem.startsWith(it) }
            var d = runCatching { LocalDate.of(today.year, mi + 1, m.groupValues[1].toInt()) }.getOrNull()
            if (d != null && d.isBefore(today)) d = d.plusYears(1)
            date = d
        }
        cut(Regex("\\s(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?\\s")) { m ->
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: today.year
            var d = runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
            if (d != null && m.groupValues[3].isEmpty() && d.isBefore(today)) d = d.plusYears(1)
            date = d
        }
        // в пятницу / в пт
        for ((k, dow) in DOW) {
            val word = if (k.length == 2) k else "$k[а-яё]*"
            val ok = cut(Regex("(?iu)\\s(?:во?\\s)?(?:следующ\\S*\\s)?$word\\s")) {
                var d = today.plusDays(1)
                while (d.dayOfWeek != dow) d = d.plusDays(1)
                date = d
            }
            if (ok) break
        }
        // время: в 15, в 15:30, в 9 вечера, 18:00
        cut(Regex("(?iu)\\s(?:в|к)?\\s?(\\d{1,2})(?::|\\.)(\\d{2})\\s")) { m ->
            time = runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
        } || cut(Regex("(?iu)\\s(?:в|к)\\s(\\d{1,2})\\s?(утра|дня|вечера|ночи|ч(?:ас(?:а|ов)?)?)?\\s")) { m ->
            var h = m.groupValues[1].toInt()
            when (m.groupValues[2].lowercase()) {
                "вечера", "дня" -> if (h < 12) h += 12
                "ночи" -> if (h == 12) h = 0
            }
            time = runCatching { LocalTime.of(h, 0) }.getOrNull()
        }
        val title = s.trim().trim(',', '.', '-', ' ').replaceFirstChar { it.uppercase() }
        if (date == null && time == null) return null
        var d = date ?: today
        // время уже прошло сегодня, а дня не указали — значит завтра
        if (date == null && time != null && d.atTime(time).isBefore(now)) d = d.plusDays(1)
        return Result(title.ifBlank { "Событие" }, d, time)
    }
}
