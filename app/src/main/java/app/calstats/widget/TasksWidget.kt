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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import app.calstats.Fmt
import app.calstats.MainActivity
import app.calstats.work.ME
import app.calstats.work.Status
import app.calstats.work.WorkOps
import app.calstats.work.WorkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val T_BG = ColorProvider(day = Color(0xFFF7F7FA), night = Color(0xFF171A33))
private val T_FG = ColorProvider(day = Color(0xFF171A33), night = Color(0xFFEEF0F4))
private val T_MUTED = ColorProvider(day = Color(0xFF8A8DA3), night = Color(0xFF8D91B0))
private val T_ACCENT = ColorProvider(day = Color(0xFFE4572E), night = Color(0xFFF08A68))
private val T_RED = ColorProvider(day = Color(0xFFD64545), night = Color(0xFFFF7A7A))
private val T_ROW = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF22264A))

private class TaskRow(val id: String, val key: String, val title: String, val due: LocalDate?, val status: Status, val priorityArrow: String, val priorityColor: Long)

private fun loadTasks(ctx: Context): List<TaskRow> {
    val w = WorkStore(ctx).load()
    return w.issues
        .filter { it.assigneeId == ME && it.status != Status.DONE }
        .sortedWith(compareBy({ it.dueDate ?: LocalDate.MAX }, { it.priority.ordinal }, { -it.updated }))
        .take(20)
        .map { TaskRow(it.id, w.key(it), it.title, it.dueDate, it.status, it.priority.arrow, it.priority.color) }
}

/** Виджет «Мои задачи»: незакрытые задачи, отметка выполнения прямо с рабочего стола. */
class TasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) { runCatching { loadTasks(context) }.getOrDefault(emptyList()) }
        provideContent { TasksContent(rows) }
    }

    companion object {
        fun refresh(ctx: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { TasksWidget().updateAll(ctx.applicationContext) }
            }
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

private val ISSUE_ID = ActionParameters.Key<String>("issue_id")

/** Нажатие на кружок: задача → «Готово». */
class CompleteIssueAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ISSUE_ID] ?: return
        withContext(Dispatchers.IO) {
            val store = WorkStore(context)
            store.save(WorkOps.setStatus(store.load(), id, Status.DONE))
        }
        TasksWidget().updateAll(context)
    }
}

@Composable
private fun TasksContent(rows: List<TaskRow>) {
    val today = LocalDate.now()
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(T_BG)
            .padding(10.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp).clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Мои задачи", style = TextStyle(color = T_FG, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(8.dp))
            Text(rows.size.toString(), style = TextStyle(color = T_ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("Открыть", style = TextStyle(color = T_MUTED, fontSize = 12.sp))
        }
        if (rows.isEmpty()) {
            Text(
                "Нет открытых задач 🎉",
                modifier = GlanceModifier.padding(8.dp).clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(color = T_MUTED, fontSize = 13.sp),
            )
            return@Column
        }
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(rows, itemId = { it.id.hashCode().toLong() }) { r ->
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(12.dp)
                            .background(T_ROW)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            GlanceModifier
                                .size(26.dp)
                                .cornerRadius(13.dp)
                                .background(T_ACCENT)
                                .clickable(actionRunCallback<CompleteIssueAction>(actionParametersOf(ISSUE_ID to r.id))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(GlanceModifier.size(20.dp).cornerRadius(10.dp).background(T_ROW)) {}
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                            Text(r.title, maxLines = 2, style = TextStyle(color = T_FG, fontSize = 13.sp))
                            val overdue = r.due != null && r.due.isBefore(today)
                            val sub = listOfNotNull(
                                r.key,
                                if (r.status == Status.IN_PROGRESS) "в работе" else null,
                                r.due?.let { Fmt.dueShort(it, today) },
                            ).joinToString(" · ")
                            Text(sub, maxLines = 1, style = TextStyle(color = if (overdue) T_RED else T_MUTED, fontSize = 11.sp))
                        }
                        Text(
                            r.priorityArrow,
                            style = TextStyle(
                                color = ColorProvider(day = Color(r.priorityColor), night = Color(r.priorityColor)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(0.dp))
    }
}
