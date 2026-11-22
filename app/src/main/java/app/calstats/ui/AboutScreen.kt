@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Changelog
import app.calstats.MainViewModel
import app.calstats.Updater
import app.calstats.Usage
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Окно «Что нового» после обновления. */
@Composable
fun WhatsNewDialog(vm: MainViewModel) {
    val ctx = LocalContext.current
    val list = remember { Changelog.unseen(ctx) }
    if (list.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            Changelog.markSeen(ctx)
            vm.whatsNewChecked = true
        }
        return
    }
    fun close() {
        Changelog.markSeen(ctx)
        vm.whatsNewChecked = true
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Что нового", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.take(3).forEach { e -> ChangeEntry(e) }
            }
        },
        confirmButton = { TextButton(onClick = { close() }) { Text("Отлично") } },
        dismissButton = { TextButton(onClick = { close(); vm.push(app.calstats.Screen.About) }) { Text("Вся история") } },
    )
}

@Composable
private fun ChangeEntry(e: Changelog.Entry) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(e.title, style = MaterialTheme.typography.titleMedium)
        Text("Версия ${e.build} · ${e.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        e.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** «О приложении»: версия, история изменений, статистика использования. */
@Composable
fun AboutScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val st = remember { Usage.stats(ctx) }
    val w = vm.work
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("О приложении", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Зелёный ритм", style = MaterialTheme.typography.displaySmall)
                    Text("Версия ${Updater.versionName(ctx)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Статистика") })
                        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("История версий") })
                    }
                }
            }
            if (tab == 1) {
                items(Changelog.entries) { e ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Box(Modifier.padding(16.dp)) { ChangeEntry(e) }
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("${st.days}", "дней с приложением", Modifier.weight(1f))
                        Kpi("${st.streak}", "дней подряд · рекорд ${st.bestStreak}", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("${st.opens}", "запусков", Modifier.weight(1f))
                        Kpi(st.first?.let { "${ChronoUnit.DAYS.between(it, vm.today) + 1}" } ?: "—", "дней с первого запуска", Modifier.weight(1f))
                    }
                }
                item {
                    SectionCard("Активность за 8 недель") {
                        UsageGrid(st.history, vm.today)
                    }
                }
                if (st.modules.isNotEmpty()) {
                    item {
                        SectionCard("Чаще всего открываете") {
                            val max = st.modules.first().second.toFloat()
                            st.modules.take(8).forEach { (t, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CardIcon(t, 18.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t.title, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                        Box(Modifier.fillMaxWidth(n / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(moduleColor(t)))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("$n", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard("Ваши данные") {
                        val rows = listOf(
                            "Задачи" to w.issues.size,
                            "Заметки" to w.notes.count { !it.archived },
                            "Операции в финансах" to w.transactions.size,
                            "Долги" to w.debts.size,
                            "Привычки · отметки" to w.habitLogs.size,
                            "Записи питания" to w.foodLog.size,
                            "Документы" to w.documents.size,
                            "Желания" to w.wishes.size,
                            "Дни учёта алкоголя" to vm.drinks.size,
                        ).filter { it.second > 0 }
                        if (rows.isEmpty()) Text("Пока пусто", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rows.forEach { (k, v) ->
                            Row {
                                Text(k, Modifier.weight(1f))
                                Text("$v", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(
                            "Статистика хранится только на этом телефоне и никуда не отправляется.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Сетка дней (как у GitHub): закрашен — день, когда открывали приложение. */
@Composable
private fun UsageGrid(days: Set<LocalDate>, today: LocalDate) {
    val start = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(7)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("пн", "", "ср", "", "пт", "", "вс").forEach { Text(it, fontSize = 9.sp, modifier = Modifier.height(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        (0 until 8).forEach { wk ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val day = start.plusDays(wk * 7L + d)
                    val on = day in days
                    Box(
                        Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(
                            when {
                                day.isAfter(today) -> MaterialTheme.colorScheme.surfaceContainerLow
                                on -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ),
                    )
                }
            }
        }
    }
    Text("Закрашены дни, когда вы открывали приложение", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
}
