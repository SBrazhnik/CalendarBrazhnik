@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.TaskItem
import kotlinx.coroutines.launch

/** Названные направления (по одному цвету на название) — для выбора у задачи. */
fun namedDirections(vm: MainViewModel): List<Pair<String, Int>> =
    vm.data.directions.values
        .filter { it.name.isNotBlank() && !it.hidden }
        .groupBy { it.name.trim().lowercase() }
        .map { (_, list) -> list.first().name.trim() to list.first().color }
        .sortedBy { it.first }

@Composable
fun TasksTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val today = vm.today
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf("") }

    val active = vm.tasks.filter { !it.done }
    val overdue = active.filter { it.due != null && it.due.isBefore(today) }.sortedBy { it.due }
    val todays = active.filter { it.due == today }.sortedBy { it.created }
    val upcoming = active.filter { it.due != null && it.due.isAfter(today) }.sortedBy { it.due }
    val noDate = active.filter { it.due == null }.sortedByDescending { it.created }
    val done = vm.tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
    val doneToday = done.count { d -> d.doneAt?.let { Fmt.dayOfMillis(it) } == today }
    val todayTotal = todays.size + doneToday

    fun delete(t: TaskItem) {
        vm.deleteTask(t)
        scope.launch {
            val r = snackbar.showSnackbar("Задача удалена", actionLabel = "Отменить", withDismissAction = true)
            if (r == SnackbarResult.ActionPerformed) vm.undoDeleteTask()
        }
    }

    fun addQuick() {
        val title = quick.trim()
        if (title.isEmpty()) return
        vm.upsertTask(TaskItem(id = vm.newTaskId(), title = title, due = today))
        quick = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Задачи", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Настройки") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = TaskItem(id = vm.newTaskId(), title = "", due = today) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Задача") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Сегодня", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$doneToday",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                " из $todayTotal выполнено",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { if (todayTotal == 0) 0f else doneToday.toFloat() / todayTotal },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        )
                        if (overdue.isNotEmpty()) {
                            Text(
                                "Просрочено: ${overdue.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
            item {
                TextField(
                    value = quick,
                    onValueChange = { quick = it },
                    placeholder = { Text("Быстро добавить задачу на сегодня") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addQuick() }),
                    trailingIcon = {
                        if (quick.isNotBlank()) {
                            IconButton(onClick = { addQuick() }) { Icon(Icons.AutoMirrored.Filled.Send, "Добавить") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (active.isEmpty() && done.isEmpty()) {
                item {
                    Text(
                        "Задач пока нет. Добавьте первую — строкой выше или кнопкой «Задача».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            section("Просрочено", overdue, vm, { editing = it }, ::delete, isError = true)
            section("Сегодня", todays, vm, { editing = it }, ::delete)
            section("Предстоящие", upcoming, vm, { editing = it }, ::delete)
            section("Без срока", noDate, vm, { editing = it }, ::delete)
            if (done.isNotEmpty()) {
                item(key = "done_header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDone = !showDone }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Выполненные · ${done.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (showDone) {
                            TextButton(onClick = { vm.clearDoneTasks() }) { Text("Очистить") }
                        }
                        Icon(if (showDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    }
                }
                if (showDone) {
                    items(done, key = { it.id }) { t ->
                        SwipeTaskRow(t, vm, { editing = it }, ::delete)
                    }
                }
            }
        }
    }

    editing?.let { t ->
        TaskSheet(
            vm = vm,
            initial = t,
            isNew = vm.tasks.none { it.id == t.id },
            onDismiss = { editing = null },
            onSave = {
                vm.upsertTask(it)
                editing = null
            },
            onDelete = {
                editing = null
                delete(t)
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<TaskItem>,
    vm: MainViewModel,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    isError: Boolean = false,
) {
    if (list.isEmpty()) return
    item(key = "h_$title") {
        Text(
            "$title · ${list.size}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
    }
    items(list, key = { it.id }) { t -> SwipeTaskRow(t, vm, onEdit, onDelete) }
}

@Composable
private fun SwipeTaskRow(t: TaskItem, vm: MainViewModel, onEdit: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                onDelete(t)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        TaskRow(t, vm, onClick = { onEdit(t) })
    }
}

@Composable
private fun TaskRow(t: TaskItem, vm: MainViewModel, onClick: () -> Unit) {
    val today = vm.today
    val direction = t.color?.let { vm.directionName(it) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskCheck(t.done, t.color) { vm.toggleTask(t) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val parts = buildList {
                    t.due?.let { add(Fmt.dueShort(it, today)) }
                    direction?.let { add(it) }
                    if (t.notes.isNotBlank()) add(t.notes.lines().first())
                }
                if (parts.isNotEmpty()) {
                    val overdue = !t.done && t.due != null && t.due.isBefore(today)
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (t.color != null) {
                Spacer(Modifier.width(8.dp))
                ColorDot(t.color, 10.dp)
            }
        }
    }
}

/** Круглая отметка выполнения в цвете направления. */
@Composable
fun TaskCheck(done: Boolean, color: Int?, onToggle: () -> Unit) {
    val accent = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (done) accent else Color.Transparent, label = "check")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, accent, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = done) {
            Icon(Icons.Filled.Check, "Выполнено", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskSheet(
    vm: MainViewModel,
    initial: TaskItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaskItem) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val today = vm.today
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial.title) }
    var notes by remember { mutableStateOf(initial.notes) }
    var due by remember { mutableStateOf(initial.due) }
    var color by remember { mutableStateOf(initial.color) }
    val directions = remember(vm.data) { namedDirections(vm) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isNew) "Новая задача" else "Задача", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Что сделать") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Срок", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = due == null, onClick = { due = null }, label = { Text("Без срока") })
                FilterChip(selected = due == today, onClick = { due = today }, label = { Text("Сегодня") })
                FilterChip(selected = due == today.plusDays(1), onClick = { due = today.plusDays(1) }, label = { Text("Завтра") })
                val custom = due?.let { it != today && it != today.plusDays(1) } == true
                FilterChip(
                    selected = custom,
                    onClick = { pickDate(ctx, due ?: today) { due = it } },
                    label = { Text(if (custom) Fmt.dueShort(due!!, today) else "Дата…") },
                )
            }

            if (directions.isNotEmpty()) {
                Text("Направление", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = color == null, onClick = { color = null }, label = { Text("Нет") })
                    directions.forEach { (name, c) ->
                        FilterChip(
                            selected = color == c,
                            onClick = { color = c },
                            label = { Text(name) },
                            leadingIcon = { ColorDot(c, 10.dp) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Filled.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить")
                    }
                }
                Button(
                    onClick = {
                        onSave(initial.copy(title = title.trim().ifEmpty { "Без названия" }, notes = notes.trim(), due = due, color = color))
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (isNew) "Добавить" else "Сохранить") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

