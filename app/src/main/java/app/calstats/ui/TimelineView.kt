package app.calstats.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.CalView
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

private const val TL_PAGES = 4001
private const val TL_START = 2000
private val HOUR = 56.dp
private val GUTTER = 48.dp
private const val DAY_MS = 86_400_000L

/** Шкала времени на 1, 3 или 7 дней — как виды «День», «3 дня», «Неделя» в Google. */
@Composable
fun TimelineView(vm: MainViewModel, byDay: Map<LocalDate, List<EventInstance>>, days: Int) {
    key(days) {
        val base = remember { if (days == 7) LocalDate.now().with(DayOfWeek.MONDAY) else LocalDate.now() }
        fun pageStart(page: Int): LocalDate =
            if (days == 7) base.plusWeeks((page - TL_START).toLong())
            else base.plusDays((page - TL_START).toLong() * days)
        fun pageOf(d: LocalDate): Int =
            if (days == 7) TL_START + ChronoUnit.WEEKS.between(base, d.with(DayOfWeek.MONDAY)).toInt()
            else TL_START + Math.floorDiv(ChronoUnit.DAYS.between(base, d), days.toLong()).toInt()

        val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { TL_PAGES }
        val scroll = rememberScrollState()
        val hourPx = with(LocalDensity.current) { HOUR.toPx() }

        LaunchedEffect(Unit) {
            val h = (LocalTime.now().hour - 1).coerceIn(0, 16)
            scroll.scrollTo((h * hourPx).toInt())
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                val s = pageStart(page)
                val sel = vm.selectedDate
                if (sel.isBefore(s) || !sel.isBefore(s.plusDays(days.toLong()))) {
                    val t = vm.today
                    vm.selectedDate = if (!t.isBefore(s) && t.isBefore(s.plusDays(days.toLong()))) t else s
                }
            }
        }
        LaunchedEffect(vm.selectedDate) {
            val target = pageOf(vm.selectedDate)
            if (target != pager.currentPage) pager.animateScrollToPage(target)
        }

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            TimelinePage(vm, pageStart(page), days, byDay, scroll)
        }
    }
}

private fun isAllDayRow(e: EventInstance) = e.allDay || e.end - e.begin >= DAY_MS

@Composable
private fun TimelinePage(
    vm: MainViewModel,
    start: LocalDate,
    days: Int,
    byDay: Map<LocalDate, List<EventInstance>>,
    scroll: ScrollState,
) {
    val dates = remember(start, days) { (0 until days).map { start.plusDays(it.toLong()) } }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        // Заголовки дней
        Row(Modifier.fillMaxWidth().padding(start = GUTTER, top = 2.dp, bottom = 4.dp)) {
            dates.forEach { d ->
                val isToday = d == vm.today
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = days > 1) {
                            vm.selectedDate = d
                            vm.calView = CalView.DAY
                        },
                    horizontalAlignment = if (days == 1) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        Fmt.weekdayShort(d.dayOfWeek.value - 1).uppercase(),
                        fontSize = 11.sp,
                        color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = if (days == 1) 6.dp else 0.dp),
                    )
                    Box(
                        Modifier
                            .size(if (days == 7) 30.dp else 38.dp)
                            .clip(CircleShape)
                            .background(if (isToday) scheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            fontSize = if (days == 7) 15.sp else 20.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) scheme.onPrimary else scheme.onSurface,
                        )
                    }
                }
            }
        }
        if (days == 1) DrinkRow(vm, start)

        // Весь день и многодневные
        val allDay = dates.map { d -> byDay[d].orEmpty().filter(::isAllDayRow) }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(start = GUTTER, end = 2.dp, bottom = 4.dp)) {
                allDay.forEach { list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.take(2).forEach { e ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(e.color))
                                    .clickable { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(e.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onEventColor(e.color))
                            }
                        }
                        if (list.size > 2) Text("+${list.size - 2}", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        // Сетка часов
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().height(HOUR * 24)) {
                Box(Modifier.width(GUTTER).fillMaxHeight()) {
                    for (h in 1..23) {
                        Text(
                            String.format("%02d:00", h),
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(GUTTER - 8.dp).offset(y = HOUR * h - 7.dp),
                        )
                    }
                }
                dates.forEach { d ->
                    DayColumn(
                        vm = vm,
                        day = d,
                        events = byDay[d].orEmpty().filterNot(::isAllDayRow),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private class Placed(val e: EventInstance, val startMin: Int, val endMin: Int) {
    var col = 0
    var cols = 1
}

/** Раскладка пересекающихся событий по колонкам, как в Google Календаре. */
private fun layoutDay(events: List<EventInstance>, day: LocalDate, zone: ZoneId): List<Placed> {
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val items = events.map {
        val s = max(it.begin, dayStart)
        val e = min(it.end, dayEnd)
        val sm = ((s - dayStart) / 60_000L).toInt()
        Placed(it, sm, max(((e - dayStart) / 60_000L).toInt(), sm + 20))
    }.sortedWith(compareBy<Placed>({ it.startMin }, { -it.endMin }))
    val cluster = mutableListOf<Placed>()
    val colEnds = mutableListOf<Int>()
    var clusterEnd = 0
    fun finish() {
        cluster.forEach { it.cols = colEnds.size.coerceAtLeast(1) }
        cluster.clear()
        colEnds.clear()
    }
    for (p in items) {
        if (cluster.isNotEmpty() && p.startMin >= clusterEnd) {
            finish()
            clusterEnd = 0
        }
        val idx = colEnds.indexOfFirst { it <= p.startMin }
        if (idx >= 0) {
            p.col = idx
            colEnds[idx] = p.endMin
        } else {
            p.col = colEnds.size
            colEnds += p.endMin
        }
        cluster += p
        clusterEnd = max(clusterEnd, p.endMin)
    }
    finish()
    return items
}

@Composable
private fun DayColumn(vm: MainViewModel, day: LocalDate, events: List<EventInstance>, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant.copy(alpha = 0.6f)
    val density = LocalDensity.current
    val placed = remember(events, day) { layoutDay(events, day, vm.zone) }
    BoxWithConstraints(
        modifier
            .drawBehind {
                val hp = HOUR.toPx()
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
                for (h in 1..23) drawLine(lineColor, Offset(0f, hp * h), Offset(size.width, hp * h), 1f)
            }
            .pointerInput(day) {
                detectTapGestures { off ->
                    val minutes = (off.y / with(density) { HOUR.toPx() } * 60).toInt()
                    val h = (minutes / 60).coerceIn(0, 23)
                    val m = if (minutes % 60 >= 30) 30 else 0
                    vm.push(Screen.Edit(presetStart = day.atTime(h, m)))
                }
            },
    ) {
        val colW = maxWidth
        placed.forEach { p ->
            val top = HOUR * (p.startMin / 60f)
            val height: Dp = (HOUR * ((p.endMin - p.startMin) / 60f)).coerceAtLeast(18.dp)
            val w = colW / p.cols
            val e = p.e
            Box(
                Modifier
                    .offset(x = w * p.col, y = top)
                    .width(w - 2.dp)
                    .height(height - 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(e.color))
                    .clickable { vm.push(Screen.Edit(e.eventId, e.begin, e.end)) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Column {
                    Text(
                        e.title,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onEventColor(e.color),
                        maxLines = if (height >= 48.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (height >= 34.dp) {
                        val s = Instant.ofEpochMilli(e.begin).atZone(vm.zone).toLocalTime()
                        val en = Instant.ofEpochMilli(e.end).atZone(vm.zone).toLocalTime()
                        Text(
                            "${Fmt.time(s)}–${Fmt.time(en)}",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = onEventColor(e.color).copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Линия текущего времени
        if (day == vm.today) {
            val now = LocalTime.now()
            val y = HOUR * ((now.hour * 60 + now.minute) / 60f)
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(Color(0xFFEA4335)))
            Box(
                Modifier
                    .offset(x = (-5).dp, y = y - 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEA4335)),
            )
        }
    }
}
