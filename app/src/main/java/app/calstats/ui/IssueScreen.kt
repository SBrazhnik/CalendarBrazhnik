@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.work.Comment
import app.calstats.work.Issue
import app.calstats.work.IssueType
import app.calstats.work.ME
import app.calstats.work.Priority
import app.calstats.work.SprintState
import app.calstats.work.Status
import app.calstats.work.WorkLog
import app.calstats.work.WorkOps
import app.calstats.work.newId
import java.time.LocalDate

/** Карточка задачи как в Jira: статус, поля, описание, подзадачи, время, комментарии. */
@Composable
fun IssueScreen(vm: MainViewModel, id: String, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val w = vm.work
    val issue = w.issue(id)
    if (issue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Задача удалена")
                TextButton(onClick = { vm.pop() }) { Text("Назад") }
            }
        }
        return
    }
    var title by remember(id) { mutableStateOf(issue.title) }
    var description by remember(id) { mutableStateOf(issue.description) }
    val latestTitle by rememberUpdatedState(title)
    val latestDesc by rememberUpdatedState(description)

    fun save(transform: (Issue) -> Issue = { it }) = vm.updateWork { ws ->
        val cur = ws.issue(id) ?: return@updateWork ws
        WorkOps.updateIssue(ws, transform(cur).copy(title = latestTitle.ifBlank { cur.title }, description = latestDesc))
    }
    // Текст сохраняем при уходе с экрана
    DisposableEffect(id) {
        onDispose {
            val cur = vm.work.issue(id)
            if (cur != null && (cur.title != latestTitle || cur.description != latestDesc)) save()
        }
    }

    var statusMenu by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }
    var estimateDialog by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    var addSub by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    val project = w.project(issue.projectId)
    val subs = w.issues.filter { it.parentId == id }.sortedBy { it.number }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeIcon(issue.type, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(w.key(issue), style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Запланировать в календаре") },
                                leadingIcon = { Icon(Icons.Filled.DateRange, null) },
                                onClick = {
                                    topMenu = false
                                    save()
                                    vm.push(Screen.Edit(presetDate = issue.dueDate ?: vm.today, presetTitle = "${w.key(issue)} ${title}"))
                                },
                            )
                            if (issue.parentId == null) {
                                IssueType.entries.filter { it != IssueType.SUBTASK && it != issue.type }.forEach { t ->
                                    DropdownMenuItem(text = { Text("Тип: ${t.title}") }, leadingIcon = { TypeIcon(t) }, onClick = {
                                        topMenu = false
                                        save { it.copy(type = t) }
                                    })
                                }
                            }
                            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                topMenu = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            w.issue(issue.parentId)?.let { parent ->
                Text(
                    "↑ ${w.key(parent)} ${parent.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { vm.push(Screen.IssueDetail(parent.id)) },
                )
            }
            Box {
                if (title.isEmpty()) Text("Название", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Статус
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor(issue.status))
                        .clickable { statusMenu = true }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(issue.status.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    Status.entries.forEach { s ->
                        DropdownMenuItem(text = { StatusPill(s) }, onClick = {
                            statusMenu = false
                            vm.updateWork { ws -> WorkOps.setStatus(ws, id, s) }
                        })
                    }
                }
            }

            // Поля
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    FieldRow("Исполнитель") {
                        var m by remember { mutableStateOf(false) }
                        Box {
                            Box(Modifier.clickable { m = true }) { PersonLine(w.person(issue.assigneeId)) }
                            PersonMenu(m, w.everyone, true, { m = false }) { p ->
                                m = false
                                save { it.copy(assigneeId = p?.id) }
                            }
                        }
                    }
                    FieldRow("Автор") {
                        var m by remember { mutableStateOf(false) }
                        Box {
                            Box(Modifier.clickable { m = true }) { PersonLine(w.person(issue.reporterId), "—") }
                            PersonMenu(m, w.everyone, false, { m = false }) { p ->
                                m = false
                                save { it.copy(reporterId = p?.id) }
                            }
                        }
                    }
                    FieldRow("Приоритет") {
                        var m by remember { mutableStateOf(false) }
                        Box {
                            Row(Modifier.clickable { m = true }, verticalAlignment = Alignment.CenterVertically) {
                                PriorityIcon(issue.priority)
                                Spacer(Modifier.width(8.dp))
                                Text(issue.priority.title)
                            }
                            DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                                Priority.entries.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.title) }, leadingIcon = { PriorityIcon(p) }, onClick = {
                                        m = false
                                        save { it.copy(priority = p) }
                                    })
                                }
                            }
                        }
                    }
                    if (issue.type != IssueType.EPIC && issue.parentId == null) {
                        FieldRow("Спринт") {
                            var m by remember { mutableStateOf(false) }
                            val sprints = w.sprints.filter { it.projectId == issue.projectId && it.state != SprintState.CLOSED }
                            Box {
                                Text(
                                    w.sprints.firstOrNull { it.id == issue.sprintId }?.name ?: "Бэклог",
                                    Modifier.clickable { m = true },
                                )
                                DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                                    DropdownMenuItem(text = { Text("Бэклог") }, onClick = {
                                        m = false
                                        save { it.copy(sprintId = null) }
                                    })
                                    sprints.forEach { s ->
                                        DropdownMenuItem(text = { Text(s.name + if (s.state == SprintState.ACTIVE) " (активный)" else "") }, onClick = {
                                            m = false
                                            save { it.copy(sprintId = s.id) }
                                        })
                                    }
                                }
                            }
                        }
                        FieldRow("Эпик") {
                            var m by remember { mutableStateOf(false) }
                            val epics = w.issues.filter { it.projectId == issue.projectId && it.type == IssueType.EPIC }
                            Box {
                                Text(w.issue(issue.epicId)?.title ?: "Нет", Modifier.clickable { m = true }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                                    DropdownMenuItem(text = { Text("Нет") }, onClick = {
                                        m = false
                                        save { it.copy(epicId = null) }
                                    })
                                    epics.forEach { e ->
                                        DropdownMenuItem(text = { Text(e.title) }, leadingIcon = { TypeIcon(IssueType.EPIC) }, onClick = {
                                            m = false
                                            save { it.copy(epicId = e.id) }
                                        })
                                    }
                                    if (epics.isEmpty()) DropdownMenuItem(text = { Text("Эпиков пока нет") }, onClick = { m = false })
                                }
                            }
                        }
                    }
                    FieldRow("Начало") {
                        DateField(issue.startDate) { d -> save { it.copy(start = d?.toString()) } }
                    }
                    FieldRow("Срок") {
                        DateField(issue.dueDate, overdue = !issue.done && issue.dueDate?.isBefore(vm.today) == true) { d ->
                            save { it.copy(due = d?.toString()) }
                        }
                    }
                    FieldRow("Метки") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            issue.labels.forEach { l ->
                                InputChip(
                                    selected = false,
                                    onClick = { save { it.copy(labels = it.labels - l) } },
                                    label = { Text(l, fontSize = 12.sp) },
                                    trailingIcon = { Icon(Icons.Filled.Close, "Убрать", Modifier.size(14.dp)) },
                                )
                            }
                            TextButton(onClick = { labelDialog = true }) { Text("+ метка") }
                        }
                    }
                    FieldRow("Проект") { Text(project?.name ?: "—") }
                }
            }

            // Описание
            Text("Описание", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Добавьте описание…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // Подзадачи
            if (issue.parentId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Подзадачи", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (subs.isNotEmpty()) Text("${subs.count { it.done }} из ${subs.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { addSub = true }) { Icon(Icons.Filled.Add, "Добавить подзадачу") }
                }
                if (subs.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { subs.count { it.done }.toFloat() / subs.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = statusColor(Status.DONE),
                    )
                }
                subs.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.push(Screen.IssueDetail(s.id)) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TaskCheck(s.done, null) { vm.toggleIssueDone(s) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.title,
                            Modifier.weight(1f),
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Avatar(w.person(s.assigneeId), 22.dp)
                    }
                }
            }

            // Учёт времени
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Учёт времени", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { estimateDialog = true }) {
                    Text(issue.estimateMin?.let { "Оценка: ${Durations.format(it)}" } ?: "Оценить")
                }
            }
            val logged = issue.loggedMin
            val est = issue.estimateMin
            if (est != null && est > 0) {
                LinearProgressIndicator(
                    progress = { (logged.toFloat() / est).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (logged > est) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Затрачено ${Durations.format(logged)} · " +
                        if (logged <= est) "осталось ${Durations.format(est - logged)}" else "перерасход ${Durations.format(logged - est)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (logged > 0) {
                Text("Затрачено ${Durations.format(logged)}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { logDialog = true }) { Text("Записать время") }
            issue.worklogs.sortedByDescending { it.date }.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(w.person(l.personId), 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${w.person(l.personId)?.name ?: "?"} · ${runCatching { Fmt.dateNoYear(LocalDate.parse(l.date)) }.getOrDefault(l.date)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Durations.format(l.minutes), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.updateWork { WorkOps.removeWorklog(it, id, l.id) } }) {
                        Icon(Icons.Filled.Close, "Удалить запись", Modifier.size(18.dp))
                    }
                }
            }

            // Комментарии
            HorizontalDivider()
            Text("Комментарии", style = MaterialTheme.typography.titleMedium)
            issue.comments.sortedBy { it.created }.forEach { c ->
                Row {
                    Avatar(w.person(c.authorId), 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.person(c.authorId)?.name ?: "?", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Fmt.dateNoYear(Fmt.dayOfMillis(c.created)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(c.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Добавить комментарий…") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val text = comment.trim()
                        if (text.isNotEmpty()) {
                            vm.updateWork { WorkOps.addComment(it, id, Comment(newId(), ME, text, System.currentTimeMillis())) }
                            comment = ""
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
            }
            Text(
                "Создана ${Fmt.date(Fmt.dayOfMillis(issue.created))} · обновлена ${Fmt.date(Fmt.dayOfMillis(issue.updated))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (addSub) CreateIssueSheet(vm, onDismiss = { addSub = false }, parent = issue)

    if (logDialog) {
        var text by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(vm.today) }
        var person by remember { mutableStateOf(ME) }
        var pm by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { logDialog = false },
            title = { Text("Записать время") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Сколько: 1ч 30м, 45м, 2") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dateNoYear(date)) }
                        Box {
                            OutlinedButton(onClick = { pm = true }) { Text(w.person(person)?.name ?: "?") }
                            PersonMenu(pm, w.everyone, false, { pm = false }) {
                                person = it?.id ?: ME
                                pm = false
                            }
                        }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Что делали (необязательно)") })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val m = Durations.parse(text) ?: return@TextButton
                        vm.updateWork { WorkOps.addWorklog(it, id, WorkLog(newId(), person, date.toString(), m, note.trim())) }
                        logDialog = false
                    },
                    enabled = Durations.parse(text) != null,
                ) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { logDialog = false }) { Text("Отмена") } },
        )
    }

    if (estimateDialog) {
        var text by remember { mutableStateOf(issue.estimateMin?.let { Durations.format(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { estimateDialog = false },
            title = { Text("Оценка") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("Например: 4ч, 1д 2ч, 90м") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    save { it.copy(estimateMin = Durations.parse(text)) }
                    estimateDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { estimateDialog = false }) { Text("Отмена") } },
        )
    }

    if (labelDialog) {
        var text by remember { mutableStateOf("") }
        val known = w.issues.flatMap { it.labels }.distinct().filter { it !in issue.labels }
        AlertDialog(
            onDismissRequest = { labelDialog = false },
            title = { Text("Метка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Новая метка") }, singleLine = true)
                    if (known.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            known.forEach { k ->
                                Text(
                                    k,
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            save { it.copy(labels = it.labels + k) }
                                            labelDialog = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val l = text.trim()
                    if (l.isNotEmpty()) save { it.copy(labels = (it.labels + l).distinct()) }
                    labelDialog = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { labelDialog = false }) { Text("Отмена") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить ${w.key(issue)}?") },
            text = { Text(if (subs.isNotEmpty()) "Вместе с подзадачами (${subs.size})." else "Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.pop()
                    vm.updateWork { WorkOps.deleteIssue(it, id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DateField(date: LocalDate?, overdue: Boolean = false, onChange: (LocalDate?) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            date?.let { Fmt.dateWithDow(it) } ?: "Не задано",
            Modifier.clickable { pickDate(ctx, date ?: LocalDate.now()) { onChange(it) } },
            color = when {
                overdue -> MaterialTheme.colorScheme.error
                date == null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (date != null) {
            IconButton(onClick = { onChange(null) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Очистить", Modifier.size(16.dp))
            }
        }
    }
}
