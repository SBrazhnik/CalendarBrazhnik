@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.work.LabResult
import app.calstats.work.Pressure
import app.calstats.work.Symptom
import app.calstats.work.Vitals
import app.calstats.work.Workspace
import app.calstats.work.newId
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private fun ld(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun f1(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v).replace('.', ',')

/** Давление, самочувствие, анализы и отчёт для врача. */
@Composable
fun VitalsView(vm: MainViewModel) {
    val ctx = LocalContext.current
    var addBp by remember { mutableStateOf(false) }
    var addSym by remember { mutableStateOf(false) }
    var addLab by remember { mutableStateOf<LabResult?>(null) }
    val w = vm.work
    val bp = w.pressure.sortedByDescending { it.at }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addBp = true }) { Text("+ Давление") }
                OutlinedButton(onClick = { addSym = true }) { Text("+ Самочувствие") }
                OutlinedButton(onClick = { addLab = LabResult(newId(), vm.today.toString(), "", 0.0) }) { Text("+ Анализ") }
            }
        }
        item {
            Card(onClick = { DoctorReport.share(ctx, vm) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("📄 Отчёт для врача (PDF)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Давление, анализы с нормами, лекарства, вес, сон, шаги и самочувствие за 30 дней — отправьте врачу или распечатайте",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        // Давление
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Давление", style = MaterialTheme.typography.titleMedium)
                    if (bp.isEmpty()) Text("Записывайте утром и вечером — увидите средние значения и тренд.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        val last30 = bp.filter { it.at > System.currentTimeMillis() - 30L * 86_400_000 }
                        if (last30.isNotEmpty()) {
                            val s = last30.map { it.sys }.average(); val d = last30.map { it.dia }.average()
                            val (cat, lvl) = Vitals.category(s.toInt(), d.toInt())
                            Text("Среднее за 30 дней: ${s.toInt()}/${d.toInt()} — $cat", style = MaterialTheme.typography.bodyMedium,
                                color = if (lvl >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                        BpChart(bp.take(30).reversed())
                        bp.take(8).forEach { p ->
                            val (cat, lvl) = Vitals.category(p.sys, p.dia)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${Fmt.dayTitle(ld(p.at).toLocalDate(), vm.today)}, ${ld(p.at).format(HM)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${p.sys}/${p.dia}" + if (p.pulse > 0) " · ♥${p.pulse}" else "", style = MaterialTheme.typography.bodyMedium,
                                    color = if (lvl >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                Text(" $cat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("  ✕", Modifier.padding(start = 4.dp).clickable { vm.updateWork { x -> x.copy(pressure = x.pressure.filter { it.id != p.id }) } },
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
        // Самочувствие
        if (w.symptoms.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Самочувствие", style = MaterialTheme.typography.titleMedium)
                    val month = w.symptoms.filter { LocalDate.parse(it.date).isAfter(vm.today.minusDays(30)) }
                    if (month.isNotEmpty()) Text("За 30 дней: " + month.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.joinToString { "${it.key} ×${it.value}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    w.symptoms.sortedByDescending { it.date }.take(10).forEach { s ->
                        Row {
                            Text(Fmt.dayTitle(LocalDate.parse(s.date), vm.today), Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.name + " " + "●".repeat(s.level) + if (s.note.isNotBlank()) " · ${s.note}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("✕", Modifier.clickable { vm.updateWork { x -> x.copy(symptoms = x.symptoms.filter { it.id != s.id }) } }, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
        // Анализы
        if (w.labs.isNotEmpty()) item { Text("Анализы", style = MaterialTheme.typography.titleMedium) }
        items(w.labs.groupBy { it.name }.entries.sortedBy { it.key }.toList(), key = { "lab" + it.key }) { (name, list) ->
            val sorted = list.sortedByDescending { it.date }
            val last = sorted.first()
            Card(onClick = { addLab = last.copy(id = newId(), date = vm.today.toString(), value = 0.0) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${f1(last.value)} ${last.unit}", style = MaterialTheme.typography.titleSmall,
                            color = if (last.status != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(when (last.status) { -1 -> " ↓"; 1 -> " ↑"; else -> " ✓" }, color = if (last.status != 0) MaterialTheme.colorScheme.error else Color(0xFF3F8A5A))
                    }
                    Text(listOfNotNull("норма " + listOfNotNull(last.min?.let { "от ${f1(it)}" }, last.max?.let { "до ${f1(it)}" }).joinToString(" ").ifBlank { "не указана" },
                        Fmt.date(LocalDate.parse(last.date)),
                        if (sorted.size > 1) "раньше: " + sorted.drop(1).take(3).joinToString { f1(it.value) } else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (addBp) BpDialog(vm) { addBp = false }
    if (addSym) SymptomDialog(vm) { addSym = false }
    addLab?.let { l -> LabDialog(vm, l) { addLab = null } }
}

@Composable
private fun BpChart(list: List<Pressure>) {
    if (list.size < 2) return
    val sysC = MaterialTheme.colorScheme.error
    val diaC = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val lo = 50f; val hi = 190f
        fun y(v: Int) = size.height * (1 - (v - lo) / (hi - lo))
        listOf(80, 120, 140).forEach { g -> drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), 1f) }
        val step = size.width / (list.size - 1)
        for (i in 1 until list.size) {
            drawLine(sysC, Offset(step * (i - 1), y(list[i - 1].sys)), Offset(step * i, y(list[i].sys)), 4f)
            drawLine(diaC, Offset(step * (i - 1), y(list[i - 1].dia)), Offset(step * i, y(list[i].dia)), 4f)
        }
    }
}

@Composable
private fun BpDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Давление") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("Верхнее") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("Нижнее") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(pulse, { pulse = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("Пульс") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                val s = sys.toIntOrNull(); val d = dia.toIntOrNull()
                if (s != null && d != null) Text(Vitals.category(s, d).first, color = if (Vitals.category(s, d).second >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(note, { note = it }, label = { Text("Заметка (после кофе, лёжа…)") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = {
            TextButton(enabled = (sys.toIntOrNull() ?: 0) in 60..260 && (dia.toIntOrNull() ?: 0) in 30..160, onClick = {
                val p = Pressure(newId(), System.currentTimeMillis(), sys.toInt(), dia.toInt(), pulse.toIntOrNull() ?: 0, note.trim())
                vm.updateWork { it.copy(pressure = it.pressure + p) }
                val (cat, lvl) = Vitals.category(p.sys, p.dia)
                vm.message = "${p.sys}/${p.dia} — $cat" + if (lvl >= 3) ". Если плохо — звоните 103" else ""
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SymptomDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Самочувствие") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (vm.work.symptoms.map { it.name } + Vitals.SYMPTOMS).distinct().take(16).forEach { s -> FilterChip(name == s, { name = s }, label = { Text(s) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Что беспокоит") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Text("Насколько сильно", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (1..5).forEach { i -> FilterChip(level == i, { level = i }, label = { Text("$i") }) } }
                OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                vm.updateWork { it.copy(symptoms = it.symptoms + Symptom(newId(), vm.today.toString(), name.trim(), level, note.trim())) }; onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun LabDialog(vm: MainViewModel, l: LabResult, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(l.name) }
    var value by remember { mutableStateOf(if (l.value != 0.0) f1(l.value) else "") }
    var unit by remember { mutableStateOf(l.unit) }
    var min by remember { mutableStateOf(l.min?.let(::f1) ?: "") }
    var max by remember { mutableStateOf(l.max?.let(::f1) ?: "") }
    var date by remember { mutableStateOf(LocalDate.parse(l.date)) }
    fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Анализ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (l.name.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Vitals.LABS.forEach { t -> FilterChip(name == t.name, { name = t.name; unit = t.unit; min = t.min?.let(::f1) ?: ""; max = t.max?.let(::f1) ?: "" }, label = { Text(t.name) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Показатель") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.weight(1f), label = { Text("Значение") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Ед.") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(min, { min = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.weight(1f), label = { Text("Норма от") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(max, { max = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.weight(1f), label = { Text("до") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text("Нормы — ориентир для взрослых; сверяйте с бланком своей лаборатории.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.date(date)) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && d(value) != null, onClick = {
                val r = LabResult(l.id, date.toString(), name.trim(), d(value)!!, unit.trim(), d(min), d(max))
                vm.updateWork { it.copy(labs = it.labs.filter { x -> x.id != r.id } + r) }
                vm.message = "${r.name}: ${f1(r.value)} ${r.unit} — " + when (r.status) { -1 -> "ниже нормы"; 1 -> "выше нормы"; else -> "в норме" }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** PDF «для врача» за 30 дней. */
object DoctorReport {
    fun share(ctx: Context, vm: MainViewModel) {
        val f = runCatching { build(ctx, vm.work, vm.today, vm.health) }.getOrElse { vm.message = "Не удалось создать отчёт: ${it.message}"; return }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Отчёт о здоровье").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Отчёт для врача"))
    }

    fun build(ctx: Context, w: Workspace, today: LocalDate, h: app.calstats.work.HealthSnapshot?): File {
        val doc = PdfDocument()
        val W = 595; val H = 842; val M = 40f
        val title = Paint().apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF2B2F35.toInt() }
        val head = Paint().apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF2B2F35.toInt() }
        val body = Paint().apply { textSize = 10.5f; color = 0xFF33373D.toInt() }
        val red = Paint(body).apply { color = 0xFFC93D3D.toInt() }
        val grey = Paint(body).apply { color = 0xFF7A808B.toInt() }
        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        var y = M
        fun line(t: String, p: Paint = body, gap: Float = 15f) {
            if (y + gap > H - M) { doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create()); y = M }
            y += gap; page.canvas.drawText(t, M, y, p)
        }
        val from = today.minusDays(30)
        line("Отчёт о здоровье", title, 20f)
        line("${Fmt.date(from)} — ${Fmt.date(today)} · подготовлено в «Зелёном ритме»", grey)
        w.healthProfile?.let { p -> line("Пол: ${if (p.sex == app.calstats.work.Sex.FEMALE) "женский" else "мужской"} · год рождения ${p.birthYear} · рост ${p.heightCm} см", body, 18f) }
        // Давление
        val bp = w.pressure.filter { ld(it.at).toLocalDate().isAfter(from) }.sortedBy { it.at }
        line("Давление", head, 26f)
        if (bp.isEmpty()) line("нет записей", grey) else {
            val s = bp.map { it.sys }.average().toInt(); val d = bp.map { it.dia }.average().toInt()
            line("Среднее: $s/$d (${Vitals.category(s, d).first}), минимум ${bp.minOf { it.sys }}/${bp.minOf { it.dia }}, максимум ${bp.maxOf { it.sys }}/${bp.maxOf { it.dia }}, измерений: ${bp.size}")
            bp.takeLast(20).forEach { p -> line("  ${ld(p.at).toLocalDate().let(Fmt::date)} ${ld(p.at).format(HM)} — ${p.sys}/${p.dia}" + (if (p.pulse > 0) ", пульс ${p.pulse}" else "") + if (p.note.isNotBlank()) " (${p.note})" else "",
                if (Vitals.category(p.sys, p.dia).second >= 2) red else body) }
        }
        // Анализы
        line("Анализы (последние значения)", head, 26f)
        val labs = w.labs.groupBy { it.name }.map { it.value.maxBy { l -> l.date } }.sortedBy { it.name }
        if (labs.isEmpty()) line("нет записей", grey)
        labs.forEach { l ->
            val norm = listOfNotNull(l.min?.let { "от ${f1(it)}" }, l.max?.let { "до ${f1(it)}" }).joinToString(" ")
            line("  ${l.name}: ${f1(l.value)} ${l.unit}" + (if (norm.isNotBlank()) "  (норма $norm)" else "") + "  ${Fmt.date(LocalDate.parse(l.date))}" + when (l.status) { -1 -> "  ↓"; 1 -> "  ↑"; else -> "" },
                if (l.status != 0) red else body)
        }
        // Лекарства
        line("Принимаемые лекарства", head, 26f)
        val meds = w.meds.filter { it.active }
        if (meds.isEmpty()) line("нет", grey)
        meds.forEach { m ->
            val taken = w.medLogs.count { it.medId == m.id && it.taken && LocalDate.parse(it.date).isAfter(from) }
            line("  ${m.name} ${m.dose} — ${m.times.joinToString()}; отмечено приёмов за 30 дней: $taken")
        }
        // Вес, сон, шаги
        line("Вес, сон, активность", head, 26f)
        val wt = w.weightLog.filter { LocalDate.parse(it.date).isAfter(from) }.sortedBy { it.date }
        if (wt.isNotEmpty()) line("  Вес: ${f1(wt.first().kg)} → ${f1(wt.last().kg)} кг")
        h?.let { s ->
            val idx = s.days.indices.filter { s.days[it].isAfter(from) }
            val sl = idx.map { s.sleepMin[it] }.filter { it > 0 }
            val st = idx.map { s.steps[it] }.filter { it > 0 }
            if (sl.isNotEmpty()) line("  Сон в среднем: ${sl.average().toInt() / 60} ч ${sl.average().toInt() % 60} мин")
            if (st.isNotEmpty()) line("  Шаги в среднем: ${st.average().toInt()} в день")
        }
        val moods = w.moods.filter { LocalDate.parse(it.date).isAfter(from) }
        if (moods.isNotEmpty()) line("  Настроение в среднем: ${f1(moods.map { it.mood }.average())} из 5")
        // Самочувствие
        val sym = w.symptoms.filter { LocalDate.parse(it.date).isAfter(from) }.sortedBy { it.date }
        line("Самочувствие и жалобы", head, 26f)
        if (sym.isEmpty()) line("нет записей", grey)
        sym.forEach { s -> line("  ${Fmt.date(LocalDate.parse(s.date))} — ${s.name}, сила ${s.level}/5" + if (s.note.isNotBlank()) " (${s.note})" else "") }
        line("Это записи пациента, не медицинское заключение.", grey, 26f)
        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val out = File(dir, "zdorovie-${today}.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
