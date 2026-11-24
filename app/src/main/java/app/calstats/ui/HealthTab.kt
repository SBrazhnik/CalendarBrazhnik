@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.health.FoodSearch
import app.calstats.health.HealthLink
import app.calstats.work.Activity
import app.calstats.work.Advice
import app.calstats.work.FOOD_BASE
import app.calstats.work.FoodItem
import app.calstats.work.HealthMath
import app.calstats.work.HealthOps
import app.calstats.work.HealthStats
import app.calstats.work.HealthProfile
import app.calstats.work.Meal
import app.calstats.work.Sex
import app.calstats.work.WeightGoal
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

internal enum class HView(val title: String) { TODAY("День"), FOOD("Питание"), BODY("Активность"), VITALS("Давление и анализы"), MEDS("Лекарства"), CYCLE("Цикл"), STATS("Статистика"), PROFILE("Профиль") }

private val PROT = Color(0xFF4A6A8A)
private val FAT = Color(0xFFC9A46A)
private val CARB = Color(0xFF6E8B3D)
private val WATER = Color(0xFF3F7C9A)
private val SLEEP = Color(0xFF6C4F7A)

/** Модуль «Здоровье и питание». */
@Composable
fun HealthTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var view by navView(vm, "health", HView.TODAY)
    var foodFor by remember { mutableStateOf<Pair<LocalDate, Meal>?>(null) }
    var weightDialog by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(vm.today) }
    LaunchedEffect(Unit) { vm.refreshHealth() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Здоровье", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (vm.healthGranted > 0) {
                        IconButton(onClick = { vm.refreshHealth(true) }) {
                            if (vm.healthLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Refresh, "Обновить")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val female = vm.work.healthProfile?.sex == app.calstats.work.Sex.FEMALE || vm.work.cycles.isNotEmpty()
                HView.entries.filter { it != HView.CYCLE || female }.forEach { v -> FilterChip(selected = view == v, onClick = { view = v }, label = { Text(v.title) }) }
            }
            when (view) {
                HView.TODAY -> TodayView(vm, day, { day = it }, onFood = { foodFor = day to it }, onProfile = { view = HView.PROFILE }, onWeight = { weightDialog = true })
                HView.FOOD -> FoodView(vm, day, { day = it }) { foodFor = day to it }
                HView.BODY -> BodyView(vm) { weightDialog = true }
                HView.STATS -> StatsView(vm) { d -> day = d; view = HView.TODAY }
                HView.MEDS -> MedsView(vm)
                HView.VITALS -> VitalsView(vm)
                HView.CYCLE -> CycleView(vm)
                HView.PROFILE -> ProfileView(vm)
            }
        }
    }

    foodFor?.let { (d, m) -> FoodSheet(vm, d, m) { foodFor = null } }
    if (weightDialog) WeightDialog(vm) { weightDialog = false }
}

// ======================= СЕГОДНЯ =======================

@Composable
private fun TodayView(vm: MainViewModel, today: LocalDate, onDay: (LocalDate) -> Unit, onFood: (Meal) -> Unit, onProfile: () -> Unit, onWeight: () -> Unit) {
    val w = vm.work
    val p = w.healthProfile
    val norms = p?.let { HealthMath.norms(it, today) }
    val entries = w.foodLog.filter { it.date == today.toString() }
    val tot = HealthMath.totals(entries)
    val water = HealthOps.water(w, today)
    val isToday = today == vm.today
    val tips = if (isToday) Advice.tips(p, norms, tot, water, vm.health, LocalTime.now().hour) else emptyList()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DayPicker(vm, today, onDay) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val goal = norms?.kcal ?: 2000
                        Box(contentAlignment = Alignment.Center) {
                            RingProgress((tot.kcal / goal).toFloat(), MaterialTheme.colorScheme.primary, 96)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${tot.kcal.roundToInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("ккал", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isToday) "Питание сегодня" else "Питание за день", style = MaterialTheme.typography.labelLarge)
                            if (norms != null) {
                                val left = norms.kcal - tot.kcal.roundToInt()
                                Text(
                                    if (left >= 0) "Осталось $left ккал" else "Сверх нормы ${-left} ккал",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text("Норма ${norms.kcal} ккал", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Норма не рассчитана", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onProfile, contentPadding = PaddingValues(0.dp)) {
                                    Text("Заполнить профиль →", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MacroBar("Белки", tot.protein, norms?.protein, PROT, Modifier.weight(1f))
                        MacroBar("Жиры", tot.fat, norms?.fat, FAT, Modifier.weight(1f))
                        MacroBar("Углеводы", tot.carbs, norms?.carbs, CARB, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { m ->
                    val kc = entries.filter { it.meal == m }.sumOf { it.kcal }.roundToInt()
                    OutlinedButton(onClick = { onFood(m) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("${m.emoji} ${m.title}" + if (kc > 0) " · $kc" else "  +")
                    }
                }
            }
        }
        item { WaterCard(vm, norms?.waterMl ?: 2000, today) }
        item { ActivityCard(vm, p, today, onWeight) }
        item { MoodCard(vm, today) }
        if (isToday) item { FastingCard(vm) }
        if (tips.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Советы", style = MaterialTheme.typography.titleMedium)
                        tips.forEach { t ->
                            Row {
                                Text(t.emoji, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(t.title, style = MaterialTheme.typography.labelLarge)
                                    Text(t.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text(
                            "Советы общие и не заменяют консультацию врача.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(vm: MainViewModel, goal: Int, today: LocalDate) {
    val ml = HealthOps.water(vm.work, today)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💧", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Вода", style = MaterialTheme.typography.titleMedium)
                    Text("$ml из $goal мл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.updateWork { HealthOps.addWater(it, today, -250) } }, enabled = ml > 0) { Text("−", fontSize = 22.sp) }
                Button(onClick = { vm.updateWork { HealthOps.addWater(it, today, 250) } }) { Text("+ стакан") }
            }
            Spacer(Modifier.height(10.dp))
            val cups = (goal / 250).coerceIn(4, 16)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until cups).forEach { i ->
                    Box(
                        Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (ml >= (i + 1) * 250) WATER else WATER.copy(alpha = 0.18f)),
                    )
                }
            }
        }
    }
}

/** Шаги, сон, пульс, вес — из Samsung Health через Health Connect. */
@Composable
private fun ActivityCard(vm: MainViewModel, p: HealthProfile?, day: LocalDate, onWeight: () -> Unit) {
    val h = vm.health
    if (vm.healthGranted == 0 || h == null) {
        ConnectCard(vm)
        return
    }
    val stepsGoal = p?.stepsGoal ?: 8000
    val idx = h.days.indexOf(day)
    if (idx < 0) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Text(
                "Шаги и сон за этот день не загружены — Samsung Health показывается за последние ${h.days.size} дней.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    val steps = h.steps.getOrElse(idx) { 0 }
    val sleep = h.sleepMin.getOrElse(idx) { 0 }
    val isToday = day == vm.today
    val dayWorkouts = h.workouts.filter { it.date == day }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Активность и сон", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Samsung Health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("🚶", "$steps", "шагов из $stepsGoal", Modifier.weight(1f))
                Metric("😴", if (sleep > 0) Fmt.duration(sleep) else "—", "сон ночью", Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = { (steps.toFloat() / stepsGoal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = CARB,
                trackColor = CARB.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isToday) Metric("❤", (h.restingHr ?: h.avgHr)?.let { "$it" } ?: "—", if (h.restingHr != null) "пульс в покое" else "пульс сегодня", Modifier.weight(1f))
                else Metric("🏋", "${dayWorkouts.size}", "тренировок", Modifier.weight(1f))
                val kg = vm.work.weightLog.lastOrNull { it.date <= day.toString() }?.kg ?: h.weightKg
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onWeight)) {
                    Metric("⚖", kg?.let { "%.1f".format(it) } ?: "—", "вес, кг · записать", Modifier)
                }
            }
            val act = h.activeKcal.getOrElse(idx) { 0.0 }
            if (act > 0) Text("🔥 Активные калории: ${act.roundToInt()} ккал", style = MaterialTheme.typography.bodySmall)
            dayWorkouts.forEach { Text("🏋 ${it.title} · ${Fmt.duration(it.minutes)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Metric(emoji: String, value: String, label: String, modifier: Modifier) {
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(HealthLink.permissionContract()) { vm.refreshHealth(true) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Шаги, сон, пульс и вес", style = MaterialTheme.typography.titleMedium)
            when (vm.healthStatus) {
                HealthLink.Status.AVAILABLE -> {
                    Text(
                        "Подключите Health Connect — туда Samsung Health передаёт данные с телефона и часов. " +
                            "В Samsung Health включите: Настройки → Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            runCatching { launcher.launch(HealthLink.permissions) }.onFailure { vm.message = "Не удалось открыть Health Connect" }
                        }) { Text("Подключить") }
                        TextButton(onClick = {
                            try { ctx.startActivity(HealthLink.settingsIntent()) } catch (_: ActivityNotFoundException) { vm.message = "Health Connect не найден" }
                        }) { Text("Настройки") }
                    }
                }
                HealthLink.Status.NEEDS_UPDATE, HealthLink.Status.UNAVAILABLE -> {
                    Text(
                        if (vm.healthStatus == HealthLink.Status.NEEDS_UPDATE) "Нужно обновить Health Connect."
                        else "На телефоне нет Health Connect. На Android 14 и новее он встроен в систему, на более старых — ставится из магазина приложений.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        try { ctx.startActivity(HealthLink.installIntent()) } catch (_: ActivityNotFoundException) { vm.message = "Не найден магазин приложений" }
                    }) { Text("Установить Health Connect") }
                }
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, goal: Int?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            "${value.roundToInt()}" + (goal?.let { " / $it г" } ?: " г"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.25f))) {
            val f = if (goal == null || goal == 0) 0f else (value / goal).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
private fun RingProgress(f: Float, color: Color, size: Int) {
    val track = color.copy(alpha = 0.22f)
    val over = f > 1f
    Canvas(Modifier.size(size.dp)) {
        val sw = 9.dp.toPx()
        val inset = sw / 2
        val s = Size(this.size.width - sw, this.size.height - sw)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(sw))
        drawArc(
            if (over) Color(0xFFC0583F) else color, -90f, 360f * f.coerceIn(0f, 1f), false, Offset(inset, inset), s,
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}

// ======================= ПИТАНИЕ =======================

@Composable
private fun FoodView(vm: MainViewModel, day: LocalDate, onDay: (LocalDate) -> Unit, onAdd: (Meal) -> Unit) {
    val w = vm.work
    val entries = w.foodLog.filter { it.date == day.toString() }
    val tot = HealthMath.totals(entries)
    val norms = w.healthProfile?.let { HealthMath.norms(it, vm.today) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DayPicker(vm, day, onDay)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Калории", style = MaterialTheme.typography.labelSmall)
                        Text("${tot.kcal.roundToInt()}" + (norms?.let { " / ${it.kcal}" } ?: ""), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    MacroBar("Б", tot.protein, norms?.protein, PROT, Modifier.weight(1f))
                    MacroBar("Ж", tot.fat, norms?.fat, FAT, Modifier.weight(1f))
                    MacroBar("У", tot.carbs, norms?.carbs, CARB, Modifier.weight(1f))
                }
            }
        }
        Meal.entries.forEach { m ->
            val list = entries.filter { it.meal == m }
            item(key = m.name) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${m.emoji} ${m.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (list.isNotEmpty()) Text("${list.sumOf { it.kcal }.roundToInt()} ккал", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onAdd(m) }) { Icon(Icons.Filled.Add, "Добавить") }
                        }
                        list.forEach { e ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${e.grams} г · Б ${e.protein.roundToInt()} · Ж ${e.fat.roundToInt()} · У ${e.carbs.roundToInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${e.kcal.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                                IconButton(onClick = { vm.updateWork { HealthOps.deleteFood(it, e.id) } }) {
                                    Icon(Icons.Filled.Close, "Удалить", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Добавление еды: свои и недавние продукты, встроенная база, Open Food Facts, штрихкод. */
@Composable
private fun FoodSheet(vm: MainViewModel, date: LocalDate, meal0: Meal, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var meal by remember { mutableStateOf(meal0) }
    var query by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<FoodItem?>(null) }
    var custom by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf(false) }
    val w = vm.work

    val local = remember(query, w.recentFoods, w.customFoods) {
        val q = query.trim().lowercase()
        val all = (w.recentFoods + w.customFoods + FOOD_BASE).distinctBy { it.name.lowercase() }
        if (q.isEmpty()) all.take(30) else all.filter { it.name.lowercase().contains(q) }
    }

    fun searchOnline() {
        if (query.isBlank()) return
        loading = true
        error = null
        scope.launch {
            runCatching { FoodSearch.search(query) }
                .onSuccess { online = it; if (it.isEmpty()) error = "В Open Food Facts ничего не нашлось" }
                .onFailure { error = "Нет связи с Open Food Facts" }
            loading = false
        }
    }

    fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val code = b.rawValue ?: return@addOnSuccessListener
                val known = (w.customFoods + w.recentFoods).firstOrNull { it.barcode == code }
                if (known != null) { pick = known; return@addOnSuccessListener }
                loading = true
                error = null
                scope.launch {
                    runCatching { FoodSearch.byBarcode(code) }
                        .onSuccess { f -> if (f != null) pick = f else error = "Штрихкод $code не найден — добавьте продукт вручную" }
                        .onFailure { error = "Нет связи с Open Food Facts" }
                    loading = false
                }
            }
            .addOnFailureListener { error = "Сканер недоступен: нужны сервисы Google Play" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp)) {
            Text("Добавить еду · ${Fmt.dayTitle(date, vm.today)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Meal.entries.forEach { m -> FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text("${m.emoji} ${m.title}") }) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; online = emptyList(); error = null },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                placeholder = { Text("Продукт или блюдо") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { searchOnline() }, enabled = query.isNotBlank() && !loading, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("В интернете") }
                OutlinedButton(onClick = { scan() }, enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Штрихкод") }
                OutlinedButton(onClick = { custom = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Свой") }
                OutlinedButton(onClick = { recipe = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Рецепт") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                if (online.isNotEmpty()) {
                    item { SectionLabel("Open Food Facts") }
                    items(online) { f -> FoodRow(f) { pick = f } }
                }
                item { SectionLabel(if (query.isBlank()) "Недавние и частые" else "Найдено у вас") }
                items(local) { f -> FoodRow(f) { pick = f } }
                if (local.isEmpty() && online.isEmpty() && query.isNotBlank()) {
                    item {
                        TextButton(onClick = { searchOnline() }) { Text("Искать «${query.trim()}» в Open Food Facts") }
                    }
                }
            }
        }
    }

    pick?.let { f ->
        PortionDialog(f, onDismiss = { pick = null }) { grams ->
            vm.updateWork { HealthOps.addFood(it, HealthMath.portion(f, grams, date, meal), f) }
            vm.message = "${f.name} — ${(f.kcal * grams / 100).roundToInt()} ккал"
            pick = null
        }
    }
    if (recipe) RecipeDialog(vm, onDismiss = { recipe = false }) { f -> recipe = false; pick = f }
    if (custom) {
        CustomFoodDialog(query, onDismiss = { custom = false }) { f ->
            vm.updateWork { HealthOps.saveCustomFood(it, f) }
            custom = false
            pick = f
        }
    }
}

@Composable
private fun SectionLabel(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun FoodRow(f: FoodItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "на 100 г · Б ${f.protein.roundToInt()} · Ж ${f.fat.roundToInt()} · У ${f.carbs.roundToInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${f.kcal.roundToInt()} ккал", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PortionDialog(f: FoodItem, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var grams by remember { mutableStateOf(f.portion.toString()) }
    val g = grams.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter(Char::isDigit).take(4) },
                    label = { Text("Вес порции, г") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(f.portion, 50, 100, 150, 200, 300).distinct().forEach { v ->
                        FilterChip(selected = g == v, onClick = { grams = v.toString() }, label = { Text("$v г") })
                    }
                }
                Text(
                    "${(f.kcal * g / 100).roundToInt()} ккал · Б ${(f.protein * g / 100).roundToInt()} · Ж ${(f.fat * g / 100).roundToInt()} · У ${(f.carbs * g / 100).roundToInt()}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(g) }, enabled = g > 0) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CustomFoodDialog(name0: String, onDismiss: () -> Unit, onSave: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf(name0.trim().replaceFirstChar { it.uppercase() }) }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой продукт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Text("На 100 г — смотрите на упаковке", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(kcal, { kcal = it }, label = { Text("Калории, ккал") }, singleLine = true, keyboardOptions = kb)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(p, { p = it }, label = { Text("Б") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it }, label = { Text("Ж") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                    OutlinedTextField(c, { c = it }, label = { Text("У") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(FoodItem(name.trim(), num(kcal) ?: 0.0, num(p) ?: 0.0, num(fat) ?: 0.0, num(c) ?: 0.0)) },
                enabled = name.isNotBlank() && num(kcal) != null,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= АКТИВНОСТЬ =======================

@Composable
private fun BodyView(vm: MainViewModel, onWeight: () -> Unit) {
    val h = vm.health
    val p = vm.work.healthProfile
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (vm.healthGranted == 0 || h == null) {
            item { ConnectCard(vm) }
        } else {
            item {
                ChartCard("Шаги за 2 недели", "в среднем ${h.steps.dropLast(1).takeLast(7).let { if (it.isEmpty()) 0 else it.average().toLong() }} за неделю") {
                    Bars(h.steps.takeLast(14).map { it.toDouble() }, h.days.takeLast(14), CARB, (p?.stepsGoal ?: 8000).toDouble())
                }
            }
            item {
                val sl = h.sleepMin.takeLast(14).filter { it > 0 }
                ChartCard("Сон", if (sl.isEmpty()) "нет данных" else "в среднем ${Fmt.duration(sl.average().toLong())}") {
                    Bars(h.sleepMin.takeLast(14).map { it / 60.0 }, h.days.takeLast(14), SLEEP, (p?.sleepGoalMin ?: 480) / 60.0)
                }
            }
            if (h.workouts.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Тренировки", style = MaterialTheme.typography.titleMedium)
                            h.workouts.take(10).forEach { t ->
                                Row {
                                    Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${Fmt.dateNoYear(t.date)} · ${Fmt.duration(t.minutes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val log = vm.work.weightLog.takeLast(30)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Вес", style = MaterialTheme.typography.titleMedium)
                            val last = log.lastOrNull()?.kg ?: h?.weightKg
                            val first = log.firstOrNull()?.kg
                            Text(
                                when {
                                    last == null -> "Записей нет"
                                    first != null && log.size > 1 -> "%.1f кг · %+.1f с %s".format(last, last - first, Fmt.dateNoYear(LocalDate.parse(log.first().date)))
                                    else -> "%.1f кг".format(last)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = onWeight) { Text("Записать") }
                    }
                    if (log.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        WeightLine(log.map { it.kg })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, sub: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Bars(values: List<Double>, days: List<LocalDate>, color: Color, goal: Double) {
    val max = maxOf(values.maxOrNull() ?: 0.0, goal) * 1.1
    val grid = MaterialTheme.colorScheme.outline
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            if (values.isEmpty() || max <= 0) return@Canvas
            val slot = size.width / values.size
            val bw = slot * 0.62f
            values.forEachIndexed { i, v ->
                val bh = (v / max * size.height).toFloat()
                drawRoundRect(
                    if (v >= goal) color else color.copy(alpha = 0.5f),
                    Offset(i * slot + (slot - bw) / 2, size.height - bh),
                    Size(bw, bh),
                    CornerRadius(bw / 4),
                )
            }
            val gy = (size.height - goal / max * size.height).toFloat()
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    if (i % 2 == days.size % 2 || i == days.lastIndex) "${d.dayOfMonth}" else "",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeightLine(values: List<Double>) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val mn = values.min() - 0.5
        val mx = values.max() + 0.5
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, (size.height - (v - mn) / (mx - mn) * size.height).toFloat()) }
        pts.zipWithNext().forEach { (a, b) -> drawLine(c, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
        pts.forEach { drawCircle(c, 3.dp.toPx(), it) }
    }
}

@Composable
private fun WeightDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val last = vm.work.weightLog.lastOrNull()?.kg ?: vm.health?.weightKg ?: vm.work.healthProfile?.weightKg
    var text by remember { mutableStateOf(last?.let { "%.1f".format(it) } ?: "") }
    val kg = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вес сегодня") },
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text("кг") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateWork { HealthOps.addWeight(it, vm.today, kg!!) }
                onDismiss()
            }, enabled = kg != null && kg in 25.0..350.0) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор дня: стрелки и календарь. */
@Composable
private fun DayPicker(vm: MainViewModel, day: LocalDate, onDay: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDay(day.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Раньше") }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                pickDate(ctx, day) { if (!it.isAfter(vm.today)) onDay(it) }
            }.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("📅 " + Fmt.dayTitle(day, vm.today), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        IconButton(onClick = { onDay(day.plusDays(1)) }, enabled = day < vm.today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Позже") }
    }
    if (day != vm.today) {
        TextButton(onClick = { onDay(vm.today) }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к сегодня") }
    }
}
}

// ======================= СТАТИСТИКА =======================

@Composable
private fun StatsView(vm: MainViewModel, onDay: (LocalDate) -> Unit) {
    var days by rememberSaveable { mutableStateOf(7) }
    val r = remember(vm.work, vm.health, days, vm.today) { HealthStats.compute(vm.work, vm.health, vm.today, days) }
    val w = vm.work
    val norms = w.healthProfile?.let { HealthMath.norms(it, vm.today) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text("Неделя") })
                FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text("Месяц") })
            }
            Text(
                "Среднее за день и сравнение с прошлым${if (days == 7) "и 7 днями" else "и 30 днями"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            }
        }
        r.metrics.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { m -> StatTile(m, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            // Калории по дням: нажмите на столбик — откроется этот день.
            val ds = (days - 1 downTo 0).map { vm.today.minusDays(it.toLong()) }
            val kc = ds.map { d -> w.foodLog.filter { it.date == d.toString() }.sumOf { it.kcal } }
            ChartCard("Калории по дням", "нажмите на день, чтобы открыть его") {
                Bars(kc, ds, PROT, (norms?.kcal ?: 2000).toDouble())
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ds.reversed().take(14).forEach { d ->
                        FilterChip(selected = false, onClick = { onDay(d) }, label = { Text(Fmt.dateNoYear(d), fontSize = 11.sp) })
                    }
                }
            }
        }
        if (r.conclusions.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Выводы", style = MaterialTheme.typography.titleMedium)
                        r.conclusions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text("Выводы общие и не заменяют консультацию врача.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(m: HealthStats.Metric, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(m.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val v = m.cur
            Text(
                when {
                    v == null -> "—"
                    m.key == "sleep" || m.key == "weight" || m.key == "mood" -> "%.1f".format(v)
                    else -> "${v.roundToInt()}"
                } + if (v != null && m.unit.isNotEmpty()) " ${m.unit}" else "",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 30.sp),
                maxLines = 1,
            )
            val d = m.deltaPct
            val delta = if (m.key == "weight" && m.cur != null && m.prev != null) "%+.1f кг".format(m.cur - m.prev) else d?.let { "%+d%%".format(it.roundToInt()) }
            if (delta != null) {
                val up = (d ?: (m.cur!! - m.prev!!)) > 0
                val good = m.goodUp?.let { it == up }
                Text(
                    "$delta к прошлому периоду",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (good) { true -> Color(0xFF3F8A5A); false -> Color(0xFFC0583F); null -> MaterialTheme.colorScheme.onSurfaceVariant },
                )
            } else {
                Text(if (m.cur == null) "нет данных" else "нет данных для сравнения", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ======================= ПРОФИЛЬ =======================

@Composable
private fun ProfileView(vm: MainViewModel) {
    val saved = vm.work.healthProfile
    var p by remember(saved) { mutableStateOf(saved ?: HealthProfile(weightKg = vm.health?.weightKg ?: 75.0)) }
    var year by remember(saved) { mutableStateOf(p.birthYear.toString()) }
    var height by remember(saved) { mutableStateOf(p.heightCm.toString()) }
    var weight by remember(saved) { mutableStateOf("%.1f".format(p.weightKg)) }
    var steps by remember(saved) { mutableStateOf(p.stepsGoal.toString()) }
    val cur = p.copy(
        birthYear = year.toIntOrNull()?.takeIf { it in 1920..2015 } ?: p.birthYear,
        heightCm = height.toIntOrNull()?.takeIf { it in 100..250 } ?: p.heightCm,
        weightKg = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..350.0 } ?: p.weightKg,
        stepsGoal = steps.toIntOrNull()?.takeIf { it in 1000..50000 } ?: p.stepsGoal,
    )
    val n = HealthMath.norms(cur, vm.today)
    val bmi = HealthMath.bmi(cur)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Пол", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s -> FilterChip(selected = p.sex == s, onClick = { p = p.copy(sex = s) }, label = { Text(s.title) }) }
        }
        val kb = KeyboardOptions(keyboardType = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Год рождения") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
            OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("Рост, см") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес, кг") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(steps, { steps = it.filter(Char::isDigit).take(5) }, label = { Text("Цель, шагов") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
        }
        Text("Активность", style = MaterialTheme.typography.labelLarge)
        Activity.entries.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { p = p.copy(activity = a) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = p.activity == a, onClick = { p = p.copy(activity = a) })
                Text(a.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Цель", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightGoal.entries.forEach { g -> FilterChip(selected = p.goal == g, onClick = { p = p.copy(goal = g) }, label = { Text(g.title) }) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Норма на день", style = MaterialTheme.typography.labelLarge)
                Text("${n.kcal} ккал", style = MaterialTheme.typography.headlineSmall)
                Text("Белки ${n.protein} г · Жиры ${n.fat} г · Углеводы ${n.carbs} г", style = MaterialTheme.typography.bodyMedium)
                Text("Вода ${n.waterMl} мл · ИМТ %.1f — %s".format(bmi, HealthMath.bmiText(bmi)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Формула Миффлина — Сан Жеора с учётом активности. Это ориентир, а не назначение врача.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Button(
            onClick = {
                vm.updateWork { it.copy(healthProfile = cur) }
                vm.message = "Профиль сохранён"
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить") }
        Spacer(Modifier.height(24.dp))
    }
}

// ======================= НА ГЛАВНОЙ =======================

@Composable
fun HealthSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val norms = w.healthProfile?.let { HealthMath.norms(it, today) }
    val tot = HealthMath.totals(w.foodLog.filter { it.date == today.toString() })
    val water = HealthOps.water(w, today)
    val h = vm.health
    Card(onClick = { vm.tab = Tab.HEALTH }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(Tab.HEALTH)
                Spacer(Modifier.width(8.dp))
                Text("Здоровье", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${tot.kcal.roundToInt()}" + (norms?.let { " / ${it.kcal}" } ?: "") + " ккал",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("💧", "$water", "мл воды", Modifier.weight(1f))
                if (h != null && vm.healthGranted > 0) {
                    Metric("🚶", "${h.todaySteps}", "шагов", Modifier.weight(1f))
                    Metric("😴", if (h.lastSleep > 0) Fmt.hours(h.lastSleep) else "—", "сон", Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(2f).clip(RoundedCornerShape(10.dp)).clickable { vm.updateWork { HealthOps.addWater(it, today, 250) } }.padding(6.dp)) {
                        Text("+ стакан воды", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
