@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.GroupStats
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Stats

@Composable
fun MainScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val stats = remember(vm.instances, vm.data, vm.period, vm.calendars, vm.now) {
        val w = vm.periodWindow()
        Stats.groupStats(vm.groups(), w, vm.now, vm.today)
            .sortedWith(compareByDescending<GroupStats> { it.agg.minutes }.thenByDescending { it.agg.days }
                .thenByDescending { it.last?.begin ?: 0L })
    }
    val total = remember(stats) {
        val all = HashSet<java.time.LocalDate>()
        stats.forEach { all.addAll(it.agg.dates) }
        Triple(all.size, stats.sumOf { it.agg.events }, stats.sumOf { it.agg.minutes })
    }
    val unnamed = stats.count { !it.group.named }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Направления")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.syncNow() }) { Icon(Icons.Filled.Refresh, "Синхронизировать") }
                    IconButton(onClick = { vm.push(Screen.Colors) }) { Icon(Icons.Filled.Edit, "Названия направлений") }
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Настройки") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.push(Screen.Edit()) }) { Icon(Icons.Filled.Add, "Новое событие") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PeriodSelector(vm.period, vm.loadStart) { vm.period = it }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                            StatBox(total.first.toString(), "дней", Modifier.weight(1f))
                            StatBox(total.second.toString(), "событий", Modifier.weight(1f))
                            StatBox(Fmt.hours(total.third), "часов", Modifier.weight(1f))
                        }
                    }
                }
                if (unnamed > 0) {
                    item {
                        Card(
                            onClick = { vm.push(Screen.Colors) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Text(
                                "Цветов без названия: $unnamed. Нажмите, чтобы назначить им направления.",
                                Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                if (vm.loadedOnce && stats.isEmpty()) {
                    item {
                        Text(
                            "В календаре пока нет событий. Добавьте событие кнопкой «+» или проверьте, " +
                                "какие календари включены в настройках.",
                            Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(stats, key = { it.group.key }) { s ->
                    DirectionCard(s, vm) { vm.push(Screen.Direction(s.group.key)) }
                }
            }
        }
    }
}

@Composable
private fun DirectionCard(s: GroupStats, vm: MainViewModel, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(s.group.color, 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    s.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (s.group.named) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                StatBox(s.agg.days.toString(), "дней", Modifier.weight(1f))
                StatBox(s.agg.events.toString(), "событий", Modifier.weight(1f))
                StatBox(Fmt.hours(s.agg.minutes), "часов", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(lastText(s, vm), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun lastText(s: GroupStats, vm: MainViewModel): String {
    val last = s.last
    val part1 = when {
        last == null -> "Ещё не было"
        Stats.ongoing(last, vm.now, vm.today) -> "Идёт сейчас: ${last.title}"
        else -> {
            val day = Stats.lastDay(last, vm.zone)
            "Последнее: ${Fmt.date(day)} · ${Fmt.ago(day, vm.today)}"
        }
    }
    val next = s.next ?: return part1
    val nd = Stats.firstDay(next, vm.zone)
    return part1 + "\nСледующее: ${Fmt.dateNoYear(nd)} (${Fmt.ago(nd, vm.today)})"
}
