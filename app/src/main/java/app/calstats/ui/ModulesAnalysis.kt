package app.calstats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.work.CarEventType
import app.calstats.work.HabitKind
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import app.calstats.work.Workspace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Строка анализа модуля: заголовок, главная цифра, пояснения. */
data class ModuleStat(val tab: Tab, val title: String, val value: String, val lines: List<String>)

object ModulesAnalysisMath {
    private fun d(s: String?) = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    private fun money(k: Long) = SplitMath.money(k)
    private fun hm(min: Int) = if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"

    fun compute(w: Workspace, start: LocalDate, endExcl: LocalDate, today: LocalDate, enabled: (Tab) -> Boolean): List<ModuleStat> {
        fun inR(x: LocalDate?) = x != null && !x.isBefore(start) && x.isBefore(endExcl)
        val z = ZoneId.systemDefault()
        val out = mutableListOf<ModuleStat>()
        val days = ChronoUnit.DAYS.between(start, minOf(endExcl, today.plusDays(1))).coerceAtLeast(1)

        // Задачи
        if (enabled(Tab.TASKS)) {
            val done = w.issues.filter { it.done && it.resolvedAt != null && inR(Instant.ofEpochMilli(it.resolvedAt).atZone(z).toLocalDate()) }
            val logged = w.issues.flatMap { it.worklogs }.filter { inR(d(it.date)) }.sumOf { it.minutes }
            val overdue = w.issues.count { !it.done && it.dueDate?.let { dd -> dd.isBefore(today) } == true }
            val created = w.issues.count { inR(Instant.ofEpochMilli(it.created).atZone(z).toLocalDate()) }
            if (done.isNotEmpty() || logged > 0 || created > 0) out += ModuleStat(
                Tab.TASKS, "Задачи", "${done.size} выполнено",
                listOfNotNull(
                    "Создано: $created",
                    if (logged > 0) "Учтено времени: ${hm(logged)}" else null,
                    if (overdue > 0) "Просрочено сейчас: $overdue" else null,
                ),
            )
        }
        // Финансы
        if (enabled(Tab.FINANCE)) {
            val tx = w.transactions.filter { inR(d(it.date)) }
            val inc = tx.filter { it.type == TxType.INCOME }.sumOf { it.amount }
            val exp = tx.filter { it.type == TxType.EXPENSE }
            val expSum = exp.sumOf { it.amount }
            if (tx.isNotEmpty()) {
                val top = exp.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }.take(3)
                    .map { (id, v) -> "${w.category(id)?.name ?: "Без категории"} — ${money(v)} (${if (expSum > 0) v * 100 / expSum else 0}%)" }
                out += ModuleStat(
                    Tab.FINANCE, "Финансы", "${if (inc - expSum >= 0) "+" else "−"}${money(kotlin.math.abs(inc - expSum))}",
                    listOf("Доходы: ${money(inc)}", "Расходы: ${money(expSum)} · в день ${money(expSum / days)}") + top,
                )
            }
            val paid = w.debts.flatMap { it.payments }.filter { inR(d(it.date)) }.sumOf { it.amount }
            if (paid > 0) out += ModuleStat(Tab.FINANCE, "Долги", money(paid), listOf("Выплачено/получено по долгам за период"))
        }
        // Привычки
        if (enabled(Tab.HABITS)) {
            val logs = w.habitLogs.filter { inR(d(it.date)) }
            val rows = w.habits.filter { !it.archived && it.kind == HabitKind.DO }.mapNotNull { h ->
                var due = 0; var ok = 0
                var x = maxOf(start, h.createdDate)
                val last = minOf(endExcl.minusDays(1), today)
                val v = logs.filter { it.habitId == h.id }.associate { it.date to it.value }
                while (!x.isAfter(last)) {
                    if (h.perWeek != null || h.scheduled(x)) { due++; if ((v[x.toString()] ?: 0) >= h.target) ok++ }
                    x = x.plusDays(1)
                }
                if (h.perWeek != null) due = ((due / 7.0) * h.perWeek).toInt().coerceAtLeast(1)
                if (due == 0) null else Triple(h, ok, due)
            }
            if (rows.isNotEmpty()) {
                val pct = rows.sumOf { it.second.coerceAtMost(it.third) } * 100 / rows.sumOf { it.third }
                out += ModuleStat(
                    Tab.HABITS, "Привычки", "$pct%",
                    rows.sortedByDescending { it.second * 100 / it.third }.map { (h, ok, due) -> "${h.emoji} ${h.name}: ${ok.coerceAtMost(due)} из $due" },
                )
            }
        }
        // Здоровье
        if (enabled(Tab.HEALTH)) {
            val lines = mutableListOf<String>()
            val food = w.foodLog.filter { inR(d(it.date)) }
            if (food.isNotEmpty()) {
                val fd = food.map { it.date }.distinct().size
                lines += "Калорий в среднем: ${(food.sumOf { it.kcal } / fd).toInt()} ккал/день ($fd дн. с записями)"
            }
            val water = w.waterLog.filter { inR(d(it.date)) }
            if (water.isNotEmpty()) lines += "Воды в среднем: ${water.sumOf { it.ml } / water.map { it.date }.distinct().size} мл/день"
            val wt = w.weightLog.filter { inR(d(it.date)) }.sortedBy { it.date }
            if (wt.size >= 2) {
                val diff = wt.last().kg - wt.first().kg
                lines += "Вес: ${"%.1f".format(wt.first().kg)} → ${"%.1f".format(wt.last().kg)} кг (${if (diff >= 0) "+" else ""}${"%.1f".format(diff)})"
            } else if (wt.size == 1) lines += "Вес: ${"%.1f".format(wt[0].kg)} кг"
            val moods = w.moods.filter { inR(d(it.date)) }
            if (moods.isNotEmpty()) lines += "Настроение в среднем: ${"%.1f".format(moods.map { it.mood }.average())} из 5"
            val meds = w.medLogs.filter { inR(d(it.date)) && it.taken }.size
            if (meds > 0) lines += "Приёмов лекарств отмечено: $meds"
            val fasts = w.fasts.filter { it.end != null && inR(Instant.ofEpochMilli(it.start).atZone(z).toLocalDate()) }
            if (fasts.isNotEmpty()) lines += "Голоданий: ${fasts.size}, в среднем ${fasts.map { (it.end!! - it.start) / 3600_000.0 }.average().let { "%.1f".format(it) }} ч"
            if (lines.isNotEmpty()) out += ModuleStat(Tab.HEALTH, "Здоровье", "${lines.size} показ.", lines)
        }
        // Дневник и цели
        if (enabled(Tab.JOURNAL)) {
            val j = w.journal.filter { inR(d(it.date)) && (it.text.isNotBlank() || it.gratitude.isNotEmpty() || it.best.isNotBlank()) }
            if (j.isNotEmpty()) out += ModuleStat(
                Tab.JOURNAL, "Дневник", "${j.size} из $days дн.",
                listOf("Благодарностей записано: ${j.sumOf { it.gratitude.size }}"),
            )
        }
        // Авто
        if (enabled(Tab.CAR)) {
            val ev = w.carEvents.filter { inR(d(it.date)) }
            if (ev.isNotEmpty()) {
                val fuel = ev.filter { it.type == CarEventType.FUEL }
                out += ModuleStat(
                    Tab.CAR, "Авто", money(ev.sumOf { it.cost }),
                    listOfNotNull(
                        if (fuel.isNotEmpty()) "Топливо: ${money(fuel.sumOf { it.cost })}, ${"%.0f".format(fuel.sumOf { it.liters ?: 0.0 })} л" else null,
                    ) + ev.filter { it.type != CarEventType.FUEL }.groupBy { it.type }
                        .map { (t, l) -> "${t.emoji} ${t.title}: ${money(l.sumOf { it.cost })}" },
                )
            }
        }
        // Питомцы
        if (enabled(Tab.PETS)) {
            val ev = w.petEvents.filter { inR(d(it.date)) }
            if (ev.isNotEmpty()) out += ModuleStat(Tab.PETS, "Питомцы", "${ev.size} событий", emptyList())
        }
        return out
    }
}

@Composable
fun ModulesAnalysis(vm: MainViewModel) {
    val start = vm.period.start(vm.loadStart)
    val end = vm.period.endExclusive(vm.loadEnd)
    val stats = remember(vm.work, start, end, vm.today, vm.disabledModules) {
        ModulesAnalysisMath.compute(vm.work, start, end, vm.today) { vm.isEnabled(it) }
    }
    if (stats.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Модули", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        stats.forEach { s ->
            Card(
                onClick = { vm.tab = s.tab },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        ModuleIcon(s.tab, false, 20.dp)
                        Text(s.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        Text(s.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    s.lines.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
