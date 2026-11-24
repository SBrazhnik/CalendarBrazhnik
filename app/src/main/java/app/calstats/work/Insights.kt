package app.calstats.work

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Закономерности между модулями: «в дни, когда сна больше 7 ч, настроение выше на 18%».
 * Сравнивает дни, где показатель выше и ниже обычного; показывает только заметную и подкреплённую разницу.
 */
object Insights {
    data class Insight(val emoji: String, val text: String, val strength: Double)

    class Series(val name: String, val unit: String, val values: Map<LocalDate, Double>, val higherIsBetter: Boolean = true, val digits: Int = 1)

    fun series(w: Workspace, sleepMin: Map<LocalDate, Long>, steps: Map<LocalDate, Long>, from: LocalDate, to: LocalDate): List<Series> {
        fun inR(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        fun ld(s: String) = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
        val z = ZoneId.systemDefault()
        val out = mutableListOf<Series>()
        out += Series("Сон", "ч", sleepMin.filter { inR(it.key) && it.value > 120 }.mapValues { it.value / 60.0 })
        out += Series("Шаги", "", steps.filter { inR(it.key) && it.value > 0 }.mapValues { it.value.toDouble() }, digits = 0)
        out += Series("Настроение", "из 5", w.moods.mapNotNull { m -> ld(m.date)?.takeIf(::inR)?.let { it to m.mood.toDouble() } }.toMap())
        out += Series("Энергия", "из 5", w.moods.filter { it.energy > 0 }.mapNotNull { m -> ld(m.date)?.takeIf(::inR)?.let { it to m.energy.toDouble() } }.toMap())
        // Траты: только если в целом траты записываются регулярно, считаем дни без трат нулём
        val spend = w.transactions.filter { it.type == TxType.EXPENSE && inR(it.localDate) }.groupBy { it.localDate }.mapValues { e -> e.value.sumOf { it.amount } / 100.0 }
        if (spend.size >= 15) {
            val first = spend.keys.min()
            val all = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { spend[it] ?: 0.0 }
            out += Series("Траты", "₽", all, higherIsBetter = false, digits = 0)
        }
        val water = w.waterLog.mapNotNull { e -> ld(e.date)?.takeIf(::inR)?.let { it to e.ml } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum().toDouble() }
        out += Series("Вода", "мл", water, digits = 0)
        val done = w.issues.mapNotNull { i -> i.resolvedAt?.let { Instant.ofEpochMilli(it).atZone(z).toLocalDate() }?.takeIf(::inR) }.groupingBy { it }.eachCount()
        if (done.size >= 10) out += Series("Выполнено задач", "", done.mapValues { it.value.toDouble() }, digits = 1)
        val habits = w.habits.filter { !it.archived && it.kind == HabitKind.DO }
        if (habits.isNotEmpty()) {
            val logs = w.habitLogs.groupBy { it.date }
            val pct = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.mapNotNull { d ->
                val due = habits.filter { it.createdDate <= d && (it.perWeek != null || it.scheduled(d)) }
                if (due.isEmpty()) null else {
                    val l = logs[d.toString()].orEmpty()
                    d to due.count { h -> (l.firstOrNull { it.habitId == h.id }?.value ?: 0) >= h.target } * 100.0 / due.size
                }
            }.toMap()
            if (pct.values.any { it > 0 }) out += Series("Привычки", "%", pct, digits = 0)
        }
        return out.filter { it.values.size >= 10 }
    }

    private fun fmt(v: Double, digits: Int) = if (digits == 0) "%,.0f".format(Locale.forLanguageTag("ru"), v).replace(',', ' ') else "%.${digits}f".format(v).replace('.', ',')

    fun find(series: List<Series>): List<Insight> {
        val by = series.associateBy { it.name }
        val pairs = listOf(
            "Сон" to "Настроение", "Сон" to "Энергия", "Сон" to "Траты", "Сон" to "Выполнено задач", "Сон" to "Привычки",
            "Шаги" to "Настроение", "Шаги" to "Сон", "Привычки" to "Настроение", "Вода" to "Энергия", "Шаги" to "Энергия",
            "Настроение" to "Траты",
        )
        val out = mutableListOf<Insight>()
        for ((a, b) in pairs) {
            val x = by[a] ?: continue
            val y = by[b] ?: continue
            val common = x.values.keys.intersect(y.values.keys)
            if (common.size < 12) continue
            val xs = common.map { x.values[it]!! }.sorted()
            val cut = xs[xs.size / 2]
            val hi = common.filter { x.values[it]!! >= cut }.map { y.values[it]!! }
            val lo = common.filter { x.values[it]!! < cut }.map { y.values[it]!! }
            if (hi.size < 5 || lo.size < 5) continue
            val mh = hi.average(); val ml = lo.average()
            val base = maxOf(abs(ml), abs(mh), 1e-9)
            val rel = (mh - ml) / base
            if (abs(rel) < 0.15) continue
            val cutTxt = fmt(cut, x.digits) + if (x.unit.isNotBlank() && x.unit != "из 5") " ${x.unit}" else ""
            val dir = if (mh > ml) "выше" else "ниже"
            val good = (mh > ml) == y.higherIsBetter
            val emoji = when (a) { "Сон" -> "😴"; "Шаги" -> "👟"; "Привычки" -> "🔁"; "Вода" -> "💧"; else -> "🙂" }
            val subj = when (a) { "Сон" -> "сна от $cutTxt"; "Шаги" -> "от $cutTxt шагов"; "Привычки" -> "привычек выполнено от $cutTxt"; "Вода" -> "воды от $cutTxt"; else -> "${a.lowercase()} от $cutTxt" }
            val what = y.name.lowercase()
            out += Insight(emoji,
                "В дни, когда $subj, $what $dir на ${(abs(rel) * 100).toInt()}% (${fmt(mh, y.digits)} против ${fmt(ml, y.digits)}${if (y.unit.isNotBlank()) " " + y.unit else ""})" +
                    if (good) "" else " — стоит присмотреться",
                abs(rel))
        }
        // Дни недели: настроение и траты
        fun weekday(s: Series, good: String, bad: String) {
            val g = s.values.entries.groupBy { it.key.dayOfWeek }.filter { it.value.size >= 3 }.mapValues { e -> e.value.map { it.value }.average() }
            if (g.size < 5) return
            val avg = g.values.average()
            val max = g.maxBy { it.value }; val min = g.minBy { it.value }
            if (avg <= 0 || (max.value - min.value) / avg < 0.25) return
            fun n(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru"))
            out += Insight("📆", "${s.name}: $good — ${n(max.key)} (${fmt(max.value, s.digits)}), $bad — ${n(min.key)} (${fmt(min.value, s.digits)})", (max.value - min.value) / avg / 2)
        }
        by["Настроение"]?.let { weekday(it, "лучше всего", "хуже всего") }
        by["Траты"]?.let { weekday(it, "больше всего тратите", "меньше всего") }
        return out.sortedByDescending { it.strength }.take(6)
    }
}
