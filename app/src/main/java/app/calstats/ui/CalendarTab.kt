@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.TaskItem
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGES = 1201
private const val START_PAGE = 600

@Composable
fun CalendarTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val today = vm.today
    val baseMonth = remember { YearMonth.now() }
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    val tasksByDay = remember(vm.tasks) { vm.tasks.filter { it.due != null }.groupBy { it.due!! } }
    val pager = rememberPagerState(
        initialPage = START_PAGE + ChronoUnit.MONTHS.between(baseMonth, YearMonth.from(vm.selectedDate)).toInt(),
    ) { PAGES }
    val scope = rememberCoroutineScope()
    val shownMonth = baseMonth.plusMonths((pager.currentPage - START_PAGE).toLong())

    // Листаем месяцы — выбранный день переходит в показанный месяц.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val m = baseMonth.plusMonths((page - START_PAGE).toLong())
            if (YearMonth.from(vm.selectedDate) != m) {
                vm.selectedDate = if (m == YearMonth.from(vm.today)) vm.today else m.atDay(1)
            }
        }
    }

    fun goTo(date: LocalDate) {
        vm.selectedDate = date
        val page = START_PAGE + ChronoUnit.MONTHS.between(baseMonth, YearMonth.from(date)).toInt()
        if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.monthName(shownMonth.monthValue), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            shownMonth.year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                },
                actions = {
                    if (vm.selectedDate != today) {
                        AssistChip(onClick = { goTo(today) }, label = { Text("Сегодня") })
                    }
                    IconButton(onClick = { vm.syncNow() }) { Icon(Icons.Filled.Refresh, "Синхронизировать") }
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Настройки") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.push(Screen.Edit(presetDate = vm.selectedDate)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Событие") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.loading && !vm.loadedOnce) LinearProgressIndicator(Modifier.fillMaxWidth())
            WeekHeader()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * 6 + 8.dp),
                beyondViewportPageCount = 1,
            ) { page ->
                val month = baseMonth.plusMonths((page - START_PAGE).toLong())
                MonthGrid(
                    month = month,
                    today = today,
                    selected = vm.selectedDate,
                    byDay = byDay,
                    tasksByDay = tasksByDay,
                    onSelect = { goTo(it) },
                )
            }
            DayAgenda(
                vm = vm,
                date = vm.selectedDate,
                events = byDay[vm.selectedDate].orEmpty(),
                tasks = tasksByDay[vm.selectedDate].orEmpty(),
            )
        }
    }
}

private val CELL_HEIGHT = 52.dp

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        for (i in 0..6) {
            Text(
                Fmt.weekdayShort(i),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (i >= 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<EventInstance>>,
    tasksByDay: Map<LocalDate, List<TaskItem>>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val gridStart = first.with(DayOfWeek.MONDAY).let { if (it.isAfter(first)) it.minusWeeks(1) else it }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT)) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        colors = byDay[date].orEmpty().map { it.color }.distinct().take(3),
                        hasTasks = tasksByDay[date].orEmpty().any { !it.done },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    colors: List<Int>,
    hasTasks: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (isSelected) scheme.primary else Color.Transparent, label = "bg")
    val fg = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        else -> scheme.onSurface
    }
    Column(
        modifier
            .alpha(if (inMonth) 1f else 0.35f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            colors.forEach { c -> Box(Modifier.size(5.dp).clip(CircleShape).background(Color(c))) }
            if (hasTasks) Box(Modifier.size(5.dp).border(1.dp, scheme.onSurfaceVariant, CircleShape))
        }
    }
}

@Composable
private fun DayAgenda(vm: MainViewModel, date: LocalDate, events: List<EventInstance>, tasks: List<TaskItem>) {
    val sorted = remember(events) { events.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().animateContentSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(Fmt.dayTitle(date, vm.today), style = MaterialTheme.typography.titleLarge)
                    Text(
                        Fmt.relativeDay(date, vm.today) + " · " +
                            if (sorted.isEmpty()) "нет событий"
                            else "${sorted.size} ${Fmt.plural(sorted.size.toLong(), "событие", "события", "событий")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sorted.isEmpty() && tasks.isEmpty()) {
                item {
                    Text(
                        "Свободный день. Нажмите «Событие», чтобы что-нибудь запланировать.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(sorted, key = { "e${it.eventId}_${it.begin}" }) { e ->
                EventCard(vm, e, date) { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) }
            }
            if (tasks.isNotEmpty()) {
                item {
                    Text(
                        "Задачи",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(tasks.sortedBy { it.done }, key = { "t${it.id}" }) { t ->
                    Card(
                        onClick = { vm.tab = app.calstats.Tab.TASKS },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TaskCheck(t.done, t.color) { vm.toggleTask(t) }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                t.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(vm: MainViewModel, e: EventInstance, day: LocalDate, onClick: () -> Unit) {
    val zone = vm.zone
    val time = if (e.allDay) {
        "Весь день"
    } else {
        val s = Instant.ofEpochMilli(e.begin).atZone(zone)
        val en = Instant.ofEpochMilli(e.end).atZone(zone)
        val from = if (s.toLocalDate() == day) Fmt.time(s.toLocalTime()) else "…"
        val to = if (en.toLocalDate() == day) Fmt.time(en.toLocalTime()) else "…"
        "$from – $to"
    }
    val direction = vm.directionName(e.color)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(e.color)),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).weight(1f)) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (direction != null) {
                    Text(
                        direction,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(e.color),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!e.allDay && e.end > e.begin) {
                Text(
                    Fmt.duration((e.end - e.begin) / 60_000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 14.dp),
                )
            }
        }
    }
}
