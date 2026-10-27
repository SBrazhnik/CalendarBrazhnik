package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.EventInstance
import app.calstats.Fmt
import app.calstats.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val MONTH_PAGES = 1201
private const val MONTH_START = 600

/** Месяц как в Google Календаре: в ячейках — цветные плашки с названиями событий. */
@Composable
fun MonthView(vm: MainViewModel, byDay: Map<LocalDate, List<EventInstance>>, onOpenDay: (LocalDate) -> Unit) {
    val base = remember { YearMonth.now() }
    fun pageOf(d: LocalDate) = MONTH_START + ChronoUnit.MONTHS.between(base, YearMonth.from(d)).toInt()
    val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { MONTH_PAGES }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val m = base.plusMonths((page - MONTH_START).toLong())
            if (YearMonth.from(vm.selectedDate) != m) {
                vm.selectedDate = if (m == YearMonth.from(vm.today)) vm.today else m.atDay(1)
            }
        }
    }
    LaunchedEffect(vm.selectedDate) {
        val target = pageOf(vm.selectedDate)
        if (target != pager.currentPage) pager.animateScrollToPage(target)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            for (i in 0..6) {
                Text(
                    Fmt.weekdayShort(i),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val month = base.plusMonths((page - MONTH_START).toLong())
            MonthPage(vm, month, byDay, onOpenDay)
        }
    }
}

@Composable
private fun MonthPage(vm: MainViewModel, month: YearMonth, byDay: Map<LocalDate, List<EventInstance>>, onOpenDay: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val gridStart = first.with(DayOfWeek.MONDAY).let { if (it.isAfter(first)) it.minusWeeks(1) else it }
    val weeks = ((ChronoUnit.DAYS.between(gridStart, month.atEndOfMonth()) / 7) + 1).toInt()
    Column(Modifier.fillMaxSize()) {
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((w * 7 + dow).toLong())
                    MonthCell(
                        vm = vm,
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        events = byDay[date].orEmpty(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpenDay(date) },
                    )
                    if (dow < 6) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MonthCell(
    vm: MainViewModel,
    date: LocalDate,
    inMonth: Boolean,
    events: List<EventInstance>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = date == vm.today
    val sorted = remember(events) { events.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
    val drink = vm.drinks[date]
    BoxWithConstraints(modifier.clickable(onClick = onClick)) {
        val chipH = 16.dp
        val maxChips = ((maxHeight - 26.dp) / (chipH + 2.dp)).toInt().coerceAtLeast(0)
        val show = if (sorted.size > maxChips) (maxChips - 1).coerceAtLeast(0) else sorted.size
        Column(Modifier.fillMaxSize().alpha(if (inMonth) 1f else 0.45f).padding(horizontal = 2.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 3.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isToday) scheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) scheme.onPrimary else scheme.onSurface,
                    )
                }
                if (drink != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 1.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (drink) DRANK_COLOR else SOBER_COLOR),
                    )
                }
            }
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sorted.take(show).forEach { e -> EventChip(e, chipH) }
                if (sorted.size > show) {
                    Text(
                        "+${sorted.size - show}",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventChip(e: EventInstance, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(e.color))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            e.title,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = onEventColor(e.color),
            fontWeight = FontWeight.Medium,
        )
    }
}
