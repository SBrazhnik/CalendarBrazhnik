@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Screen
import app.calstats.work.Issue
import app.calstats.work.IssueType
import app.calstats.work.ME
import app.calstats.work.Priority
import app.calstats.work.Sprint
import app.calstats.work.SprintState
import app.calstats.work.Status
import app.calstats.work.WorkOps
import app.calstats.work.newId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val TASK_VIEWS = listOf("ALL" to "Все задачи", "BOARD" to "Доска", "BACKLOG" to "Бэклог", "TIMELINE" to "Таймлайн", "TIME" to "Время")

/** Вкладка «Задачи» — таск-трекер в духе Jira. */
@Composable
fun TasksTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    val project = w.project(vm.currentProjectId)
    var projectMenu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Box {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { projectMenu = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (project != null) {
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(project.color)), contentAlignment = Alignment.Center) {
                                    Text(project.key.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(project?.name ?: "Все проекты", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("Все проекты") }, onClick = {
                                vm.selectProject(null)
                                projectMenu = false
                            })
                            w.projects.filter { !it.archived }.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name}  ·  ${p.key}") },
                                    leadingIcon = { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(p.color))) },
                                    onClick = {
                                        vm.selectProject(p.id)
                                        projectMenu = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Новый проект") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = {
                                projectMenu = false
                                vm.push(Screen.ProjectEdit(null))
                            })
                            if (project != null) {
                                DropdownMenuItem(text = { Text("Настройки проекта") }, onClick = {
                                    projectMenu = false
                                    vm.push(Screen.ProjectEdit(project.id))
                                })
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.push(Screen.People) }) { Icon(painterResource(R.drawable.ic_group), "Люди и группы") }
                },
            )
        },
        floatingActionButton = {
            if (w.projects.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Создать") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (w.projects.isEmpty()) {
                EmptyProjects(vm)
                return@Column
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TASK_VIEWS.forEach { (k, title) ->
                    FilterChip(selected = vm.taskView == k, onClick = { vm.selectTaskView(k) }, label = { Text(title) })
                }
                FilterChip(
                    selected = vm.onlyMine,
                    onClick = { vm.onlyMine = !vm.onlyMine },
                    label = { Text("Только мои") },
                    leadingIcon = { Avatar(w.person(ME), 18.dp) },
                )
            }
            when (vm.taskView) {
                "ALL" -> AllIssuesView(vm)
                "BACKLOG" -> BacklogView(vm)
                "TIMELINE" -> GanttView(vm)
                "TIME" -> TimeView(vm)
                else -> BoardView(vm)
            }
        }
    }

    if (creating) CreateIssueSheet(vm, onDismiss = { creating = false })
}

@Composable
private fun EmptyProjects(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Здесь будут ваши проекты", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Проект — это доска задач: «Ремонт», «Работа», «Дом». В нём есть спринты, бэклог, сроки и учёт времени.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.push(Screen.ProjectEdit(null)) }) { Text("Создать проект") }
    }
}

/** Задачи с учётом выбранного проекта и фильтра «Только мои». */
private fun visibleIssues(vm: MainViewModel): List<Issue> {
    val pid = vm.currentProjectId
    return vm.work.issues.filter { (pid == null || it.projectId == pid) && (!vm.onlyMine || it.assigneeId == ME) }
}

// ======================= ДОСКА =======================

@Composable
private fun BoardView(vm: MainViewModel) {
    val w = vm.work
    val issues = visibleIssues(vm).filter { i ->
        if (i.type == IssueType.EPIC) return@filter false
        val sprint = WorkOps.activeSprint(w, i.projectId)
        sprint == null || i.sprintId == sprint.id
    }
    val active = vm.currentProjectId?.let { WorkOps.activeSprint(w, it) }
    Column(Modifier.fillMaxSize()) {
        if (active != null) {
            val left = active.endDate?.let { ChronoUnit.DAYS.between(vm.today, it) }
            Text(
                active.name + (left?.let { if (it >= 0) " · осталось ${Fmt.days(it)}" else " · просрочен" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        LazyRow(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Status.entries.toList(), key = { it.name }) { st ->
                val col = issues.filter { it.status == st }.sortedBy { it.order }
                Column(
                    Modifier
                        .width(286.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(8.dp),
                ) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor(st)))
                        Spacer(Modifier.width(8.dp))
                        Text(st.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(col.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(col, key = { it.id }) { i -> BoardCard(vm, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(vm: MainViewModel, i: Issue) {
    val w = vm.work
    var menu by remember { mutableStateOf(false) }
    val subs = w.issues.filter { it.parentId == i.id }
    val due = i.dueDate
    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = { vm.push(Screen.IssueDetail(i.id)) }, onLongClick = { menu = true }),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(i.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (i.labels.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        i.labels.take(3).forEach { LabelChip(it) }
                    }
                }
                w.issue(i.epicId)?.let { epic ->
                    Text(
                        epic.title,
                        fontSize = 11.sp,
                        color = Color(IssueType.EPIC.color),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(IssueType.EPIC.color).copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeIcon(i.type)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        w.key(i),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (i.done) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.weight(1f))
                    if (subs.isNotEmpty()) {
                        Text("${subs.count { it.done }}/${subs.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (due != null) {
                        val overdue = !i.done && due.isBefore(vm.today)
                        Text(
                            Fmt.dateNoYear(due),
                            fontSize = 11.sp,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    PriorityIcon(i.priority)
                    Spacer(Modifier.width(6.dp))
                    Avatar(w.person(i.assigneeId), 22.dp)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Status.entries.filter { it != i.status }.forEach { s ->
                DropdownMenuItem(text = { Text("→ ${s.title}") }, onClick = {
                    menu = false
                    vm.updateWork { WorkOps.setStatus(it, i.id, s) }
                })
            }
        }
    }
}

@Composable
fun LabelChip(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ======================= БЭКЛОГ И СПРИНТЫ =======================

@Composable
private fun BacklogView(vm: MainViewModel) {
    val w = vm.work
    val pid = vm.currentProjectId
    if (pid == null) {
        PickProjectHint(vm)
        return
    }
    val issues = visibleIssues(vm).filter { it.type != IssueType.EPIC && it.parentId == null }
    val sprints = w.sprints.filter { it.projectId == pid && it.state != SprintState.CLOSED }
        .sortedWith(compareBy({ it.state != SprintState.ACTIVE }, { it.start ?: "9999" }))
    var completing by remember { mutableStateOf<Sprint?>(null) }
    var editing by remember { mutableStateOf<Sprint?>(null) }
    var showDone by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sprints, key = { it.id }) { s ->
            val list = issues.filter { it.sprintId == s.id }.sortedBy { it.order }
            SprintCard(vm, s, list, onComplete = { completing = s }, onEdit = { editing = s })
        }
        item(key = "backlog") {
            val list = issues.filter { it.sprintId == null && (showDone || !it.done) }.sortedBy { it.order }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Бэклог", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Скрыть готовые" else "Показать готовые") }
                    }
                    if (list.isEmpty()) {
                        Text("Пусто. Новые задачи попадают сюда.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    list.forEach { IssueRow(vm, it) }
                }
            }
        }
        item(key = "add_sprint") {
            OutlinedButton(onClick = { vm.updateWork { WorkOps.addSprint(it, pid) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Создать спринт")
            }
        }
    }

    completing?.let { s ->
        val unfinished = issues.count { it.sprintId == s.id && !it.done }
        val next = w.sprints.filter { it.projectId == pid && it.state == SprintState.PLANNED && it.id != s.id }
        AlertDialog(
            onDismissRequest = { completing = null },
            title = { Text("Завершить «${s.name}»?") },
            text = {
                Text(
                    if (unfinished == 0) "Все задачи спринта выполнены."
                    else "Незавершённых задач: $unfinished. Куда их перенести?",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    next.firstOrNull()?.let { n ->
                        if (unfinished > 0) {
                            TextButton(onClick = {
                                vm.updateWork { WorkOps.completeSprint(it, s.id, n.id) }
                                completing = null
                            }) { Text("В «${n.name}»") }
                        }
                    }
                    TextButton(onClick = {
                        vm.updateWork { WorkOps.completeSprint(it, s.id, null) }
                        completing = null
                    }) { Text(if (unfinished > 0) "В бэклог" else "Завершить") }
                    TextButton(onClick = { completing = null }) { Text("Отмена") }
                }
            },
        )
    }
    editing?.let { s -> SprintDialog(vm, s) { editing = null } }
}

@Composable
private fun PickProjectHint(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Выберите проект", style = MaterialTheme.typography.titleMedium)
        vm.work.projects.filter { !it.archived }.forEach { p ->
            Card(onClick = { vm.selectProject(p.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(p.color)), contentAlignment = Alignment.Center) {
                        Text(p.key.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SprintCard(vm: MainViewModel, s: Sprint, list: List<Issue>, onComplete: () -> Unit, onEdit: () -> Unit) {
    val w = vm.work
    var menu by remember { mutableStateOf(false) }
    val anyActive = WorkOps.activeSprint(w, s.projectId) != null
    val est = list.sumOf { it.estimateMin ?: 0 }
    val done = list.count { it.done }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (s.state == SprintState.ACTIVE) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    val dates = listOfNotNull(s.startDate?.let { Fmt.dateNoYear(it) }, s.endDate?.let { Fmt.dateNoYear(it) }).joinToString(" – ")
                    Text(
                        listOf(s.state.title, dates, "$done/${list.size} готово", if (est > 0) "оценка ${Durations.format(est)}" else "")
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.goal.isNotBlank()) Text("Цель: ${s.goal}", style = MaterialTheme.typography.bodySmall)
                }
                when (s.state) {
                    SprintState.ACTIVE -> Button(onClick = onComplete) { Text("Завершить") }
                    SprintState.PLANNED -> OutlinedButton(onClick = { vm.updateWork { WorkOps.startSprint(it, s.id) } }, enabled = !anyActive) { Text("Начать") }
                    SprintState.CLOSED -> {}
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = {
                            menu = false
                            onEdit()
                        })
                        DropdownMenuItem(text = { Text("Удалить спринт") }, onClick = {
                            menu = false
                            vm.updateWork { WorkOps.deleteSprint(it, s.id) }
                        })
                    }
                }
            }
            if (list.isEmpty()) {
                Text(
                    "Перенесите сюда задачи из бэклога (меню ⋮ у задачи).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            list.forEach { IssueRow(vm, it) }
        }
    }
}

@Composable
private fun SprintDialog(vm: MainViewModel, s: Sprint, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(s.name) }
    var goal by remember { mutableStateOf(s.goal) }
    var start by remember { mutableStateOf(s.startDate ?: vm.today) }
    var end by remember { mutableStateOf(s.endDate ?: vm.today.plusDays(13)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Спринт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(goal, { goal = it }, label = { Text("Цель спринта") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, start) { start = it } }) { Text(Fmt.dateNoYear(start)) }
                    Text("–", Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(onClick = { pickDate(ctx, end) { end = it } }) { Text(Fmt.dateNoYear(end)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateWork { WorkOps.updateSprint(it, s.copy(name = name.ifBlank { s.name }, goal = goal, start = start.toString(), end = end.toString())) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Строка задачи в списке (бэклог, спринты). */
@Composable
fun IssueRow(vm: MainViewModel, i: Issue) {
    val w = vm.work
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.push(Screen.IssueDetail(i.id)) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeIcon(i.type)
            Spacer(Modifier.width(8.dp))
            Text(
                w.key(i),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (i.done) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.width(8.dp))
            Text(i.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            PriorityIcon(i.priority)
            Spacer(Modifier.width(6.dp))
            Avatar(w.person(i.assigneeId), 20.dp)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "Ещё", Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val sprints = w.sprints.filter { it.projectId == i.projectId && it.state != SprintState.CLOSED && it.id != i.sprintId }
                    sprints.forEach { s ->
                        DropdownMenuItem(text = { Text("В «${s.name}»") }, onClick = {
                            menu = false
                            vm.updateWork { WorkOps.updateIssue(it, i.copy(sprintId = s.id)) }
                        })
                    }
                    if (i.sprintId != null) {
                        DropdownMenuItem(text = { Text("В бэклог") }, onClick = {
                            menu = false
                            vm.updateWork { WorkOps.updateIssue(it, i.copy(sprintId = null)) }
                        })
                    }
                    HorizontalDivider()
                    Status.entries.filter { it != i.status }.forEach { s ->
                        DropdownMenuItem(text = { Text("Статус: ${s.title}") }, onClick = {
                            menu = false
                            vm.updateWork { WorkOps.setStatus(it, i.id, s) }
                        })
                    }
                }
            }
        }
    }
}

// ======================= ТАЙМЛАЙН (ГАНТ) =======================

@Composable
private fun GanttView(vm: MainViewModel) {
    val w = vm.work
    val withDates = visibleIssues(vm).filter { (it.start != null || it.due != null) && it.parentId == null }
        .sortedWith(compareBy({ it.type != IssueType.EPIC }, { it.startDate ?: it.dueDate }))
    val noDates = visibleIssues(vm).count { it.start == null && it.due == null && it.parentId == null }
    if (withDates.isEmpty()) {
        Text(
            "Пока нечего показать. Укажите у задач даты начала и срока — они появятся на таймлайне.",
            Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val today = vm.today
    val minDate = (withDates.mapNotNull { it.startDate ?: it.dueDate } + today).minOrNull()!!.minusDays(3)
    val maxDate = (withDates.mapNotNull { it.dueDate ?: it.startDate } + today).maxOrNull()!!.plusDays(14)
    val days = ChronoUnit.DAYS.between(minDate, maxDate).toInt() + 1
    val dayW = 26.dp
    val rowH = 40.dp
    val hScroll = rememberScrollState(initial = 0)
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(top = 6.dp).verticalScroll(rememberScrollState())) {
        Row {
            // Колонка названий
            Column(Modifier.width(150.dp)) {
                Box(Modifier.height(40.dp))
                withDates.forEach { i ->
                    Row(
                        Modifier
                            .height(rowH)
                            .fillMaxWidth()
                            .clickable { vm.push(Screen.IssueDetail(i.id)) }
                            .padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypeIcon(i.type, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(i.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                    }
                }
            }
            // Шкала
            Column(Modifier.horizontalScroll(hScroll)) {
                Row(Modifier.height(40.dp)) {
                    for (d in 0 until days) {
                        val date = minDate.plusDays(d.toLong())
                        val isToday = date == today
                        Column(Modifier.width(dayW), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (date.dayOfMonth == 1 || d == 0) Fmt.monthShort(date.monthValue) else "",
                                fontSize = 9.sp,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                date.dayOfMonth.toString(),
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) scheme.primary
                                else if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) scheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else scheme.onSurface,
                            )
                        }
                    }
                }
                withDates.forEach { i ->
                    val s = i.startDate ?: i.dueDate!!
                    val e = (i.dueDate ?: i.startDate!!).let { if (it.isBefore(s)) s else it }
                    val offset = ChronoUnit.DAYS.between(minDate, s).toInt()
                    val len = ChronoUnit.DAYS.between(s, e).toInt() + 1
                    Box(Modifier.height(rowH).width(dayW * days)) {
                        // выходные и «сегодня»
                        Row {
                            for (d in 0 until days) {
                                val date = minDate.plusDays(d.toLong())
                                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                                Box(
                                    Modifier
                                        .width(dayW)
                                        .fillMaxHeight()
                                        .background(
                                            when {
                                                date == today -> scheme.primary.copy(alpha = 0.12f)
                                                weekend -> scheme.surfaceVariant.copy(alpha = 0.45f)
                                                else -> Color.Transparent
                                            },
                                        ),
                                )
                            }
                        }
                        val barColor = if (i.type == IssueType.EPIC) Color(IssueType.EPIC.color) else statusColor(i.status)
                        Box(
                            Modifier
                                .padding(start = dayW * offset, top = 8.dp)
                                .width(dayW * len - 2.dp)
                                .height(rowH - 16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(barColor.copy(alpha = if (i.done) 0.45f else 0.9f))
                                .clickable { vm.push(Screen.IssueDetail(i.id)) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(w.key(i), color = Color.White, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (noDates > 0) {
            Text(
                "Задач без дат: $noDates — они не показаны.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

// ======================= УЧЁТ ВРЕМЕНИ =======================

@Composable
private fun TimeView(vm: MainViewModel) {
    val w = vm.work
    var weekStart by remember { mutableStateOf(vm.today.with(DayOfWeek.MONDAY)) }
    val weekEnd = weekStart.plusDays(6)
    val pid = vm.currentProjectId
    data class Entry(val issue: Issue, val log: app.calstats.work.WorkLog, val date: LocalDate)
    val entries = w.issues.filter { pid == null || it.projectId == pid }.flatMap { i ->
        i.worklogs.mapNotNull { l ->
            val d = runCatching { LocalDate.parse(l.date) }.getOrNull() ?: return@mapNotNull null
            if (d.isBefore(weekStart) || d.isAfter(weekEnd)) null
            else if (vm.onlyMine && l.personId != ME) null
            else Entry(i, l, d)
        }
    }.sortedByDescending { it.date }
    val total = entries.sumOf { it.log.minutes }
    val perDay = (0..6).map { k -> entries.filter { it.date == weekStart.plusDays(k.toLong()) }.sumOf { it.log.minutes } }
    val maxDay = (perDay.maxOrNull() ?: 0).coerceAtLeast(60)
    val projIssues = w.issues.filter { pid == null || it.projectId == pid }
    val estTotal = projIssues.sumOf { it.estimateMin ?: 0 }
    val loggedTotal = projIssues.sumOf { it.loggedMin }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSecondary) }
                        Text(Fmt.range(weekStart, weekEnd), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSecondary) }
                    }
                    Text(Durations.format(total), style = MaterialTheme.typography.headlineMedium)
                    Text("затрачено за неделю", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        perDay.forEachIndexed { k, m ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((70f * m / maxDay).dp.coerceAtLeast(2.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Text(Fmt.weekdayShort(k), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        if (estTotal > 0 || loggedTotal > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Оценка и факт по ${if (pid == null) "всем проектам" else "проекту"}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Оценено ${Durations.format(estTotal)} · затрачено ${Durations.format(loggedTotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (estTotal > 0) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (loggedTotal.toFloat() / estTotal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = if (loggedTotal > estTotal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "За эту неделю время не записано. Откройте задачу и нажмите «Записать время».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(entries, key = { it.log.id }) { e ->
            Card(onClick = { vm.push(Screen.IssueDetail(e.issue.id)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(w.person(e.log.personId), 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${w.key(e.issue)} · ${e.issue.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Fmt.dateWithDow(e.date) + if (e.log.note.isNotBlank()) " · ${e.log.note}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Text(Durations.format(e.log.minutes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// ======================= СОЗДАНИЕ ЗАДАЧИ =======================

@Composable
fun CreateIssueSheet(vm: MainViewModel, onDismiss: () -> Unit, parent: Issue? = null) {
    val ctx = LocalContext.current
    val w = vm.work
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var projectId by remember { mutableStateOf(parent?.projectId ?: vm.currentProjectId ?: w.projects.firstOrNull { !it.archived }?.id) }
    var type by remember { mutableStateOf(if (parent != null) IssueType.SUBTASK else IssueType.TASK) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var assignee by remember { mutableStateOf<String?>(ME) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    val active = projectId?.let { WorkOps.activeSprint(w, it) }
    var toSprint by remember { mutableStateOf(vm.taskView == "BOARD" && active != null) }
    var personMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }

    /** Создаёт задачу из того, что введено. Название по умолчанию — первая строка описания. */
    fun create(): Boolean {
        if (saved) return false
        val pid = projectId ?: return false
        val t = title.trim().ifEmpty { description.trim().lineSequence().firstOrNull()?.take(80).orEmpty() }
        if (t.isEmpty()) return false
        saved = true
        vm.updateWork { ws ->
            WorkOps.createIssue(
                ws,
                Issue(
                    id = newId(),
                    projectId = pid,
                    number = 0,
                    title = t,
                    description = description.trim(),
                    type = type,
                    priority = priority,
                    assigneeId = assignee,
                    due = due?.toString(),
                    parentId = parent?.id,
                    sprintId = parent?.sprintId ?: if (toSprint) active?.id else null,
                ),
            ).first
        }
        return true
    }

    // Смахнули окно или нажали «назад» — ничего не теряем: введённое сохраняется задачей.
    ModalBottomSheet(
        onDismissRequest = {
            if (create()) vm.message = "Задача сохранена автоматически"
            onDismiss()
        },
        sheetState = sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (parent != null) "Новая подзадача" else "Создать задачу", style = MaterialTheme.typography.headlineSmall)
            if (parent == null) {
                Box {
                    OutlinedButton(onClick = { projectMenu = true }) {
                        Text("Проект: " + (w.project(projectId)?.name ?: "—"))
                    }
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        w.projects.filter { !it.archived }.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                projectId = p.id
                                projectMenu = false
                            })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(IssueType.TASK, IssueType.STORY, IssueType.BUG, IssueType.EPIC).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.title) }, leadingIcon = { TypeIcon(t, 14.dp) })
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Что нужно сделать?") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                minLines = 2,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { personMenu = true }) {
                        Avatar(w.person(assignee), 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(w.person(assignee)?.name ?: "Не назначено", maxLines = 1)
                    }
                    PersonMenu(personMenu, w.everyone, true, { personMenu = false }) {
                        assignee = it?.id
                        personMenu = false
                    }
                }
                OutlinedButton(onClick = { pickDate(ctx, due ?: vm.today) { due = it } }) {
                    Text(due?.let { "Срок: " + Fmt.dateNoYear(it) } ?: "Срок")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.title) }, leadingIcon = { PriorityIcon(p) })
                }
            }
            if (active != null && parent == null && type != IssueType.EPIC) {
                FilterChip(selected = toSprint, onClick = { toSprint = !toSprint }, label = { Text("Добавить в «${active.name}»") })
            }
            Button(
                onClick = {
                    if (create()) onDismiss()
                },
                enabled = (title.isNotBlank() || description.isNotBlank()) && projectId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Создать") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ======================= ВСЕ ЗАДАЧИ =======================

/** Все задачи из всех проектов: поиск, фильтр по статусу, смена статуса прямо из списка. */
@Composable
private fun AllIssuesView(vm: MainViewModel) {
    val w = vm.work
    var query by remember { mutableStateOf("") }
    var statuses by remember { mutableStateOf(setOf(Status.TODO, Status.IN_PROGRESS, Status.REVIEW)) }
    val all = w.issues.filter { it.type != IssueType.EPIC && (!vm.onlyMine || it.assigneeId == ME) }
    val q = query.trim().lowercase()
    val shown = all.filter { i ->
        i.status in statuses && (q.isEmpty() || i.title.lowercase().contains(q) || w.key(i).lowercase().contains(q) ||
            i.labels.any { it.lowercase().contains(q) })
    }
    val byProject = shown.groupBy { it.projectId }
    val projects = w.projects.filter { it.id in byProject.keys }.sortedBy { it.name }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск по названию, ключу, метке") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Status.entries.forEach { st ->
                    val n = all.count { it.status == st }
                    FilterChip(
                        selected = st in statuses,
                        onClick = { statuses = if (st in statuses) statuses - st else statuses + st },
                        label = { Text("${st.title} · $n") },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor(st))) },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (all.isEmpty()) "Задач пока нет — нажмите «Создать»." else "Ничего не найдено по фильтрам.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        projects.forEach { p ->
            val list = byProject[p.id].orEmpty().sortedWith(
                compareBy<Issue>({ it.status.ordinal }, { it.priority.ordinal }, { it.dueDate ?: LocalDate.MAX }, { it.number }),
            )
            item(key = "p_${p.id}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            vm.selectProject(p.id)
                            vm.selectTaskView("BOARD")
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Color(p.color)), contentAlignment = Alignment.Center) {
                        Text(p.key.take(1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { "a_${it.id}" }) { i -> AllIssueRow(vm, i) }
        }
    }
}

@Composable
private fun AllIssueRow(vm: MainViewModel, i: Issue) {
    val w = vm.work
    var statusMenu by remember { mutableStateOf(false) }
    Card(
        onClick = { vm.push(Screen.IssueDetail(i.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(i.type)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    i.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (i.done) TextDecoration.LineThrough else null,
                )
                val due = i.dueDate
                val overdue = due != null && !i.done && due.isBefore(vm.today)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.key(i), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    PriorityIcon(i.priority)
                    if (due != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Fmt.dueShort(due, vm.today),
                            fontSize = 12.sp,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                StatusPill(i.status) { statusMenu = true }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    Status.entries.forEach { st ->
                        DropdownMenuItem(
                            text = { StatusPill(st) },
                            onClick = {
                                statusMenu = false
                                vm.updateWork { WorkOps.setStatus(it, i.id, st) }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Avatar(w.person(i.assigneeId), 24.dp)
        }
    }
}
