@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
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
        val year = vm.period.anchor.year
        val monthly = remember(group, year, vm.now, vm.data) {
            (1..12).map { m ->
                val p = Period(PeriodType.MONTH, java.time.LocalDate.of(year, m, 1), vm.period.customStart, vm.period.customEndInclusive)
                Stats.aggregate(group.instances, vm.periodWindow(p))
            }
        }
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
            item { MonthlyCard(year, monthly, Color(group.color)) }
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

@Composable
private fun MonthlyCard(year: Int, monthly: List<Agg>, color: Color) {
    val byHours = monthly.any { it.minutes > 0 }
    val maxValue = monthly.maxOf { if (byHours) it.minutes else it.days.toLong() }.coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("По месяцам — $year", style = MaterialTheme.typography.titleMedium)
            monthly.forEachIndexed { idx, a ->
                val value = if (byHours) a.minutes else a.days.toLong()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.monthShort(idx + 1), Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f).height(14.dp)) {
                        if (value > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(value.toFloat() / maxValue)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color),
                            )
                        }
                    }
                    Text(
                        if (a.events == 0) "—" else "${Fmt.hours(a.minutes)} ч · ${a.days} дн",
                        Modifier.width(96.dp).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
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

