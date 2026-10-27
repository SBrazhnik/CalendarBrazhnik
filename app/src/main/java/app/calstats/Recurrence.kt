package app.calstats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Периодичность события (подмножество RRULE, которое понимает Google Календарь). */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    enum class Freq(val rrule: String) { DAILY("DAILY"), WEEKLY("WEEKLY"), MONTHLY("MONTHLY"), YEARLY("YEARLY") }

    fun toRRule(): String = buildString {
        append("FREQ=").append(freq.rrule)
        if (interval > 1) append(";INTERVAL=").append(interval)
        if (freq == Freq.WEEKLY && byDay.isNotEmpty()) {
            append(";BYDAY=").append(byDay.sorted().joinToString(",") { DAY_CODES[it.value - 1] })
        }
        if (until != null) append(";UNTIL=").append(until.format(DateTimeFormatter.BASIC_ISO_DATE)).append("T235959Z")
        else if (count != null) append(";COUNT=").append(count)
    }

    /** «Каждую неделю: пн, ср, пт · до 31 дек 2026» */
    fun describe(): String {
        val base = when (freq) {
            Freq.DAILY -> if (interval == 1) "Каждый день" else "Каждые $interval ${Fmt.plural(interval.toLong(), "день", "дня", "дней")}"
            Freq.WEEKLY -> {
                val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                when {
                    interval == 1 && byDay == weekdays -> "По будням (пн–пт)"
                    else -> {
                        val head = if (interval == 1) "Каждую неделю" else "Каждые $interval ${Fmt.plural(interval.toLong(), "неделю", "недели", "недель")}"
                        if (byDay.isEmpty()) head else head + ": " + byDay.sorted().joinToString(", ") { Fmt.weekdayShort(it.value - 1).lowercase() }
                    }
                }
            }
            Freq.MONTHLY -> if (interval == 1) "Каждый месяц" else "Каждые $interval ${Fmt.plural(interval.toLong(), "месяц", "месяца", "месяцев")}"
            Freq.YEARLY -> if (interval == 1) "Каждый год" else "Каждые $interval ${Fmt.plural(interval.toLong(), "год", "года", "лет")}"
        }
        return when {
            until != null -> "$base · до ${Fmt.date(until)}"
            count != null -> "$base · $count ${Fmt.plural(count.toLong(), "раз", "раза", "раз")}"
            else -> base
        }
    }

    companion object {
        private val DAY_CODES = arrayOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

        fun parse(rrule: String?): Recurrence? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.removePrefix("RRULE:").split(";").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0].uppercase() to kv[1] else null
            }.toMap()
            val freq = Freq.entries.firstOrNull { it.rrule == parts["FREQ"]?.uppercase() } ?: return null
            val byDay = parts["BYDAY"]?.split(",")?.mapNotNull { code ->
                val c = code.takeLast(2).uppercase()
                DAY_CODES.indexOf(c).takeIf { it >= 0 }?.let { DayOfWeek.of(it + 1) }
            }?.toSet() ?: emptySet()
            val until = parts["UNTIL"]?.let { u ->
                runCatching { LocalDate.parse(u.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
            }
            return Recurrence(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                byDay = byDay,
                until = until,
                count = parts["COUNT"]?.toIntOrNull(),
            )
        }

        /** Готовые варианты для выбора, как в Google Календаре. */
        fun presets(start: LocalDate): List<Recurrence?> = listOf(
            null,
            Recurrence(Freq.DAILY),
            Recurrence(Freq.WEEKLY, byDay = setOf(start.dayOfWeek)),
            Recurrence(Freq.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
            Recurrence(Freq.MONTHLY),
            Recurrence(Freq.YEARLY),
        )
    }
}

/** Напоминания: варианты в минутах до начала. */
object ReminderOptions {
    val all = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

    fun label(minutes: Int): String = when {
        minutes == 0 -> "В момент начала"
        minutes < 60 -> "За $minutes мин"
        minutes % 10080 == 0 -> "За ${minutes / 10080} ${Fmt.plural((minutes / 10080).toLong(), "неделю", "недели", "недель")}"
        minutes % 1440 == 0 -> "За ${minutes / 1440} ${Fmt.plural((minutes / 1440).toLong(), "день", "дня", "дней")}"
        minutes % 60 == 0 -> "За ${minutes / 60} ч"
        else -> "За ${minutes / 60} ч ${minutes % 60} мин"
    }
}
