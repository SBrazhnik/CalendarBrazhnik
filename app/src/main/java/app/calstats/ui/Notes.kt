@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.calstats.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.RecorderService
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.work.CheckItem
import app.calstats.work.Issue
import app.calstats.work.ME
import app.calstats.work.Note
import app.calstats.work.NoteFile
import app.calstats.work.NoteFolder
import app.calstats.work.NoteImport
import app.calstats.work.NoteRemind
import app.calstats.work.NoteRepeat
import app.calstats.work.PALETTE
import app.calstats.work.Project
import app.calstats.work.QuickParse
import app.calstats.work.WorkOps
import app.calstats.work.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Фон карточки заметки: в тёмной теме цвет приглушаем. */
@Composable
private fun noteBg(c: Int?): Color {
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    return when {
        c == null -> MaterialTheme.colorScheme.surfaceContainer
        dark -> Color(c).copy(alpha = 0.30f)
        else -> Color(c).copy(alpha = 0.35f)
    }
}

private val RU = Locale.forLanguageTag("ru")
private fun zdt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun ms(d: LocalDateTime) = d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun remindText(ms: Long, today: LocalDate = LocalDate.now()): String {
    val z = zdt(ms)
    return Fmt.dayTitle(z.toLocalDate(), today) + ", " + z.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun noteTitle(n: Note) = n.title.ifBlank { n.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("☐ ")?.removePrefix("• ").orEmpty() }.ifBlank { "Без названия" }

/** Модуль «Заметки»: папки, напоминания, оформление, вложения, запись совещаний. */
@Composable
fun NotesTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folder by navView(vm, "notesFolder", "ALL")
    var archive by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var quickReminder by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val w = vm.work

    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val notes: List<Note>? = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openInputStream(uri)?.use { NoteImport.readKeepZip(it) } } catch (e: Exception) { null }
            }
            if (notes == null) vm.message = "Не удалось прочитать архив"
            else {
                var added = 0
                vm.updateWork { ww -> val r = WorkOps.importNotes(ww, notes); added = r.second; r.first }
                vm.message = if (notes.isEmpty()) "В архиве не найдено заметок Google Keep" else "Импортировано заметок: $added"
            }
        }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) newRecordingNote(vm, ctx) else vm.message = "Без доступа к микрофону запись невозможна"
    }

    val folders = w.noteFolders.sortedBy { it.order }
    val reminders = folder == "REM"
    val q = query.trim().lowercase()
    val shown = w.notes.filter { it.archived == archive }.filter { n ->
        (folder == "ALL" || folder == "REM" || n.folderId == folder) &&
            (q.isEmpty() || n.asText().lowercase().contains(q) || n.labels.any { it.lowercase().contains(q) } || n.files.any { it.name.lowercase().contains(q) })
    }.sortedByDescending { it.updated }

    fun newNote(checklist: Boolean) {
        val n = Note(newId(), folderId = folder.takeIf { it != "ALL" && it != "REM" }, isChecklist = checklist,
            checklist = if (checklist) listOf(CheckItem(newId(), "")) else emptyList())
        vm.updateWork { WorkOps.upsertNote(it, n) }
        vm.push(Screen.NoteEdit(n.id))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (search) OutlinedTextField(query, { query = it }, placeholder = { Text("Поиск в заметках") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    else Text(if (archive) "Архив заметок" else "Заметки", style = MaterialTheme.typography.headlineSmall)
                },
                actions = {
                    IconButton(onClick = { search = !search; if (!search) query = "" }) { Icon(if (search) Icons.Filled.Close else Icons.Filled.Search, "Поиск") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Новая папка") }, onClick = { menu = false; editFolder = NoteFolder(newId(), "", order = folders.size) })
                            DropdownMenuItem(text = { Text(if (archive) "Все заметки" else "Архив") }, onClick = { menu = false; archive = !archive })
                            DropdownMenuItem(text = { Text("Импорт из Google Keep (Takeout)") }, onClick = {
                                menu = false; importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text("Как перенести из Samsung, Keep, Яндекса") }, onClick = { menu = false; help = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!reminders) {
                    SmallFloatingActionButton(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) newRecordingNote(vm, ctx)
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("🎙") }
                    SmallFloatingActionButton(onClick = { newNote(true) }) { Icon(Icons.AutoMirrored.Filled.List, "Новый список") }
                    ExtendedFloatingActionButton(onClick = { newNote(false) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Заметка") })
                } else ExtendedFloatingActionButton(onClick = { quickReminder = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Напоминание") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Папки — как в Samsung Notes: цветные, долгое нажатие — изменить
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FolderChip("Все", null, folder == "ALL", w.notes.count { !it.archived }) { folder = "ALL" }
                FolderChip("⏰ Напоминания", null, reminders, w.notes.count { it.remindAt != null && !it.remindDone && !it.archived }) { folder = "REM" }
                folders.forEach { f ->
                    FolderChip(f.name, f.color, folder == f.id, w.notes.count { it.folderId == f.id && !it.archived }, onLong = { editFolder = f }) { folder = f.id }
                }
                FolderChip("+ Папка", null, false, null) { editFolder = NoteFolder(newId(), "", order = folders.size) }
            }
            if (reminders) {
                val active = shown.filter { it.remindAt != null && !it.remindDone }.sortedBy { it.remindAt }
                val done = shown.filter { it.remindAt != null && it.remindDone }.sortedByDescending { it.updated }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (active.isEmpty()) item {
                        Text("Напоминаний нет. Нажмите «Напоминание» или поставьте ⏰ в любой заметке — по итогам совещания удобно сделать по напоминанию на каждое поручение.",
                            Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val now = System.currentTimeMillis()
                    val groups = listOf(
                        "Просрочено" to active.filter { it.remindAt!! < now },
                        "Сегодня" to active.filter { it.remindAt!! >= now && zdt(it.remindAt!!).toLocalDate() == vm.today },
                        "Завтра" to active.filter { zdt(it.remindAt!!).toLocalDate() == vm.today.plusDays(1) },
                        "Позже" to active.filter { zdt(it.remindAt!!).toLocalDate().isAfter(vm.today.plusDays(1)) },
                    )
                    groups.forEach { (t, l) ->
                        if (l.isNotEmpty()) {
                            item { GridLabel("$t · ${l.size}") }
                            items(l, key = { it.id }) { n -> ReminderRow(vm, n) }
                        }
                    }
                    if (done.isNotEmpty()) item {
                        TextButton(onClick = { showDone = !showDone }) { Text((if (showDone) "Скрыть" else "Показать") + " выполненные · ${done.size}") }
                    }
                    if (showDone) items(done, key = { "d" + it.id }) { n -> ReminderRow(vm, n) }
                }
            } else {
                if (shown.isEmpty()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when { q.isNotEmpty() -> "Ничего не найдено"; archive -> "Архив пуст"; else -> "Заметок пока нет" }, style = MaterialTheme.typography.titleLarge)
                        if (!archive && q.isEmpty()) {
                            Text("Создайте заметку, список или 🎙 запись совещания. Заметки можно раскладывать по цветным папкам, оформлять, прикладывать фото и файлы, ставить напоминания.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { help = true }) { Text("Как перенести заметки из других приложений") }
                        }
                    }
                }
                val pinned = shown.filter { it.pinned }
                val others = shown.filter { !it.pinned }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp,
                ) {
                    if (pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) { GridLabel("Закреплённые") }
                        items(pinned.size, key = { pinned[it].id }) { i -> NoteCard(vm, pinned[i]) { vm.push(Screen.NoteEdit(pinned[i].id)) } }
                        if (others.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { GridLabel("Остальные") }
                    }
                    items(others.size, key = { others[it].id }) { i -> NoteCard(vm, others[i]) { vm.push(Screen.NoteEdit(others[i].id)) } }
                }
            }
        }
    }

    editFolder?.let { f -> FolderDialog(vm, f, onDeleted = { if (folder == f.id) folder = "ALL" }) { editFolder = null } }
    if (quickReminder) ReminderDialog(vm, null, onDismiss = { quickReminder = false })
    if (help) AlertDialog(
        onDismissRequest = { help = false },
        title = { Text("Перенос заметок") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Samsung, Google и Яндекс не дают сторонним приложениям доступ к своим заметкам. Переносить можно так:")
                Text("• Одна заметка — в Samsung Notes, Keep или Яндексе → «Поделиться» → «Зелёный ритм» → «Заметка».")
                Text("• Все заметки Google Keep — takeout.google.com → только «Google Keep» → архив выберите здесь: ⋮ → «Импорт из Google Keep».")
                Text("• Обратно — в заметке «Поделиться».")
            }
        },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Понятно") } },
    )
}

private fun newRecordingNote(vm: MainViewModel, ctx: Context) {
    val n = Note(newId(), title = "Совещание " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", RU)))
    vm.updateWork { WorkOps.upsertNote(it, n) }
    vm.push(Screen.NoteEdit(n.id))
    RecorderService.start(ctx, n.id)
}

@Composable
private fun FolderChip(name: String, color: Int?, selected: Boolean, count: Int?, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(color))); Spacer(Modifier.width(6.dp)) }
        val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        Text(name, style = MaterialTheme.typography.labelLarge, color = fg)
        if (count != null && count > 0) Text("  $count", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.6f))
    }
}

@Composable
private fun FolderDialog(vm: MainViewModel, f: NoteFolder, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    val exists = vm.work.noteFolders.any { it.id == f.id }
    var name by remember { mutableStateOf(f.name) }
    var color by remember { mutableStateOf(f.color) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Папка" else "Новая папка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Text("Цвет", style = MaterialTheme.typography.labelLarge)
                ColorPalette(color ?: 0) { color = it }
                if (color != null) TextButton(onClick = { color = null }) { Text("Без цвета") }
                if (exists) {
                    Row {
                        TextButton(onClick = { vm.updateWork { w -> val l = w.noteFolders.sortedBy { it.order }.toMutableList(); val i = l.indexOfFirst { it.id == f.id }; if (i > 0) { val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t }; w.copy(noteFolders = l.mapIndexed { k, x -> x.copy(order = k) }) } }) { Text("← Левее") }
                        TextButton(onClick = { vm.updateWork { w -> val l = w.noteFolders.sortedBy { it.order }.toMutableList(); val i = l.indexOfFirst { it.id == f.id }; if (i in 0 until l.lastIndex) { val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t }; w.copy(noteFolders = l.mapIndexed { k, x -> x.copy(order = k) }) } }) { Text("Правее →") }
                    }
                    TextButton(onClick = { confirmDel = true }) { Text("Удалить папку", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val nf = f.copy(name = name.trim(), color = color)
                vm.updateWork { w -> w.copy(noteFolders = if (exists) w.noteFolders.map { if (it.id == f.id) nf else it } else w.noteFolders + nf) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (confirmDel) AlertDialog(
        onDismissRequest = { confirmDel = false },
        title = { Text("Удалить папку «${f.name}»?") },
        text = { Text("Заметки из неё не удалятся — останутся во «Все».") },
        confirmButton = { TextButton(onClick = {
            vm.updateWork { w -> w.copy(noteFolders = w.noteFolders.filter { it.id != f.id }, notes = w.notes.map { if (it.folderId == f.id) it.copy(folderId = null) else it }) }
            confirmDel = false; onDeleted(); onDismiss()
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Отмена") } },
    )
}

@Composable
private fun ReminderRow(vm: MainViewModel, n: Note) {
    val over = n.remindAt != null && !n.remindDone && n.remindAt < System.currentTimeMillis()
    val folder = vm.work.noteFolders.firstOrNull { it.id == n.folderId }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(noteBg(n.color)).clickable { vm.push(Screen.NoteEdit(n.id)) }.padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(n.remindDone, { vm.updateWork { w -> w.copy(notes = w.notes.map { if (it.id == n.id) (if (n.remindDone) it.copy(remindDone = false) else NoteRemind.complete(it)).copy(updated = System.currentTimeMillis()) else it }) } })
        Column(Modifier.weight(1f)) {
            Text(noteTitle(n), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (n.remindDone) TextDecoration.LineThrough else null)
            Text(
                listOfNotNull(n.remindAt?.let { remindText(it, vm.today) }, n.repeat.takeIf { it != NoteRepeat.NONE }?.let { "🔁 " + it.title.lowercase() },
                    if (n.toCalendar) "📅" else null, folder?.name).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GridLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun NoteCard(vm: MainViewModel, n: Note, onClick: () -> Unit) {
    val folder = vm.work.noteFolders.firstOrNull { it.id == n.folderId }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(noteBg(n.color))
            .then(if (n.color == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        if (n.title.isNotBlank()) {
            Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        n.files.firstOrNull { it.isImage }?.let { img ->
            NoteThumb(vm, img, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(6.dp))
        }
        if (n.isChecklist) {
            val open = n.checklist.filter { !it.checked && it.text.isNotBlank() }
            open.take(6).forEach { c -> Text("☐ ${c.text}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (open.size > 6) Text("…ещё ${open.size - 6}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (n.body.isNotBlank()) {
            Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 9, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        }
        val meta = listOfNotNull(
            n.remindAt?.takeIf { !n.remindDone }?.let { "⏰ " + remindText(it, vm.today) },
            n.files.count { it.isAudio }.takeIf { it > 0 }?.let { "🎙 $it" },
            n.files.count { !it.isAudio && !it.isImage }.takeIf { it > 0 }?.let { "📎 $it" },
        )
        if (meta.isNotEmpty() || folder != null || n.labels.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                folder?.let { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        f.color?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(it))); Spacer(Modifier.width(4.dp)) }
                        Text(f.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                meta.forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                n.labels.take(2).forEach { LabelChip(it) }
            }
        }
    }
}

@Composable
private fun NoteThumb(vm: MainViewModel, f: NoteFile, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, f.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(ctx.filesDir, f.path)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var s = 1; while (o.outWidth / s > 600) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("🖼")
    }
}

private class CheckDraft(val id: String, text: String, checked: Boolean) {
    var text by mutableStateOf(text)
    var checked by mutableStateOf(checked)
}

// ======================= РЕДАКТОР =======================

/** Редактор заметки: сохраняется сам по ходу ввода и при выходе. */
@Composable
fun NoteEditScreen(vm: MainViewModel, id: String, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val original = vm.work.notes.firstOrNull { it.id == id }
    if (original == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { vm.pop() }) { Text("Заметка удалена — назад") } }
        return
    }
    var title by remember(id) { mutableStateOf(original.title) }
    var body by remember(id) { mutableStateOf(original.body) }
    var rich by remember(id) { mutableStateOf(original.rich) }
    var isChecklist by remember(id) { mutableStateOf(original.isChecklist) }
    val items = remember(id) { mutableStateListOf<CheckDraft>().apply { original.checklist.forEach { add(CheckDraft(it.id, it.text, it.checked)) } } }
    var color by remember(id) { mutableStateOf(original.color) }
    var pinned by remember(id) { mutableStateOf(original.pinned) }
    var archived by remember(id) { mutableStateOf(original.archived) }
    var labels by remember(id) { mutableStateOf(original.labels) }
    var folderId by remember(id) { mutableStateOf(original.folderId) }
    var menu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(false) }
    var smart by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val rs = remember(id) { RichState() }

    fun build(): Note {
        val cur = vm.work.notes.firstOrNull { it.id == id } ?: original
        return cur.copy(
            title = title, body = if (isChecklist) "" else body, rich = if (isChecklist) "" else rich, isChecklist = isChecklist,
            checklist = if (isChecklist) items.map { CheckItem(it.id, it.text, it.checked) } else emptyList(),
            color = color, pinned = pinned, archived = archived, labels = labels, folderId = folderId,
        )
    }
    val latest by rememberUpdatedState(build())
    fun persist(n: Note) {
        val cur = vm.work.notes.firstOrNull { it.id == id } ?: return
        if (cur.copy(updated = 0) != n.copy(updated = 0)) vm.updateWork { WorkOps.upsertNote(it, n.copy(updated = System.currentTimeMillis())) }
    }
    fun updateNote(f: (Note) -> Note) { persist(build()); vm.updateWork { w -> w.copy(notes = w.notes.map { if (it.id == id) f(it).copy(updated = System.currentTimeMillis()) else it }) } }

    LaunchedEffect(latest) { delay(500); if (!deleted) persist(latest) }
    DisposableEffect(id) {
        onDispose {
            if (deleted) return@onDispose
            val n = build()
            if (n.isEmpty && RecorderService.noteId != id) vm.updateWork { WorkOps.deleteNote(it, id) } else persist(n)
        }
    }

    // ---- вложения ----
    fun addFiles(uris: List<Uri>) = scope.launch {
        val added = withContext(Dispatchers.IO) { uris.mapNotNull { copyIn(ctx, id, it) } }
        if (added.isNotEmpty()) updateNote { it.copy(files = it.files + added) }
        if (added.size < uris.size) vm.message = "Не все файлы удалось добавить"
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addFiles(it) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { addFiles(it) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile ?: return@rememberLauncherForActivityResult
        if (ok && f.length() > 0) updateNote { it.copy(files = it.files + NoteFile(newId(), "Фото " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM HH:mm", RU)) + ".jpg", "image/jpeg", f.relativeTo(ctx.filesDir).path, f.length())) }
        else f.delete()
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) RecorderService.start(ctx, id) else vm.message = "Нужен доступ к микрофону" }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { val f = File(RecorderService.dir(ctx, id), "photo-${System.currentTimeMillis()}.jpg"); cameraFile = f; camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)) }
    }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { t ->
            if (isChecklist) items.add(CheckDraft(newId(), t, false)) else rs.insert(t.replaceFirstChar { it.uppercase() } + " ")
        }
    }
    fun dictate() {
        try {
            dictation.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU").putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите — текст появится в заметке"))
        } catch (_: ActivityNotFoundException) { vm.message = "Нет распознавания речи — установите приложение Google" }
    }
    fun record() {
        if (RecorderService.noteId == id) { RecorderService.stop(ctx); return }
        if (RecorderService.noteId != null) { vm.message = "Уже идёт запись в другой заметке"; return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) RecorderService.start(ctx, id)
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    val note = vm.work.notes.firstOrNull { it.id == id } ?: original
    Scaffold(
        containerColor = if (color == null) MaterialTheme.colorScheme.background else noteBg(color),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {},
                actions = {
                    IconButton(onClick = { reminder = true }) { Text("⏰", fontSize = 20.sp) }
                    Box {
                        IconButton(onClick = { attachMenu = true }) { Text("📎", fontSize = 20.sp) }
                        DropdownMenu(attachMenu, { attachMenu = false }) {
                            DropdownMenuItem(text = { Text("📷 Снять фото") }, onClick = { attachMenu = false; camPerm.launch(Manifest.permission.CAMERA) })
                            DropdownMenuItem(text = { Text("🖼 Фото из галереи") }, onClick = { attachMenu = false; pickPhotos.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
                            DropdownMenuItem(text = { Text("📄 Файл: PDF, документ, аудио…") }, onClick = { attachMenu = false; pickFiles.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text(if (RecorderService.noteId == id) "⏹ Остановить запись" else "🎙 Записать совещание") }, onClick = { attachMenu = false; record() })
                            DropdownMenuItem(text = { Text("🗣 Надиктовать текст") }, onClick = { attachMenu = false; dictate() })
                        }
                    }
                    IconButton(onClick = { pinned = !pinned }) { Icon(if (pinned) Icons.Filled.Star else Icons.Outlined.Star, if (pinned) "Открепить" else "Закрепить") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("✨ Умные функции") }, onClick = { menu = false; smart = true })
                            DropdownMenuItem(text = { Text("🎨 Цвет заметки") }, onClick = { menu = false; colorDialog = true })
                            DropdownMenuItem(text = { Text("📁 Папка") }, onClick = { menu = false; folderDialog = true })
                            DropdownMenuItem(text = { Text("🏷 Метки") }, onClick = { menu = false; labelDialog = true })
                            DropdownMenuItem(text = { Text(if (isChecklist) "Сделать обычным текстом" else "Сделать списком с галочками") }, onClick = {
                                menu = false
                                if (isChecklist) {
                                    body = items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }; rich = ""
                                    items.clear()
                                } else {
                                    items.clear()
                                    body.lines().filter { it.isNotBlank() }.forEach { l ->
                                        items.add(CheckDraft(newId(), l.trim().removePrefix("☑").removePrefix("☐").removePrefix("•").trim(), l.trimStart().startsWith("☑")))
                                    }
                                    if (items.isEmpty()) items.add(CheckDraft(newId(), "", false))
                                    body = ""; rich = ""
                                }
                                isChecklist = !isChecklist
                            })
                            DropdownMenuItem(text = { Text("Поделиться") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; shareText(ctx, latest.asText(), latest.title.ifBlank { null }) })
                            DropdownMenuItem(text = { Text(if (archived) "Вернуть из архива" else "В архив") }, onClick = {
                                menu = false; archived = !archived; persist(build())
                                vm.message = if (archived) "Заметка в архиве" else "Заметка возвращена"; vm.pop()
                            })
                            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (RecorderService.noteId == id) RecordingBanner { RecorderService.stop(ctx) }
                Box {
                    if (title.isEmpty()) Text("Заголовок", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = title, onValueChange = { title = it },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Напоминание и папка — чипами под заголовком
                val folder = vm.work.noteFolders.firstOrNull { it.id == folderId }
                if (note.remindAt != null || folder != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.remindAt?.let { at ->
                        FilterChip(selected = !note.remindDone, onClick = { reminder = true }, label = {
                            Text((if (note.remindDone) "✓ " else "⏰ ") + remindText(at, vm.today) + (if (note.repeat != NoteRepeat.NONE) " 🔁" else "") + if (note.toCalendar) " 📅" else "")
                        })
                    }
                    folder?.let { f -> FilterChip(selected = false, onClick = { folderDialog = true }, label = { Text(f.name) },
                        leadingIcon = f.color?.let { c -> { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c))) } }) }
                }
                if (isChecklist) {
                    val open = items.filter { !it.checked }
                    val done = items.filter { it.checked }
                    open.forEach { d -> CheckRow(d, onDelete = { items.remove(d) }, onEnter = { items.add(items.indexOf(d) + 1, CheckDraft(newId(), "", false)) }) }
                    TextButton(onClick = { items.add(items.count { !it.checked }.coerceAtMost(items.size), CheckDraft(newId(), "", false)) }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Пункт")
                    }
                    if (done.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Отмечено: ${done.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        done.forEach { d -> CheckRow(d, onDelete = { items.remove(d) }, onEnter = {}) }
                    }
                } else {
                    val fg = MaterialTheme.colorScheme.onSurface.toArgb()
                    val hint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                    val accent = MaterialTheme.colorScheme.primary.toArgb()
                    AndroidView(
                        factory = { c ->
                            RichEditText(c, rs) { t, r -> body = t; rich = r }.also { e ->
                                rs.view = e
                                e.setTextColor(fg); e.setHintTextColor(hint); e.hint = "Заметка"
                                e.highlightColor = (accent and 0x00FFFFFF) or 0x44000000
                                runCatching { e.typeface = androidx.core.content.res.ResourcesCompat.getFont(c, app.calstats.R.font.inter) }
                                e.load(body, rich)
                                e.minHeight = (160 * c.resources.displayMetrics.density).toInt()
                            }
                        },
                        update = { e -> e.setTextColor(fg) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (note.files.isNotEmpty()) Attachments(vm, note) { f ->
                    updateNote { it.copy(files = it.files.filter { x -> x.id != f.id }) }
                    File(ctx.filesDir, f.path).delete()
                }
                if (labels.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { labels.forEach { LabelChip(it) } }
                Text(
                    "Изменено ${Fmt.date(Fmt.dayOfMillis(original.updated))}" + if (original.source.isNotBlank()) " · из «${original.source}»" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 40.dp),
                )
            }
            if (rs.focused && !isChecklist) FormatBar(rs, onDictate = { dictate() })
        }
    }

    if (reminder) ReminderDialog(vm, note) { reminder = false }
    if (smart) SmartDialog(vm, id, rs, { title }, { body }, onReplace = { t -> rs.view?.load(t, ""); body = t; rich = "" }) { smart = false }
    if (colorDialog) AlertDialog(
        onDismissRequest = { colorDialog = false }, title = { Text("Цвет заметки") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ColorPalette(color ?: 0) { color = it; colorDialog = false }; TextButton(onClick = { color = null; colorDialog = false }) { Text("Без цвета") } } },
        confirmButton = { TextButton(onClick = { colorDialog = false }) { Text("Готово") } },
    )
    if (folderDialog) AlertDialog(
        onDismissRequest = { folderDialog = false }, title = { Text("Папка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clickable { folderId = null; folderDialog = false }.padding(vertical = 10.dp)) { Text("Без папки") }
                vm.work.noteFolders.sortedBy { it.order }.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { folderId = f.id; folderDialog = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(f.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant))
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, fontWeight = if (f.id == folderId) FontWeight.Bold else null)
                    }
                }
                var newName by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), placeholder = { Text("Новая папка") }, singleLine = true)
                    TextButton(enabled = newName.isNotBlank(), onClick = {
                        val f = NoteFolder(newId(), newName.trim(), PALETTE[vm.work.noteFolders.size % PALETTE.size], vm.work.noteFolders.size)
                        vm.updateWork { it.copy(noteFolders = it.noteFolders + f) }; folderId = f.id; folderDialog = false
                    }) { Text("Создать") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { folderDialog = false }) { Text("Закрыть") } },
    )
    if (labelDialog) {
        var text by remember { mutableStateOf("") }
        val known = vm.work.notes.flatMap { it.labels }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { labelDialog = false }, title = { Text("Метки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (known + labels).distinct().forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = l in labels, onCheckedChange = { labels = if (it) labels + l else labels - l }); Text(l) }
                    }
                    OutlinedTextField(text, { text = it }, label = { Text("Новая метка") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { text.trim().takeIf { it.isNotEmpty() }?.let { labels = (labels + it).distinct() }; labelDialog = false }) { Text("Готово") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить заметку?") },
        text = { Text("Её можно будет вернуть из корзины в течение 30 дней.") },
        confirmButton = { TextButton(onClick = { deleted = true; confirmDelete = false; vm.updateWork { WorkOps.deleteNote(it, id) }; vm.pop() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
}

/** Скопировать выбранный файл к заметке. */
private fun copyIn(ctx: Context, noteId: String, uri: Uri): NoteFile? = runCatching {
    val r = ctx.contentResolver
    var name = "файл"; var size = 0L
    r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1) }
    }
    val mime = r.getType(uri) ?: "application/octet-stream"
    val fid = newId()
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeLast(80)
    val f = File(RecorderService.dir(ctx, noteId), "$fid-$safe")
    r.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: return null
    var dur = 0L
    if (mime.startsWith("audio/") || mime.startsWith("video/")) dur = runCatching {
        val m = android.media.MediaMetadataRetriever()
        try { m.setDataSource(f.path); m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { m.release() }
    }.getOrDefault(0L)
    NoteFile(fid, name, mime, f.relativeTo(ctx.filesDir).path, if (size > 0) size else f.length(), dur)
}.getOrNull()

private fun openFile(ctx: Context, f: NoteFile) {
    val file = File(ctx.filesDir, f.path)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
    }
}

private fun fmtDur(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
private fun fmtSize(b: Long) = when { b >= 1_048_576 -> "%.1f МБ".format(b / 1_048_576.0); b >= 1024 -> "${b / 1024} КБ"; else -> "$b Б" }

@Composable
private fun RecordingBanner(onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = SystemClock.elapsedRealtime() } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(10.dp))
        Text("Идёт запись  " + fmtDur(now - RecorderService.startedAt), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onStop) { Text("Стоп") }
    }
}

@Composable
private fun Attachments(vm: MainViewModel, n: Note, onDelete: (NoteFile) -> Unit) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf<NoteFile?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val imgs = n.files.filter { it.isImage }
        if (imgs.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            imgs.forEach { f -> Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { openFile(ctx, f) }, onLongClick = { ask = f })) { NoteThumb(vm, f, Modifier.fillMaxSize()) } }
        }
        n.files.filter { it.isAudio }.forEach { f -> AudioRow(f, onLong = { ask = f }) }
        n.files.filter { !it.isImage && !it.isAudio }.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .combinedClickable(onClick = { openFile(ctx, f) }, onLongClick = { ask = f }).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.emoji, fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtSize(f.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    ask?.let { f ->
        AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(f.name) },
            text = { Text(fmtSize(f.size) + if (f.durationMs > 0) " · " + fmtDur(f.durationMs) else "") },
            confirmButton = { TextButton(onClick = { openFile(ctx, f); ask = null }) { Text("Открыть") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(ctx.filesDir, f.path))
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
                        ask = null
                    }) { Text("Отправить") }
                    TextButton(onClick = { onDelete(f); ask = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

@Composable
private fun AudioRow(f: NoteFile, onLong: () -> Unit) {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    DisposableEffect(f.id) { onDispose { player?.release() } }
    LaunchedEffect(playing) { while (playing) { posMs = player?.currentPosition?.toLong() ?: 0; delay(300) } }
    val total = if (f.durationMs > 0) f.durationMs else player?.duration?.toLong() ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).combinedClickable(onClick = {}, onLongClick = onLong).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp).clickable {
                val p = player ?: MediaPlayer().also { mp ->
                    runCatching { mp.setDataSource(File(ctx.filesDir, f.path).path); mp.prepare() }.onFailure { return@clickable }
                    mp.setOnCompletionListener { playing = false; posMs = 0 }
                    player = mp
                }
                if (playing) { p.pause(); playing = false } else { runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }; p.start(); playing = true }
            }) { Box(contentAlignment = Alignment.Center) { Text(if (playing) "⏸" else "▶", color = MaterialTheme.colorScheme.onPrimary) } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmtDur(posMs) + " / " + fmtDur(total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                player?.let { p -> if (playing) runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) } }
            }) { Text("${speed}×".replace(".0×", "×")) }
        }
        if (total > 0) androidx.compose.material3.Slider(
            value = (posMs.toFloat() / total).coerceIn(0f, 1f),
            onValueChange = { v -> posMs = (v * total).toLong(); player?.seekTo(posMs.toInt()) },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
    }
}

/** Панель над клавиатурой, как в Samsung Notes. */
@Composable
private fun FormatBar(rs: RichState, onDictate: () -> Unit) {
    var sizeMenu by remember { mutableStateOf(false) }
    var colorFor by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Btn(label: String, active: Boolean = false, enabled: Boolean = true, style: TextStyle = TextStyle(), onClick: () -> Unit) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable(enabled = enabled, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 18.sp, style = style,
                        color = when { !enabled -> MaterialTheme.colorScheme.outline; active -> MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.onSurface })
                }
            }
            Btn("↶", enabled = rs.canUndo) { rs.undo() }
            Btn("↷", enabled = rs.canRedo) { rs.redo() }
            Btn("B", "b" in rs.active, style = TextStyle(fontWeight = FontWeight.Bold)) { rs.toggle(Rich.B) }
            Btn("I", "i" in rs.active, style = TextStyle(fontStyle = FontStyle.Italic)) { rs.toggle(Rich.I) }
            Btn("U", "u" in rs.active, style = TextStyle(textDecoration = TextDecoration.Underline)) { rs.toggle(Rich.U) }
            Btn("S", "s" in rs.active, style = TextStyle(textDecoration = TextDecoration.LineThrough)) { rs.toggle(Rich.S) }
            Box {
                Btn("Aa") { sizeMenu = true }
                DropdownMenu(sizeMenu, { sizeMenu = false }) {
                    listOf("Заголовок" to 1.6f, "Подзаголовок" to 1.3f, "Обычный" to 1f, "Мелкий" to 0.85f).forEach { (t, v) ->
                        DropdownMenuItem(text = { Text(t, fontSize = (16 * v).sp) }, onClick = { rs.size(v); sizeMenu = false })
                    }
                }
            }
            Btn("🅰") { colorFor = Rich.FG }
            Btn("🖍") { colorFor = Rich.BG }
            Btn("•") { rs.line("• ") }
            Btn("1.") { rs.line("1. ") }
            Btn("☑") { rs.line("☐ ") }
            Btn("🎤") { onDictate() }
            Btn("⌫ₐ") { rs.clear() }
        }
    }
    colorFor?.let { k ->
        AlertDialog(
            onDismissRequest = { colorFor = null },
            title = { Text(if (k == Rich.FG) "Цвет текста" else "Маркер") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPalette(0) { c -> if (k == Rich.FG) rs.color(c) else rs.highlight((c and 0x00FFFFFF) or 0x66000000); colorFor = null }
                    TextButton(onClick = { if (k == Rich.FG) rs.color(null) else rs.highlight(null); colorFor = null }) { Text("Убрать") }
                }
            },
            confirmButton = { TextButton(onClick = { colorFor = null }) { Text("Отмена") } },
        )
    }
}

/** Напоминание для заметки (или новое — как в Samsung Reminder). */
@Composable
fun ReminderDialog(vm: MainViewModel, note: Note?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val start = note?.remindAt?.let { zdt(it).toLocalDateTime() } ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var repeat by remember { mutableStateOf(note?.repeat ?: NoteRepeat.NONE) }
    var toCal by remember { mutableStateOf(note?.toCalendar ?: false) }
    var text by remember { mutableStateOf("") }
    val now = LocalDateTime.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) "Новое напоминание" else "Напоминание") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note == null) OutlinedTextField(text, { v ->
                    text = v
                    QuickParse.parse(v, now)?.let { r -> date = r.date; r.time?.let { time = it } }
                }, label = { Text("О чём напомнить") }, placeholder = { Text("позвонить Ивану завтра в 10") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val presets = listOf(
                        "Через час" to now.plusHours(1), "Вечером" to now.toLocalDate().atTime(19, 0),
                        "Завтра утром" to now.toLocalDate().plusDays(1).atTime(9, 0),
                        "В понедельник" to now.toLocalDate().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atTime(9, 0),
                    )
                    presets.forEach { (t, d) -> FilterChip(date == d.toLocalDate() && time.withSecond(0).withNano(0) == d.toLocalTime().withSecond(0).withNano(0), { date = d.toLocalDate(); time = d.toLocalTime().withSecond(0).withNano(0) }, label = { Text(t) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dayTitle(date, vm.today)) }
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(ctx, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show()
                    }) { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
                Text("Повтор", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { NoteRepeat.entries.forEach { r -> FilterChip(repeat == r, { repeat = r }, label = { Text(r.title) }) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Добавить в календарь", style = MaterialTheme.typography.bodyLarge)
                        Text("Событие в Google Календаре в это время", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(toCal, { toCal = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = note != null || text.isNotBlank(), onClick = {
                val at = ms(date.atTime(time))
                if (note == null) {
                    val title = QuickParse.parse(text, now)?.title?.takeIf { it != "Событие" } ?: text.trim()
                    val n = Note(newId(), title = title.replaceFirstChar { it.uppercase() }, remindAt = at, repeat = repeat, toCalendar = toCal)
                    vm.updateWork { WorkOps.upsertNote(it, n) }
                } else vm.updateWork { w -> w.copy(notes = w.notes.map { if (it.id == note.id) it.copy(remindAt = at, repeat = repeat, toCalendar = toCal, remindDone = false, updated = System.currentTimeMillis()) else it }) }
                if (toCal) app.calstats.CalendarExport.Settings(ctx).setOn(app.calstats.CalendarExport.Source.NOTES, true)
                vm.message = "⏰ Напомню " + remindText(at, vm.today).lowercase()
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (note?.remindAt != null) TextButton(onClick = {
                    vm.updateWork { w -> w.copy(notes = w.notes.map { if (it.id == note.id) it.copy(remindAt = null, remindDone = false, toCalendar = false, repeat = NoteRepeat.NONE) else it }) }
                    onDismiss()
                }) { Text("Убрать", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun CheckRow(d: CheckDraft, onDelete: () -> Unit, onEnter: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = d.checked, onCheckedChange = { d.checked = it })
        BasicTextField(
            value = d.text,
            onValueChange = { v -> if (v.endsWith("\n")) { d.text = v.trimEnd('\n'); onEnter() } else d.text = v },
            textStyle = TextStyle(
                color = if (d.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp, textDecoration = if (d.checked) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onEnter() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Удалить пункт", Modifier.size(16.dp)) }
    }
}

// ======================= УМНЫЕ ФУНКЦИИ =======================


/** Навести порядок: пробелы, заглавные буквы, маркеры, лишние пустые строки. */
fun tidyText(s: String): String {
    val lines = s.lines().map { l ->
        var t = l.trim().replace(Regex(" {2,}"), " ")
        t = t.replace(Regex("^[-*–] "), "• ").replace(Regex("^\\[ ?] "), "☐ ").replace(Regex("^\\[x] ", RegexOption.IGNORE_CASE), "☑ ")
        val pre = Regex("^(• |☐ |☑ |\\d+\\. )").find(t)?.value.orEmpty()
        val rest = t.removePrefix(pre)
        pre + rest.replaceFirstChar { it.uppercase() }.replace(Regex("([.!?]) +(\\p{Ll})")) { m -> m.groupValues[1] + " " + m.groupValues[2].uppercase() }
            .replace(Regex(" ([,.!?;:])"), "$1")
    }
    return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
}

@Composable
private fun SmartDialog(vm: MainViewModel, id: String, rs: RichState, title: () -> String, body: () -> String, onReplace: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf("menu") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val now = LocalDateTime.now()
    val text = body()
    // Строки с датами — кандидаты в напоминания/задачи (итоги совещания)
    val dated = remember(text) {
        text.lines().map { it.trim() }.filter { it.length > 3 }.mapNotNull { l ->
            QuickParse.parse(l.removePrefix("☐ ").removePrefix("• ").removePrefix("☑ "), now)?.let { r -> l to r }
        }
    }
    val todos = remember(text) { text.lines().map { it.trim() }.filter { it.startsWith("☐ ") || it.startsWith("• ") }.map { it.drop(2).trim() }.filter { it.isNotBlank() } }

    /** Перевод — системным переводчиком (Google Переводчик / Samsung), без лишних мегабайт в приложении. */
    fun translate() {
        val src = rs.selectedOrAll().ifBlank { text }
        val pm = ctx.packageManager
        val tries = listOf(
            Intent("android.intent.action.TRANSLATE").putExtra(Intent.EXTRA_TEXT, src),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, src).setPackage("com.google.android.apps.translate"),
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, src).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
        )
        val i = tries.firstOrNull { it.resolveActivity(pm) != null }
        if (i == null) { vm.message = "Установите Google Переводчик — перевод откроется в нём"; return }
        runCatching { ctx.startActivity(if (i.action == Intent.ACTION_PROCESS_TEXT) Intent.createChooser(i, "Перевести") else i) }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mode) { "translate" -> "Перевести на…"; "dates" -> "Напоминания из текста"; "result" -> "Результат"; else -> "✨ Умные функции" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                busy?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
                when (mode) {
                    "menu" -> {
                        @Composable fun Item(t: String, sub: String, onClick: () -> Unit) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)) {
                                Text(t, style = MaterialTheme.typography.bodyLarge)
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Item("🧹 Навести порядок", "Заглавные буквы, пробелы, «-» → маркеры, лишние пустые строки") {
                            val t = tidyText(text); if (t == text) vm.message = "Уже аккуратно" else { onReplace(t); vm.message = "Готово ✓" }; onDismiss()
                        }
                        Item("🌐 Перевести", "Откроется переводчик телефона. Выделите часть текста — переведётся только она") { translate() }
                        Item("⏰ Напоминания из дат в тексте", if (dated.isEmpty()) "Дат в тексте не нашёл: пишите «до пятницы», «25.11 в 15:00»" else "Найдено: ${dated.size}") { if (dated.isNotEmpty()) mode = "dates" }
                        Item("✅ Пункты → в «Задачи»", if (todos.isEmpty()) "Нет пунктов «☐» или «•»" else "Поручения с совещания станут задачами: ${todos.size}") {
                            if (todos.isEmpty()) return@Item
                            vm.updateWork { w0 ->
                                var w = w0
                                if (w.projects.none { !it.archived }) w = WorkOps.upsertProject(w, Project(newId(), "ЛИЧ", "Личное", PALETTE[0]))
                                val p = w.projects.first { !it.archived }
                                todos.forEach { t ->
                                    val r = QuickParse.parse(t, now)
                                    w = WorkOps.createIssue(w, Issue(newId(), p.id, 0, (r?.title?.takeIf { it != "Событие" } ?: t).take(120), assigneeId = ME, due = r?.date?.toString(),
                                        description = "Из заметки «${title().ifBlank { "без названия" }}»")).first
                                }
                                w
                            }
                            if (!vm.isEnabled(Tab.TASKS)) vm.setModuleEnabled(Tab.TASKS, true)
                            vm.message = "Создано задач: ${todos.size}"; onDismiss()
                        }
                        Item("📋 Скопировать текст", "Без оформления") {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("note", text)); onDismiss()
                        }
                        Item("🔤 Орфография", "Подчёркивает ошибки клавиатура Samsung/Google прямо при наборе — нажмите на слово, чтобы исправить") { onDismiss() }
                    }
                    "dates" -> {
                        Text("Каждая строка станет отдельным напоминанием в «⏰ Напоминания»:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        dated.forEach { (l, r) -> Text("• ${r.title} — " + Fmt.dayTitle(r.date, vm.today) + (r.time?.let { ", " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: ", 9:00"), style = MaterialTheme.typography.bodyMedium) }
                    }
                    "result" -> SelectableText(result.orEmpty())
                }
            }
        },
        confirmButton = {
            when (mode) {
                "dates" -> TextButton(onClick = {
                    val folder = vm.work.notes.firstOrNull { it.id == id }?.folderId
                    vm.updateWork { w -> w.copy(notes = w.notes + dated.map { (_, r) ->
                        Note(newId(), title = r.title.replaceFirstChar { it.uppercase() }, body = "Из заметки «${title().ifBlank { "без названия" }}»", folderId = folder,
                            remindAt = ms(r.date.atTime(r.time ?: LocalTime.of(9, 0))))
                    }) }
                    vm.message = "Создано напоминаний: ${dated.size}"; onDismiss()
                }) { Text("Создать ${dated.size}") }
                "result" -> TextButton(onClick = { onReplace(text + "\n\n" + result.orEmpty()); onDismiss() }) { Text("Добавить ниже") }
                else -> TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
        dismissButton = {
            if (mode == "result") Row {
                TextButton(onClick = { onReplace(result.orEmpty()); onDismiss() }) { Text("Заменить") }
                TextButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("t", result)); vm.message = "Скопировано"; onDismiss() }) { Text("Копировать") }
            } else if (mode != "menu") TextButton(onClick = { mode = "menu" }) { Text("Назад") }
        },
    )
}

@Composable
private fun SelectableText(t: String) = androidx.compose.foundation.text.selection.SelectionContainer { Text(t, style = MaterialTheme.typography.bodyMedium) }
