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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private sealed interface SchedItem {
    data class MonthHeader(val month: YearMonth) : SchedItem
    data class Day(val date: LocalDate, val events: List<EventInstance>, val tasks: List<TaskItem>) : SchedItem
}

/** «Расписание» как в Google: список дней с событиями, заголовки месяцев, сегодня всегда видно. */
@Composable
fun ScheduleView(vm: MainViewModel, byDay: Map<LocalDate, List<EventInstance>>) {
    val today = vm.today
    val tasksByDay = remember(vm.tasks) { vm.tasks.filter { it.due != null }.groupBy { it.due!! } }
    val items = remember(byDay, tasksByDay, today) {
        val from = today.minusDays(60)
        val dates = (byDay.keys + tasksByDay.keys + today).filter { !it.isBefore(from) }.toSortedSet()
        val out = mutableListOf<SchedItem>()
        var lastMonth: YearMonth? = null
        for (d in dates) {
            val m = YearMonth.from(d)
            if (m != lastMonth) {
                out += SchedItem.MonthHeader(m)
                lastMonth = m
            }
            val ev = byDay[d].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.begin }))
            out += SchedItem.Day(d, ev, tasksByDay[d].orEmpty())
        }
        out
    }
    fun indexOf(d: LocalDate): Int {
        val i = items.indexOfFirst { it is SchedItem.Day && !it.date.isBefore(d) }
        return if (i < 0) items.lastIndex.coerceAtLeast(0) else i
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(vm.selectedDate))
    LaunchedEffect(vm.selectedDate) { list.animateScrollToItem(indexOf(vm.selectedDate)) }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(items, key = {
            when (it) {
                is SchedItem.MonthHeader -> "m${it.month}"
                is SchedItem.Day -> "d${it.date}"
            }
        }) { item ->
            when (item) {
                is SchedItem.MonthHeader -> MonthBanner(item.month)
                is SchedItem.Day -> DayBlock(vm, item)
            }
        }
    }
}

@Composable
private fun MonthBanner(m: YearMonth) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "${Fmt.monthName(m.monthValue)} ${m.year}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}

@Composable
private fun DayBlock(vm: MainViewModel, item: SchedItem.Day) {
    val d = item.date
    val isToday = d == vm.today
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(
            Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    vm.selectedDate = d
                    vm.calView = app.calstats.CalView.DAY
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Fmt.weekdayShort(d.dayOfWeek.value - 1).uppercase(),
                fontSize = 11.sp,
                color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isToday) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.dayOfMonth.toString(),
                    fontSize = 18.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) scheme.onPrimary else scheme.onSurface,
                )
            }
            vm.drinks[d]?.let { drank ->
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .size(width = 16.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (drank) DRANK_COLOR else SOBER_COLOR),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.events.isEmpty() && item.tasks.isEmpty()) {
                Text(
                    "Ничего не запланировано",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            item.events.forEach { e -> ScheduleEvent(vm, e, d) }
            item.tasks.sortedBy { it.done }.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskCheck(t.done, t.color) { vm.toggleTask(t) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleEvent(vm: MainViewModel, e: EventInstance, day: LocalDate) {
    val fg = onEventColor(e.color)
    val time = if (e.allDay) {
        null
    } else {
        val s = Instant.ofEpochMilli(e.begin).atZone(vm.zone)
        val en = Instant.ofEpochMilli(e.end).atZone(vm.zone)
        val from = if (s.toLocalDate() == day) Fmt.time(s.toLocalTime()) else "…"
        val to = if (en.toLocalDate() == day) Fmt.time(en.toLocalTime()) else "…"
        "$from – $to"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(e.color))
            .clickable { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(e.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (time != null) Text(time, color = fg.copy(alpha = 0.85f), fontSize = 12.sp)
        vm.directionName(e.color)?.let { Text(it, color = fg.copy(alpha = 0.85f), fontSize = 11.sp) }
    }
}
