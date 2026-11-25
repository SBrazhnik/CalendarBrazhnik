package app.calstats.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calstats.MainViewModel
import app.calstats.Net
import app.calstats.work.DayInfo
import app.calstats.work.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** Загрузка погоды и курса доллара в историю календаря. */
object DayInfoSync {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("daymeta", Context.MODE_PRIVATE)
    fun show(ctx: Context) = sp(ctx).getBoolean("show", true)
    fun setShow(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("show", on).apply()
    fun city(ctx: Context) = sp(ctx).getString("city", null)
    fun coords(ctx: Context): Pair<Double, Double>? {
        val p = sp(ctx); if (!p.contains("lat")) return null
        return p.getFloat("lat", 0f).toDouble() to p.getFloat("lon", 0f).toDouble()
    }
    fun setCity(ctx: Context, name: String, lat: Double, lon: Double) =
        sp(ctx).edit().putString("city", name).putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).putLong("last", 0).apply()

    /** Если город не задан — берём последнее известное местоположение телефона (без запроса GPS). */
    private fun autoLocate(ctx: Context): Boolean {
        val ok = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!ok) return false
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        @Suppress("MissingPermission")
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return false
        setCity(ctx, "Моё местоположение", loc.latitude, loc.longitude)
        return true
    }

    /** Обновить: свежая погода и курс раз в 2 часа; месяц из прошлого — архивом, если его ещё нет в истории. */
    suspend fun refresh(ctx: Context, vm: MainViewModel, month: YearMonth, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val have = vm.work.dayInfo.associateBy { it.date }
        val p = sp(ctx)
        val stale = force || System.currentTimeMillis() - p.getLong("last", 0) > 2 * 3600_000L
        val mStart = month.atDay(1); val mEnd = month.atEndOfMonth().let { if (it.isAfter(today.plusDays(13))) today.plusDays(13) else it }
        val monthMissing = !mStart.isAfter(today) && generateSequence(mStart) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(mEnd, today)) }
            .any { have[it.toString()]?.let { d -> d.usd == null || d.tMax == null } != false }
        if (!stale && !monthMissing) return@withContext null
        val fresh = mutableListOf<DayInfo>()
        val errors = mutableListOf<String>()
        // Погода
        if (coords(ctx) == null) autoLocate(ctx)
        coords(ctx)?.let { (lat, lon) ->
            runCatching { fresh += Net.weather(lat, lon) }.onFailure { errors += "погода: " + Net.why(it) }
            if (mStart.isBefore(today.minusDays(30))) {
                val to = minOf(mEnd, today.minusDays(2))
                if (!to.isBefore(mStart)) runCatching { fresh += Net.weather(lat, lon, mStart, to) }.onFailure { errors += "архив погоды: " + Net.why(it) }
            }
        }
        // Курс доллара ЦБ (курс на завтра ЦБ публикует днём)
        val from = minOf(mStart, today.minusDays(31))
        val to = today.plusDays(1)
        runCatching {
            val rates = Weather.fillRates(Net.usdRange(from.minusDays(10), to), from, to)
            fresh += rates.map { (d, v) -> DayInfo(d.toString(), usd = v) }
        }.onFailure { errors += "курс ЦБ: " + Net.why(it) }
        if (fresh.isNotEmpty()) withContext(Dispatchers.Main) { vm.updateWork { it.copy(dayInfo = Weather.merge(it.dayInfo, fresh)) } }
        if (errors.isEmpty()) p.edit().putLong("last", System.currentTimeMillis()).apply()
        errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}

private fun money(v: Double) = "%,.2f".format(v).replace(',', ' ').replace('.', ',')

/** Карточка дня в календаре: погода и курс доллара. Нажатие — история. */
@Composable
fun DayInfoCard(vm: MainViewModel, date: LocalDate) {
    val ctx = LocalContext.current
    if (!DayInfoSync.show(ctx)) return
    var history by remember { mutableStateOf(false) }
    val info = remember(vm.work.dayInfo, date) { vm.work.dayInfo.firstOrNull { it.date == date.toString() } }
    val prevUsd = remember(vm.work.dayInfo, date) { vm.work.dayInfo.lastOrNull { it.date < date.toString() && it.usd != null }?.usd }
    Card(onClick = { history = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val (emoji, text) = Weather.describe(info?.code)
            if (info?.tMax != null) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Weather.temp(info.tMax)} / ${Weather.temp(info.tMin)}", style = MaterialTheme.typography.titleMedium)
                    Text(text + if (date.isAfter(vm.today)) " · прогноз" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(if (DayInfoSync.coords(ctx) == null) "🌤 Укажите город для погоды" else if (date.isAfter(vm.today.plusDays(13))) "🌤 Прогноза пока нет" else "🌤 Погода загружается…",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(info?.usd?.let { "$ ${money(it)} ₽" } ?: "$ —", style = MaterialTheme.typography.titleMedium)
                val ch = if (info?.usd != null && prevUsd != null) info.usd - prevUsd else null
                Text(
                    when { ch == null -> "курс ЦБ"; kotlin.math.abs(ch) < 0.005 -> "без изменений"; ch > 0 -> "▲ ${money(ch)}"; else -> "▼ ${money(-ch)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = when { ch == null || kotlin.math.abs(ch) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant; ch > 0 -> Color(0xFFC0503F); else -> Color(0xFF2E8B57) },
                )
            }
        }
    }
    if (history) DayInfoHistory(vm, YearMonth.from(date)) { history = false }
}

/** История погоды и курса по месяцам: графики и таблица по дням. */
@Composable
fun DayInfoHistory(vm: MainViewModel, start: YearMonth, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(start) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(month) {
        busy = true; err = DayInfoSync.refresh(ctx, vm, month); busy = false
    }
    val days = remember(vm.work.dayInfo, month) {
        vm.work.dayInfo.filter { it.date.startsWith(month.toString()) }.sortedBy { it.date }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("←") }
                    Text("Погода и курс $", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    TextButton(onClick = { settings = true }) { Text("⚙") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text(app.calstats.Fmt.monthName(month.monthValue) + " " + month.year, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now().plusMonths(1)) { Text("›") }
                }
                if (busy) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Обновляю…", style = MaterialTheme.typography.bodySmall)
                }
                err?.let { Text("⚠ $it", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val usd = days.mapNotNull { d -> d.usd?.let { d.date to it } }
                    if (usd.size >= 2) item {
                        val first = usd.first().second; val last = usd.last().second
                        Text("Курс доллара: ${money(usd.minOf { it.second })} – ${money(usd.maxOf { it.second })} ₽, за месяц " +
                            (if (last >= first) "▲ " else "▼ ") + money(kotlin.math.abs(last - first)), style = MaterialTheme.typography.bodyMedium)
                        Chart(usd.map { it.second.toFloat() }, MaterialTheme.colorScheme.primary)
                    }
                    val temps = days.filter { it.tMax != null }
                    if (temps.size >= 2) item {
                        Text("Температура: от ${Weather.temp(temps.minOf { it.tMin ?: it.tMax!! })} до ${Weather.temp(temps.maxOf { it.tMax!! })}", style = MaterialTheme.typography.bodyMedium)
                        Chart(temps.map { it.tMax!!.toFloat() }, Color(0xFFE08A3C), temps.map { (it.tMin ?: it.tMax!!).toFloat() }, Color(0xFF4A86C5))
                    }
                    if (days.isEmpty() && !busy) item { Text("За этот месяц данных нет.", style = MaterialTheme.typography.bodyMedium) }
                    items(days.reversed(), key = { it.date }) { d ->
                        val date = LocalDate.parse(d.date)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${date.dayOfMonth} ${app.calstats.Fmt.weekdayShort(date.dayOfWeek.value - 1)}", Modifier.width(64.dp),
                                fontWeight = if (date == vm.today) FontWeight.Bold else null, style = MaterialTheme.typography.bodyMedium)
                            Text(Weather.describe(d.code).first, Modifier.width(32.dp))
                            Text(if (d.tMax != null) "${Weather.temp(d.tMax)} / ${Weather.temp(d.tMin)}" else "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(d.usd?.let { money(it) + " ₽" } ?: "—", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    item {
                        Text("Погода — Open-Meteo${DayInfoSync.city(ctx)?.let { " ($it)" }.orEmpty()}; курс — официальный курс ЦБ РФ. История хранится в приложении и попадает в резервную копию.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (settings) DayInfoSettings(onDismiss = { settings = false }) { scope.launch { busy = true; err = DayInfoSync.refresh(ctx, vm, month, force = true); busy = false } }
}

@Composable
private fun Chart(values: List<Float>, color: Color, second: List<Float>? = null, color2: Color = color) {
    val all = values + second.orEmpty()
    val lo = all.min(); val hi = all.max().let { if (it - lo < 0.01f) lo + 1f else it }
    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 6.dp)) {
        fun line(v: List<Float>, c: Color) {
            if (v.size < 2) return
            val p = Path()
            v.forEachIndexed { i, x ->
                val o = Offset(size.width * i / (v.size - 1), size.height * (1 - (x - lo) / (hi - lo)))
                if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            }
            drawPath(p, c, style = Stroke(width = 2.5.dp.toPx()))
        }
        line(values, color); second?.let { line(it, color2) }
    }
}

@Composable
private fun DayInfoSettings(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Triple<String, Double, Double>>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(DayInfoSync.show(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Погода и курс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Показывать в календаре", Modifier.weight(1f))
                    Switch(show, { show = it; DayInfoSync.setShow(ctx, it) })
                }
                Text("Город: " + (DayInfoSync.city(ctx) ?: "не задан"), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Москва, Казань…") }, singleLine = true)
                Button(enabled = q.isNotBlank(), onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Net.geocode(q.trim()) } }
                        found = r.getOrNull(); err = r.exceptionOrNull()?.let { Net.why(it) }
                    }
                }) { Text("Найти город") }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                found?.let { l ->
                    if (l.isEmpty()) Text("Не нашлось", style = MaterialTheme.typography.bodySmall)
                    l.forEach { (name, lat, lon) ->
                        Text(name, Modifier.fillMaxWidth().clickable { DayInfoSync.setCity(ctx, name, lat, lon); onChanged(); onDismiss() }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
