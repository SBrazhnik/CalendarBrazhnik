@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Sobriety
import app.calstats.Tab
import app.calstats.work.HABIT_TEMPLATES
import app.calstats.work.Habit
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.HabitOps
import app.calstats.work.PALETTE
import app.calstats.work.newId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private const val ALCOHOL = "alcohol"
private val HABIT_EMOJIS = listOf("✅", "💧", "🤸", "📖", "🚶", "😴", "🧘", "🍎", "🥗", "💊", "🦷", "🧠", "✍", "🎸", "🇬🇧", "💰", "🍬", "🚭", "📵", "☕")

/** Модуль «Привычки». */
@Composable
fun HabitsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    val today = vm.today
    var edit by remember { mutableStateOf<Habit?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    val habits = w.habits.filter { !it.archived }
    val doToday = habits.filter { it.kind == HabitKind.DO && it.scheduled(today) }
    val doneToday = doToday.count { HabitMath.done(it, HabitMath.values(w.habitLogs, it.id), today) }
    val weekStart = today.with(DayOfWeek.MONDAY)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Привычки", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { edit = Habit(newId(), "", color = PALETTE[w.habits.size % PALETTE.size], created = today.toString()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Привычка") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ring(if (doToday.isEmpty()) 0f else doneToday.toFloat() / doToday.size, MaterialTheme.colorScheme.primary, 72)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Сегодня", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (doToday.isEmpty()) "Нет привычек на сегодня" else "$doneToday из ${doToday.size} выполнено",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(Fmt.dayTitle(today, today), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // Шапка недели
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    (0..6).forEach { k ->
                        val d = weekStart.plusDays(k.toLong())
                        Text(
                            Fmt.weekdayShort(k) + "\n" + d.dayOfMonth,
                            Modifier.width(30.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (vm.alcoholHabitShown) item(key = ALCOHOL) { AlcoholCard(vm, weekStart) { detail = ALCOHOL } }
            items(habits, key = { it.id }) { h ->
                if (h.kind == HabitKind.DO) DoCard(vm, h, weekStart) { detail = h.id } else QuitCard(vm, h, weekStart) { detail = h.id }
            }
            if (habits.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Начните с готовой привычки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HABIT_TEMPLATES.forEach { t ->
                                FilterChip(selected = false, onClick = {
                                    vm.updateWork { HabitOps.upsert(it, t.copy(id = newId(), created = today.toString())) }
                                }, label = { Text("${t.emoji} ${t.name}") })
                            }
                        }
                    }
                }
            }
            val archived = w.habits.filter { it.archived }
            if (archived.isNotEmpty()) {
                item { Text("Архив", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(archived, key = { "a" + it.id }) { h ->
                    Card(onClick = { edit = h }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text("${h.emoji} ${h.name}", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!vm.alcoholHabitShown) {
                item { TextButton(onClick = { vm.showAlcoholHabit(true) }) { Text("Показать «Без алкоголя»") } }
            }
        }
    }

    edit?.let { h -> HabitDialog(vm, h) { edit = null } }
    detail?.let { id ->
        HabitDetailSheet(vm, id, onEdit = { h ->
            detail = null
            edit = h
        }) { detail = null }
    }
}

@Composable
private fun Ring(f: Float, color: Color, size: Int) {
    val track = color.copy(alpha = 0.25f)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val s = 9.dp.toPx()
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(color, -90f, 360f * f.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HabitTitle(emoji: String, name: String, sub: String, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Кружок дня: заливка по прогрессу. state: null — вне графика / будущее. */
@Composable
private fun DayDot(fill: Float?, color: Color, isToday: Boolean, bad: Boolean = false, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(30.dp)
            .padding(3.dp)
            .clip(CircleShape)
            .background(
                when {
                    fill == null -> Color.Transparent
                    bad -> DRANK_COLOR
                    fill >= 1f -> color
                    fill > 0f -> color.copy(alpha = 0.25f + 0.5f * fill)
                    else -> scheme.surfaceVariant
                },
            )
            .then(if (isToday) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (fill != null && fill >= 1f && !bad) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
    }
}

@Composable
private fun DoCard(vm: MainViewModel, h: Habit, weekStart: LocalDate, onOpen: () -> Unit) {
    val v = HabitMath.values(vm.work.habitLogs, h.id)
    val streak = HabitMath.streak(h, v, vm.today)
    val color = Color(h.color)
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val todayV = v[vm.today] ?: 0
                HabitTitle(
                    h.emoji, h.name,
                    listOfNotNull(
                        if (streak > 0) "🔥 $streak" else null,
                        if (h.target > 1) "сегодня $todayV/${h.target} ${h.unit}".trim() else h.unit.takeIf { it.isNotBlank() },
                        if (h.days.size < 7) h.days.sorted().joinToString(",") { Fmt.weekdayShort(it - 1).lowercase() } else null,
                    ).joinToString(" · ").ifEmpty { "каждый день" },
                    color,
                    Modifier.weight(1f),
                )
                (0..6).forEach { k ->
                    val d = weekStart.plusDays(k.toLong())
                    val future = d.isAfter(vm.today)
                    val fill = if (future || !h.scheduled(d) || d.isBefore(h.createdDate.minusDays(60))) null
                    else ((v[d] ?: 0).toFloat() / h.target)
                    DayDot(fill, color, d == vm.today, onClick = if (future) null else ({ vm.updateWork { HabitOps.tap(it, h, d) } }))
                }
            }
        }
    }
}

@Composable
private fun QuitCard(vm: MainViewModel, h: Habit, weekStart: LocalDate, onOpen: () -> Unit) {
    val v = HabitMath.values(vm.work.habitLogs, h.id)
    val sum = Sobriety.summary(HabitMath.quitMap(v), vm.today)
    QuitLayout(
        vm, h.emoji, h.name, Color(h.color), sum, weekStart,
        mark = { d -> v[d]?.let { it > 0 } },
        set = { d, slip -> vm.updateWork { HabitOps.set(it, h.id, d, slip?.let { s -> if (s) 1 else 0 }) } },
        onOpen = onOpen,
    )
}

/** «Без алкоголя» — те же отметки, что «Пил / Не пил» в календаре. */
@Composable
private fun AlcoholCard(vm: MainViewModel, weekStart: LocalDate, onOpen: () -> Unit) {
    val sum = remember(vm.drinks, vm.today) { Sobriety.summary(vm.drinks, vm.today) }
    QuitLayout(
        vm, "🍷", "Без алкоголя", SOBER_COLOR, sum, weekStart,
        mark = { d -> vm.drinks[d] },
        set = { d, drank -> vm.setDrink(d, drank) },
        onOpen = onOpen,
    )
}

@Composable
private fun QuitLayout(
    vm: MainViewModel,
    emoji: String,
    name: String,
    color: Color,
    sum: Sobriety.Summary,
    weekStart: LocalDate,
    mark: (LocalDate) -> Boolean?,
    set: (LocalDate, Boolean?) -> Unit,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitTitle(
                    emoji, name,
                    when (val s = sum.streak) {
                        null -> "отметок пока нет"
                        0 -> "сегодня — срыв"
                        else -> "🌱 ${Fmt.days(s.toLong())} подряд" + if (sum.longest > s) " · рекорд ${sum.longest}" else ""
                    },
                    color,
                    Modifier.weight(1f),
                )
                (0..6).forEach { k ->
                    val d = weekStart.plusDays(k.toLong())
                    val m = mark(d)
                    val future = d.isAfter(vm.today)
                    // нажатие по кругу: нет отметки → удержался → сорвался → нет отметки
                    DayDot(
                        if (future) null else when (m) { true -> 1f; false -> 1f; null -> 0f },
                        color, d == vm.today, bad = m == true,
                        onClick = if (future) null else ({ set(d, when (m) { null -> false; false -> true; true -> null }) }),
                    )
                }
            }
            if (mark(vm.today) == null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { set(vm.today, false) }) { Text("Сегодня удержался") }
                    TextButton(onClick = { set(vm.today, true) }) { Text("Сорвался", color = DRANK_COLOR) }
                }
            }
        }
    }
}

// ======================= ПОДРОБНО =======================

@Composable
private fun HabitDetailSheet(vm: MainViewModel, id: String, onEdit: (Habit) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val w = vm.work
    val h = w.habits.firstOrNull { it.id == id }
    var month by remember { mutableStateOf(YearMonth.from(vm.today)) }
    val isAlcohol = id == ALCOHOL
    val color = if (isAlcohol) SOBER_COLOR else Color(h?.color ?: 0)
    val v = if (h != null) HabitMath.values(w.habitLogs, h.id) else emptyMap()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isAlcohol) "🍷 Без алкоголя" else "${h?.emoji} ${h?.name}", style = MaterialTheme.typography.headlineSmall)
            // Сводка
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (isAlcohol || h?.kind == HabitKind.QUIT) {
                    val sum = Sobriety.summary(if (isAlcohol) vm.drinks else HabitMath.quitMap(v), vm.today)
                    StatBox((sum.streak ?: 0).toString(), "дней подряд")
                    StatBox(sum.longest.toString(), "рекорд")
                    val map = if (isAlcohol) vm.drinks else HabitMath.quitMap(v)
                    val m = map.filterKeys { YearMonth.from(it) == month }
                    StatBox(m.count { !it.value }.toString(), "чистых в месяце")
                    StatBox(m.count { it.value }.toString(), "срывов")
                } else if (h != null) {
                    StatBox(HabitMath.streak(h, v, vm.today).toString(), "серия")
                    StatBox(HabitMath.best(h, v, vm.today).toString(), "рекорд")
                    StatBox("${HabitMath.rate(h, v, vm.today)}%", "за 30 дней")
                    StatBox(v.count { it.value >= h.target }.toString(), "всего дней")
                }
            }
            // Календарь месяца
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") }
                Text("${Fmt.monthName(month.monthValue)} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") }
            }
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { k -> Text(Fmt.weekdayShort(k), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val start = monthGridStart(month)
            for (week in 0 until monthWeeks(month)) {
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { k ->
                        val d = start.plusDays(week * 7L + k)
                        val inMonth = YearMonth.from(d) == month
                        val future = d.isAfter(vm.today)
                        val bg: Color
                        val fg: Color
                        if (isAlcohol || h?.kind == HabitKind.QUIT) {
                            val m = if (isAlcohol) vm.drinks[d] else v[d]?.let { it > 0 }
                            bg = when (m) { true -> DRANK_COLOR; false -> color; null -> Color.Transparent }
                            fg = if (m != null) Color.White else MaterialTheme.colorScheme.onSurface
                        } else {
                            val f = if (h == null) 0f else (v[d] ?: 0).toFloat() / h.target
                            bg = when {
                                f >= 1f -> color
                                f > 0f -> color.copy(alpha = 0.4f)
                                else -> Color.Transparent
                            }
                            fg = if (f >= 1f) Color.White else MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .then(if (d == vm.today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable(enabled = inMonth && !future) {
                                    if (isAlcohol) vm.setDrink(d, when (vm.drinks[d]) { null -> false; false -> true; true -> null })
                                    else if (h != null) {
                                        if (h.kind == HabitKind.DO) vm.updateWork { HabitOps.tap(it, h, d) }
                                        else vm.updateWork { HabitOps.set(it, h.id, d, when (v[d]) { null -> 0; 0 -> 1; else -> null }) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                color = if (!inMonth || future) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else fg,
                            )
                        }
                    }
                }
            }
            Text(
                if (isAlcohol || h?.kind == HabitKind.QUIT) "Нажмите на день: удержался → сорвался → без отметки."
                else "Нажмите на день, чтобы отметить${if ((h?.target ?: 1) > 1) " (каждое нажатие +1)" else ""}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAlcohol) {
                    OutlinedButton(onClick = { vm.tab = Tab.CALENDAR; onDismiss() }) { Text("Открыть календарь") }
                    TextButton(onClick = { vm.showAlcoholHabit(false); onDismiss() }) { Text("Скрыть из привычек") }
                } else if (h != null) {
                    Button(onClick = { onEdit(h) }) { Text("Изменить") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= СОЗДАНИЕ И ИЗМЕНЕНИЕ =======================

@Composable
private fun HabitDialog(vm: MainViewModel, h: Habit, onDismiss: () -> Unit) {
    val exists = vm.work.habits.any { it.id == h.id }
    var name by remember { mutableStateOf(h.name) }
    var emoji by remember { mutableStateOf(h.emoji) }
    var color by remember { mutableStateOf(h.color) }
    var kind by remember { mutableStateOf(h.kind) }
    var target by remember { mutableStateOf(h.target.toString()) }
    var unit by remember { mutableStateOf(h.unit) }
    var days by remember { mutableStateOf(h.days.toSet()) }
    var archived by remember { mutableStateOf(h.archived) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Привычка" else "Новая привычка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!exists) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HABIT_TEMPLATES.forEach { t ->
                            FilterChip(selected = name == t.name, onClick = {
                                name = t.name; emoji = t.emoji; color = t.color; kind = t.kind; target = t.target.toString(); unit = t.unit
                            }, label = { Text("${t.emoji} ${t.name}") })
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HabitKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.title) }) }
                }
                if (kind == HabitKind.DO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(target, { v -> target = v.filter { it.isDigit() }.take(3) }, label = { Text("Раз в день") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp))
                        OutlinedTextField(unit, { unit = it }, label = { Text("Единица") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Дни", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { d ->
                            FilterChip(selected = d in days, onClick = { days = if (d in days && days.size > 1) days - d else days + d }, label = { Text(Fmt.weekdayShort(d - 1)) })
                        }
                    }
                }
                Text("Значок и цвет", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HABIT_EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                ColorPicker(color) { color = it }
                if (exists) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(archived, { archived = it })
                        Text("В архиве")
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Удалить привычку", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                vm.updateWork {
                    HabitOps.upsert(it, h.copy(name = name.trim(), emoji = emoji, color = color, kind = kind,
                        target = target.toIntOrNull()?.coerceIn(1, 999) ?: 1, unit = unit.trim(),
                        days = if (kind == HabitKind.DO) days.sorted() else (1..7).toList(), archived = archived))
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${h.name}»?") },
            text = { Text("Вся история отметок удалится. Чтобы сохранить её, отправьте привычку в архив.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork { HabitOps.delete(it, h.id) }
                    confirmDelete = false
                    onDismiss()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

// ======================= НА ГЛАВНОЙ =======================

@Composable
fun HabitsSummary(vm: MainViewModel) {
    val w = vm.work
    val today = vm.today
    val list = w.habits.filter { !it.archived && it.kind == HabitKind.DO && it.scheduled(today) }
    val done = list.count { HabitMath.done(it, HabitMath.values(w.habitLogs, it.id), today) }
    Card(onClick = { vm.tab = Tab.HABITS }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(Tab.HABITS)
                Spacer(Modifier.width(8.dp))
                Text("Привычки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (list.isNotEmpty()) Text("$done из ${list.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Text("Добавьте привычки — воду, зарядку, чтение", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { h ->
                    val v = HabitMath.values(w.habitLogs, h.id)
                    val ok = HabitMath.done(h, v, today)
                    val c = Color(h.color)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (ok) c else c.copy(alpha = 0.15f))
                            .clickable { vm.updateWork { HabitOps.tap(it, h, today) } }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.emoji)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            h.name + if (h.target > 1) " ${v[today] ?: 0}/${h.target}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ok) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (vm.alcoholHabitShown) {
                val sum = remember(vm.drinks, today) { Sobriety.summary(vm.drinks, today) }
                sum.streak?.let { s ->
                    Text(
                        "🌱 " + Sobriety.streakText(sum),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (s > 0) SOBER_COLOR else DRANK_COLOR,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
