package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.work.FinanceOps
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.HabitOps
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.Status
import java.time.Instant

/** «Мой день»: события, задачи, привычки, платежи и дни рождения на сегодня — одним списком. */
@Composable
fun MyDayCard(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    var expanded by rememberSaveable { mutableStateOf(true) }
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    val now = System.currentTimeMillis()
    val events = byDay[today].orEmpty().filter { it.allDay || it.end > now }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    val tasks = if (vm.isEnabled(Tab.TASKS)) w.issues.filter {
        it.assigneeId == ME && (it.dueDate?.let { d -> !d.isAfter(today) } == true) && (it.status != Status.DONE || it.resolvedAt?.let { r -> r > today.atStartOfDay(vm.zone).toInstant().toEpochMilli() } == true)
    }.sortedBy { it.status == Status.DONE } else emptyList()
    val habits = if (vm.isEnabled(Tab.HABITS)) w.habits.filter { !it.archived && it.kind == HabitKind.DO && it.scheduled(today) } else emptyList()
    val pays = if (vm.isEnabled(Tab.FINANCE)) w.recurrings.filter { it.active && !it.next.isAfter(today) } else emptyList()
    val birthdays = vm.birthdaysOn(today)
    val total = events.size + tasks.count { it.status != Status.DONE } + habits.count { !HabitMath.done(it, HabitMath.values(w.habitLogs, it.id), today) } + pays.size
    if (events.isEmpty() && tasks.isEmpty() && habits.isEmpty() && pays.isEmpty() && birthdays.isEmpty()) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Мой день", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (total == 0) "всё сделано ✓" else "осталось $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (!expanded) return@Column
            birthdays.forEach { b -> Line("🎂", b, null) {} }
            events.take(6).forEach { e ->
                val time = if (e.allDay) "весь день" else Fmt.time(Instant.ofEpochMilli(e.begin).atZone(vm.zone).toLocalTime())
                Line(null, e.title, time, dot = Color(e.color)) { vm.tab = Tab.CALENDAR }
            }
            tasks.take(6).forEach { i ->
                val done = i.status == Status.DONE
                Row(
                    Modifier.fillMaxWidth().clickable { vm.push(Screen.IssueDetail(i.id)) }.padding(start = 6.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(done, { vm.toggleIssueDone(i) })
                    Text(
                        i.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (i.dueDate?.isBefore(today) == true && !done) Text("просрочено", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            habits.forEach { h ->
                val v = HabitMath.values(w.habitLogs, h.id)[today] ?: 0
                val done = v >= h.target
                Row(
                    Modifier.fillMaxWidth().clickable { vm.updateWork { HabitOps.tap(it, h, today) } }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (done) moduleColor(Tab.HABITS) else moduleColor(Tab.HABITS).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.width(12.dp))
                    Text("${h.emoji} ${h.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (h.target > 1) Text("$v/${h.target}", style = MaterialTheme.typography.labelMedium)
                }
            }
            pays.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔁  ${r.name} · ${SplitMath.money(r.amount)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    TextButton(onClick = { vm.updateWork { FinanceOps.payRecurring(it, r.id, today) } }) { Text("Оплачено") }
                }
            }
        }
    }
}

@Composable
private fun Line(emoji: String?, text: String, right: String?, dot: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dot != null) Box(Modifier.size(10.dp).clip(CircleShape).background(dot)) else Text(emoji ?: "")
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (right != null) Text(right, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Событие обычным текстом: «завтра в 15 к врачу». */
@Composable
fun QuickEventDialog(vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    val r = remember(text) { app.calstats.work.QuickParse.parse(text, java.time.LocalDateTime.now()) }
    fun open(full: Boolean) {
        vm.quickEvent = false
        if (r == null || full && r.title.isBlank()) vm.push(Screen.Edit(presetDate = r?.date ?: vm.today, presetTitle = r?.title ?: text.trim().ifBlank { null }))
        else vm.push(Screen.Edit(presetDate = r.date, presetStart = r.start, presetAllDay = r.time == null, presetTitle = r.title))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.quickEvent = false },
        title = { Text("Новое событие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("завтра в 15 к врачу") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                )
                if (r != null) {
                    Text(
                        "📅 ${r.title} — ${Fmt.dayTitle(r.date, vm.today)}" + (r.time?.let { ", ${Fmt.time(it)}" } ?: ", весь день"),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Напишите, когда и что: «в пятницу в 19:30 кино», «25 декабря ужин», «через 2 часа созвон».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { open(false) }, enabled = text.isNotBlank()) { Text("Дальше") } },
        dismissButton = { TextButton(onClick = { vm.quickEvent = false; vm.push(Screen.Edit(presetDate = vm.today)) }) { Text("Обычная форма") } },
    )
}

/** Итог недели на Главной (в выходные и в понедельник). */
@Composable
fun WeekSummaryCard(vm: MainViewModel) {
    val r = remember(vm.work, vm.instances, vm.today) { app.calstats.WeekReport.build(null, vm.work, vm.today, vm.instances) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Итог недели", style = MaterialTheme.typography.titleMedium)
            Text("${Fmt.dateNoYear(r.from)} — ${Fmt.dateNoYear(r.to)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.lines().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
