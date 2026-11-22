package app.calstats.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.TextStyle
import app.calstats.MainActivity
import app.calstats.work.FinanceMath
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.HabitOps
import app.calstats.work.HealthMath
import app.calstats.work.HealthOps
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import app.calstats.work.WorkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val BG = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF172A20))
private val FG = ColorProvider(day = Color(0xFF121721), night = Color(0xFFEDE6D6))
private val MUTED = ColorProvider(day = Color(0xFF5E6472), night = Color(0xFFA9B3A4))
private val ACCENT = ColorProvider(day = Color(0xFF1F3A2B), night = Color(0xFFD4A960))
private val CHIP = ColorProvider(day = Color(0xFFF3F2F7), night = Color(0xFF1F3528))
private val DONE = ColorProvider(day = Color(0xFFB5653A), night = Color(0xFFE0954F))

private class HabitCell(val id: String, val emoji: String, val name: String, val v: Int, val target: Int)
private class TodayData(val habits: List<HabitCell>, val water: Int, val waterGoal: Int, val kcal: Int, val spent: Long)

private fun load(ctx: Context): TodayData {
    val w = WorkStore(ctx).load()
    val today = LocalDate.now()
    val habits = w.habits.filter { !it.archived && it.kind == HabitKind.DO && it.scheduled(today) }.take(6).map {
        HabitCell(it.id, it.emoji, it.name, HabitMath.values(w.habitLogs, it.id)[today] ?: 0, it.target)
    }
    val goal = w.healthProfile?.let { HealthMath.norms(it, today).waterMl } ?: 2000
    val spent = w.transactions.filter { it.type == TxType.EXPENSE && it.localDate == today }.sumOf { it.amount }
    return TodayData(habits, HealthOps.water(w, today), goal, w.foodLog.filter { it.date == today.toString() }.sumOf { it.kcal }.toInt(), spent)
}

/** Виджет «Сегодня»: привычки, вода и траты — отметить прямо с рабочего стола. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = withContext(Dispatchers.IO) { runCatching { load(context) }.getOrNull() }
        provideContent { Content(d) }
    }

    companion object {
        fun refresh(ctx: Context) {
            CoroutineScope(Dispatchers.Default).launch { runCatching { TodayWidget().updateAll(ctx.applicationContext) } }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val HABIT = ActionParameters.Key<String>("habit")

class TapHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT] ?: return
        withContext(Dispatchers.IO) {
            val s = WorkStore(context)
            val w = s.load()
            val h = w.habits.firstOrNull { it.id == id } ?: return@withContext
            s.save(HabitOps.tap(w, h, LocalDate.now()))
        }
        TodayWidget().updateAll(context)
    }
}

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            val s = WorkStore(context)
            s.save(HealthOps.addWater(s.load(), LocalDate.now(), 250))
        }
        TodayWidget().updateAll(context)
    }
}

@Composable
private fun Content(d: TodayData?) {
    Column(GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(BG).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
            Text("Сегодня", style = TextStyle(color = FG, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            if (d != null && d.spent > 0) Text("−" + SplitMath.money(d.spent), style = TextStyle(color = MUTED, fontSize = 12.sp))
        }
        if (d == null) return@Column
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("💧 ${d.water} / ${d.waterGoal} мл", style = TextStyle(color = FG, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
            Box(
                GlanceModifier.cornerRadius(14.dp).background(ACCENT).padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<AddWaterAction>()),
            ) { Text("+ стакан", style = TextStyle(color = BG, fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
        }
        if (d.kcal > 0) Text("🍽 ${d.kcal} ккал", style = TextStyle(color = MUTED, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 4.dp))
        Spacer(GlanceModifier.height(8.dp))
        if (d.habits.isEmpty()) {
            Text("Добавьте привычки в приложении", style = TextStyle(color = MUTED, fontSize = 12.sp))
        }
        d.habits.chunked(3).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                row.forEachIndexed { i, h ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    val done = h.v >= h.target
                    Column(
                        GlanceModifier.defaultWeight().cornerRadius(12.dp).background(if (done) DONE else CHIP).padding(6.dp)
                            .clickable(actionRunCallback<TapHabitAction>(actionParametersOf(HABIT to h.id))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(h.emoji, style = TextStyle(fontSize = 16.sp))
                        Text(
                            if (h.target > 1) "${h.v}/${h.target}" else h.name,
                            maxLines = 1,
                            style = TextStyle(color = if (done) BG else FG, fontSize = 10.sp),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun unused() = FinanceMath
