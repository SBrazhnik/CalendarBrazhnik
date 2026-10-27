@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.calstats.MainViewModel
import app.calstats.work.Group
import app.calstats.work.ME
import app.calstats.work.PALETTE
import app.calstats.work.Person
import app.calstats.work.Project
import app.calstats.work.WorkOps
import app.calstats.work.newId

/** Люди и группы: семья, коллеги, друзья — для задач и общих расходов. */
@Composable
fun PeopleScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var editPerson by remember { mutableStateOf<Person?>(null) }
    var editGroup by remember { mutableStateOf<Group?>(null) }
    var editMe by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Люди и группы") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Добавьте близких, коллег и друзей. Им можно назначать задачи и делить с ними расходы. " +
                        "Группы удобны, чтобы сразу добавить всех: «Семья», «Ремонт», «Работа».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionTitle("Группы") { editGroup = Group(newId(), "", emptyList(), PALETTE[w.groups.size % PALETTE.size]) } }
            if (w.groups.isEmpty()) item { Hint("Групп пока нет") }
            items(w.groups, key = { it.id }) { g ->
                Card(onClick = { editGroup = g }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(g.color)), contentAlignment = Alignment.Center) {
                            Text(g.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${g.memberIds.size} уч.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AvatarStack(g.memberIds.mapNotNull { w.person(it) })
                    }
                }
            }
            item { SectionTitle("Люди") { editPerson = Person(newId(), "", PALETTE[(w.people.size + 1) % PALETTE.size]) } }
            item {
                PersonCard(w.person(ME)!!, subtitle = "Это вы") { editMe = true }
            }
            items(w.people, key = { it.id }) { p ->
                val groups = w.groups.filter { p.id in it.memberIds }.joinToString(", ") { it.name }
                PersonCard(p, subtitle = groups.ifEmpty { p.note }) { editPerson = p }
            }
        }
    }

    editPerson?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        var note by remember(p.id) { mutableStateOf(p.note) }
        var color by remember(p.id) { mutableStateOf(p.color) }
        val exists = w.people.any { it.id == p.id }
        AlertDialog(
            onDismissRequest = { editPerson = null },
            title = { Text(if (exists) "Человек" else "Новый человек") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Имя") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    OutlinedTextField(note, { note = it }, label = { Text("Кто это (необязательно)") }, singleLine = true)
                    ColorPicker(color) { color = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.updateWork { WorkOps.upsertPerson(it, p.copy(name = name.trim(), note = note.trim(), color = color)) }
                    editPerson = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    if (exists) {
                        TextButton(onClick = {
                            vm.updateWork { WorkOps.deletePerson(it, p.id) }
                            editPerson = null
                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editPerson = null }) { Text("Отмена") }
                }
            },
        )
    }

    editGroup?.let { g ->
        var name by remember(g.id) { mutableStateOf(g.name) }
        var members by remember(g.id) { mutableStateOf(g.memberIds.toSet()) }
        var color by remember(g.id) { mutableStateOf(g.color) }
        val exists = w.groups.any { it.id == g.id }
        AlertDialog(
            onDismissRequest = { editGroup = null },
            title = { Text(if (exists) "Группа" else "Новая группа") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Название") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    ColorPicker(color) { color = it }
                    Text("Участники", style = MaterialTheme.typography.titleSmall)
                    w.everyone.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { members = if (p.id in members) members - p.id else members + p.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = p.id in members, onCheckedChange = { members = if (it) members + p.id else members - p.id })
                            Avatar(p, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p.name)
                        }
                    }
                    if (w.people.isEmpty()) {
                        Text(
                            "Сначала добавьте людей в разделе «Люди».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.updateWork { WorkOps.upsertGroup(it, g.copy(name = name.trim(), memberIds = w.everyone.map { p -> p.id }.filter { id -> id in members }, color = color)) }
                    editGroup = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    if (exists) {
                        TextButton(onClick = {
                            vm.updateWork { WorkOps.deleteGroup(it, g.id) }
                            editGroup = null
                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editGroup = null }) { Text("Отмена") }
                }
            },
        )
    }

    if (editMe) {
        var name by remember { mutableStateOf(w.meName) }
        AlertDialog(
            onDismissRequest = { editMe = false },
            title = { Text("Ваше имя") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork { it.copy(meName = name.trim().ifEmpty { "Я" }) }
                    editMe = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editMe = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Добавить") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PersonCard(p: Person, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Несколько аватаров внахлёст. */
@Composable
fun AvatarStack(people: List<Person>, size: androidx.compose.ui.unit.Dp = 26.dp, max: Int = 4) {
    Row {
        people.take(max).forEachIndexed { i, p ->
            Box(
                Modifier
                    .offset(x = (-8 * i).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) { Avatar(p, size) }
        }
        if (people.size > max) {
            Text(
                "+${people.size - max}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.offset(x = (-8 * max + 4).dp).align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { c ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** Создание и настройка проекта. */
@Composable
fun ProjectEditScreen(vm: MainViewModel, id: String?, snackbar: SnackbarHostState) {
    val w = vm.work
    val existing = w.project(id)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var keyTouched by remember { mutableStateOf(existing != null) }
    var color by remember { mutableStateOf(existing?.color ?: PALETTE[w.projects.size % PALETTE.size]) }
    var groupId by remember { mutableStateOf(existing?.groupId) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Новый проект" else "Проект") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (!keyTouched) key = WorkOps.suggestKey(it)
                },
                label = { Text("Название проекта") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    keyTouched = true
                    key = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6)
                },
                label = { Text("Ключ (префикс задач, например ДОМ-12)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Цвет", style = MaterialTheme.typography.titleSmall)
            ColorPicker(color) { color = it }
            Text("Группа (кто участвует)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("Без группы") })
                w.groups.forEach { g ->
                    androidx.compose.material3.FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                }
            }
            if (w.groups.isEmpty()) {
                OutlinedButton(onClick = { vm.push(app.calstats.Screen.People) }) { Text("Создать группу") }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = name.isNotBlank() && key.isNotBlank(),
                onClick = {
                    val p = (existing ?: Project(newId(), key, name, color)).copy(
                        name = name.trim(), key = key.trim(), color = color, groupId = groupId, description = description.trim(),
                    )
                    vm.updateWork { WorkOps.upsertProject(it, p) }
                    vm.selectProject(p.id)
                    vm.pop()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (existing == null) "Создать проект" else "Сохранить") }
        }
    }

    if (confirmDelete && existing != null) {
        val count = w.issues.count { it.projectId == existing.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${existing.name}»?") },
            text = { Text("Будут удалены задачи проекта ($count) и спринты.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork { WorkOps.deleteProject(it, existing.id) }
                    vm.selectProject(null)
                    confirmDelete = false
                    vm.pop()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
