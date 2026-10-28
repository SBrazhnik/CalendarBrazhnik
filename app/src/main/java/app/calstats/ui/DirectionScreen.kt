@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
import java.time.LocalDate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Agg
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Period
import app.calstats.PeriodType
import app.calstats.Screen
import app.calstats.Stats
import java.time.Instant

@Composable
fun DirectionScreen(vm: MainViewModel, key: String, snackbar: SnackbarHostState) {
    val group = remember(vm.instances, vm.data, vm.calendars, key) { vm.groups().firstOrNull { it.key == key } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (group != null) {
                            ColorDot(group.color, 16.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(group?.name ?: "Направление", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        floatingActionButton = {
            if (group != null) {
                FloatingActionButton(onClick = { vm.push(Screen.Edit(presetColor = group.color)) }) {
                    Icon(Icons.Filled.Add, "Новое событие")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (group == null) {
            Text(
                "Направление не найдено — возможно, его переименовали.",
                Modifier.padding(pad).padding(16.dp),
            )
            return@Scaffold
        }

        val w = remember(vm.period, vm.now, vm.data) { vm.periodWindow() }
        val agg = remember(group, w) { Stats.aggregate(group.instances, w) }
        val (last, next) = remember(group, vm.now) { Stats.lastAndNext(group.instances, vm.now, vm.today) }
        val events = remember(group, w) {
            group.instances.filter { Stats.contribution(it, w) != null }.sortedByDescending { it.begin }
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PeriodSelector(vm.period, vm.loadStart) { vm.period = it } }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            StatBox(agg.days.toString(), "дней", Modifier.weight(1f))
                            StatBox(agg.events.toString(), "событий", Modifier.weight(1f))
                            StatBox(Fmt.hours(agg.minutes), "часов", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        val lastLine = when {
                            last == null -> "Ещё не было"
                            Stats.ongoing(last, vm.now, vm.today) -> "Идёт сейчас: ${last.title}"
                            else -> {
                                val d = Stats.lastDay(last, vm.zone)
                                "С последнего события прошло: ${Fmt.ago(d, vm.today)}\n${Fmt.dateWithDow(d)} — ${last.title}"
                            }
                        }
                        Text(lastLine, style = MaterialTheme.typography.bodyMedium)
                        if (next != null) {
                            val nd = Stats.firstDay(next, vm.zone)
                            Text(
                                "Следующее: ${Fmt.dateWithDow(nd)} (${Fmt.ago(nd, vm.today)}) — ${next.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { BreakdownCard(vm, group) }
            item {
                Text(
                    "События за период: ${events.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(events, key = { "${it.eventId}_${it.begin}" }) { e ->
                EventRow(e, vm) { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) }
                HorizontalDivider()
            }
        }
    }
}

private class BreakdownRow(val label: String, val agg: Agg, val period: Period)

/**
 * Разбивка направления по месяцам выбранного года (или по годам).
 * Каждая строка считается тем же способом, что и итог за период, поэтому суммы сходятся.
 */
@Composable
private fun BreakdownCard(vm: MainViewModel, group: app.calstats.DirectionGroup) {
    val periodYear = when (vm.period.type) {
        PeriodType.ALL -> vm.today.year
        PeriodType.CUSTOM -> vm.period.customEndInclusive.year
        else -> vm.period.start(vm.loadStart).year
    }
    var year by remember(periodYear) { mutableIntStateOf(periodYear) }
    var byYears by remember { mutableStateOf(vm.period.type == PeriodType.ALL) }
    val color = Color(group.color)
    val firstYear = remember(group) {
        group.instances.minOfOrNull { Stats.firstDay(it, vm.zone).year }?.coerceAtLeast(vm.loadStart.year) ?: vm.today.year
    }
    val rows = remember(group, year, byYears, vm.now, vm.data) {
        if (byYears) {
            (vm.today.year downTo firstYear).map { y ->
                val p = Period(PeriodType.YEAR, LocalDate.of(y, 1, 1), vm.period.customStart, vm.period.customEndInclusive)
                BreakdownRow(y.toString(), Stats.aggregate(group.instances, vm.periodWindow(p)), p)
            }
        } else {
            (1..12).map { m ->
                val p = Period(PeriodType.MONTH, LocalDate.of(year, m, 1), vm.period.customStart, vm.period.customEndInclusive)
                BreakdownRow(Fmt.monthShort(m), Stats.aggregate(group.instances, vm.periodWindow(p)), p)
            }
        }
    }
    val total = remember(rows) {
        val days = HashSet<LocalDate>()
        rows.forEach { days.addAll(it.agg.dates) }
        Triple(days.size, rows.sumOf { it.agg.events }, rows.sumOf { it.agg.minutes })
    }
    val byHours = rows.any { it.agg.minutes > 0 }
    val maxValue = rows.maxOfOrNull { if (byHours) it.agg.minutes else it.agg.days.toLong() }?.coerceAtLeast(1) ?: 1

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!byYears) {
                    IconButton(onClick = { year-- }, modifier = Modifier.size(36.dp)) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                    Text("$year", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { year++ }, enabled = year < vm.today.year + 1, modifier = Modifier.size(36.dp)) {
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Text("По годам", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                FilterChip(selected = !byYears, onClick = { byYears = false }, label = { Text("Месяцы") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = byYears, onClick = { byYears = true }, label = { Text("Годы") })
            }
            // Шапка таблицы
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("", Modifier.width(44.dp))
                Spacer(Modifier.weight(1f))
                HeaderCell("дни")
                HeaderCell("соб.")
                HeaderCell("часы")
            }
            rows.forEach { r ->
                val a = r.agg
                val value = if (byHours) a.minutes else a.days.toLong()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = a.events > 0) { vm.period = r.period }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.label, Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f).height(12.dp)) {
                        if (value > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(value.toFloat() / maxValue)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color),
                            )
                        }
                    }
                    ValueCell(if (a.events == 0) "—" else a.days.toString())
                    ValueCell(if (a.events == 0) "—" else a.events.toString())
                    ValueCell(if (a.events == 0) "—" else Fmt.hours(a.minutes))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (byYears) "Всего" else "За год", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                ValueCell(total.first.toString(), bold = true)
                ValueCell(total.second.toString(), bold = true)
                ValueCell(Fmt.hours(total.third), bold = true)
            }
            Text(
                "Нажмите на строку, чтобы открыть этот период. Пересекающиеся по времени события одного направления " +
                    "не удваивают часы; события «весь день» дают дни, но не часы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String) {
    Text(
        text,
        Modifier.width(48.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

@Composable
private fun ValueCell(text: String, bold: Boolean = false) {
    Text(
        text,
        Modifier.width(48.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun EventRow(e: EventInstance, vm: MainViewModel, onClick: () -> Unit) {
    val day = Stats.firstDay(e, vm.zone)
    val timePart = if (e.allDay) {
        val lastDay = Stats.lastDay(e, vm.zone)
        if (lastDay == day) "весь день" else "до ${Fmt.date(lastDay)}"
    } else {
        val s = Instant.ofEpochMilli(e.begin).atZone(vm.zone).toLocalTime()
        val en = Instant.ofEpochMilli(e.end).atZone(vm.zone).toLocalTime()
        "${Fmt.time(s)}–${Fmt.time(en)} · ${Fmt.duration((e.end - e.begin) / 60_000L)}"
    }
    val ago = if (Stats.ongoing(e, vm.now, vm.today)) "идёт сейчас" else Fmt.ago(Stats.lastDay(e, vm.zone), vm.today)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${Fmt.dateWithDow(day)} · $timePart",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(ago, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

