package app.calstats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.HabitOps
import app.calstats.work.HealthOps
import app.calstats.work.JournalEntry
import app.calstats.work.SplitMath
import app.calstats.work.TxType

/** «Итог дня» за минуту: настроение, привычки, вода, траты, запись в дневник. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(vm: MainViewModel) {
    val day = vm.today
    val w = vm.work
    val entry = w.journal.firstOrNull { it.date == day.toString() }
    var best by remember { mutableStateOf(entry?.best.orEmpty()) }
    var note by remember { mutableStateOf(entry?.text.orEmpty()) }
    fun done() {
        if (best != entry?.best.orEmpty() || note != entry?.text.orEmpty()) {
            vm.updateWork { ww ->
                val cur = ww.journal.firstOrNull { it.date == day.toString() } ?: JournalEntry(day.toString())
                val e = cur.copy(best = best.trim(), text = note.trim(), updated = System.currentTimeMillis())
                ww.copy(journal = ww.journal.filter { it.date != e.date } + e)
            }
        }
        vm.checkIn = false
    }
    ModalBottomSheet(onDismissRequest = { done() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🌙 Итог дня", style = MaterialTheme.typography.headlineSmall)

            if (vm.isEnabled(Tab.HEALTH)) MoodCard(vm, day)

            val habits = w.habits.filter { !it.archived && it.kind == HabitKind.DO && (it.perWeek != null || it.scheduled(day)) }
            if (vm.isEnabled(Tab.HABITS) && habits.isNotEmpty()) Block("Привычки") {
                habits.forEach { h ->
                    val ok = HabitMath.done(h, HabitMath.values(w.habitLogs, h.id), day)
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.updateWork { HabitOps.set(it, h.id, day, if (ok) null else h.target) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(ok, { c -> vm.updateWork { HabitOps.set(it, h.id, day, if (c) h.target else null) } })
                        Text("${h.emoji} ${h.name}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (vm.isEnabled(Tab.HEALTH)) Block("Вода") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${HealthOps.water(w, day)} мл", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.updateWork { HealthOps.addWater(it, day, 250) } }) { Text("+ стакан") }
                }
            }

            if (vm.isEnabled(Tab.FINANCE)) Block("Траты за сегодня") {
                val tx = w.transactions.filter { it.date == day.toString() && it.type == TxType.EXPENSE }
                Text(
                    if (tx.isEmpty()) "Пока ничего не записано" else "Всего ${SplitMath.money(tx.sumOf { it.amount })}",
                    style = MaterialTheme.typography.titleMedium,
                )
                tx.takeLast(5).forEach { t ->
                    Text("${w.category(t.categoryId)?.emoji ?: "•"} ${SplitMath.money(t.amount)} ${t.note}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                // Трата прямо здесь — всегда записывается как расход
                var spend by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(spend, { spend = it }, Modifier.weight(1f), placeholder = { Text("500 продукты") }, singleLine = true)
                    Button(enabled = Regex("\\d").containsMatchIn(spend), onClick = {
                        val r = app.calstats.work.SmartInput.asKind(app.calstats.work.SmartInput.Kind.EXPENSE, spend, java.time.LocalDateTime.of(day, java.time.LocalTime.now()))
                        vm.message = vm.applySmart(r); spend = ""
                    }) { Text("Записать") }
                }
            }

            Block("Дневник") {
                OutlinedTextField(best, { best = it }, Modifier.fillMaxWidth(), label = { Text("Лучшее за день") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Пара слов о дне") }, minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            Button(onClick = { done(); vm.message = "Итог дня сохранён. Спокойной ночи 🌙" }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Готово") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** «План дня» утром: мой день + три главных дела (станут задачами на сегодня). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningSheet(vm: MainViewModel) {
    val top = remember { androidx.compose.runtime.mutableStateListOf("", "", "") }
    fun done() {
        val list = top.map { it.trim() }.filter { it.isNotBlank() }
        if (list.isNotEmpty()) {
            vm.updateWork { w0 ->
                var w = w0
                if (w.projects.none { !it.archived }) w = app.calstats.work.WorkOps.upsertProject(w, app.calstats.work.Project(app.calstats.work.newId(), "ЛИЧ", "Личное", app.calstats.work.PALETTE[0]))
                val p = w.projects.first { !it.archived }
                list.forEach { t ->
                    w = app.calstats.work.WorkOps.createIssue(w, app.calstats.work.Issue(app.calstats.work.newId(), p.id, 0, t, assigneeId = app.calstats.work.ME,
                        due = vm.today.toString(), priority = app.calstats.work.Priority.HIGH, labels = listOf("главное"))).first
                }
                w
            }
            if (!vm.isEnabled(Tab.TASKS)) vm.setModuleEnabled(Tab.TASKS, true)
            vm.message = "Главное на сегодня: ${list.size} — в задачах"
        }
        vm.morning = false
    }
    ModalBottomSheet(onDismissRequest = { done() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("☀ План на ${app.calstats.Fmt.dayTitle(vm.today, vm.today).lowercase()}", style = MaterialTheme.typography.headlineSmall)
            MyDayCard(vm)
            Block("Три главных дела") {
                top.forEachIndexed { i, v ->
                    OutlinedTextField(v, { top[i] = it }, Modifier.fillMaxWidth(), placeholder = { Text("${i + 1}.") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
            Button(onClick = { done() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Хорошего дня") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
