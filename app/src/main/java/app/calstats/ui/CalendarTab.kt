@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.calstats.EventInstance
import app.calstats.Stats
import java.time.Instant
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import app.calstats.CalView
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Sobriety
import java.time.LocalDate

val SOBER_COLOR = Color(0xFF2FA36B)
val DRANK_COLOR = Color(0xFFD64545)

/** Цвет текста поверх цвета события: на светлых — тёмный, на тёмных — белый. */
fun onEventColor(c: Int): Color = if (ColorUtils.calculateLuminance(c) > 0.6) Color(0xFF1F1F1F) else Color.White

/**
 * Модуль «Календарь»: сверху месяц (смахните вверх — свернётся до дат и цветных полос),
 * снизу детализация выбранного дня с переключением режима.
 */
@Composable
fun CalendarTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    val visibleEvents = remember(vm.instances, vm.calendars) {
        val ids = vm.visibleCalendarIds()
        vm.instances.filter { it.calendarId in ids }
    }
    var moreMenu by remember { mutableStateOf(false) }
    val d = vm.selectedDate
    val title = Fmt.monthName(d.monthValue) + if (d.year != vm.today.year) " ${d.year}" else ""
    val collapsed = vm.monthCollapsed

    // Прокрутка детализации вверх сворачивает месяц, прокрутка вниз до упора — разворачивает.
    val nested = remember(vm) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f && !vm.monthCollapsed) {
                    vm.setMonthCollapsed(true)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 12f && vm.monthCollapsed && source == NestedScrollSource.UserInput) {
                    vm.setMonthCollapsed(false)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pickDate(ctx, vm.selectedDate) { vm.selectedDate = it } }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Icon(Icons.Filled.ArrowDropDown, "Перейти к дате")
                    }
                },
                actions = {
                    TodayButton(vm.today.dayOfMonth) { vm.selectedDate = vm.today }
                    IconButton(onClick = { vm.setMonthCollapsed(!collapsed) }) {
                        Icon(if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, if (collapsed) "Развернуть месяц" else "Свернуть месяц")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Синхронизировать") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = {
                                    moreMenu = false
                                    vm.syncNow()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = {
                                    moreMenu = false
                                    vm.push(Screen.Settings)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.push(Screen.Edit(presetDate = vm.selectedDate)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Filled.Add, "Новое событие") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().nestedScroll(nested)) {
            if (vm.loading && !vm.loadedOnce) LinearProgressIndicator(Modifier.fillMaxWidth())
            // Месяц: свайп вверх/вниз по нему сворачивает и разворачивает
            var drag by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -40f) vm.setMonthCollapsed(true)
                            if (drag > 40f) vm.setMonthCollapsed(false)
                        },
                        onVerticalDrag = { _, dy -> drag += dy },
                    )
                },
            ) {
                MonthPane(vm, visibleEvents, collapsed)
            }
            // Ручка и режимы детализации
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxSize().padding(top = 6.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setMonthCollapsed(!collapsed) }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { drag = 0f },
                                    onDragEnd = {
                                        if (drag < -30f) vm.setMonthCollapsed(true)
                                        if (drag > 30f) vm.setMonthCollapsed(false)
                                    },
                                    onVerticalDrag = { _, dy -> drag += dy },
                                )
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CalView.entries.forEach { v ->
                            FilterChip(selected = vm.calView == v, onClick = { vm.calView = v }, label = { Text(v.title) })
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (vm.calView) {
                            CalView.LIST -> DayList(vm, vm.selectedDate, byDay[vm.selectedDate].orEmpty())
                            CalView.SCHEDULE -> ScheduleView(vm, byDay)
                            else -> TimelineView(vm, byDay, vm.calView.days)
                        }
                    }
                }
            }
        }
    }
}

/** Список выбранного дня: отметка алкоголя, события, мои задачи со сроком. */
@Composable
private fun DayList(vm: MainViewModel, date: LocalDate, events: List<EventInstance>) {
    val sorted = remember(events) { events.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
    val issues = remember(vm.work, date) { vm.myIssuesByDue()[date].orEmpty().sortedBy { it.done } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        if (!date.isAfter(vm.today)) {
            item(key = "drink") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { DrinkRow(vm, date, divider = false) }
            }
        }
        if (sorted.isEmpty() && issues.isEmpty()) {
            item {
                Text(
                    "Свободный день. Нажмите «+», чтобы что-нибудь запланировать.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(sorted, key = { "e${it.eventId}_${it.begin}" }) { e -> DayEventCard(vm, e, date) }
        if (issues.isNotEmpty()) {
            item { Text("Задачи на этот день", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
            items(issues, key = { "i${it.id}" }) { i ->
                Card(
                    onClick = { vm.push(Screen.IssueDetail(i.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TaskCheck(i.done, null) { vm.toggleIssueDone(i) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                i.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (i.done) TextDecoration.LineThrough else null,
                            )
                            Text(vm.work.key(i), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(i.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventCard(vm: MainViewModel, e: EventInstance, day: LocalDate) {
    val time = if (e.allDay) {
        val first = Stats.firstDay(e, vm.zone)
        val last = Stats.lastDay(e, vm.zone)
        if (first == last) "Весь день" else "Весь день · ${Fmt.dateNoYear(first)} – ${Fmt.dateNoYear(last)}"
    } else {
        val s = Instant.ofEpochMilli(e.begin).atZone(vm.zone)
        val en = Instant.ofEpochMilli(e.end).atZone(vm.zone)
        val from = if (s.toLocalDate() == day) Fmt.time(s.toLocalTime()) else Fmt.dateNoYear(s.toLocalDate()) + " " + Fmt.time(s.toLocalTime())
        val to = if (en.toLocalDate() == day) Fmt.time(en.toLocalTime()) else Fmt.dateNoYear(en.toLocalDate()) + " " + Fmt.time(en.toLocalTime())
        "$from – $to"
    }
    val direction = vm.directionName(e.color)
    Card(
        onClick = { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(e.color)),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).weight(1f)) {
                Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (direction != null) {
                    Text(direction, style = MaterialTheme.typography.labelMedium, color = Color(e.color), modifier = Modifier.padding(top = 2.dp))
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

/** Кнопка «Сегодня» — квадратик с числом, как в Google Календаре. */
@Composable
private fun TodayButton(day: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.8.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Компактная строка «Алкоголь: Не пил / Пил · N дней без алкоголя» для выбранного дня. */
@Composable
fun DrinkRow(vm: MainViewModel, date: LocalDate, divider: Boolean = true) {
    if (date.isAfter(vm.today)) return
    val mark = vm.drinks[date]
    val summary = remember(vm.drinks, vm.today) { Sobriety.summary(vm.drinks, vm.today) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Алкоголь", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            FilterChip(
                selected = mark == false,
                onClick = { vm.setDrink(date, if (mark == false) null else false) },
                label = { Text("Не пил") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SOBER_COLOR, selectedLabelColor = Color.White),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = mark == true,
                onClick = { vm.setDrink(date, if (mark == true) null else true) },
                label = { Text("Пил") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DRANK_COLOR, selectedLabelColor = Color.White),
            )
        }
        val streak = summary.streak
        if (streak != null) {
            Text(
                Sobriety.streakText(summary) +
                    (summary.lastDrank?.let { " · последний раз ${Fmt.dateNoYear(it)}" } ?: "") +
                    if (summary.longest > 0) " · рекорд ${summary.longest}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (streak > 0) SOBER_COLOR else DRANK_COLOR,
            )
        }
    }
    if (divider) HorizontalDivider()
}
