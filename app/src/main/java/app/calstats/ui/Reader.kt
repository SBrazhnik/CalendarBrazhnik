@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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

/** Открыть электронную книгу: страницы листаются влево-вправо, как в бумажной книге. */
@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(vm: MainViewModel, bookId: String) {
    val ctx = LocalContext.current
    val book = vm.work.readBooks.firstOrNull { it.id == bookId }
    if (book?.file == null) { LaunchedEffect(Unit) { vm.message = "Файл книги не найден"; vm.pop() }; return }
    val prefs = remember { ReaderPrefs(ctx) }
    val th = THEMES[prefs.theme.coerceIn(0, THEMES.lastIndex)]
    val paras by produceState<List<Para>?>(null, book.file) {
        value = withContext(Dispatchers.IO) { runCatching { EbookParser.load(File(ctx.filesDir, book.file), book.format, ctx.cacheDir) }.getOrElse { emptyList() } }
    }
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val opened = remember { System.currentTimeMillis() }
    // Место, к которому привязываемся при перевёрстке (смена шрифта, поворот)
    var anchor by remember { mutableStateOf(book.position to book.positionOffset) }

    DisposableEffect(prefs.bright) {
        val win = (ctx as? Activity)?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        win?.attributes = win?.attributes?.apply { screenBrightness = prefs.bright }
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            win?.attributes = win?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    DisposableEffect(bookId) {
        onDispose {
            val min = ((System.currentTimeMillis() - opened) / 60_000).toInt()
            if (min >= 1) vm.updateWork { w -> w.copy(
                readBooks = w.readBooks.map { if (it.id == bookId) it.copy(minutes = it.minutes + min) else it },
                readLogs = w.readLogs + ReadLog(vm.today.toString(), bookId, 0, min),
            ) }
        }
    }
    BackHandler(controls || settings || contents) { controls = false; settings = false; contents = false }

    BoxWithConstraints(Modifier.fillMaxSize().background(th.bg)) {
        val density = LocalDensity.current
        val topInset = WindowInsets.statusBars.getTop(density)
        val bottomInset = WindowInsets.navigationBars.getBottom(density)
        val marginPx = with(density) { prefs.margin.dp.roundToPx() }
        val headPx = with(density) { 14.dp.roundToPx() }
        val footPx = with(density) { 30.dp.roundToPx() }
        val pageW = (constraints.maxWidth - 2 * marginPx).coerceAtLeast(100)
        val pageH = (constraints.maxHeight - topInset - bottomInset - headPx - footPx).coerceAtLeast(100)
        val textPx = with(density) { prefs.size.sp.toPx() }
        val style = remember(pageW, pageH, textPx, prefs.line, prefs.serif, prefs.justify) { PageStyle(pageW, pageH, textPx, prefs.line, prefs.serif, prefs.justify) }
        val renderer = remember(style) { PageRenderer(style) }
        val list = paras
        val pages = remember(style, list) { mutableStateListOf<List<Slice>>() }
        var done by remember(style, list) { mutableStateOf(false) }
        var restored by remember(style, list) { mutableStateOf(false) }
        val pager = rememberPagerState(pageCount = { pages.size })

        // Вёрстка: из кэша мгновенно, иначе в фоне, первые страницы — сразу
        LaunchedEffect(style, list) {
            val ps = list ?: return@LaunchedEffect
            if (ps.isEmpty()) { done = true; return@LaunchedEffect }
            val f = Paginator.cacheFile(ctx.cacheDir, bookId, style)
            val cached = withContext(Dispatchers.IO) { if (f.isFile) Paginator.load(f) else null }
            if (!cached.isNullOrEmpty()) { pages.addAll(cached); done = true; return@LaunchedEffect }
            val all = withContext(Dispatchers.Default) { Paginator.paginate(ps, style) { batch -> pages.addAll(batch) } }
            done = true
            withContext(Dispatchers.IO) { Paginator.save(f, bookId, all) }
        }
        // Возврат на место
        LaunchedEffect(pages.size, done) {
            if (restored || pages.isEmpty()) return@LaunchedEffect
            val target = Paginator.pageOf(pages, anchor.first, anchor.second)
            if (target < pages.size - 1 || done) { pager.scrollToPage(target); restored = true }
        }
        // Запоминаем место
        LaunchedEffect(restored) {
            if (!restored) return@LaunchedEffect
            launch { snapshotFlow { pager.currentPage }.collect { pg -> pages.getOrNull(pg)?.firstOrNull()?.let { anchor = it.para to it.start } } }
            snapshotFlow { pager.currentPage }.debounce(800).collect { pg ->
                val s = pages.getOrNull(pg)?.firstOrNull() ?: return@collect
                val last = done && pg >= pages.size - 1
                vm.updateWork { w -> w.copy(readBooks = w.readBooks.map { if (it.id == bookId) it.copy(position = s.para, positionOffset = s.start, current = pg + 1,
                    total = if (done) pages.size else maxOf(it.total, pages.size),
                    status = if (last) BookStatus.DONE else if (it.status == BookStatus.PLAN) BookStatus.ACTIVE else it.status,
                    finished = if (last) (it.finished ?: vm.today.toString()) else it.finished, started = it.started ?: vm.today.toString()) else it }) }
                if (last && book.status != BookStatus.DONE) vm.message = "🎉 «${book.title}» прочитана!"
            }
        }

        val heads = remember(list) { list?.withIndex()?.filter { it.value.heading }?.map { it.index }.orEmpty() }
        val cur = pager.currentPage
        val firstSlice = pages.getOrNull(cur)?.firstOrNull()
        val chapter = firstSlice?.let { s -> heads.lastOrNull { it <= s.para }?.let { list?.getOrNull(it)?.text } }.orEmpty()
        val pct = if (pages.isEmpty() || !done) null else (cur + 1) * 100 / pages.size
        val marked = firstSlice != null && book.bookmarks.any { b -> pages.getOrNull(cur)?.any { s -> s.para == b.para && b.off in s.start until maxOf(s.end, s.start + 1) } == true }
        val fgArgb = th.fg.toArgb()

        when {
            list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = th.fg)
            list.isEmpty() -> Text("Не удалось прочитать книгу. Поддерживаются EPUB, FB2 и TXT.", Modifier.align(Alignment.Center).padding(32.dp), color = th.fg)
            else -> HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { pg ->
                // Эффект перелистывания: страница уезжает поверх следующей, под ней — тень
                val off = (pager.currentPage - pg) + pager.currentPageOffsetFraction
                Box(
                    Modifier.fillMaxSize().zIndex(if (off > 0f) 1f else 0f)
                        .graphicsLayer { if (off < 0f) translationX = off * size.width }
                        .drawWithContent {
                            drawContent()
                            if (off > 0f && off < 1f) drawRect(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f * (1f - off)), Color.Transparent), startX = size.width, endX = size.width + 28.dp.toPx()),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width, 0f), size = androidx.compose.ui.geometry.Size(28.dp.toPx(), size.height),
                            )
                        }
                        .background(th.bg)
                        .pointerInput(pages) {
                            detectTapGestures { o ->
                                val w = size.width
                                when {
                                    controls -> controls = false
                                    o.x < w * 0.3f -> scope.launch { if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1) }
                                    o.x > w * 0.7f -> scope.launch { if (pager.currentPage < pages.size - 1) pager.animateScrollToPage(pager.currentPage + 1) }
                                    else -> controls = true
                                }
                            }
                        },
                ) {
                    val page = pages.getOrNull(pg)
                    if (page != null) androidx.compose.foundation.Canvas(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset(marginPx, topInset + headPx) }
                            .size(with(density) { pageW.toDp() }, with(density) { pageH.toDp() })
                    ) { drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, list, page, fgArgb) } }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = with(density) { marginPx.toDp() }).height(with(density) { footPx.toDp() }),
                        verticalAlignment = Alignment.CenterVertically) {
                        val ch = page?.firstOrNull()?.let { s -> heads.lastOrNull { it <= s.para }?.let { list.getOrNull(it)?.text } }.orEmpty()
                        Text(ch, Modifier.weight(1f), color = th.fg.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pg + 1} / ${if (done) pages.size.toString() else "…"}", color = th.fg.copy(alpha = 0.45f), fontSize = 11.sp)
                    }
                }
            }
        }
        if (list != null && list.isNotEmpty() && !restored) Column(Modifier.align(Alignment.Center).background(th.bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = th.fg)
            Spacer(Modifier.height(10.dp))
            Text("Раскладываю страницы… ${pages.size}", color = th.fg, fontSize = 13.sp)
        }
        // Тёплый фильтр: меньше синего света вечером
        if (prefs.warm > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8A00).copy(alpha = prefs.warm * 0.25f)))
        if (controls && list != null) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = th.bg.copy(alpha = 0.97f), shadowElevation = 6.dp) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.pop() }) { Text("←", color = th.fg, fontSize = 20.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = th.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(chapter.ifBlank { book.author }, color = th.fg.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        val pg = pages.getOrNull(pager.currentPage) ?: return@TextButton
                        val s = pg.first()
                        vm.updateWork { w -> w.copy(readBooks = w.readBooks.map { b ->
                            if (b.id != bookId) b
                            else if (marked) b.copy(bookmarks = b.bookmarks.filterNot { m -> pg.any { x -> x.para == m.para && m.off in x.start until maxOf(x.end, x.start + 1) } })
                            else b.copy(bookmarks = b.bookmarks + BookMark(s.para, style.text(list[s.para]).substring(s.start).trim().take(80), off = s.start))
                        }) }
                    }) { Text(if (marked) "🔖" else "🏷", fontSize = 20.sp) }
                    TextButton(onClick = { contents = true }) { Text("☰", color = th.fg, fontSize = 20.sp) }
                    TextButton(onClick = { settings = true }) { Text("Aa", color = th.fg, fontSize = 18.sp) }
                }
            }
            if (pages.isNotEmpty()) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = th.bg.copy(alpha = 0.97f), shadowElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    var drag by remember(cur) { mutableStateOf(cur.toFloat()) }
                    Slider(drag, { drag = it }, valueRange = 0f..(pages.size - 1).coerceAtLeast(1).toFloat(),
                        onValueChangeFinished = { scope.launch { pager.scrollToPage(drag.toInt().coerceIn(0, pages.size - 1)) } })
                    Text("Страница ${drag.toInt() + 1} из ${if (done) pages.size else "…"}" + (pct?.let { " · $it%" } ?: "") + " · листайте пальцем или касанием у края",
                        color = th.fg.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        if (contents && list != null) {
            fun go(para: Int, off: Int) {
                val t = Paginator.pageOf(pages, para, off)
                if (!done && t >= pages.size - 1) vm.message = "Страницы ещё раскладываются, попробуйте через пару секунд"
                else scope.launch { pager.scrollToPage(t) }
                contents = false; controls = false
            }
            AlertDialog(
                onDismissRequest = { contents = false },
                title = { Text("Оглавление и закладки") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (book.bookmarks.isNotEmpty()) {
                            Text("Закладки", style = MaterialTheme.typography.labelLarge)
                            book.bookmarks.sortedWith(compareBy({ it.para }, { it.off })).forEach { m ->
                                val pg = if (pages.isEmpty()) null else Paginator.pageOf(pages, m.para, m.off) + 1
                                Text("🔖 ${pg?.let { "стр. $it · " }.orEmpty()}${m.text}", Modifier.fillMaxWidth().clickable { go(m.para, m.off) }.padding(vertical = 8.dp),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Text("Главы", style = MaterialTheme.typography.labelLarge)
                        if (heads.isEmpty()) Text("В книге нет оглавления", style = MaterialTheme.typography.bodySmall)
                        heads.forEach { i ->
                            Text(list[i].text, Modifier.fillMaxWidth().clickable { go(i, 0) }.padding(vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { contents = false }) { Text("Закрыть") } },
            )
        }
    }

    if (settings) ModalBottomSheet(onDismissRequest = { settings = false; prefs.save() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Оформление", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                THEMES.forEachIndexed { i, t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(t.bg).clickable { prefs.theme = i; prefs.save() }, contentAlignment = Alignment.Center) {
                            Text("Аа", color = t.fg, fontWeight = if (prefs.theme == i) FontWeight.Bold else null)
                        }
                        Text(t.name + if (prefs.theme == i) " ✓" else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // Размер и интервал меняем «шагами» — страницы перекладываются после отпускания
            var size by remember { mutableStateOf(prefs.size.toFloat()) }
            Text("Размер текста: ${size.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(size, { size = it }, valueRange = 14f..30f, steps = 15, onValueChangeFinished = { prefs.size = size.toInt(); prefs.save() })
            var line by remember { mutableStateOf(prefs.line) }
            Text("Межстрочный интервал: ${"%.1f".format(line)}", style = MaterialTheme.typography.labelLarge)
            Slider(line, { line = it }, valueRange = 1.2f..2.0f, steps = 7, onValueChangeFinished = { prefs.line = (line * 10).roundToInt() / 10f; prefs.save() })
            var margin by remember { mutableStateOf(prefs.margin.toFloat()) }
            Text("Поля: ${margin.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(margin, { margin = it }, valueRange = 8f..48f, steps = 9, onValueChangeFinished = { prefs.margin = margin.toInt(); prefs.save() })
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
}

/** Поиск бесплатных книг (общественное достояние) в нескольких библиотеках и загрузка в приложение. */
@Composable
fun FreeBooksDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("ru") }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Net.FreeBook>?>(null) }
    var fail by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (q.isBlank()) return
        busy = "Ищу в библиотеках…"; fail = null; note = null
        scope.launch {
            val r = runCatching { Net.searchFree(q.trim(), lang) }
            busy = null
            results = r.getOrNull()?.books
            note = r.getOrNull()?.errors?.takeIf { it.isNotEmpty() }?.joinToString("\n") { "⚠ $it" }
            fail = r.exceptionOrNull()?.let { "Библиотеки недоступны:\n" + (it.message ?: Net.why(it)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бесплатные книги") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Легальные библиотеки общественного достояния: Викитека (русская классика) и Project Gutenberg. Купленные или скачанные книги (EPUB, FB2, TXT) добавляйте кнопкой «Открыть файл».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Война и мир, Чехов, Пушкин…") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ru" to "Русские", "en" to "English", "" to "Все").forEach { (k, t) -> FilterChip(lang == k, { lang = k; if (q.isNotBlank()) search() }, label = { Text(t) }) }
                }
                Button(onClick = { search() }, enabled = q.isNotBlank() && busy == null) { Text("Найти") }
                busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) } }
                fail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                results?.let { r ->
                    if (r.isEmpty()) Text("Ничего не нашлось. Попробуйте название произведения или фамилию автора.", style = MaterialTheme.typography.bodySmall)
                    r.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(b.authors, b.source).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(enabled = busy == null, onClick = {
                                busy = if (b.source == "Викитека") "Викитека собирает книгу — до минуты…" else "Скачиваю «${b.title}»…"
                                fail = null
                                scope.launch {
                                    val id = newId()
                                    val f = File(ctx.filesDir, "books/$id.${b.fmt}")
                                    val dl = withContext(Dispatchers.IO) { runCatching { Net.download(b.url, f) } }
                                    busy = null
                                    if (dl.isFailure) { f.delete(); fail = "Не удалось скачать: " + Net.why(dl.exceptionOrNull()); return@launch }
                                    val (t, a) = withContext(Dispatchers.IO) { EbookParser.meta(f, b.fmt) }
                                    val book = Book(id, b.title.ifBlank { t }, b.authors.ifBlank { a }, file = "books/$id.${b.fmt}", format = b.fmt, status = BookStatus.ACTIVE,
                                        started = vm.today.toString(), source = "${b.source}: ${b.id}")
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
fun importEbook(ctx: Context, vm: MainViewModel, uri: android.net.Uri, mime: String? = null) {
    val r = ctx.contentResolver
    var name = "книга"
    runCatching { r.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name } }
    if (name == "книга") uri.lastPathSegment?.let { name = it.substringAfterLast('/') }
    val lower = name.lowercase()
    val fmt = when { lower.endsWith(".fb2.zip") -> "fb2.zip"; lower.endsWith(".fb2") -> "fb2"; lower.endsWith(".epub") -> "epub"; lower.endsWith(".txt") -> "txt"
        mime == "application/epub+zip" -> "epub"; mime?.contains("fictionbook") == true -> "fb2"; mime == "text/plain" -> "txt"; else -> null }
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
