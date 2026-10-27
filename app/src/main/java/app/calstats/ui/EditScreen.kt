@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.CalendarInfo
import app.calstats.EventColorOption
import app.calstats.EventDetails
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Recurrence
import app.calstats.ReminderOptions
import app.calstats.Screen
import app.calstats.Stats
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Создание и редактирование события — в духе редактора Google Календаря. */
@Composable
fun EditScreen(vm: MainViewModel, screen: Screen.Edit, snackbar: SnackbarHostState) {
    val isNew = screen.eventId == null
    val zone = vm.zone

    var loaded by remember { mutableStateOf(isNew) }
    var details by remember { mutableStateOf<EventDetails?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var calendar by remember { mutableStateOf<CalendarInfo?>(if (isNew) vm.defaultCalendar() else null) }
    var colorKey by remember { mutableStateOf<String?>(null) }
    var allDay by remember { mutableStateOf(screen.presetAllDay) }
    var recurrence by remember { mutableStateOf<Recurrence?>(null) }
    var reminders by remember { mutableStateOf(listOf(30)) }
    val initial = remember {
        screen.presetStart ?: run {
            val nextHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)
            val d = screen.presetDate
            if (d == null || d == nextHour.toLocalDate()) nextHour else d.atTime(9, 0)
        }
    }
    var start by remember { mutableStateOf(initial) }
    var end by remember { mutableStateOf(initial.plusHours(1)) }
    var presetApplied by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }
    var repeatDialog by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(screen.eventId) {
        val id = screen.eventId ?: return@LaunchedEffect
        val d = vm.loadEvent(id)
        if (d == null) {
            vm.message = "Событие не найдено"
            vm.pop()
            return@LaunchedEffect
        }
        details = d
        title = d.title
        description = d.description
        calendar = vm.calendarById(d.calendarId)
        colorKey = d.colorKey
        allDay = d.allDay
        recurrence = Recurrence.parse(d.rrule)
        reminders = d.reminders
        val b = screen.instanceBegin ?: d.dtStart
        val e = screen.instanceEnd ?: d.dtEnd ?: (b + (d.durationMs ?: 3_600_000L))
        if (d.allDay) {
            val sd = Stats.utcDate(b)
            var ed = Stats.utcDate(e).minusDays(1)
            if (ed.isBefore(sd)) ed = sd
            start = sd.atTime(9, 0)
            end = ed.atTime(10, 0)
        } else {
            start = Instant.ofEpochMilli(b).atZone(zone).toLocalDateTime()
            end = Instant.ofEpochMilli(e).atZone(zone).toLocalDateTime()
        }
        loaded = true
    }

    val colorOptions by produceState<List<EventColorOption>?>(null, calendar) {
        value = calendar?.let { vm.eventColors(it) } ?: emptyList()
    }

    LaunchedEffect(colorOptions) {
        val opts = colorOptions ?: return@LaunchedEffect
        if (isNew && !presetApplied && screen.presetColor != null) {
            colorKey = opts.firstOrNull { it.color == screen.presetColor }?.key
            presetApplied = true
        } else if (colorKey != null && opts.isNotEmpty() && opts.none { it.key == colorKey }) {
            colorKey = null
        }
    }

    val wasRecurring = details?.recurring == true

    fun save() {
        val cal = calendar
        if (cal == null) {
            vm.message = "Нет календаря, в который можно записать событие"
            return
        }
        var begin: Long
        var finish: Long
        if (allDay) {
            if (end.toLocalDate().isBefore(start.toLocalDate())) {
                vm.message = "Дата окончания раньше даты начала"
                return
            }
            begin = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            finish = end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            begin = start.atZone(zone).toInstant().toEpochMilli()
            finish = end.atZone(zone).toInstant().toEpochMilli()
            if (finish <= begin) {
                vm.message = "Окончание должно быть позже начала"
                return
            }
        }
        // Редактируем серию, открытую с одного повторения: сдвигаем всю серию на ту же разницу.
        val d = details
        val instBegin = screen.instanceBegin
        if (d != null && wasRecurring && instBegin != null && d.allDay == allDay) {
            val delta = begin - instBegin
            val len = finish - begin
            begin = d.dtStart + delta
            finish = begin + len
        }
        vm.saveEvent(
            existingId = screen.eventId,
            calendar = cal,
            title = title.trim().ifEmpty { "(без названия)" },
            description = description,
            begin = begin,
            end = finish,
            allDay = allDay,
            colorKey = colorKey,
            rrule = recurrence?.toRRule(),
            reminders = reminders,
        )
    }

    fun setStart(newStart: LocalDateTime) {
        val dur = Duration.between(start, end).let { if (it.isNegative) Duration.ofHours(1) else it }
        start = newStart
        end = newStart.plus(dur)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.Filled.Close, "Закрыть") }
                },
                actions = {
                    if (!isNew && loaded) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                    Button(onClick = { save() }, enabled = loaded, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Сохранить")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Название — крупно, без рамки, как в Google
            Box(Modifier.padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                if (title.isEmpty()) {
                    Text(
                        "Добавьте название",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider()

            // Время
            EditRow(painterResource(R.drawable.ic_schedule)) {
                Text("Весь день", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            DateTimeLine(
                date = start.toLocalDate(),
                time = start.toLocalTime(),
                showTime = !allDay,
                onDate = { d -> setStart(d.atTime(start.toLocalTime())) },
                onTime = { t -> setStart(start.toLocalDate().atTime(t)) },
            )
            DateTimeLine(
                date = end.toLocalDate(),
                time = end.toLocalTime(),
                showTime = !allDay,
                onDate = { d -> end = d.atTime(end.toLocalTime()) },
                onTime = { t -> end = end.toLocalDate().atTime(t) },
            )
            if (!allDay && end.isAfter(start)) {
                Text(
                    Fmt.duration(Duration.between(start, end).toMinutes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, bottom = 6.dp),
                )
            }

            // Повтор
            EditRow(painterResource(R.drawable.ic_repeat), onClick = { repeatDialog = true }) {
                Text(recurrence?.describe() ?: "Не повторяется", style = MaterialTheme.typography.bodyLarge)
            }
            if (wasRecurring) {
                Text(
                    "Изменения применятся ко всем повторениям этого события.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            HorizontalDivider()

            // Календарь
            val writable = vm.calendars.filter { it.writable }
            Box {
                EditRow(
                    rememberVectorPainter(Icons.Filled.DateRange),
                    onClick = if (isNew) ({ calendarMenu = true }) else null,
                ) {
                    calendar?.let { ColorDot(it.color, 12.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(calendar?.name ?: "Выберите календарь", style = MaterialTheme.typography.bodyLarge)
                        calendar?.let {
                            Text(it.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                DropdownMenu(expanded = calendarMenu, onDismissRequest = { calendarMenu = false }) {
                    writable.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.name} · ${c.accountName}") },
                            leadingIcon = { ColorDot(c.color) },
                            onClick = {
                                calendar = c
                                calendarMenu = false
                            },
                        )
                    }
                }
            }

            // Направление (цвет)
            EditRow(painterResource(R.drawable.ic_palette)) {
                Text("Направление", style = MaterialTheme.typography.bodyLarge)
            }
            val cal = calendar
            val opts = colorOptions
            if (cal == null || opts == null) {
                CircularProgressIndicator(Modifier.padding(start = 72.dp, bottom = 8.dp).size(24.dp))
            } else {
                val sorted = opts.sortedBy { if (vm.directionName(it.color) != null) 0 else 1 }
                FlowRow(
                    Modifier.padding(start = 64.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorOption(cal.color, vm.directionName(cal.color) ?: "Цвет календаря", colorKey == null) { colorKey = null }
                    sorted.forEach { o ->
                        ColorOption(o.color, vm.directionName(o.color) ?: "без названия", colorKey == o.key) { colorKey = o.key }
                    }
                }
            }
            HorizontalDivider()

            // Напоминания
            reminders.sorted().forEachIndexed { i, m ->
                EditRow(if (i == 0) rememberVectorPainter(Icons.Filled.Notifications) else null) {
                    Text(ReminderOptions.label(m), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { reminders = reminders - m }) { Icon(Icons.Filled.Close, "Убрать") }
                }
            }
            EditRow(
                if (reminders.isEmpty()) rememberVectorPainter(Icons.Filled.Notifications) else null,
                onClick = { reminderDialog = true },
            ) {
                Text(
                    "Добавить уведомление",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            // Описание
            EditRow(painterResource(R.drawable.ic_notes)) {
                Box(Modifier.weight(1f)) {
                    if (description.isEmpty()) {
                        Text("Добавьте описание", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (repeatDialog) {
        val presets = remember(start) { Recurrence.presets(start.toLocalDate()) }
        AlertDialog(
            onDismissRequest = { repeatDialog = false },
            title = { Text("Повтор") },
            text = {
                Column {
                    presets.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    recurrence = p
                                    repeatDialog = false
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = recurrence == p, onClick = {
                                recurrence = p
                                repeatDialog = false
                            })
                            Text(p?.describe() ?: "Не повторяется")
                        }
                    }
                    val isCustom = recurrence != null && recurrence !in presets
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                repeatDialog = false
                                customRepeat = true
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isCustom, onClick = {
                            repeatDialog = false
                            customRepeat = true
                        })
                        Text(if (isCustom) "Другое: ${recurrence?.describe()}" else "Другое…")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { repeatDialog = false }) { Text("Готово") } },
        )
    }

    if (customRepeat) {
        CustomRepeatDialog(
            initial = recurrence ?: Recurrence(Recurrence.Freq.WEEKLY, byDay = setOf(start.dayOfWeek)),
            startDate = start.toLocalDate(),
            onDismiss = { customRepeat = false },
            onDone = {
                recurrence = it
                customRepeat = false
            },
        )
    }

    if (reminderDialog) {
        AlertDialog(
            onDismissRequest = { reminderDialog = false },
            title = { Text("Уведомление") },
            text = {
                Column {
                    ReminderOptions.all.filter { it !in reminders }.forEach { m ->
                        Text(
                            ReminderOptions.label(m),
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    reminders = (reminders + m).sorted()
                                    reminderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reminderDialog = false }) { Text("Отмена") } },
        )
    }

    if (confirmDelete) {
        val instBegin = screen.instanceBegin
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (wasRecurring) "Удалить повторяющееся событие?" else "Удалить событие?") },
            text = { Text("Событие удалится и из Google Календаря.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (wasRecurring && instBegin != null) {
                        TextButton(onClick = {
                            confirmDelete = false
                            screen.eventId?.let { vm.deleteEvent(it, calendar, instBegin) }
                        }) { Text("Только это событие") }
                    }
                    TextButton(onClick = {
                        confirmDelete = false
                        screen.eventId?.let { vm.deleteEvent(it, calendar) }
                    }) { Text(if (wasRecurring) "Все события серии" else "Удалить") }
                    TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
                }
            },
        )
    }
}

@Composable
private fun rememberVectorPainter(v: androidx.compose.ui.graphics.vector.ImageVector): Painter =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(v)

/** Строка редактора: иконка слева (72dp колонка) и содержимое. */
@Composable
private fun EditRow(icon: Painter?, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun DateTimeLine(date: LocalDate, time: LocalTime, showTime: Boolean, onDate: (LocalDate) -> Unit, onTime: (LocalTime) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 72.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Fmt.dateWithDow(date).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .clickable { pickDate(ctx, date) { onDate(it) } }
                .padding(vertical = 10.dp),
        )
        if (showTime) {
            Text(
                Fmt.time(time),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clickable { TimePickerDialog(ctx, { _, h, m -> onTime(LocalTime.of(h, m)) }, time.hour, time.minute, true).show() }
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun ColorOption(color: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ColorDot(color, 32.dp, selected)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** «Другое…»: каждые N дней/недель/месяцев/лет, дни недели, окончание. */
@Composable
private fun CustomRepeatDialog(initial: Recurrence, startDate: LocalDate, onDismiss: () -> Unit, onDone: (Recurrence) -> Unit) {
    val ctx = LocalContext.current
    var interval by remember { mutableStateOf(initial.interval.toString()) }
    var freq by remember { mutableStateOf(initial.freq) }
    var days by remember { mutableStateOf(initial.byDay.ifEmpty { setOf(startDate.dayOfWeek) }) }
    var endMode by remember { mutableStateOf(if (initial.until != null) 1 else if (initial.count != null) 2 else 0) }
    var until by remember { mutableStateOf(initial.until ?: startDate.plusMonths(3)) }
    var count by remember { mutableStateOf((initial.count ?: 10).toString()) }
    val units = listOf(
        Recurrence.Freq.DAILY to "день", Recurrence.Freq.WEEKLY to "неделя",
        Recurrence.Freq.MONTHLY to "месяц", Recurrence.Freq.YEARLY to "год",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Своя периодичность") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Каждые ")
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> interval = v.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle.Default,
                        modifier = Modifier.width(72.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { (f, label) ->
                        FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(label) })
                    }
                }
                if (freq == Recurrence.Freq.WEEKLY) {
                    Text("По дням", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days && days.size > 1) days - d else days + d },
                                label = { Text(Fmt.weekdayShort(d.value - 1)) },
                            )
                        }
                    }
                }
                Text("Окончание", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 0, onClick = { endMode = 0 })
                    Text("Никогда")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 1, onClick = { endMode = 1 })
                    Text("До ")
                    TextButton(onClick = {
                        endMode = 1
                        pickDate(ctx, until) { until = it }
                    }) { Text(Fmt.date(until)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 2, onClick = { endMode = 2 })
                    OutlinedTextField(
                        value = count,
                        onValueChange = { v ->
                            count = v.filter { it.isDigit() }.take(3)
                            endMode = 2
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp),
                    )
                    Text(" повторений")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(
                    Recurrence(
                        freq = freq,
                        interval = interval.toIntOrNull()?.coerceIn(1, 999) ?: 1,
                        byDay = if (freq == Recurrence.Freq.WEEKLY) days else emptySet(),
                        until = if (endMode == 1) until else null,
                        count = if (endMode == 2) count.toIntOrNull()?.coerceAtLeast(1) else null,
                    ),
                )
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
