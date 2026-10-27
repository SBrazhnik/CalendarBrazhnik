package app.calstats.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.calstats.CalendarRepository
import app.calstats.Fmt
import app.calstats.MainActivity
import app.calstats.SobrietyStore
import app.calstats.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BG = ColorProvider(day = Color(0xFFF7F7FA), night = Color(0xFF171A33))
private val FG = ColorProvider(day = Color(0xFF171A33), night = Color(0xFFEEF0F4))
private val MUTED = ColorProvider(day = Color(0xFF8A8DA3), night = Color(0xFF8D91B0))
private val ACCENT = ColorProvider(day = Color(0xFFE4572E), night = Color(0xFFF08A68))
private val ON_ACCENT = ColorProvider(day = Color.White, night = Color(0xFF171A33))
private val SOBER = ColorProvider(day = Color(0xFF2FA36B), night = Color(0xFF2FA36B))
private val DRANK = ColorProvider(day = Color(0xFFD64545), night = Color(0xFFD64545))

private class WidgetData(
    val month: YearMonth,
    val today: LocalDate,
    val gridStart: LocalDate,
    val weeks: Int,
    val colors: Map<LocalDate, List<Int>>,
    val drinks: Map<LocalDate, Boolean>,
    val upcoming: List<Pair<String, String>>,
    val noAccess: Boolean,
)

private fun loadData(ctx: Context): WidgetData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val first = month.atDay(1)
    val gridStart = first.with(DayOfWeek.MONDAY).let { if (it.isAfter(first)) it.minusWeeks(1) else it }
    val weeks = ((ChronoUnit.DAYS.between(gridStart, month.atEndOfMonth()) / 7) + 1).toInt()
    val drinks = SobrietyStore(ctx).load()
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    if (!granted) return WidgetData(month, today, gridStart, weeks, emptyMap(), drinks, emptyList(), true)

    val repo = CalendarRepository(ctx)
    val visible = repo.calendars().filter { it.visible }.map { it.id }.toSet()
    val from = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = gridStart.plusWeeks(weeks.toLong()).plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val inst = repo.instances(from, to).filter { it.calendarId in visible }
    val colors = HashMap<LocalDate, MutableList<Int>>()
    for (i in inst) {
        var d = Stats.firstDay(i, zone)
        val last = Stats.lastDay(i, zone)
        var guard = 0
        while (!d.isAfter(last) && guard < 42) {
            val l = colors.getOrPut(d) { mutableListOf() }
            if (i.color !in l) l += i.color
            d = d.plusDays(1)
            guard++
        }
    }
    val now = System.currentTimeMillis()
    val upcoming = inst
        .filter { !it.allDay && it.end > now && Stats.firstDay(it, zone) <= today.plusDays(1) }
        .sortedBy { it.begin }
        .take(4)
        .map { e ->
            val s = Instant.ofEpochMilli(e.begin).atZone(zone)
            val prefix = if (s.toLocalDate() == today) Fmt.time(s.toLocalTime()) else "завтра " + Fmt.time(s.toLocalTime())
            prefix to e.title
        }
    return WidgetData(month, today, gridStart, weeks, colors, drinks, upcoming, false)
}

/** Компактный виджет: месяц с точками событий, отметками алкоголя и ближайшими делами. */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { runCatching { loadData(context) }.getOrNull() }
        provideContent { Content(data) }
    }

    companion object {
        /** Обновить все виджеты (после изменений в приложении). */
        fun refresh(ctx: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { CalendarWidget().updateAll(ctx.applicationContext) }
            }
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

@Composable
private fun Content(d: WidgetData?) {
    val size = LocalSize.current
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(BG)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (d == null) {
            Text("Не удалось загрузить календарь", style = TextStyle(color = FG, fontSize = 12.sp))
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.monthName(d.month.monthValue),
                style = TextStyle(color = FG, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(d.month.year.toString(), style = TextStyle(color = ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        if (d.noAccess) {
            Text("Откройте приложение и разрешите доступ к календарю", style = TextStyle(color = MUTED, fontSize = 12.sp))
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth()) {
            for (i in 0..6) {
                Text(
                    Fmt.weekdayShort(i),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = if (i >= 5) ACCENT else MUTED, fontSize = 10.sp, textAlign = TextAlign.Center),
                )
            }
        }
        val showList = size.height >= 290.dp && d.upcoming.isNotEmpty()
        val gridH = size.height - 16.dp - 26.dp - 16.dp - if (showList) (14.dp * (d.upcoming.size + 1) + 8.dp) else 0.dp
        val cellH = (gridH / d.weeks).coerceIn(18.dp, 52.dp)
        for (w in 0 until d.weeks) {
            Row(GlanceModifier.fillMaxWidth().height(cellH)) {
                for (dow in 0 until 7) {
                    val date = d.gridStart.plusDays((w * 7 + dow).toLong())
                    DayCell(d, date, cellH, GlanceModifier.defaultWeight())
                }
            }
        }
        if (showList) {
            Spacer(GlanceModifier.height(6.dp))
            d.upcoming.forEach { (time, title) ->
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(time, style = TextStyle(color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(title, maxLines = 1, style = TextStyle(color = FG, fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: WidgetData, date: LocalDate, cellH: androidx.compose.ui.unit.Dp, modifier: GlanceModifier) {
    val inMonth = YearMonth.from(date) == d.month
    val isToday = date == d.today
    val circle = if (cellH >= 30.dp) 22.dp else 18.dp
    Column(modifier.height(cellH), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier
                .size(circle)
                .cornerRadius(circle / 2)
                .background(if (isToday) ACCENT else ColorProvider(day = Color.Transparent, night = Color.Transparent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = TextStyle(
                    color = when {
                        isToday -> ON_ACCENT
                        inMonth -> FG
                        else -> MUTED
                    },
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (cellH >= 26.dp) {
            Row(GlanceModifier.height(6.dp), verticalAlignment = Alignment.CenterVertically) {
                d.colors[date].orEmpty().take(3).forEach { c ->
                    Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(Color(c))) {}
                    Spacer(GlanceModifier.width(2.dp))
                }
                d.drinks[date]?.let { drank ->
                    Box(GlanceModifier.size(width = 8.dp, height = 3.dp).cornerRadius(1.dp).background(if (drank) DRANK else SOBER)) {}
                }
            }
        }
    }
}
