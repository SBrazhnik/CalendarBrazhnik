@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.work.CycleLog
import app.calstats.work.FOOD_BASE
import app.calstats.work.Fast
import app.calstats.work.FoodItem
import app.calstats.work.HealthMore
import app.calstats.work.HealthOps
import app.calstats.work.Ingredient
import app.calstats.work.Med
import app.calstats.work.Mood
import app.calstats.work.newId
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val FAST_C = Color(0xFF3F7C9A)
private val CYCLE_C = Color(0xFFB0656B)

/** Настроение дня: 5 смайлов, теги и заметка. */
@Composable
fun MoodCard(vm: MainViewModel, day: LocalDate) {
    val cur = vm.work.moods.firstOrNull { it.date == day.toString() }
    var note by remember(day, cur?.note) { mutableStateOf(cur?.note.orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Самочувствие", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthMore.MOODS.forEachIndexed { i, e ->
                    val sel = cur?.mood == i + 1
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { vm.updateWork { HealthMore.setMood(it, (cur ?: Mood(day.toString(), 3)).copy(mood = i + 1)) } },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = if (sel) 30.sp else 24.sp) }
                }
            }
            if (cur != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HealthMore.MOOD_TAGS.forEach { t ->
                        FilterChip(selected = t in cur.tags, onClick = {
                            vm.updateWork { HealthMore.setMood(it, cur.copy(tags = if (t in cur.tags) cur.tags - t else cur.tags + t)) }
                        }, label = { Text(t, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    note, { note = it }, Modifier.fillMaxWidth(), placeholder = { Text("Как прошёл день") },
                    trailingIcon = { if (note != cur.note) TextButton(onClick = { vm.updateWork { HealthMore.setMood(it, cur.copy(note = note.trim())) } }) { Text("OK") } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
}

/** Интервальное голодание: таймер и цель. */
@Composable
fun FastingCard(vm: MainViewModel) {
    val f = HealthMore.activeFast(vm.work)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(f?.id) { while (f != null) { now = System.currentTimeMillis(); delay(30_000) } }
    var goal by remember { mutableStateOf(16) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Интервальное голодание", style = MaterialTheme.typography.titleMedium)
            if (f == null) {
                val last = vm.work.fasts.lastOrNull { it.end != null }
                if (last != null) {
                    val h = (last.end!! - last.start) / 3_600_000.0
                    Text("Прошлое: ${"%.1f".format(h)} ч из ${last.goalH}" + if (h >= last.goalH) " ✓" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HealthMore.FAST_PRESETS.forEach { g -> FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text("$g:${24 - g}") }) }
                }
                Button(onClick = { vm.updateWork { it.copy(fasts = it.fasts + Fast(newId(), System.currentTimeMillis(), goalH = goal)) } }) { Text("Начать голодание") }
            } else {
                val ms = now - f.start
                val goalMs = f.goalH * 3_600_000L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(84.dp)) {
                            drawArc(FAST_C.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(8.dp.toPx()))
                            drawArc(FAST_C, -90f, 360f * (ms.toFloat() / goalMs).coerceIn(0f, 1f), false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("${ms / 3_600_000}:${"%02d".format(ms / 60_000 % 60)}", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ms >= goalMs) "Цель ${f.goalH} ч достигнута 🎉" else "Осталось ${Fmt.duration((goalMs - ms) / 60_000)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Можно есть с " + Fmt.time(java.time.Instant.ofEpochMilli(f.start + goalMs).atZone(vm.zone).toLocalTime()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.updateWork { w -> w.copy(fasts = w.fasts.map { if (it.id == f.id) it.copy(end = System.currentTimeMillis()) else it }) } }) { Text("Закончить") }
                    TextButton(onClick = { vm.updateWork { w -> w.copy(fasts = w.fasts.filter { it.id != f.id }) } }) { Text("Отменить") }
                }
            }
        }
    }
}

/** Лекарства: расписание на день с отметками и список курсов. */
@Composable
fun MedsView(vm: MainViewModel) {
    var edit by remember { mutableStateOf<Med?>(null) }
    val today = vm.today
    val list = HealthMore.medsOn(vm.work, today)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Сегодня", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    if (list.isEmpty()) Text("Приёмов нет", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { (m, t) ->
                        val ok = HealthMore.taken(vm.work, m.id, today, t)
                        Row(Modifier.fillMaxWidth().clickable { vm.updateWork { HealthMore.toggleMed(it, m.id, today, t) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(ok, { vm.updateWork { HealthMore.toggleMed(it, m.id, today, t) } })
                            Text(t, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(52.dp))
                            Text("💊 ${m.name}" + if (m.dose.isNotBlank()) " · ${m.dose}" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item { Text("Мои лекарства", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        items(vm.work.meds, key = { it.id }) { m ->
            Card(onClick = { edit = m }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(m.name + if (!m.active) " (пауза)" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(m.dose.takeIf { it.isNotBlank() }, m.times.sorted().joinToString(", "),
                            if (m.days.size < 7) m.days.sorted().joinToString(",") { Fmt.weekdayShort(it - 1).lowercase() } else "ежедневно",
                            m.until?.let { "до ${Fmt.dateNoYear(LocalDate.parse(it))}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { edit = Med(newId(), "") }, modifier = Modifier.fillMaxWidth()) { Text("+ Лекарство или витамины") }
            Text(
                "Напомню в указанное время — с кнопкой «Принял». Приложение не заменяет назначения врача.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    edit?.let { m -> MedDialog(vm, m) { edit = null } }
}

@Composable
private fun MedDialog(vm: MainViewModel, m: Med, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.meds.any { it.id == m.id }
    var name by remember { mutableStateOf(m.name) }
    var dose by remember { mutableStateOf(m.dose) }
    var times by remember { mutableStateOf(m.times) }
    var days by remember { mutableStateOf(m.days.toSet()) }
    var until by remember { mutableStateOf(m.until?.let { LocalDate.parse(it) }) }
    var active by remember { mutableStateOf(m.active) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Лекарство" else "Новое лекарство") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(dose, { dose = it }, label = { Text("Доза: 1 таблетка, 5 мл") }, singleLine = true)
                Text("Время приёма", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.sorted().forEach { t -> FilterChip(selected = true, onClick = { if (times.size > 1) times = times - t }, label = { Text("$t ✕") }) }
                    TextButton(onClick = {
                        android.app.TimePickerDialog(ctx, { _, h, mm -> times = (times + "%02d:%02d".format(h, mm)).distinct() }, 9, 0, true).show()
                    }) { Text("+ время") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d -> FilterChip(selected = d in days, onClick = { days = if (d in days && days.size > 1) days - d else days + d }, label = { Text(Fmt.weekdayShort(d - 1)) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, until ?: vm.today.plusDays(14)) { until = it } }) { Text(until?.let { "Курс до ${Fmt.date(it)}" } ?: "Курс без срока") }
                    if (until != null) TextButton(onClick = { until = null }) { Text("✕") }
                }
                if (exists) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(active, { active = it }); Text("Принимаю сейчас") }
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(meds = w.meds.filter { it.id != m.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val nm = m.copy(name = name.trim(), dose = dose.trim(), times = times.sorted(), days = days.sorted(), until = until?.toString(), active = active)
                vm.updateWork { w -> w.copy(meds = if (exists) w.meds.map { if (it.id == m.id) nm else it } else w.meds + nm) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Женский цикл: отметка начала, прогноз, длина. */
@Composable
fun CycleView(vm: MainViewModel) {
    val ctx = LocalContext.current
    val info = HealthMore.cycle(vm.work, vm.today)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (info == null) {
                    Text("Отметьте первый день последних месячных — посчитаю цикл и прогноз.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("День цикла", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${info.dayOfCycle}", style = MaterialTheme.typography.displaySmall, color = CYCLE_C)
                    if (info.inPeriod) Text("Идут месячные", color = CYCLE_C)
                    val d = ChronoUnit.DAYS.between(vm.today, info.next)
                    Text("Следующие — ${Fmt.dateNoYear(info.next)} (через ${Fmt.days(d)})", style = MaterialTheme.typography.bodyMedium)
                    Text("Вероятная овуляция — ${Fmt.dateNoYear(info.ovulation)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Средняя длина цикла: ${info.avgLen} дн.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.updateWork { it.copy(cycles = (it.cycles.filter { c -> c.start != vm.today.toString() } + CycleLog(vm.today.toString())).sortedBy { c -> c.start }) } }) { Text("Начались сегодня") }
                    OutlinedButton(onClick = { pickDate(ctx, vm.today) { d -> vm.updateWork { it.copy(cycles = (it.cycles.filter { c -> c.start != d.toString() } + CycleLog(d.toString())).sortedBy { c -> c.start }) } } }) { Text("Другая дата") }
                }
            }
        }
        if (vm.work.cycles.isNotEmpty()) {
            Text("История", style = MaterialTheme.typography.titleMedium)
            vm.work.cycles.sortedByDescending { it.start }.take(12).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.date(LocalDate.parse(c.start)), Modifier.weight(1f))
                    TextButton(onClick = { vm.updateWork { it.copy(cycles = it.cycles - c) } }) { Text("✕") }
                }
            }
        }
        Text("Прогноз приблизительный и не является методом контрацепции.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Свой рецепт: ингредиенты из базы → калорийность блюда на 100 г. */
@Composable
fun RecipeDialog(vm: MainViewModel, onDismiss: () -> Unit, onSaved: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var ing by remember { mutableStateOf(listOf<Ingredient>()) }
    var cooked by remember { mutableStateOf("") }
    val base = (vm.work.customFoods + vm.work.recentFoods + FOOD_BASE).distinctBy { it.name.lowercase() }
    val found = if (q.isBlank()) emptyList() else base.filter { it.name.contains(q.trim(), true) }.take(6)
    val res = if (ing.isEmpty()) null else HealthMore.recipe(name.ifBlank { "Блюдо" }, ing, cooked.toIntOrNull())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Рецепт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название блюда") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                ing.forEachIndexed { i, x ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(x.item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        OutlinedTextField(
                            x.grams.toString(), { v -> ing = ing.toMutableList().also { l -> l[i] = x.copy(grams = v.filter(Char::isDigit).toIntOrNull() ?: 0) } },
                            Modifier.width(80.dp), singleLine = true, suffix = { Text("г") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        TextButton(onClick = { ing = ing - x }) { Text("✕") }
                    }
                }
                OutlinedTextField(q, { q = it }, label = { Text("+ ингредиент: поиск") }, singleLine = true)
                found.forEach { f ->
                    Text("${f.name} · ${f.kcal.roundToInt()} ккал/100 г", Modifier.fillMaxWidth().clickable { ing = ing + Ingredient(f, 100); q = "" }.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(cooked, { cooked = it.filter(Char::isDigit) }, label = { Text("Вес готового блюда, г (если знаете)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                res?.let { Text("На 100 г: ${it.kcal.roundToInt()} ккал · Б ${it.protein.roundToInt()} · Ж ${it.fat.roundToInt()} · У ${it.carbs.roundToInt()}", color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(enabled = res != null && name.isNotBlank(), onClick = {
                val f = res!!.copy(name = name.trim())
                vm.updateWork { HealthOps.saveCustomFood(it, f) }
                onSaved(f)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
