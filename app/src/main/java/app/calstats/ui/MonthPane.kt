package app.calstats.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val PAGES = 1201
private const val START = 600

/** Отрезок события внутри одной недели: с какого по какой столбец и на какой «дорожке». */
class WeekSeg(val e: EventInstance, val startCol: Int, val endCol: Int, val lane: Int, val cutLeft: Boolean, val cutRight: Boolean)

/** Раскладка событий недели по дорожкам: многодневные — одной длинной полосой через все дни. */
fun weekSegments(weekStart: LocalDate, events: List<EventInstance>, zone: ZoneId): List<WeekSeg> {
    val weekEnd = weekStart.plusDays(6)
    data class Raw(val e: EventInstance, val s: Int, val en: Int, val cutL: Boolean, val cutR: Boolean)
    val raws = events.mapNotNull { e ->
        val first = Stats.firstDay(e, zone)
        val last = Stats.lastDay(e, zone)
        if (last.isBefore(weekStart) || first.isAfter(weekEnd)) return@mapNotNull null
        val s = if (first.isBefore(weekStart)) weekStart else first
        val en = if (last.isAfter(weekEnd)) weekEnd else last
        Raw(e, ChronoUnit.DAYS.between(weekStart, s).toInt(), ChronoUnit.DAYS.between(weekStart, en).toInt(), first.isBefore(weekStart), last.isAfter(weekEnd))
    }.sortedWith(compareBy<Raw>({ it.s }, { -(it.en - it.s) }, { !it.e.allDay }, { it.e.begin }))
    val lanes = mutableListOf<BooleanArray>()
    return raws.map { r ->
        var lane = lanes.indexOfFirst { occ -> (r.s..r.en).none { occ[it] } }
        if (lane < 0) {
            lanes += BooleanArray(7)
            lane = lanes.lastIndex
        }
        for (c in r.s..r.en) lanes[lane][c] = true
        WeekSeg(r.e, r.s, r.en, lane, r.cutL, r.cutR)
    }
}

fun monthGridStart(month: YearMonth): LocalDate {
    val first = month.atDay(1)
    return first.with(DayOfWeek.MONDAY).let { if (it.isAfter(first)) it.minusWeeks(1) else it }
}

fun monthWeeks(month: YearMonth): Int =
    ((ChronoUnit.DAYS.between(monthGridStart(month), month.atEndOfMonth()) / 7) + 1).toInt()

/**
 * Верхняя часть модуля «Календарь»: месяц.
 * Развёрнут — полосы событий с названиями; свёрнут — только даты и тонкие цветные полосы.
 */
@Composable
fun MonthPane(vm: MainViewModel, events: List<EventInstance>, collapsed: Boolean) {
    val base = remember { YearMonth.now() }
    fun pageOf(d: LocalDate) = START + ChronoUnit.MONTHS.between(base, YearMonth.from(d)).toInt()
    val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { PAGES }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val m = base.plusMonths((page - START).toLong())
            if (YearMonth.from(vm.selectedDate) != m) {
                vm.selectedDate = if (m == YearMonth.from(vm.today)) vm.today else m.atDay(1)
            }
        }
    }
    LaunchedEffect(vm.selectedDate) {
        val target = pageOf(vm.selectedDate)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }

    val shown = base.plusMonths((pager.currentPage - START).toLong())
    val rowH by animateDpAsState(if (collapsed) 34.dp else 62.dp, label = "row")
    val height = rowH * monthWeeks(shown)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            for (i in 0..6) {
                Text(
                    Fmt.weekdayShort(i),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (i >= 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(height),
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val month = base.plusMonths((page - START).toLong())
            MonthGrid(vm, month, events, collapsed, rowH)
        }
    }
}

@Composable
private fun MonthGrid(vm: MainViewModel, month: YearMonth, events: List<EventInstance>, collapsed: Boolean, rowH: Dp) {
    val zone = vm.zone
    val gridStart = monthGridStart(month)
    val weeks = monthWeeks(month)
    val gridEnd = gridStart.plusDays(weeks * 7L - 1)
    // События, попадающие в сетку месяца (один раз на страницу).
    val inGrid = remember(events, month) {
        events.filter { e -> !Stats.lastDay(e, zone).isBefore(gridStart) && !Stats.firstDay(e, zone).isAfter(gridEnd) }
    }
    val segsByWeek = remember(inGrid, month) {
        (0 until weeks).map { w -> weekSegments(gridStart.plusWeeks(w.toLong()), inGrid, zone) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        for (w in 0 until weeks) {
            WeekRow(vm, gridStart.plusWeeks(w.toLong()), month, segsByWeek[w], collapsed, rowH)
        }
    }
}

@Composable
private fun WeekRow(vm: MainViewModel, weekStart: LocalDate, month: YearMonth, segs: List<WeekSeg>, collapsed: Boolean, rowH: Dp) {
    val scheme = MaterialTheme.colorScheme
    val numberH = if (collapsed) 20.dp else 24.dp
    val barH = if (collapsed) 3.dp else 13.dp
    val gap = if (collapsed) 2.dp else 2.dp
    val maxLanes = if (collapsed) 3 else 2
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowH)) {
        val colW = maxWidth / 7
        // Числа и клики по дням
        Row(Modifier.fillMaxSize()) {
            for (c in 0..6) {
                val date = weekStart.plusDays(c.toLong())
                val inMonth = YearMonth.from(date) == month
                val isToday = date == vm.today
                val isSelected = date == vm.selectedDate
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected && !collapsed) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.selectedDate = date }
                        .alpha(if (inMonth) 1f else 0.4f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(numberH)
                            .clip(CircleShape)
                            .background(if (isSelected) scheme.primary else Color.Transparent)
                            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            fontSize = if (collapsed) 11.sp else 12.sp,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSelected -> scheme.onPrimary
                                isToday -> scheme.primary
                                else -> scheme.onSurface
                            },
                        )
                    }
                    vm.drinks[date]?.let { drank ->
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 3.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (drank) DRANK_COLOR else SOBER_COLOR),
                        )
                    }
                }
            }
        }
        // Полосы событий
        val top = numberH + 3.dp
        segs.filter { it.lane < maxLanes }.forEach { s ->
            val color = Color(s.e.color)
            val left = colW * s.startCol + if (s.cutLeft) 0.dp else 2.dp
            val right = colW * (s.endCol + 1) - if (s.cutRight) 0.dp else 2.dp
            val shape = RoundedCornerShape(
                topStart = if (s.cutLeft) 0.dp else 4.dp, bottomStart = if (s.cutLeft) 0.dp else 4.dp,
                topEnd = if (s.cutRight) 0.dp else 4.dp, bottomEnd = if (s.cutRight) 0.dp else 4.dp,
            )
            Box(
                Modifier
                    .offset(x = left, y = top + (barH + gap) * s.lane)
                    .width(right - left)
                    .height(barH)
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!collapsed) {
                    Text(
                        s.e.title,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = onEventColor(s.e.color),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        // «+N» для дней, где не всё поместилось
        for (c in 0..6) {
            val hidden = segs.count { it.lane >= maxLanes && c in it.startCol..it.endCol }
            if (hidden > 0) {
                Text(
                    if (collapsed) "•" else "+$hidden",
                    fontSize = 9.sp,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = colW * c, y = top + (barH + gap) * maxLanes - if (collapsed) 4.dp else 1.dp)
                        .width(colW),
                )
            }
        }
    }
}
