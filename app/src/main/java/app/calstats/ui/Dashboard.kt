@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Period
import app.calstats.PeriodType
import app.calstats.R
import app.calstats.Screen
import app.calstats.Sobriety
import app.calstats.Stats
import app.calstats.Tab
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import app.calstats.TileSize
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.Status
import app.calstats.work.WishStatus
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Главная: сводка со всех включённых модулей, быстрый запуск и общие настройки. */
@Composable
fun DashboardTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val name = vm.work.meName.takeIf { it.isNotBlank() && it != "Я" }
    var txSheet by remember { mutableStateOf<app.calstats.work.Transaction?>(null) }
    txSheet?.let { t -> TxSheet(vm, t) { txSheet = null } }

    val drag = remember { TileDrag() }
    vm.tileSettings?.let { TileSettingsSheet(vm, it) }
    CompositionLocalProvider(LocalTileDrag provides drag) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (vm.editHome) "Настройка" else "Зелёный ритм", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (vm.editHome) {
                        TextButton(onClick = { vm.editHome = false }) { Text("Готово", fontWeight = FontWeight.Bold) }
                    } else {
                        IconButton(onClick = { vm.push(Screen.Search) }) { Icon(Icons.Filled.Search, "Поиск") }
                        IconButton(onClick = { vm.editHome = true }) { Icon(Icons.Filled.Edit, "Настроить Главную") }
                        Box(Modifier.padding(end = 8.dp).clip(CircleShape).clickable { vm.push(Screen.Profile) }) { Avatar(vm, 34.dp) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок в стиле corpdance: крупное «ГЛАВНАЯ» и рукописная дата.
            item {
                Column(Modifier.padding(bottom = 2.dp)) {
                    Text("Главная", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "✦ " + Fmt.dayTitle(vm.today, vm.today).replaceFirstChar { it.lowercase() } + " · " + greeting.lowercase() + (name?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!vm.editHome) item { MyDayCard(vm) }
            if (!vm.editHome) item { Column { QuickActions(vm) { txSheet = it } } }
            if (!vm.editHome && (vm.today.dayOfWeek.value >= 6 || vm.today.dayOfWeek.value == 1)) item { WeekSummaryCard(vm) }
            if (vm.editHome) {
                item {
                    Text(
                        "Зажмите плитку и перетащите на новое место. ⚙ — размер, что показывать на плитке, скрыть.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            tileRows(vm.homeTiles.map { it to vm.tileSize(it) }).forEach { row ->
                item(key = row.joinToString("+") { it.first.name }) {
                    val lifted = row.any { it.first == drag.dragging }
                    if (!vm.editHome && row.size == 1 && row[0].second == TileSize.LARGE && row[0].first != Tab.ACCOUNTS) {
                        Box(Modifier.zIndex(if (lifted) 1f else 0f).tileDrag(vm, row[0].first)) { LargeCard(vm, row[0].first) }
                    } else {
                        Row(Modifier.zIndex(if (lifted) 1f else 0f).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (t, z) ->
                                val w = units(z).toFloat()
                                if (z == TileSize.SMALL) SmallTile(vm, t, Modifier.weight(w).fillMaxHeight())
                                else MediumTile(vm, t, Modifier.weight(w).fillMaxHeight())
                            }
                            val free = 6 - row.sumOf { units(it.second) }
                            if (free > 0) Spacer(Modifier.weight(free.toFloat()))
                        }
                    }
                }
            }
            if (vm.editHome) {
                val hidden = vm.homeOrder.filter { vm.isEnabled(it) && it in vm.homeHidden }
                if (hidden.isNotEmpty()) {
                    item { Text("Скрыты с Главной", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    hidden.forEach { t ->
                        item(key = "h" + t.name) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CardIcon(t)
                                    Spacer(Modifier.width(10.dp))
                                    Text(t.title, Modifier.weight(1f))
                                    TextButton(onClick = { vm.setTileHidden(t, false) }) { Text("Показать") }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { vm.push(Screen.Modules) }, modifier = Modifier.fillMaxWidth()) { Text("Включить или выключить модули") }
                }
            } else {
                item {
                    Text(
                        "Зажмите плитку и перетащите, чтобы поменять местами",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
    }
}

/** Состояние перетаскивания плиток на Главной. */
class TileDrag {
    var dragging by mutableStateOf<Tab?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var target by mutableStateOf<Tab?>(null)
    var start: Rect = Rect.Zero
    val bounds = HashMap<Tab, Rect>()
    fun reset() {
        dragging = null
        target = null
        offset = Offset.Zero
    }
}

val LocalTileDrag = staticCompositionLocalOf { TileDrag() }

/** Зажать плитку и перетащить на место другой. */
@Composable
private fun Modifier.tileDrag(vm: MainViewModel, t: Tab): Modifier {
    val d = LocalTileDrag.current
    val haptic = LocalHapticFeedback.current
    val lifted = d.dragging == t
    val ring = MaterialTheme.colorScheme.primary
    return this
        .onGloballyPositioned { if (d.dragging != t) d.bounds[t] = it.boundsInRoot() }
        .zIndex(if (lifted) 2f else 0f)
        .graphicsLayer {
            if (lifted) {
                translationX = d.offset.x
                translationY = d.offset.y
                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 16f
                alpha = 0.95f
            }
        }
        .then(if (d.target == t) Modifier.border(2.dp, ring, MaterialTheme.shapes.medium) else Modifier)
        .pointerInput(t) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.editHome = true
                    d.start = d.bounds[t] ?: Rect.Zero
                    d.offset = Offset.Zero
                    d.dragging = t
                },
                onDrag = { change, amount ->
                    change.consume()
                    d.offset += amount
                    val c = d.start.center + d.offset
                    d.target = d.bounds.entries.firstOrNull { it.key != t && it.key in vm.homeTiles && it.value.contains(c) }?.key
                },
                onDragEnd = {
                    d.target?.let { vm.moveTileTo(t, it) }
                    d.reset()
                },
                onDragCancel = { d.reset() },
            )
        }
}

/** Ширина плитки в шестых долях ряда. */
private fun units(z: TileSize) = when (z) { TileSize.SMALL -> 2; TileSize.MEDIUM -> 3; TileSize.LARGE -> 6 }

/** Раскладка плиток по рядам по 6 долей: большая — весь ряд, средняя — половина, маленькая — треть. Порядок сохраняется. */
fun tileRows(items: List<Pair<Tab, TileSize>>): List<List<Pair<Tab, TileSize>>> {
    val rows = ArrayList<List<Pair<Tab, TileSize>>>()
    var cur = ArrayList<Pair<Tab, TileSize>>()
    var used = 0
    items.forEach { it ->
        val u = units(it.second)
        if (used + u > 6) {
            rows += cur
            cur = ArrayList()
            used = 0
        }
        cur += it
        used += u
    }
    if (cur.isNotEmpty()) rows += cur
    return rows
}

/** Панель управления плиткой в режиме настройки. */
@Composable
private fun TileControls(vm: MainViewModel, t: Tab) {
    val vis = vm.homeTiles
    val i = vis.indexOf(t)
    val z = vm.tileSize(t)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.moveTile(t, -1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Раньше", Modifier.size(20.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { vm.tileSettings = t }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("⚙ " + when (z) { TileSize.SMALL -> "S"; TileSize.MEDIUM -> "M"; TileSize.LARGE -> "L" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        IconButton(onClick = { vm.moveTile(t, 1) }, enabled = i in 0 until vis.lastIndex, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Позже", Modifier.size(20.dp))
        }
    }
}

/** Маленькая плитка: значок, крупное число, подпись. */
@Composable
private fun SmallTile(vm: MainViewModel, t: Tab, modifier: Modifier) {
    val info = tileInfo(vm, t)
    FlatCard(vm, t, modifier, onClick = { vm.tab = t }) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(t, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(t.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                info.value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 28.sp),
                color = if (info.alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(info.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vm.editHome) TileControls(vm, t)
        }
    }
}

/** Плоская карточка: без тени и рамки, нажатие — открыть, удержание — сменить размер. */
@Composable
private fun FlatCard(vm: MainViewModel, t: Tab, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.tileDrag(vm, t).clip(MaterialTheme.shapes.medium).clickable { if (!vm.editHome) onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun CardTitle(t: Tab, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CardIcon(t, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Карточка модуля на главной (большая). */
@Composable
private fun SummaryCard(t: Tab, vm: MainViewModel, subtitle: String? = null, onClick: () -> Unit = { vm.tab = t }, content: @Composable () -> Unit) {
    FlatCard(vm, t, Modifier.fillMaxWidth(), onClick) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            CardTitle(t, subtitle)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LargeCard(vm: MainViewModel, t: Tab) {
    when (t) {
        Tab.CALENDAR -> CalendarSummary(vm)
        Tab.TASKS -> TasksSummary(vm)
        Tab.FINANCE -> FinanceSummary(vm)
        Tab.HABITS -> HabitsSummary(vm)
        Tab.HEALTH -> HealthSummary(vm)
        Tab.DOCS -> DocsSummary(vm)
        Tab.MONEY -> MoneySummary(vm)
        Tab.ANALYSIS -> AnalysisSummary(vm)
        Tab.WISHES -> WishesSummary(vm)
        Tab.NOTES -> NotesSummary(vm)
        Tab.ACCOUNTS, Tab.GUIDE, Tab.SHOPPING, Tab.CARDS, Tab.GOALS, Tab.JOURNAL, Tab.CAR, Tab.HOME_UTIL, Tab.PETS,
        Tab.MEALS, Tab.WORKOUTS, Tab.TRIPS, Tab.READING, Tab.CHORES, Tab.PLANTS -> MediumTile(vm, t, Modifier.fillMaxWidth())
        Tab.HOME -> {}
    }
}

/** Что показывает средняя плитка: крупное число, подпись, строка ниже и (необязательно) прогресс. */
private data class TileInfo(val value: String, val unit: String, val caption: String, val progress: Float? = null, val alert: Boolean = false)

@Composable
private fun tileInfo(vm: MainViewModel, t: Tab): TileInfo {
    val w = vm.work
    val today = vm.today
    return when (t) {
        Tab.CALENDAR -> {
            val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
            val now = System.currentTimeMillis()
            val left = byDay[today].orEmpty().filter { it.allDay || it.end > now }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
            val next = left.firstOrNull { !it.allDay } ?: left.firstOrNull()
            val nextText = next?.let { (if (it.allDay) "" else Fmt.time(Instant.ofEpochMilli(it.begin).atZone(vm.zone).toLocalTime()) + " ") + it.title } ?: "сегодня свободно"
            when (vm.tileVariant(t)) {
                "week" -> {
                    val wk = (0..6).sumOf { byDay[today.plusDays(it.toLong())].orEmpty().size }
                    TileInfo("$wk", "за 7 дней", nextText)
                }
                "next" -> TileInfo(next?.let { if (it.allDay) "весь день" else Fmt.time(Instant.ofEpochMilli(it.begin).atZone(vm.zone).toLocalTime()) } ?: "—", "дальше", next?.title ?: "сегодня свободно")
                else -> TileInfo("${left.size}", Fmt.plural(left.size.toLong(), "событие", "события", "событий"), nextText)
            }
        }
        Tab.TASKS -> {
            val mine = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
            val overdue = mine.count { it.dueDate?.isBefore(today) == true }
            val todayN = mine.count { it.dueDate == today }
            when (vm.tileVariant(t)) {
                "today" -> TileInfo("$todayN", "на сегодня", if (overdue > 0) "просрочено: $overdue" else "открыто ${mine.size}", alert = overdue > 0)
                "overdue" -> TileInfo("$overdue", "просрочено", "открыто ${mine.size}", alert = overdue > 0)
                else -> TileInfo("${mine.size}", "открыто", if (overdue > 0) "просрочено: $overdue" else "без просрочек", alert = overdue > 0)
            }
        }
        Tab.FINANCE -> {
            val spent = w.transactions.filter { it.type == app.calstats.work.TxType.EXPENSE && it.localDate == today }.sumOf { it.amount }
            val month = app.calstats.work.FinanceMath.expense(app.calstats.work.FinanceMath.inMonth(w.transactions, java.time.YearMonth.from(today)))
            val debts = app.calstats.work.DebtMath.summary(w, today)
            val debtLine = if (debts.owedToMe != 0L || debts.iOwe != 0L) "долги ${SplitMath.money(debts.net, sign = true)}" else "за месяц ${SplitMath.money(month)}"
            when (vm.tileVariant(t)) {
                "month" -> TileInfo(SplitMath.money(month), "за месяц", "сегодня ${SplitMath.money(spent)}")
                "balance" -> TileInfo(SplitMath.money(app.calstats.work.FinanceMath.total(w)), "на счетах", debtLine)
                "savings" -> TileInfo(SplitMath.money(app.calstats.work.DebtMath.savings(w)), "накоплено", debtLine)
                "debts" -> TileInfo(
                    SplitMath.money(kotlin.math.abs(debts.net)), if (debts.net >= 0) "вам должны (итог)" else "вы должны (итог)",
                    "мне ${SplitMath.money(debts.owedToMe)} · я ${SplitMath.money(debts.iOwe)}", alert = debts.overdue > 0,
                )
                "owed" -> TileInfo(SplitMath.money(debts.owedToMe), "мне должны", if (debts.overdue > 0) "просрочено: ${debts.overdue}" else "я должен ${SplitMath.money(debts.iOwe)}", alert = debts.overdue > 0)
                "iowe" -> TileInfo(SplitMath.money(debts.iOwe), "я должен", "мне должны ${SplitMath.money(debts.owedToMe)}", alert = debts.overdue > 0)
                else -> TileInfo(SplitMath.money(spent), "сегодня", debtLine)
            }
        }
        Tab.HABITS -> {
            val list = w.habits.filter { !it.archived && it.kind == app.calstats.work.HabitKind.DO && it.scheduled(today) }
            val done = list.count { app.calstats.work.HabitMath.done(it, app.calstats.work.HabitMath.values(w.habitLogs, it.id), today) }
            val sum = remember(vm.drinks, today) { Sobriety.summary(vm.drinks, today) }
            TileInfo(
                "$done/${list.size}", "сегодня",
                if (vm.alcoholHabitShown && (sum.streak ?: 0) > 0) "🌱 " + Sobriety.streakText(sum) else if (list.isEmpty()) "добавьте привычку" else "выполнено",
                if (list.isEmpty()) null else done.toFloat() / list.size,
            )
        }
        Tab.HEALTH -> {
            val norms = w.healthProfile?.let { app.calstats.work.HealthMath.norms(it, today) }
            val kcal = w.foodLog.filter { it.date == today.toString() }.sumOf { it.kcal }.toInt()
            val steps = vm.health?.todaySteps?.takeIf { vm.healthGranted > 0 }
            val water = app.calstats.work.HealthOps.water(w, today)
            when (vm.tileVariant(t)) {
                "water" -> TileInfo("$water", "мл воды", "🍽 $kcal ккал", norms?.let { water.toFloat() / it.waterMl })
                "steps" -> TileInfo(steps?.toString() ?: "—", "шагов", if (steps == null) "подключите Samsung Health" else "🍽 $kcal ккал",
                    steps?.let { s -> s.toFloat() / (w.healthProfile?.stepsGoal ?: 8000) })
                "sleep" -> {
                    val sl = vm.health?.lastSleep?.takeIf { vm.healthGranted > 0 && it > 0 }
                    TileInfo(sl?.let { Fmt.hours(it) } ?: "—", "ч сна", "🚶 ${steps ?: "—"}")
                }
                else -> TileInfo(
                    "$kcal", "ккал" + (norms?.let { " из ${it.kcal}" } ?: ""),
                    listOfNotNull("💧 $water мл", steps?.let { "🚶 $it" }).joinToString(" · "),
                    norms?.let { kcal.toFloat() / it.kcal },
                )
            }
        }
        Tab.DOCS -> {
            val att = app.calstats.work.DocsMath.attention(w.documents, today)
            val subs = app.calstats.work.DocsMath.subscriptions(w).filter { it.active && it.type == app.calstats.work.TxType.EXPENSE }
            if (att.isNotEmpty()) TileInfo("${att.size}", "внимание", att.first().let { "${it.kind.emoji} ${it.name}" }, alert = true)
            else TileInfo("${w.documents.count { !it.archived }}", "документов", if (subs.isEmpty()) "всё в порядке" else "подписки ${SplitMath.money(subs.sumOf { it.monthly })}/мес")
        }
        Tab.MONEY -> {
            val net = remember(w.expenses, w.books) { w.books.sumOf { b -> SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L } }
            TileInfo(SplitMath.money(kotlin.math.abs(net)), when { net > 0 -> "вам должны"; net < 0 -> "вы должны"; else -> "в расчёте" }, "${w.books.count { !it.archived }} счетов")
        }
        Tab.ANALYSIS -> {
            val top = remember(vm.instances, vm.data, vm.calendars, vm.now) {
                val p = Period(PeriodType.MONTH, vm.today, vm.period.customStart, vm.period.customEndInclusive)
                Stats.groupStats(vm.groups(), vm.periodWindow(p), vm.now, vm.today).filter { it.agg.events > 0 }.maxByOrNull { it.agg.minutes }
            }
            TileInfo(top?.let { Fmt.hours(it.agg.minutes) } ?: "0", "ч", top?.group?.name?.let { "больше всего: $it" } ?: Fmt.monthName(today.monthValue))
        }
        Tab.WISHES -> {
            val next = w.wishLists.filter { !it.archived }.mapNotNull { l -> nextOccurrence(l.localDate, today, l.yearly)?.let { l to it } }.minByOrNull { it.second }
            TileInfo(
                "${w.wishes.count { it.status == WishStatus.WANTED }}", "желаний",
                next?.let { (l, d) -> "${l.emoji} " + if (d == today) "сегодня" else "через ${Fmt.days(ChronoUnit.DAYS.between(today, d))}" } ?: "поводов нет",
            )
        }
        Tab.NOTES -> {
            val last = w.notes.filter { !it.archived && !it.isEmpty }.maxByOrNull { it.updated }
            TileInfo("${w.notes.count { !it.archived }}", "заметок", last?.let { it.title.ifBlank { it.asText().lineSequence().firstOrNull().orEmpty() } } ?: "пока пусто")
        }
        Tab.ACCOUNTS -> {
            val n = listOf(vm.healthGranted > 0, vm.calendars.any { it.accountType == "com.google" }, vm.telegramState.connected).count { it }
            TileInfo("$n", "подключено", "Samsung Health, Google, Telegram")
        }
        Tab.GUIDE -> TileInfo("${GUIDES.size}", "инструкций", "Как пользоваться модулями")
        Tab.SHOPPING -> {
            val left = w.shopItems.filter { !it.done }
            TileInfo("${left.size}", "купить", left.take(3).joinToString(", ") { it.name }.ifBlank { "список пуст" })
        }
        Tab.CARDS -> {
            val top = w.cards.sortedByDescending { it.uses }.take(3)
            TileInfo("${w.cards.size}", "карт", top.joinToString(", ") { it.store }.ifBlank { "добавьте первую карту" })
        }
        Tab.GOALS -> {
            val g = w.lifeGoals.filter { it.year == today.year }
            TileInfo(if (g.isEmpty()) "—" else "${(g.map { it.progress }.average() * 100).toInt()}%", "прогресс года", "${g.count { it.done }} из ${g.size} целей", if (g.isEmpty()) null else g.map { it.progress }.average().toFloat())
        }
        Tab.JOURNAL -> {
            val s = app.calstats.work.Journal.streak(w.journal, today)
            val wrote = w.journal.any { it.date == today.toString() && it.text.isNotBlank() }
            TileInfo("$s", "дней подряд", if (wrote) "сегодня записано ✓" else "запишите день")
        }
        Tab.CAR -> {
            val c = w.cars.firstOrNull()
            TileInfo(c?.let { SplitMath.money(app.calstats.work.CarMath.monthCost(w.carEvents, it.id, java.time.YearMonth.from(today))) } ?: "—", "за месяц",
                c?.let { app.calstats.work.CarMath.nextService(it, w.carEvents, today)?.kmLeft?.let { k -> "до ТО $k км" } ?: it.name } ?: "добавьте машину")
        }
        Tab.HOME_UTIL -> {
            val due = w.meters.count { m -> w.meterReadings.none { it.meterId == m.id && java.time.YearMonth.from(java.time.LocalDate.parse(it.date)) == java.time.YearMonth.from(today) } }
            TileInfo("$due", "не передано", if (w.meters.isEmpty()) "добавьте счётчики" else "показания за месяц", alert = due > 0 && today.dayOfMonth >= 15)
        }
        Tab.PETS -> {
            val up = app.calstats.work.Pets.upcoming(w.petEvents, today, 14)
            TileInfo("${w.pets.size}", "питомцев", up.firstOrNull()?.let { "${it.type.emoji} ${it.type.title.lowercase()} скоро" } ?: "всё по графику", alert = up.isNotEmpty())
        }
        Tab.MEALS -> {
            val today0 = w.mealPlans.filter { it.date == today.toString() }
            val next = today0.sortedBy { it.slot.ordinal }.firstOrNull()
            TileInfo("${today0.size}", "блюд сегодня", next?.let { p -> "${p.slot.emoji} " + (w.recipes.firstOrNull { it.id == p.recipeId }?.title ?: p.text) } ?: "спланируйте меню")
        }
        Tab.WORKOUTS -> {
            val n = app.calstats.work.Training.weekCount(w, today)
            val last = w.workouts.maxByOrNull { it.date }
            TileInfo("$n", "за неделю", last?.let { "последняя: ${Fmt.dayTitle(java.time.LocalDate.parse(it.date), today).lowercase()}" } ?: "запишите тренировку")
        }
        Tab.TRIPS -> {
            val t0 = w.trips.filter { !it.endDate.isBefore(today) }.minByOrNull { it.start }
            if (t0 == null) TileInfo("—", "", "поездок не запланировано")
            else {
                val d = app.calstats.work.Trips.daysLeft(t0, today)
                TileInfo(if (d > 0) "$d" else "✈", if (d > 0) Fmt.plural(d, "день", "дня", "дней") else "в пути", t0.title,
                    progress = if (t0.packing.isEmpty()) null else t0.packing.count { it.done }.toFloat() / t0.packing.size)
            }
        }
        Tab.READING -> {
            val done = app.calstats.work.Reading.doneThisYear(w, today.year)
            val cur = w.books.firstOrNull { it.status == app.calstats.work.BookStatus.ACTIVE }
            TileInfo("$done/${w.readingGoal}", "за год", cur?.let { "📖 ${it.title}" } ?: "добавьте книгу", progress = done.toFloat() / maxOf(1, w.readingGoal))
        }
        Tab.CHORES -> {
            val due = w.chores.count { it.daysLeft(today) <= 0 }
            TileInfo("$due", "на сегодня", w.chores.minByOrNull { it.daysLeft(today) }?.title ?: "добавьте дела", alert = w.chores.any { it.daysLeft(today) < -2 })
        }
        Tab.PLANTS -> {
            val due = w.plants.count { it.waterIn(today) <= 0 }
            TileInfo("$due", "полить", if (w.plants.isEmpty()) "добавьте растения" else if (due == 0) "все политы ✓" else w.plants.filter { it.waterIn(today) <= 0 }.joinToString { it.name }, alert = due > 0)
        }
        Tab.HOME -> TileInfo("", "", "")
    }
}

/** Средняя плитка: заголовок, крупное число с подписью, строка и тонкий прогресс. */
@Composable
private fun MediumTile(vm: MainViewModel, t: Tab, modifier: Modifier) {
    val info = tileInfo(vm, t)
    FlatCard(vm, t, modifier, onClick = { vm.tab = t }) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            CardTitle(t)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    info.value,
                    style = MaterialTheme.typography.displaySmall,
                    color = if (info.alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Text(info.unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.weight(1f))
            info.progress?.let { f ->
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(moduleColor(t).copy(alpha = 0.16f))) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(moduleColor(t)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(info.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (vm.editHome) TileControls(vm, t)
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CalendarSummary(vm: MainViewModel) {
    val today = vm.today
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    val now = System.currentTimeMillis()
    val todays = byDay[today].orEmpty().filter { it.allDay || it.end > now }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    val tomorrow = byDay[today.plusDays(1)].orEmpty().size
    val summary = remember(vm.drinks, vm.today) { Sobriety.summary(vm.drinks, vm.today) }
    SummaryCard(Tab.CALENDAR, vm, subtitle = if (todays.isEmpty()) null else "${todays.size} сегодня", onClick = {
        vm.selectedDate = today
        vm.tab = Tab.CALENDAR
    }) {
        if (todays.isEmpty()) Muted("На сегодня больше ничего нет" + if (tomorrow > 0) " · завтра событий: $tomorrow" else "")
        todays.take(3).forEach { e ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 4.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (e.allDay) "весь день" else Fmt.time(Instant.ofEpochMilli(e.begin).atZone(vm.zone).toLocalTime()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp),
                )
                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (todays.size > 3) Muted("и ещё ${todays.size - 3}")
        summary.streak?.let { s ->
            Spacer(Modifier.height(8.dp))
            Text(
                Sobriety.streakText(summary) + if (summary.longest > 0) " · рекорд ${summary.longest}" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (s > 0) SOBER_COLOR else DRANK_COLOR,
            )
        }
    }
}

@Composable
private fun TasksSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val mine = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
    val overdue = mine.count { it.dueDate?.isBefore(today) == true }
    val inWork = mine.count { it.status == Status.IN_PROGRESS }
    val focus = mine.sortedWith(compareBy({ it.dueDate ?: java.time.LocalDate.MAX }, { it.priority.ordinal })).take(3)
    SummaryCard(Tab.TASKS, vm, subtitle = "${mine.size} открыто", onClick = {
        vm.selectTaskView("ALL")
        vm.tab = Tab.TASKS
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniNumber(mine.size.toString(), "мои открытые")
            MiniNumber(inWork.toString(), "в работе")
            MiniNumber(overdue.toString(), "просрочено", if (overdue > 0) MaterialTheme.colorScheme.error else null)
        }
        if (focus.isNotEmpty()) Spacer(Modifier.height(8.dp))
        focus.forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskCheck(i.done, null) { vm.toggleIssueDone(i) }
                Spacer(Modifier.width(10.dp))
                Text(
                    i.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (i.done) TextDecoration.LineThrough else null,
                )
                i.dueDate?.let {
                    Text(
                        Fmt.dueShort(it, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (mine.isEmpty()) Muted("Открытых задач нет")
    }
}

@Composable
private fun MiniNumber(value: String, label: String, color: Color? = null) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoneySummary(vm: MainViewModel) {
    val w = vm.work
    val nets = remember(w.expenses, w.books) { w.books.map { b -> SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L } }
    val owedToMe = nets.filter { it > 0 }.sum()
    val iOwe = -nets.filter { it < 0 }.sum()
    SummaryCard(Tab.MONEY, vm, subtitle = "${w.books.count { !it.archived }} счетов") {
        if (owedToMe == 0L && iOwe == 0L) {
            Muted(if (w.books.isEmpty()) "Создайте счёт для общих трат" else "Все в расчёте")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniNumber(SplitMath.money(owedToMe), "вам должны", if (owedToMe > 0) Color(0xFF22A06B) else null)
                MiniNumber(SplitMath.money(iOwe), "вы должны", if (iOwe > 0) Color(0xFFD64545) else null)
            }
        }
    }
}

@Composable
private fun AnalysisSummary(vm: MainViewModel) {
    val top = remember(vm.instances, vm.data, vm.calendars, vm.now) {
        val p = Period(PeriodType.MONTH, vm.today, vm.period.customStart, vm.period.customEndInclusive)
        Stats.groupStats(vm.groups(), vm.periodWindow(p), vm.now, vm.today)
            .filter { it.agg.events > 0 }
            .sortedByDescending { it.agg.minutes }
            .take(4)
    }
    val max = top.maxOfOrNull { it.agg.minutes }?.coerceAtLeast(1) ?: 1
    SummaryCard(Tab.ANALYSIS, vm, subtitle = Fmt.monthName(vm.today.monthValue)) {
        if (top.isEmpty()) Muted("В этом месяце пока нет событий по направлениям")
        top.forEach { g ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.group.name, Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxWidth(g.agg.minutes.toFloat() / max)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(g.group.color)),
                    )
                }
                Text("${Fmt.hours(g.agg.minutes)} ч", Modifier.width(56.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WishesSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val upcoming = w.wishLists.filter { !it.archived }
        .mapNotNull { l -> nextOccurrence(l.localDate, today, l.yearly)?.let { l to it } }
        .sortedBy { it.second }
        .take(2)
    val wanted = w.wishes.count { it.status == WishStatus.WANTED }
    SummaryCard(Tab.WISHES, vm, subtitle = "$wanted желаний") {
        if (upcoming.isEmpty()) Muted(if (w.wishLists.isEmpty()) "Соберите свой список желаний или подарков близким" else "Ближайших поводов нет")
        upcoming.forEach { (l, d) ->
            val days = ChronoUnit.DAYS.between(today, d)
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(l.occasion, Fmt.dateNoYear(d)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (days == 0L) "сегодня" else "через ${Fmt.days(days)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (days <= 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotesSummary(vm: MainViewModel) {
    val notes = vm.work.notes.filter { !it.archived && !it.isEmpty }
        .sortedWith(compareByDescending<app.calstats.work.Note> { it.pinned }.thenByDescending { it.updated })
        .take(3)
    SummaryCard(Tab.NOTES, vm, subtitle = "${vm.work.notes.count { !it.archived }} заметок") {
        if (notes.isEmpty()) Muted("Заметок пока нет")
        notes.forEach { n ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                    vm.tab = Tab.NOTES
                    vm.push(Screen.NoteEdit(n.id))
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(n.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(10.dp))
                Text(
                    n.title.ifBlank { n.asText().lineSequence().firstOrNull().orEmpty() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Ближайшая дата повода: для ежегодных (день рождения) — в этом или следующем году; прошедшие разовые — null. */
fun nextOccurrence(d: java.time.LocalDate?, today: java.time.LocalDate, yearly: Boolean): java.time.LocalDate? {
    if (d == null) return null
    if (!d.isBefore(today)) return d
    if (!yearly) return null
    var n = d.withYear(today.year)
    if (n.isBefore(today)) n = n.plusYears(1)
    return n
}

/** Быстрые действия «Мой день»: записать трату, задачу, событие, заметку. */
@Composable
private fun QuickActions(vm: MainViewModel, onTx: (app.calstats.work.Transaction) -> Unit) {
    data class A(val t: Tab, val label: String, val run: () -> Unit)
    val actions = buildList {
        if (vm.isEnabled(Tab.FINANCE)) add(A(Tab.FINANCE, "Трата") {
            val acc = vm.work.accounts.firstOrNull { !it.archived }
            if (acc == null) {
                vm.tab = Tab.FINANCE
                vm.message = "Сначала добавьте счёт"
            } else {
                if (vm.work.finCategories.isEmpty()) vm.updateWork { app.calstats.work.FinanceOps.ensureDefaults(it) }
                onTx(app.calstats.work.Transaction(app.calstats.work.newId(), app.calstats.work.TxType.EXPENSE, 0, acc.id, date = vm.today.toString()))
            }
        })
        if (vm.isEnabled(Tab.CALENDAR)) add(A(Tab.CALENDAR, "Событие") { vm.quickEvent = true })
        if (vm.isEnabled(Tab.TASKS)) add(A(Tab.TASKS, "Задача") {
            vm.selectTaskView("ALL")
            vm.tab = Tab.TASKS
            vm.message = "Нажмите «Создать»"
        })
        if (vm.isEnabled(Tab.NOTES)) add(A(Tab.NOTES, "Заметка") {
            val n = app.calstats.work.Note(app.calstats.work.newId())
            vm.updateWork { app.calstats.work.WorkOps.upsertNote(it, n) }
            vm.tab = Tab.NOTES
            vm.push(Screen.NoteEdit(n.id))
        })
    }
    // Строка быстрой записи: голосом или текстом — всё разберётся само
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { vm.quickInput = false }.padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Записать: трату, задачу, воду…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.primary).clickable { vm.quickInput = true },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(app.calstats.R.drawable.ic_mic_tile), "Голосом", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        if (java.time.LocalTime.now().hour >= 18) {
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { vm.checkIn = true }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("🌙 Итог дня", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
    if (actions.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { a ->
            Card(
                onClick = a.run,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(moduleColor(a.t).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        ModuleIcon(a.t, true, 18.dp, moduleColor(a.t))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("+ " + a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FinanceSummary(vm: MainViewModel) {
    val w = vm.work
    val month = java.time.YearMonth.from(vm.today)
    val txs = app.calstats.work.FinanceMath.inMonth(w.transactions, month)
    val spentToday = w.transactions.filter { it.type == app.calstats.work.TxType.EXPENSE && it.localDate == vm.today }.sumOf { it.amount }
    val spentMonth = app.calstats.work.FinanceMath.expense(txs)
    val limit = w.budgets.sumOf { it.monthlyLimit }
    val upcoming = w.recurrings.filter { it.active && !it.next.isAfter(vm.today.plusDays(3)) }.sortedBy { it.next }
    val debts = app.calstats.work.DebtMath.summary(w, vm.today)
    val sec = vm.finSections
    SummaryCard(Tab.FINANCE, vm, subtitle = if (w.accounts.isEmpty()) null else SplitMath.money(app.calstats.work.FinanceMath.total(w))) {
        if (w.accounts.isEmpty() && w.debts.isEmpty()) {
            Muted("Добавьте счета и записывайте траты — здесь появится сводка")
        } else {
            if ("savings" in sec) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    MiniNumber(SplitMath.money(app.calstats.work.DebtMath.savings(w)), "накоплено")
                    MiniNumber(SplitMath.money(app.calstats.work.DebtMath.savings(w) + debts.net), "капитал с долгами")
                }
            }
            if ("spent" in sec || "budget" in sec) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if ("spent" in sec) MiniNumber(SplitMath.money(spentToday), "сегодня")
                if ("spent" in sec) MiniNumber(SplitMath.money(spentMonth), "за месяц")
                if ("budget" in sec && limit > 0) MiniNumber(
                    SplitMath.money((limit - w.budgets.sumOf { b -> txs.filter { it.type == app.calstats.work.TxType.EXPENSE && it.categoryId == b.categoryId }.sumOf { it.amount } }).coerceAtLeast(0)),
                    "осталось в бюджете",
                )
            }
            if ("debts" in sec && (debts.owedToMe != 0L || debts.iOwe != 0L)) {
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MiniNumber(SplitMath.money(debts.owedToMe), "мне должны")
                    MiniNumber(SplitMath.money(debts.iOwe), "я должен")
                    MiniNumber(SplitMath.money(debts.net, sign = true), "итог")
                }
                val near = w.debts.filter { !it.closed && it.dueDate != null }.sortedBy { it.dueDate }.firstOrNull()
                near?.let { d ->
                    val st = app.calstats.work.DebtMath.status(d, vm.today)
                    val who = w.counterparties.firstOrNull { it.id == d.partyId }?.name.orEmpty()
                    val days = ChronoUnit.DAYS.between(vm.today, d.dueDate)
                    Text(
                        "💸 " + (if (d.dir == app.calstats.work.DebtDir.I_OWE) "Вернуть $who" else "$who вернёт") + " ${SplitMath.money(st.totalDue)} — " +
                            when { days < 0 -> "просрочено на ${-days} дн."; days == 0L -> "сегодня"; else -> "через ${Fmt.days(days)}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if ("payments" in sec) upcoming.take(2).forEach { r ->
                val d = ChronoUnit.DAYS.between(vm.today, r.next)
                Text(
                    "🔔 ${r.name} — ${SplitMath.money(r.amount)}, " + when {
                        d < 0 -> "просрочен"
                        d == 0L -> "сегодня"
                        d == 1L -> "завтра"
                        else -> "через ${Fmt.days(d)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (d <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
