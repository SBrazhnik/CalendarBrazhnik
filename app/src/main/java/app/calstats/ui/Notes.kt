@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.work.CheckItem
import app.calstats.work.Note
import app.calstats.work.NoteImport
import app.calstats.work.WorkOps
import app.calstats.work.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Цвета заметок (как в Keep, но спокойнее). */
private val NOTE_COLORS = listOf(
    0xFFF28B82, 0xFFFBBC04, 0xFFFFF475, 0xFFCCFF90, 0xFFA7FFEB, 0xFFCBF0F8, 0xFFAECBFA, 0xFFD7AEFB, 0xFFFDCFE8, 0xFFE6C9A8,
).map { it.toInt() }

/** Фон карточки заметки: в тёмной теме цвет приглушаем. */
@Composable
private fun noteBg(c: Int?): Color {
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    return when {
        c == null -> MaterialTheme.colorScheme.surfaceContainer
        dark -> Color(c).copy(alpha = 0.32f)
        else -> Color(c).copy(alpha = 0.55f)
    }
}

/** Модуль «Заметки». */
@Composable
fun NotesTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var archive by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }

    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val notes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { NoteImport.readKeepZip(it) } }.getOrNull()
            }
            if (notes == null) {
                vm.message = "Не удалось прочитать архив"
            } else {
                var added = 0
                vm.updateWork { w -> WorkOps.importNotes(w, notes).also { added = it.second }.first }
                vm.message = if (notes.isEmpty()) "В архиве не найдено заметок Google Keep" else "Импортировано заметок: $added"
            }
        }
    }

    val all = vm.work.notes.filter { it.archived == archive }
    val labels = vm.work.notes.flatMap { it.labels }.distinct().sorted()
    val q = query.trim().lowercase()
    val shown = all.filter { n ->
        (label == null || label in n.labels) && (q.isEmpty() || n.asText().lowercase().contains(q) || n.labels.any { it.lowercase().contains(q) })
    }.sortedByDescending { it.updated }
    val pinned = shown.filter { it.pinned }
    val others = shown.filter { !it.pinned }

    fun newNote(checklist: Boolean) {
        val n = Note(
            newId(),
            labels = listOfNotNull(label),
            isChecklist = checklist,
            checklist = if (checklist) listOf(CheckItem(newId(), "")) else emptyList(),
        )
        vm.updateWork { WorkOps.upsertNote(it, n) }
        vm.push(Screen.NoteEdit(n.id))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (search) {
                        OutlinedTextField(
                            query, { query = it }, placeholder = { Text("Поиск в заметках") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (archive) "Архив заметок" else "Заметки", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        search = !search
                        if (!search) query = ""
                    }) { Icon(if (search) Icons.Filled.Close else Icons.Filled.Search, "Поиск") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (archive) "Все заметки" else "Архив") }, onClick = {
                                menu = false
                                archive = !archive
                            })
                            DropdownMenuItem(text = { Text("Импорт из Google Keep (Takeout)") }, onClick = {
                                menu = false
                                importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text("Как перенести из Samsung, Keep, Яндекса") }, onClick = {
                                menu = false
                                help = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = { newNote(true) }) { Icon(Icons.AutoMirrored.Filled.List, "Новый список") }
                ExtendedFloatingActionButton(onClick = { newNote(false) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Заметка") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (labels.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = label == null, onClick = { label = null }, label = { Text("Все") })
                    labels.forEach { l -> FilterChip(selected = label == l, onClick = { label = if (label == l) null else l }, label = { Text(l) }) }
                }
            }
            if (shown.isEmpty()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            q.isNotEmpty() -> "Ничего не найдено"
                            archive -> "Архив пуст"
                            else -> "Заметок пока нет"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!archive && q.isEmpty()) {
                        Text(
                            "Создайте заметку или список. Из Samsung Notes, Google Keep и Яндекс Заметок можно прислать " +
                                "сюда текст через «Поделиться», а все заметки Keep — загрузить архивом.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { help = true }) { Text("Как перенести заметки") }
                    }
                }
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                if (pinned.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) { GridLabel("Закреплённые") }
                    items(pinned, key = { it.id }) { n -> NoteCard(n) { vm.push(Screen.NoteEdit(n.id)) } }
                    if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { GridLabel("Остальные") }
                }
                items(others, key = { it.id }) { n -> NoteCard(n) { vm.push(Screen.NoteEdit(n.id)) } }
            }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("Перенос заметок") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Samsung, Google и Яндекс не дают сторонним приложениям доступ к своим заметкам, " +
                            "поэтому автоматической синхронизации с ними нет. Переносить можно так:",
                    )
                    Text("• Одна заметка — откройте её в Samsung Notes, Keep или Яндексе → «Поделиться» → «Личный календарь» → «Заметка». Списки с галочками станут чек-листом.")
                    Text("• Все заметки Google Keep сразу — откройте takeout.google.com, выберите только «Google Keep», скачайте архив и выберите его здесь: меню ⋮ → «Импорт из Google Keep». Повторный импорт не создаёт дублей.")
                    Text("• Обратно — в заметке нажмите «Поделиться» и выберите нужное приложение.")
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Понятно") } },
        )
    }
}

@Composable
private fun GridLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(noteBg(n.color))
            .then(if (n.color == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (n.title.isNotBlank()) {
            Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        if (n.isChecklist) {
            val open = n.checklist.filter { !it.checked && it.text.isNotBlank() }
            open.take(6).forEach { c -> Text("☐ ${c.text}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (open.size > 6) Text("…ещё ${open.size - 6}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val done = n.checklist.count { it.checked }
            if (done > 0) Text("+ отмечено: $done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (n.body.isNotBlank()) {
            Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
        if (n.labels.isNotEmpty() || n.source.isNotBlank()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                n.labels.take(3).forEach { LabelChip(it) }
                if (n.source.isNotBlank()) {
                    Text(n.source, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private class CheckDraft(val id: String, text: String, checked: Boolean) {
    var text by mutableStateOf(text)
    var checked by mutableStateOf(checked)
}

/** Редактор заметки: сохраняется сам по ходу ввода и при выходе. */
@Composable
fun NoteEditScreen(vm: MainViewModel, id: String, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val original = vm.work.notes.firstOrNull { it.id == id }
    if (original == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { vm.pop() }) { Text("Заметка удалена — назад") } }
        return
    }
    var title by remember(id) { mutableStateOf(original.title) }
    var body by remember(id) { mutableStateOf(original.body) }
    var isChecklist by remember(id) { mutableStateOf(original.isChecklist) }
    val items = remember(id) { mutableStateListOf<CheckDraft>().apply { original.checklist.forEach { add(CheckDraft(it.id, it.text, it.checked)) } } }
    var color by remember(id) { mutableStateOf(original.color) }
    var pinned by remember(id) { mutableStateOf(original.pinned) }
    var archived by remember(id) { mutableStateOf(original.archived) }
    var labels by remember(id) { mutableStateOf(original.labels) }
    var menu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    fun build(): Note = original.copy(
        title = title,
        body = if (isChecklist) "" else body,
        isChecklist = isChecklist,
        checklist = if (isChecklist) items.map { CheckItem(it.id, it.text, it.checked) } else emptyList(),
        color = color,
        pinned = pinned,
        archived = archived,
        labels = labels,
    )
    val latest by rememberUpdatedState(build())

    fun persist(n: Note) {
        val cur = vm.work.notes.firstOrNull { it.id == id } ?: return
        if (cur.copy(updated = 0) != n.copy(updated = 0)) vm.updateWork { WorkOps.upsertNote(it, n.copy(updated = System.currentTimeMillis())) }
    }

    // Автосохранение через полсекунды после изменения
    LaunchedEffect(latest) {
        delay(500)
        if (!deleted) persist(latest)
    }
    // При выходе: пустую заметку удаляем, остальное сохраняем
    DisposableEffect(id) {
        onDispose {
            if (deleted) return@onDispose
            val n = build()
            if (n.isEmpty) vm.updateWork { WorkOps.deleteNote(it, id) } else persist(n)
        }
    }

    Scaffold(
        containerColor = noteBg(color).let { if (color == null) MaterialTheme.colorScheme.background else it },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {},
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(if (pinned) Icons.Filled.Star else Icons.Outlined.Star, if (pinned) "Открепить" else "Закрепить")
                    }
                    Box {
                        IconButton(onClick = { colorMenu = true }) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                        }
                        DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                            FlowRow(Modifier.padding(8.dp).width(220.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable {
                                        color = null
                                        colorMenu = false
                                    },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Close, "Без цвета", Modifier.size(16.dp)) }
                                NOTE_COLORS.forEach { c ->
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                            .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                            .clickable {
                                                color = c
                                                colorMenu = false
                                            },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { shareText(ctx, latest.asText(), latest.title.ifBlank { null }) }) { Icon(Icons.Filled.Share, "Поделиться") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (isChecklist) "Сделать обычным текстом" else "Сделать списком с галочками") }, onClick = {
                                menu = false
                                if (isChecklist) {
                                    body = items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
                                    items.clear()
                                } else {
                                    items.clear()
                                    body.lines().filter { it.isNotBlank() }.forEach { l ->
                                        val checked = l.trimStart().startsWith("☑")
                                        items.add(CheckDraft(newId(), l.trim().removePrefix("☑").removePrefix("☐").trim(), checked))
                                    }
                                    if (items.isEmpty()) items.add(CheckDraft(newId(), "", false))
                                    body = ""
                                }
                                isChecklist = !isChecklist
                            })
                            DropdownMenuItem(text = { Text("Метки") }, onClick = {
                                menu = false
                                labelDialog = true
                            })
                            DropdownMenuItem(text = { Text(if (archived) "Вернуть из архива" else "В архив") }, onClick = {
                                menu = false
                                archived = !archived
                                persist(build())
                                vm.message = if (archived) "Заметка в архиве" else "Заметка возвращена"
                                vm.pop()
                            })
                            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                menu = false
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
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                if (title.isEmpty()) Text("Заголовок", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isChecklist) {
                val open = items.filter { !it.checked }
                val done = items.filter { it.checked }
                open.forEach { d -> CheckRow(d, onDelete = { items.remove(d) }, onEnter = {
                    val idx = items.indexOf(d)
                    items.add(idx + 1, CheckDraft(newId(), "", false))
                }) }
                TextButton(onClick = { items.add(items.count { !it.checked }.coerceAtMost(items.size), CheckDraft(newId(), "", false)) }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Пункт")
                }
                if (done.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Отмечено: ${done.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    done.forEach { d -> CheckRow(d, onDelete = { items.remove(d) }, onEnter = {}) }
                }
            } else {
                Box {
                    if (body.isEmpty()) Text("Заметка", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { labels.forEach { LabelChip(it) } }
            }
            Text(
                "Изменено ${Fmt.date(Fmt.dayOfMillis(original.updated))}" + if (original.source.isNotBlank()) " · из «${original.source}»" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 40.dp),
            )
        }
    }

    if (labelDialog) {
        var text by remember { mutableStateOf("") }
        val known = vm.work.notes.flatMap { it.labels }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { labelDialog = false },
            title = { Text("Метки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (known + labels).distinct().forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = l in labels, onCheckedChange = { labels = if (it) labels + l else labels - l })
                            Text(l)
                        }
                    }
                    OutlinedTextField(text, { text = it }, label = { Text("Новая метка") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val l = text.trim()
                    if (l.isNotEmpty()) labels = (labels + l).distinct()
                    labelDialog = false
                }) { Text("Готово") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заметку?") },
            confirmButton = {
                TextButton(onClick = {
                    deleted = true
                    confirmDelete = false
                    vm.updateWork { WorkOps.deleteNote(it, id) }
                    vm.pop()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CheckRow(d: CheckDraft, onDelete: () -> Unit, onEnter: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = d.checked, onCheckedChange = { d.checked = it })
        BasicTextField(
            value = d.text,
            onValueChange = { v ->
                if (v.endsWith("\n")) {
                    d.text = v.trimEnd('\n')
                    onEnter()
                } else d.text = v
            },
            textStyle = TextStyle(
                color = if (d.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                textDecoration = if (d.checked) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onEnter() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Удалить пункт", Modifier.size(16.dp)) }
    }
}
