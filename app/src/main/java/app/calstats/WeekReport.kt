package app.calstats

import android.content.Context
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import app.calstats.work.Workspace
import java.time.LocalDate
import java.time.ZoneId

/** Итог недели: события, задачи, траты, привычки, питание. */
data class WeekReport(
    val from: LocalDate,
    val to: LocalDate,
    val events: Int,
    val eventHours: Long,
    val tasksDone: Int,
    val spent: Long,
    val spentPrev: Long,
    val habitsPct: Int?,
    val foodDays: Int,
    val waterAvg: Int?,
) {
    fun lines(): List<String> = buildList {
        add("📅 Событий: $events" + if (eventHours > 0) " · ${eventHours} ч" else "")
        if (tasksDone > 0) add("✅ Задач выполнено: $tasksDone")
        if (spent > 0) {
            val d = if (spentPrev > 0) ((spent - spentPrev) * 100 / spentPrev).toInt() else null
            add("💸 Потрачено: ${SplitMath.money(spent)}" + (d?.let { if (it >= 0) " (+$it% к прошлой)" else " ($it% к прошлой)" } ?: ""))
        }
        habitsPct?.let { add("🔁 Привычки выполнены на $it%") }
        if (foodDays > 0) add("🍽 Дневник питания: $foodDays из 7 дней")
        waterAvg?.let { add("💧 Воды в среднем $it мл в день") }
    }

    companion object {
        /** Неделя: в понедельник — прошлая, иначе — текущая (пн–вс). */
        fun weekOf(today: LocalDate): Pair<LocalDate, LocalDate> {
            val base = if (today.dayOfWeek.value == 1) today.minusDays(1) else today
            val from = base.minusDays(base.dayOfWeek.value - 1L)
            return from to from.plusDays(6)
        }

        fun build(ctx: Context?, w: Workspace, today: LocalDate, events: List<EventInstance>? = null): WeekReport {
            val (from, to) = weekOf(today)
            val zone = ZoneId.systemDefault()
            val a = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val evs = events?.filter { it.begin in a until b }
                ?: ctx?.let { runCatching { CalendarRepository(it).instances(a, b) }.getOrNull() }.orEmpty()
            val hours = evs.filter { !it.allDay }.sumOf { (it.end - it.begin) / 60_000 } / 60
            val done = w.issues.count { it.assigneeId == ME && (it.resolvedAt ?: 0) in a until b }
            fun spentIn(f: LocalDate, t: LocalDate) = w.transactions.filter { it.type == TxType.EXPENSE && it.localDate in f..t }.sumOf { it.amount }
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(to, today)) }.toList()
            var planned = 0
            var ok = 0
            w.habits.filter { !it.archived && it.kind == HabitKind.DO }.forEach { h ->
                val v = HabitMath.values(w.habitLogs, h.id)
                days.filter { h.scheduled(it) }.forEach { d -> planned++; if (HabitMath.done(h, v, d)) ok++ }
            }
            val water = w.waterLog.filter { runCatching { LocalDate.parse(it.date) in from..to }.getOrDefault(false) && it.ml > 0 }
            return WeekReport(
                from, to, evs.size, hours, done,
                spentIn(from, to), spentIn(from.minusDays(7), to.minusDays(7)),
                if (planned > 0) ok * 100 / planned else null,
                w.foodLog.map { it.date }.distinct().count { runCatching { LocalDate.parse(it) in from..to }.getOrDefault(false) },
                if (water.isEmpty()) null else water.sumOf { it.ml } / water.size,
            )
        }
    }
}
