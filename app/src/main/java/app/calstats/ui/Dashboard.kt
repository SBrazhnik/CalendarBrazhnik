@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Period
import app.calstats.PeriodType
import app.calstats.R
import app.calstats.Screen
import app.calstats.Sobriety
import app.calstats.Stats
import app.calstats.Tab
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.Status
import app.calstats.work.WishStatus
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Главная: сводка со всех включённых модулей, быстрый запуск и общие настройки. */
@Composable
fun DashboardTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val name = vm.work.meName.takeIf { it.isNotBlank() && it != "Я" }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(greeting + (name?.let { ", $it" } ?: ""), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            Fmt.dayTitle(vm.today, vm.today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.push(Screen.Modules) }) { Icon(painterResource(R.drawable.ic_apps), "Модули") }
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Настройки") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { QuickLaunch(vm) }
            vm.enabledModules.forEach { t ->
                item(key = t.name) {
                    when (t) {
                        Tab.CALENDAR -> CalendarSummary(vm)
                        Tab.TASKS -> TasksSummary(vm)
                        Tab.MONEY -> MoneySummary(vm)
                        Tab.ANALYSIS -> AnalysisSummary(vm)
                        Tab.WISHES -> WishesSummary(vm)
                        Tab.NOTES -> NotesSummary(vm)
                        Tab.HOME -> {}
                    }
                }
            }
            item {
                Card(
                    onClick = { vm.push(Screen.Settings) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Общие настройки", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Синхронизация, резервная копия, уведомления",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Плитки всех включённых модулей + «Настроить». */
@Composable
private fun QuickLaunch(vm: MainViewModel) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        vm.enabledModules.forEach { t ->
            Tile(Modifier.weight(1f), t.title, { ModuleBadge(t, 44.dp) }) { vm.tab = t }
        }
        Tile(Modifier.weight(1f), "Модули", {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_apps), null) }
        }) { vm.push(Screen.Modules) }
    }
}

@Composable
private fun Tile(modifier: Modifier, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Карточка модуля на главной. */
@Composable
private fun SummaryCard(t: Tab, vm: MainViewModel, subtitle: String? = null, onClick: () -> Unit = { vm.tab = t }, content: @Composable () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleBadge(t, 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CalendarSummary(vm: MainViewModel) {
    val today = vm.today
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    val now = System.currentTimeMillis()
    val todays = byDay[today].orEmpty().filter { it.allDay || it.end > now }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    val tomorrow = byDay[today.plusDays(1)].orEmpty().size
    val summary = remember(vm.drinks, vm.today) { Sobriety.summary(vm.drinks, vm.today) }
    SummaryCard(Tab.CALENDAR, vm, subtitle = if (todays.isEmpty()) null else "${todays.size} сегодня", onClick = {
        vm.selectedDate = today
        vm.tab = Tab.CALENDAR
    }) {
        if (todays.isEmpty()) Muted("На сегодня больше ничего нет" + if (tomorrow > 0) " · завтра событий: $tomorrow" else "")
        todays.take(3).forEach { e ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 4.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (e.allDay) "весь день" else Fmt.time(Instant.ofEpochMilli(e.begin).atZone(vm.zone).toLocalTime()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp),
                )
                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (todays.size > 3) Muted("и ещё ${todays.size - 3}")
        summary.streak?.let { s ->
            Spacer(Modifier.height(8.dp))
            Text(
                Sobriety.streakText(summary) + if (summary.longest > 0) " · рекорд ${summary.longest}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (s > 0) SOBER_COLOR else DRANK_COLOR,
            )
        }
    }
}

@Composable
private fun TasksSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val mine = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
    val overdue = mine.count { it.dueDate?.isBefore(today) == true }
    val inWork = mine.count { it.status == Status.IN_PROGRESS }
    val focus = mine.sortedWith(compareBy({ it.dueDate ?: java.time.LocalDate.MAX }, { it.priority.ordinal })).take(3)
    SummaryCard(Tab.TASKS, vm, subtitle = "${mine.size} открыто", onClick = {
        vm.selectTaskView("ALL")
        vm.tab = Tab.TASKS
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniNumber(mine.size.toString(), "мои открытые")
            MiniNumber(inWork.toString(), "в работе")
            MiniNumber(overdue.toString(), "просрочено", if (overdue > 0) MaterialTheme.colorScheme.error else null)
        }
        if (focus.isNotEmpty()) Spacer(Modifier.height(8.dp))
        focus.forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskCheck(i.done, null) { vm.toggleIssueDone(i) }
                Spacer(Modifier.width(10.dp))
                Text(
                    i.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (i.done) TextDecoration.LineThrough else null,
                )
                i.dueDate?.let {
                    Text(
                        Fmt.dueShort(it, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (mine.isEmpty()) Muted("Открытых задач нет")
    }
}

@Composable
private fun MiniNumber(value: String, label: String, color: Color? = null) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoneySummary(vm: MainViewModel) {
    val w = vm.work
    val nets = remember(w.expenses, w.books) { w.books.map { b -> SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L } }
    val owedToMe = nets.filter { it > 0 }.sum()
    val iOwe = -nets.filter { it < 0 }.sum()
    SummaryCard(Tab.MONEY, vm, subtitle = "${w.books.count { !it.archived }} счетов") {
        if (owedToMe == 0L && iOwe == 0L) {
            Muted(if (w.books.isEmpty()) "Создайте счёт для общих трат" else "Все в расчёте")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniNumber(SplitMath.money(owedToMe), "вам должны", if (owedToMe > 0) Color(0xFF22A06B) else null)
                MiniNumber(SplitMath.money(iOwe), "вы должны", if (iOwe > 0) Color(0xFFD64545) else null)
            }
        }
    }
}

@Composable
private fun AnalysisSummary(vm: MainViewModel) {
    val top = remember(vm.instances, vm.data, vm.calendars, vm.now) {
        val p = Period(PeriodType.MONTH, vm.today, vm.period.customStart, vm.period.customEndInclusive)
        Stats.groupStats(vm.groups(), vm.periodWindow(p), vm.now, vm.today)
            .filter { it.agg.events > 0 }
            .sortedByDescending { it.agg.minutes }
            .take(4)
    }
    val max = top.maxOfOrNull { it.agg.minutes }?.coerceAtLeast(1) ?: 1
    SummaryCard(Tab.ANALYSIS, vm, subtitle = Fmt.monthName(vm.today.monthValue)) {
        if (top.isEmpty()) Muted("В этом месяце пока нет событий по направлениям")
        top.forEach { g ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.group.name, Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxWidth(g.agg.minutes.toFloat() / max)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(g.group.color)),
                    )
                }
                Text("${Fmt.hours(g.agg.minutes)} ч", Modifier.width(56.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WishesSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val upcoming = w.wishLists.filter { !it.archived }
        .mapNotNull { l -> nextOccurrence(l.localDate, today, l.yearly)?.let { l to it } }
        .sortedBy { it.second }
        .take(2)
    val wanted = w.wishes.count { it.status == WishStatus.WANTED }
    SummaryCard(Tab.WISHES, vm, subtitle = "$wanted желаний") {
        if (upcoming.isEmpty()) Muted(if (w.wishLists.isEmpty()) "Соберите свой список желаний или подарков близким" else "Ближайших поводов нет")
        upcoming.forEach { (l, d) ->
            val days = ChronoUnit.DAYS.between(today, d)
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(l.occasion, Fmt.dateNoYear(d)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (days == 0L) "сегодня" else "через ${Fmt.days(days)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (days <= 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotesSummary(vm: MainViewModel) {
    val notes = vm.work.notes.filter { !it.archived && !it.isEmpty }
        .sortedWith(compareByDescending<app.calstats.work.Note> { it.pinned }.thenByDescending { it.updated })
        .take(3)
    SummaryCard(Tab.NOTES, vm, subtitle = "${vm.work.notes.count { !it.archived }} заметок") {
        if (notes.isEmpty()) Muted("Заметок пока нет")
        notes.forEach { n ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    vm.tab = Tab.NOTES
                    vm.push(Screen.NoteEdit(n.id))
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(n.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(10.dp))
                Text(
                    n.title.ifBlank { n.asText().lineSequence().firstOrNull().orEmpty() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Ближайшая дата повода: для ежегодных (день рождения) — в этом или следующем году; прошедшие разовые — null. */
fun nextOccurrence(d: java.time.LocalDate?, today: java.time.LocalDate, yearly: Boolean): java.time.LocalDate? {
    if (d == null) return null
    if (!d.isBefore(today)) return d
    if (!yearly) return null
    var n = d.withYear(today.year)
    if (n.isBefore(today)) n = n.plusYears(1)
    return n
}
