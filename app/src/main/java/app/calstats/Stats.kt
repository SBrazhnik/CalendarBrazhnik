package app.calstats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

enum class PeriodType(val label: String) {
    WEEK("Неделя"), MONTH("Месяц"), YEAR("Год"), ALL("Всё время"), CUSTOM("Период"),
}

/** Выбранный период. Даты конца — не включительно. */
data class Period(
    val type: PeriodType,
    val anchor: LocalDate,
    val customStart: LocalDate,
    val customEndInclusive: LocalDate,
) {
    fun start(loadStart: LocalDate): LocalDate = when (type) {
        PeriodType.WEEK -> anchor.with(DayOfWeek.MONDAY)
        PeriodType.MONTH -> anchor.withDayOfMonth(1)
        PeriodType.YEAR -> anchor.withDayOfYear(1)
        PeriodType.ALL -> loadStart
        PeriodType.CUSTOM -> customStart
    }

    fun endExclusive(loadEnd: LocalDate): LocalDate = when (type) {
        PeriodType.WEEK -> anchor.with(DayOfWeek.MONDAY).plusWeeks(1)
        PeriodType.MONTH -> anchor.withDayOfMonth(1).plusMonths(1)
        PeriodType.YEAR -> anchor.withDayOfYear(1).plusYears(1)
        PeriodType.ALL -> loadEnd
        PeriodType.CUSTOM -> customEndInclusive.plusDays(1)
    }

    val navigable: Boolean get() = type == PeriodType.WEEK || type == PeriodType.MONTH || type == PeriodType.YEAR

    fun shift(step: Long): Period = when (type) {
        PeriodType.WEEK -> copy(anchor = anchor.plusWeeks(step))
        PeriodType.MONTH -> copy(anchor = anchor.plusMonths(step))
        PeriodType.YEAR -> copy(anchor = anchor.plusYears(step))
        else -> this
    }

    fun title(loadStart: LocalDate): String = when (type) {
        PeriodType.WEEK -> {
            val s = anchor.with(DayOfWeek.MONDAY)
            Fmt.range(s, s.plusDays(6))
        }
        PeriodType.MONTH -> Fmt.monthTitle(anchor)
        PeriodType.YEAR -> "${anchor.year} год"
        PeriodType.ALL -> "С ${loadStart.year} года по сегодня"
        PeriodType.CUSTOM -> Fmt.range(customStart, customEndInclusive)
    }

    companion object {
        fun initial(today: LocalDate) = Period(PeriodType.MONTH, today, today.withDayOfYear(1), today)
    }
}

/** Направление: все цвета с одинаковым названием (или один безымянный цвет). */
class DirectionGroup(
    val key: String,
    val name: String,
    val named: Boolean,
    val color: Int,
    val colors: Set<Int>,
    val instances: List<EventInstance>,
)

class Agg(val days: Int, val events: Int, val minutes: Long, val dates: Set<LocalDate>)

class GroupStats(val group: DirectionGroup, val agg: Agg, val last: EventInstance?, val next: EventInstance?)

/** Отрезок времени, в котором считаем статистику. */
class Window(
    val startMs: Long,
    val endMs: Long,
    val startDate: LocalDate,
    val endDateExcl: LocalDate,
    val zone: ZoneId,
)

/** Вклад события в окно: минуты, дни и (для событий со временем) обрезанный интервал. */
class Contribution(val minutes: Long, val dates: List<LocalDate>, val startMs: Long = 0, val endMs: Long = 0)

object Stats {
    fun groupKey(color: Int, setting: DirectionSetting?): String {
        val n = setting?.name?.trim().orEmpty()
        return if (n.isNotEmpty()) "n:" + n.lowercase() else "c:$color"
    }

    fun buildGroups(
        instances: List<EventInstance>,
        data: AppData,
        includedCalendarIds: Set<Long>,
    ): List<DirectionGroup> {
        val lists = LinkedHashMap<String, MutableList<EventInstance>>()
        val colorCounts = HashMap<String, HashMap<Int, Int>>()
        val names = HashMap<String, String>()
        val seen = HashSet<String>()
        for (i in instances) {
            if (i.calendarId !in includedCalendarIds) continue
            val s = data.directions[i.color]
            if (s?.hidden == true) continue
            val key = groupKey(i.color, s)
            // Одно и то же событие в двух календарях (например, Google и Samsung) считаем один раз.
            if (!seen.add("$key|${i.title.trim().lowercase()}|${i.begin}|${i.end}|${i.allDay}")) continue
            lists.getOrPut(key) { mutableListOf() } += i
            val counts = colorCounts.getOrPut(key) { HashMap() }
            counts[i.color] = (counts[i.color] ?: 0) + 1
            if (key.startsWith("n:") && key !in names) names[key] = s!!.name.trim()
        }
        return lists.map { (key, list) ->
            val counts = colorCounts[key]!!
            DirectionGroup(
                key = key,
                name = names[key] ?: "Без названия",
                named = key.startsWith("n:"),
                color = counts.maxByOrNull { it.value }!!.key,
                colors = counts.keys,
                instances = list,
            )
        }
    }

    fun window(
        startDate: LocalDate,
        endDateExcl: LocalDate,
        now: Long,
        includeFuture: Boolean,
        zone: ZoneId,
    ): Window {
        var endMs = endDateExcl.atStartOfDay(zone).toInstant().toEpochMilli()
        var endDate = endDateExcl
        if (!includeFuture) {
            endMs = min(endMs, now)
            val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
            if (endDate.isAfter(tomorrow)) endDate = tomorrow
        }
        return Window(startDate.atStartOfDay(zone).toInstant().toEpochMilli(), endMs, startDate, endDate, zone)
    }

    fun utcDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun dates(from: LocalDate, toExcl: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = from
        while (d.isBefore(toExcl)) {
            out += d
            d = d.plusDays(1)
        }
        return out
    }

    /** Сколько минут и какие дни событие занимает внутри окна (null — не попадает). */
    fun contribution(i: EventInstance, w: Window): Contribution? {
        if (i.allDay) {
            val bd = utcDate(i.begin)
            var ed = utcDate(i.end)
            if (!ed.isAfter(bd)) ed = bd.plusDays(1)
            val from = maxOf(bd, w.startDate)
            val to = minOf(ed, w.endDateExcl)
            if (!from.isBefore(to)) return null
            return Contribution(0, dates(from, to))
        }
        if (i.end <= i.begin) {
            if (i.begin < w.startMs || i.begin >= w.endMs) return null
            return Contribution(0, listOf(localDate(i.begin, w.zone)))
        }
        val s = max(i.begin, w.startMs)
        val e = min(i.end, w.endMs)
        if (e <= s) return null
        val from = localDate(s, w.zone)
        val to = localDate(e - 1, w.zone).plusDays(1)
        return Contribution((e - s) / 60_000L, dates(from, to), s, e)
    }

    /**
     * Дни, события и часы в окне. Часы считаются по объединению интервалов:
     * если два события направления пересекаются по времени, общее время не удваивается.
     */
    fun aggregate(list: List<EventInstance>, w: Window): Agg {
        var events = 0
        val days = HashSet<LocalDate>()
        val intervals = ArrayList<LongArray>()
        for (i in list) {
            val c = contribution(i, w) ?: continue
            events++
            days.addAll(c.dates)
            if (c.endMs > c.startMs) intervals += longArrayOf(c.startMs, c.endMs)
        }
        return Agg(days.size, events, unionMs(intervals) / 60_000L, days)
    }

    fun unionMs(intervals: List<LongArray>): Long {
        if (intervals.isEmpty()) return 0
        val sorted = intervals.sortedBy { it[0] }
        var total = 0L
        var curS = sorted[0][0]
        var curE = sorted[0][1]
        for (k in 1 until sorted.size) {
            val (s, e) = sorted[k][0] to sorted[k][1]
            if (s <= curE) {
                if (e > curE) curE = e
            } else {
                total += curE - curS
                curS = s
                curE = e
            }
        }
        return total + (curE - curS)
    }

    fun started(i: EventInstance, now: Long, today: LocalDate): Boolean =
        if (i.allDay) !utcDate(i.begin).isAfter(today) else i.begin <= now

    fun ongoing(i: EventInstance, now: Long, today: LocalDate): Boolean =
        started(i, now, today) && if (i.allDay) utcDate(i.end).isAfter(today) else i.end > now

    /** Последний день, который занимает событие. */
    fun lastDay(i: EventInstance, zone: ZoneId): LocalDate =
        if (i.allDay) {
            val bd = utcDate(i.begin)
            val ed = utcDate(i.end).minusDays(1)
            if (ed.isBefore(bd)) bd else ed
        } else {
            localDate(if (i.end > i.begin) i.end - 1 else i.begin, zone)
        }

    fun firstDay(i: EventInstance, zone: ZoneId): LocalDate =
        if (i.allDay) utcDate(i.begin) else localDate(i.begin, zone)

    fun lastAndNext(list: List<EventInstance>, now: Long, today: LocalDate): Pair<EventInstance?, EventInstance?> {
        var last: EventInstance? = null
        var next: EventInstance? = null
        for (i in list) {
            if (started(i, now, today)) {
                if (last == null || i.begin >= last.begin) last = i
            } else if (next == null || i.begin < next.begin) {
                next = i
            }
        }
        return last to next
    }

    fun groupStats(groups: List<DirectionGroup>, w: Window, now: Long, today: LocalDate): List<GroupStats> =
        groups.map { g ->
            val (last, next) = lastAndNext(g.instances, now, today)
            GroupStats(g, aggregate(g.instances, w), last, next)
        }
}
