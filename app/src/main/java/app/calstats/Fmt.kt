package app.calstats

import java.time.LocalDate
import java.time.LocalTime
import java.time.Period as JPeriod
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Форматирование дат, чисел и «сколько прошло» по-русски. */
object Fmt {
    private val ru: Locale = Locale.forLanguageTag("ru")

    private val monthsNom = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val monthsShort = arrayOf(
        "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
    )
    private val monthsShortNom = arrayOf(
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
    )
    private val weekDays = arrayOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val weekDaysFull = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val monthsGen = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    /** «Вторник, 27 октября» (+ год, если не текущий). */
    fun dayTitle(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        "${weekDaysFull[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${monthsGen[d.monthValue - 1]}" +
            if (d.year != today.year) " ${d.year}" else ""

    fun dayOfMillis(ms: Long): LocalDate =
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    fun monthName(month: Int): String = monthsNom[month - 1]
    fun weekdayShort(i: Int): String = weekDays[i].replaceFirstChar { it.uppercase() }

    /** «Сегодня», «Завтра», «Вчера», «Через 3 дня», «5 дней назад». */
    fun relativeDay(d: LocalDate, today: LocalDate): String {
        val n = ChronoUnit.DAYS.between(today, d)
        return when {
            n == 0L -> "Сегодня"
            n == 1L -> "Завтра"
            n == -1L -> "Вчера"
            n > 1 -> "Через ${days(n)}"
            else -> "${days(-n)} назад".replaceFirstChar { it.uppercase() }
        }
    }

    /** Срок задачи коротко: «сегодня», «завтра», «пт, 30 окт». */
    fun dueShort(d: LocalDate, today: LocalDate): String {
        val n = ChronoUnit.DAYS.between(today, d)
        return when (n) {
            0L -> "сегодня"
            1L -> "завтра"
            -1L -> "вчера"
            else -> "${weekDays[d.dayOfWeek.value - 1]}, ${dateNoYear(d)}" + if (d.year != today.year) " ${d.year}" else ""
        }
    }

    fun plural(n: Long, one: String, few: String, many: String): String {
        val a = (abs(n) % 100).toInt()
        val b = a % 10
        return when {
            a in 11..14 -> many
            b == 1 -> one
            b in 2..4 -> few
            else -> many
        }
    }

    fun date(d: LocalDate): String = "${d.dayOfMonth} ${monthsShort[d.monthValue - 1]} ${d.year}"
    fun dateNoYear(d: LocalDate): String = "${d.dayOfMonth} ${monthsShort[d.monthValue - 1]}"
    fun dateWithDow(d: LocalDate): String = "${weekDays[d.dayOfWeek.value - 1]}, ${date(d)}"
    fun monthTitle(d: LocalDate): String = "${monthsNom[d.monthValue - 1]} ${d.year}"
    fun monthShort(month: Int): String = monthsShortNom[month - 1]
    fun time(t: LocalTime): String = String.format(ru, "%02d:%02d", t.hour, t.minute)

    fun range(from: LocalDate, toInclusive: LocalDate): String = when {
        from == toInclusive -> date(from)
        from.year == toInclusive.year && from.month == toInclusive.month ->
            "${from.dayOfMonth}–${toInclusive.dayOfMonth} ${monthsShort[from.monthValue - 1]} ${from.year}"
        from.year == toInclusive.year -> "${dateNoYear(from)} – ${date(toInclusive)}"
        else -> "${date(from)} – ${date(toInclusive)}"
    }

    /** Часы одним числом: «12», «3,5». */
    fun hours(minutes: Long): String {
        if (minutes % 60 == 0L) return (minutes / 60).toString()
        return String.format(ru, "%.1f", minutes / 60.0)
    }

    /** Длительность: «2 ч 30 мин». */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m мин"
            m == 0L -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    fun days(n: Long): String = "$n ${plural(n, "день", "дня", "дней")}"

    /** «сегодня», «вчера», «15 дней назад (2 мес. 3 дн.)», «через 4 дня». */
    fun ago(day: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(day, today)
        return when {
            d == 0L -> "сегодня"
            d == 1L -> "вчера"
            d == -1L -> "завтра"
            d < 0 -> "через ${days(-d)}"
            d < 31 -> "${days(d)} назад"
            else -> "${days(d)} назад (${approx(day, today)})"
        }
    }

    private fun approx(from: LocalDate, to: LocalDate): String {
        val p = JPeriod.between(from, to)
        val parts = mutableListOf<String>()
        if (p.years > 0) parts += "${p.years} ${plural(p.years.toLong(), "год", "года", "лет")}"
        if (p.months > 0) parts += "${p.months} мес."
        if (p.years == 0 && p.days > 0) parts += "${p.days} дн."
        return parts.joinToString(" ")
    }
}
