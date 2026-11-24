@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.MainViewModel
import app.calstats.Net
import app.calstats.work.Book
import app.calstats.work.BookMark
import app.calstats.work.BookStatus
import app.calstats.work.EbookParser
import app.calstats.work.Para
import app.calstats.work.ReadLog
import app.calstats.work.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Темы чтения: фон, текст. Сепия и тёмные — меньше устают глаза. */
private data class RTheme(val name: String, val bg: Color, val fg: Color)
private val THEMES = listOf(
    RTheme("Белая", Color(0xFFFFFFFF), Color(0xFF2B2F35)),
    RTheme("Сепия", Color(0xFFF4ECD8), Color(0xFF5B4636)),
    RTheme("Серая", Color(0xFFE6E6E3), Color(0xFF333333)),
    RTheme("Ночь", Color(0xFF1E2125), Color(0xFFC9C5BD)),
    RTheme("Чёрная", Color(0xFF000000), Color(0xFFA8A49C)),
)

/** Настройки чтения (общие для всех книг). */
private class ReaderPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("reader", Context.MODE_PRIVATE)
    var size by mutableStateOf(p.getInt("size", 19))
    var line by mutableStateOf(p.getFloat("line", 1.55f))
    var theme by mutableStateOf(p.getInt("theme", 1))
    var serif by mutableStateOf(p.getBoolean("serif", true))
    var margin by mutableStateOf(p.getInt("margin", 22))
    var bright by mutableStateOf(p.getFloat("bright", -1f))
    var warm by mutableStateOf(p.getFloat("warm", 0f))
    var justify by mutableStateOf(p.getBoolean("justify", true))
    fun save() = p.edit().putInt("size", size).putFloat("line", line).putInt("theme", theme).putBoolean("serif", serif).putInt("margin", margin)
        .putFloat("bright", bright).putFloat("warm", warm).putBoolean("justify", justify).apply()
}

/** Открыть электронную книгу. */
@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(vm: MainViewModel, bookId: String) {
    val ctx = LocalContext.current
    val book = vm.work.readBooks.firstOrNull { it.id == bookId }
    if (book?.file == null) { LaunchedEffect(Unit) { vm.message = "Файл книги не найден"; vm.pop() }; return }
    val prefs = remember { ReaderPrefs(ctx) }
    val th = THEMES[prefs.theme.coerceIn(0, THEMES.lastIndex)]
    val paras by produceState<List<Para>?>(null, book.file) {
        value = withContext(Dispatchers.IO) { runCatching { EbookParser.parse(File(ctx.filesDir, book.file), book.format) }.getOrElse { emptyList() } }
    }
    val state = rememberLazyListState(book.position, book.positionOffset)
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val opened = remember { System.currentTimeMillis() }

    // Экран не гаснет, яркость — своя для чтения
    DisposableEffect(prefs.bright) {
        val win = (ctx as? Activity)?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        win?.attributes = win?.attributes?.apply { screenBrightness = prefs.bright }
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            win?.attributes = win?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    // Запоминаем место
    LaunchedEffect(paras) {
        val total = paras?.size ?: return@LaunchedEffect
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.debounce(1200).collect { (i, off) ->
            vm.updateWork { w -> w.copy(readBooks = w.readBooks.map { if (it.id == bookId) it.copy(position = i, positionOffset = off, current = i + 1, total = total,
                status = if (i + 2 >= total) BookStatus.DONE else if (it.status == BookStatus.PLAN) BookStatus.ACTIVE else it.status,
                finished = if (i + 2 >= total) (it.finished ?: vm.today.toString()) else it.finished, started = it.started ?: vm.today.toString()) else it }) }
        }
    }
    DisposableEffect(bookId) {
        onDispose {
            // Время чтения — в статистику
            val min = ((System.currentTimeMillis() - opened) / 60_000).toInt()
            if (min >= 1) vm.updateWork { w -> w.copy(
                readBooks = w.readBooks.map { if (it.id == bookId) it.copy(minutes = it.minutes + min) else it },
                readLogs = w.readLogs + ReadLog(vm.today.toString(), bookId, 0, min),
            ) }
        }
    }
    BackHandler(controls || settings || contents) { controls = false; settings = false; contents = false }

    val list = paras
    val pct = if (list.isNullOrEmpty()) 0 else ((state.firstVisibleItemIndex + 1) * 100 / list.size)
    val marked = book.bookmarks.any { it.para == state.firstVisibleItemIndex }
    Box(Modifier.fillMaxSize().background(th.bg)) {
        if (list == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = th.fg)
        else if (list.isEmpty()) Text("Не удалось прочитать книгу. Поддерживаются EPUB, FB2 и TXT.", Modifier.align(Alignment.Center).padding(32.dp), color = th.fg)
        else LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().pointerInput(Unit) {
                detectTapGestures { o ->
                    val third = size.width / 3
                    when {
                        controls -> controls = false
                        o.x < third -> scope.launch { state.animateScrollBy(-size.height * 0.9f) }
                        o.x > third * 2 -> scope.launch { state.animateScrollBy(size.height * 0.9f) }
                        else -> controls = true
                    }
                }
            },
            state = state,
            contentPadding = PaddingValues(start = prefs.margin.dp, end = prefs.margin.dp, top = 24.dp, bottom = 120.dp),
        ) {
            itemsIndexed(list) { i, p ->
                val fam = if (prefs.serif) FontFamily.Serif else FontFamily.SansSerif
                if (p.heading) Text(p.text, Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp), color = th.fg, fontFamily = fam,
                    fontSize = (prefs.size + 4).sp, lineHeight = ((prefs.size + 4) * 1.3f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                else Text(
                    (if (prefs.justify) " " else "") + p.text, Modifier.fillMaxWidth().padding(bottom = (prefs.size * 0.45f).dp), color = th.fg, fontFamily = fam,
                    fontSize = prefs.size.sp, lineHeight = (prefs.size * prefs.line).sp, textAlign = if (prefs.justify) TextAlign.Justify else TextAlign.Start,
                )
                if (book.bookmarks.any { it.para == i }) Text("🔖", color = th.fg, fontSize = 12.sp)
            }
        }
        // Тёплый фильтр: меньше синего света вечером
        if (prefs.warm > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8A00).copy(alpha = prefs.warm * 0.25f)))
        // Тонкая строка прогресса снизу
        if (!controls && list != null) Text("$pct%", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp), color = th.fg.copy(alpha = 0.5f), fontSize = 11.sp)
        if (controls && list != null) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = th.bg.copy(alpha = 0.97f), shadowElevation = 6.dp) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.pop() }) { Text("←", color = th.fg, fontSize = 20.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = th.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        if (book.author.isNotBlank()) Text(book.author, color = th.fg.copy(alpha = 0.6f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        val i = state.firstVisibleItemIndex
                        vm.updateWork { w -> w.copy(readBooks = w.readBooks.map { b ->
                            if (b.id != bookId) b else if (b.bookmarks.any { it.para == i }) b.copy(bookmarks = b.bookmarks.filter { it.para != i })
                            else b.copy(bookmarks = b.bookmarks + BookMark(i, list[i].text.take(80)))
                        }) }
                    }) { Text(if (marked) "🔖" else "🏷", fontSize = 20.sp) }
                    TextButton(onClick = { contents = true }) { Text("☰", color = th.fg, fontSize = 20.sp) }
                    TextButton(onClick = { settings = true }) { Text("Aa", color = th.fg, fontSize = 18.sp) }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = th.bg.copy(alpha = 0.97f), shadowElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    var drag by remember(state.firstVisibleItemIndex) { mutableStateOf(state.firstVisibleItemIndex.toFloat()) }
                    Slider(drag, { drag = it }, valueRange = 0f..(list.size - 1).coerceAtLeast(1).toFloat(),
                        onValueChangeFinished = { scope.launch { state.scrollToItem(drag.toInt()) } })
                    Text("$pct% · абзац ${state.firstVisibleItemIndex + 1} из ${list.size} · касание слева/справа — листать", color = th.fg.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }

    if (settings) ModalBottomSheet(onDismissRequest = { settings = false; prefs.save() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Оформление", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                THEMES.forEachIndexed { i, t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(t.bg).clickable { prefs.theme = i; prefs.save() }
                            .then(if (prefs.theme == i) Modifier.background(t.bg) else Modifier), contentAlignment = Alignment.Center) {
                            Text("Аа", color = t.fg, fontWeight = if (prefs.theme == i) FontWeight.Bold else null)
                        }
                        Text(t.name + if (prefs.theme == i) " ✓" else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Размер текста: ${prefs.size}", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.size.toFloat(), { prefs.size = it.toInt() }, valueRange = 14f..30f, onValueChangeFinished = { prefs.save() })
            Text("Межстрочный интервал: ${"%.1f".format(prefs.line)}", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.line, { prefs.line = it }, valueRange = 1.2f..2.1f, onValueChangeFinished = { prefs.save() })
            Text("Поля: ${prefs.margin}", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.margin.toFloat(), { prefs.margin = it.toInt() }, valueRange = 8f..48f, onValueChangeFinished = { prefs.save() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(prefs.serif, { prefs.serif = true; prefs.save() }, label = { Text("С засечками", fontFamily = FontFamily.Serif) })
                FilterChip(!prefs.serif, { prefs.serif = false; prefs.save() }, label = { Text("Без засечек") })
                FilterChip(prefs.justify, { prefs.justify = !prefs.justify; prefs.save() }, label = { Text("По ширине") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Яркость: " + if (prefs.bright < 0) "как в телефоне" else "${(prefs.bright * 100).toInt()}%", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Switch(prefs.bright >= 0, { prefs.bright = if (it) 0.4f else -1f; prefs.save() })
            }
            if (prefs.bright >= 0) Slider(prefs.bright, { prefs.bright = it.coerceAtLeast(0.02f) }, valueRange = 0.02f..1f, onValueChangeFinished = { prefs.save() })
            Text("Тёплый фильтр (меньше синего света): ${(prefs.warm * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(prefs.warm, { prefs.warm = it }, valueRange = 0f..1f, onValueChangeFinished = { prefs.save() })
            Text("Совет: вечером — «Ночь» или «Сепия», тёплый фильтр 40–60%, яркость пониже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (contents && list != null) {
        val heads = list.withIndex().filter { it.value.heading }
        AlertDialog(
            onDismissRequest = { contents = false },
            title = { Text("Оглавление и закладки") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (book.bookmarks.isNotEmpty()) {
                        Text("Закладки", style = MaterialTheme.typography.labelLarge)
                        book.bookmarks.sortedBy { it.para }.forEach { m ->
                            Text("🔖 ${(m.para + 1) * 100 / list.size}% · ${m.text}", Modifier.fillMaxWidth().clickable { scope.launch { state.scrollToItem(m.para) }; contents = false; controls = false }
                                .padding(vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("Главы", style = MaterialTheme.typography.labelLarge)
                    if (heads.isEmpty()) Text("В книге нет оглавления", style = MaterialTheme.typography.bodySmall)
                    heads.forEach { (i, p) ->
                        Text(p.text, Modifier.fillMaxWidth().clickable { scope.launch { state.scrollToItem(i) }; contents = false; controls = false }.padding(vertical = 8.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contents = false }) { Text("Закрыть") } },
        )
    }
}

/** Поиск бесплатных книг (общественное достояние) и загрузка в приложение. */
@Composable
fun FreeBooksDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("ru") }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Net.FreeBook>?>(null) }
    var fail by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (q.isBlank()) return
        busy = "Ищу…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Net.searchFree(q.trim(), lang) } }
            busy = null
            results = r.getOrNull()
            fail = r.exceptionOrNull()?.let { "Библиотека недоступна: " + Net.why(it) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бесплатные книги") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Классика в общественном достоянии из Project Gutenberg — легально и бесплатно. Свои книги (EPUB, FB2, TXT) добавляйте кнопкой «Открыть файл».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Толстой, Чехов, Pushkin…") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ru" to "Русские", "en" to "English", "" to "Все").forEach { (k, t) -> FilterChip(lang == k, { lang = k; if (q.isNotBlank()) search() }, label = { Text(t) }) }
                }
                Button(onClick = { search() }, enabled = q.isNotBlank() && busy == null) { Text("Найти") }
                busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                fail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                results?.let { r ->
                    if (r.isEmpty()) Text("Ничего не нашлось. Попробуйте фамилию автора, на русском и латиницей.", style = MaterialTheme.typography.bodySmall)
                    r.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(b.authors, b.lang.uppercase()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            val url = b.epub ?: b.txt
                            if (url != null) TextButton(enabled = busy == null, onClick = {
                                busy = "Скачиваю «${b.title}»…"
                                scope.launch {
                                    val fmt = if (b.epub != null) "epub" else "txt"
                                    val id = newId()
                                    val f = File(ctx.filesDir, "books/$id.$fmt")
                                                    val dl = withContext(Dispatchers.IO) { runCatching { Net.download(url, f) } }
                                    busy = null
                                    if (dl.isFailure) { f.delete(); fail = "Не удалось скачать: " + Net.why(dl.exceptionOrNull()); return@launch }
                                    val book = Book(id, b.title, b.authors, file = "books/$id.$fmt", format = fmt, status = BookStatus.ACTIVE, started = vm.today.toString(),
                                        source = "Project Gutenberg #${b.id}")
                                    vm.updateWork { it.copy(readBooks = it.readBooks + book) }
                                    onDismiss()
                                    vm.push(app.calstats.Screen.Reader(id))
                                }
                            }) { Text("Скачать") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Добавить свою электронную книгу с телефона (EPUB, FB2, FB2.ZIP, TXT). */
fun importEbook(ctx: Context, vm: MainViewModel, uri: android.net.Uri) {
    val r = ctx.contentResolver
    var name = "книга"
    r.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
    val lower = name.lowercase()
    val fmt = when { lower.endsWith(".fb2.zip") -> "fb2.zip"; lower.endsWith(".fb2") -> "fb2"; lower.endsWith(".epub") -> "epub"; lower.endsWith(".txt") -> "txt"; else -> null }
    if (fmt == null) { vm.message = "Поддерживаются EPUB, FB2 и TXT"; return }
    val id = newId()
    val f = File(ctx.filesDir, "books/$id.$fmt").apply { parentFile?.mkdirs() }
    runCatching { r.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } }.onFailure { vm.message = "Не удалось открыть файл"; return }
    val (t, a) = EbookParser.meta(f, fmt)
    val book = Book(id, t.ifBlank { name.substringBeforeLast('.').removeSuffix(".fb2") }, a, file = "books/$id.$fmt", format = fmt, status = BookStatus.ACTIVE,
        started = vm.today.toString(), source = "Файл")
    vm.updateWork { it.copy(readBooks = it.readBooks + book) }
    vm.push(app.calstats.Screen.Reader(id))
}
