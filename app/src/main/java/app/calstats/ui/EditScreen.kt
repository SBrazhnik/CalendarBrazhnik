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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.CalendarInfo
import app.calstats.EventColorOption
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Stats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun EditScreen(vm: MainViewModel, screen: Screen.Edit, snackbar: SnackbarHostState) {
    val isNew = screen.eventId == null
    val zone = vm.zone

    var loaded by remember { mutableStateOf(isNew) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var calendar by remember { mutableStateOf<CalendarInfo?>(if (isNew) vm.defaultCalendar() else null) }
    var colorKey by remember { mutableStateOf<String?>(null) }
    var allDay by remember { mutableStateOf(false) }
    var recurring by remember { mutableStateOf(false) }
    val initial = remember {
        LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)
    }
    var start by remember { mutableStateOf(initial) }
    var end by remember { mutableStateOf(initial.plusHours(1)) }
    var presetApplied by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }

    LaunchedEffect(screen.eventId) {
        val id = screen.eventId ?: return@LaunchedEffect
        val d = vm.loadEvent(id)
        if (d == null) {
            vm.message = "Событие не найдено"
            vm.pop()
            return@LaunchedEffect
        }
        title = d.title
        description = d.description
        calendar = vm.calendarById(d.calendarId)
        colorKey = d.colorKey
        allDay = d.allDay
        recurring = d.recurring
        val b = screen.instanceBegin ?: d.dtStart
        val e = screen.instanceEnd ?: d.dtEnd ?: (b + 3_600_000L)
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

    fun save() {
        val cal = calendar
        if (cal == null) {
            vm.message = "Нет календаря, в который можно записать событие"
            return
        }
        val begin: Long
        val finish: Long
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
            if (!recurring && finish <= begin) {
                vm.message = "Окончание должно быть позже начала"
                return
            }
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
            timeEditable = !recurring,
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
                title = { Text(if (isNew) "Новое событие" else "Событие") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    if (!isNew && loaded) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Календарь
            val writable = vm.calendars.filter { it.writable }
            if (isNew) {
                Box {
                    OutlinedButton(onClick = { calendarMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        calendar?.let { ColorDot(it.color) }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            calendar?.let { "${it.name} · ${it.accountName}" } ?: "Выберите календарь",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
            } else {
                Text(
                    "Календарь: " + (calendar?.let { "${it.name} · ${it.accountName}" } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Направление (цвет)
            Text("Направление", style = MaterialTheme.typography.titleSmall)
            val cal = calendar
            val opts = colorOptions
            if (cal == null || opts == null) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                val sorted = opts.sortedBy { if (vm.directionName(it.color) != null) 0 else 1 }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorOption(
                        color = cal.color,
                        label = vm.directionName(cal.color) ?: "Цвет календаря",
                        selected = colorKey == null,
                    ) { colorKey = null }
                    sorted.forEach { o ->
                        ColorOption(
                            color = o.color,
                            label = vm.directionName(o.color) ?: "без названия",
                            selected = colorKey == o.key,
                        ) { colorKey = o.key }
                    }
                }
                if (opts.isEmpty()) {
                    Text(
                        "У этого календаря нет отдельных цветов событий — используется цвет календаря.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recurring) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "Это повторяющееся событие. Название, направление и описание изменятся для всей серии. " +
                            "Время отдельного повторения меняйте в приложении Google Календарь.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Весь день", Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it }, enabled = !recurring)
            }

            DateTimeRow(
                label = "Начало",
                value = start,
                showTime = !allDay,
                enabled = !recurring,
                onDate = { d -> setStart(d.atTime(start.toLocalTime())) },
                onTime = { t -> setStart(start.toLocalDate().atTime(t)) },
            )
            DateTimeRow(
                label = "Окончание",
                value = end,
                showTime = !allDay,
                enabled = !recurring,
                onDate = { d -> end = d.atTime(end.toLocalTime()) },
                onTime = { t -> end = end.toLocalDate().atTime(t) },
            )
            if (!allDay && end.isAfter(start)) {
                Text(
                    "Длительность: " + Fmt.duration(Duration.between(start, end).toMinutes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (isNew) "Добавить в календарь" else "Сохранить")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить событие?") },
            text = {
                Text(
                    if (recurring) "Будут удалены все повторения этого события. Оно удалится и из Google Календаря."
                    else "Событие удалится и из Google Календаря."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    screen.eventId?.let { vm.deleteEvent(it, calendar) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ColorOption(color: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ColorDot(color, 36.dp, selected)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    value: LocalDateTime,
    showTime: Boolean,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    val ctx = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { pickDate(ctx, value.toLocalDate()) { onDate(it) } },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { Text(Fmt.dateWithDow(value.toLocalDate())) }
            if (showTime) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> onTime(LocalTime.of(h, m)) }, value.hour, value.minute, true).show()
                    },
                    enabled = enabled,
                ) { Text(Fmt.time(value.toLocalTime())) }
            }
        }
    }
}
