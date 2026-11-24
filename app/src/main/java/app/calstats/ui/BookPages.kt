package app.calstats.ui

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.calstats.work.Para
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.sync.withLock

/** Кусок абзаца на странице: символы [start, end) абзаца para. */
data class Slice(val para: Int, val start: Int, val end: Int)

/**
 * Вёрстка книги по страницам. Одна и та же функция строк используется
 * и для разбивки, и для рисования — поэтому текст никогда не обрезается и не наезжает.
 */
class PageStyle(
    val width: Int, val height: Int, val textPx: Float, val line: Float, val serif: Boolean, val justify: Boolean,
) {
    val gap = (textPx * 0.35f).toInt()
    val headTop = (textPx * 1.6f).toInt()
    val headGap = (textPx * 1.0f).toInt()
    fun key() = "${width}x${height}_${textPx.toInt()}_${(line * 100).toInt()}_${serif}_${justify}_v3"

    fun paint(heading: Boolean) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = if (heading) textPx * 1.25f else textPx
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (heading) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Текст абзаца так, как он выводится (с красной строкой). */
    fun text(p: Para): String = if (p.heading) p.text else " " + p.text

    /** Строки целого абзаца. Без переносов слов — так вёрстка в разы быстрее. */
    fun layout(text: String, heading: Boolean, paint: TextPaint): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (heading) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, if (heading) 1.15f else line)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        if (justify && !heading && Build.VERSION.SDK_INT >= 26) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return b.build()
    }
}

/** Рисует одну страницу на Canvas — теми же правилами, что и разбивка. Вёрстка абзацев кэшируется. */
class PageRenderer(private val st: PageStyle) {
    private val body = st.paint(false); private val head = st.paint(true)
    private val cache = android.util.LruCache<Int, StaticLayout>(64)
    fun draw(c: android.graphics.Canvas, paras: List<Para>, page: List<Slice>, color: Int) {
        body.color = color; head.color = color
        var y = 0
        for (s in page) {
            val p = paras.getOrNull(s.para) ?: continue
            val l = cache.get(s.para) ?: st.layout(st.text(p), p.heading, if (p.heading) head else body).also { cache.put(s.para, it) }
            val first = l.getLineForOffset(s.start)
            var last = first
            while (last + 1 < l.lineCount && l.getLineEnd(last + 1) <= s.end) last++
            val top = if (p.heading && y > 0 && s.start == 0) st.headTop else 0
            val lt = l.getLineTop(first); val h = l.getLineBottom(last) - lt
            c.save()
            c.translate(0f, (y + top - lt).toFloat())
            c.clipRect(0, lt, st.width, lt + h)
            l.draw(c)
            c.restore()
            y += top + h + if (p.heading) st.headGap else st.gap
        }
    }
}

object Paginator {
    /**
     * Разбивает книгу на страницы. onBatch вызывается по ходу — первые страницы
     * можно читать, пока остальные считаются.
     */
    fun paginate(paras: List<Para>, st: PageStyle, from: Int = 0, to: Int = paras.size, onBatch: (List<List<Slice>>) -> Unit): List<List<Slice>> {
        val body = st.paint(false); val head = st.paint(true)
        val pages = ArrayList<List<Slice>>()
        var cur = ArrayList<Slice>()
        var y = 0
        var lastSent = 0
        fun flush() { if (cur.isNotEmpty()) { pages += cur; cur = ArrayList() }; y = 0 }
        for (i in from until to) {
            val p = paras[i]
            // Глава начинается с новой страницы
            if (p.heading && cur.isNotEmpty() && !(paras.getOrNull(i - 1)?.heading ?: false)) flush()
            val text = st.text(p)
            val l = st.layout(text, p.heading, if (p.heading) head else body)
            var line = 0
            while (line < l.lineCount) {
                val top = if (p.heading && y > 0 && line == 0) st.headTop else 0
                val avail = st.height - y - top
                val base = l.getLineTop(line)
                var next = line
                while (next < l.lineCount && l.getLineBottom(next) - base <= avail) next++
                if (next == l.lineCount) {
                    cur += Slice(i, l.getLineStart(line), text.length)
                    y += top + (l.getLineBottom(l.lineCount - 1) - base) + if (p.heading) st.headGap else st.gap
                    break
                }
                if (next == line) { if (cur.isEmpty()) next = line + 1 else { flush(); continue } }
                cur += Slice(i, l.getLineStart(line), l.getLineEnd(next - 1))
                flush()
                line = next
            }
            // первые страницы — сразу, дальше пачками
            if (pages.size - lastSent >= (if (lastSent == 0) 3 else 40)) { onBatch(pages.subList(lastSent, pages.size).toList()); lastSent = pages.size }
        }
        flush()
        if (pages.size > lastSent) onBatch(pages.subList(lastSent, pages.size).toList())
        return pages
    }

    /** Начало главы, в которой находится абзац: с него вёрстка не зависит от предыдущего текста. */
    fun chapterStart(paras: List<Para>, para: Int): Int {
        var i = para.coerceIn(0, maxOf(0, paras.lastIndex))
        while (i > 0 && !(paras[i].heading && !paras[i - 1].heading)) i--
        return i
    }

    // ---------- кэш страниц на диске: повторное открытие мгновенное ----------

    fun cacheFile(cacheDir: File, bookId: String, st: PageStyle) = File(cacheDir, "pages/${bookId}_${st.key()}")

    fun load(f: File): List<List<Slice>>? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { i ->
            List(i.readInt()) { List(i.readInt()) { Slice(i.readInt(), i.readInt(), i.readInt()) } }
        }
    }.getOrNull()

    fun save(f: File, bookId: String, pages: List<List<Slice>>) = runCatching {
        f.parentFile?.mkdirs()
        // пишем во временный файл и переименовываем — файл никогда не бывает «наполовину записан»
        val tmp = File(f.parentFile, f.name + ".tmp" + System.nanoTime())
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(pages.size)
            for (pg in pages) { o.writeInt(pg.size); for (s in pg) { o.writeInt(s.para); o.writeInt(s.start); o.writeInt(s.end) } }
        }
        f.parentFile?.listFiles { x -> x.name.startsWith(bookId + "_") && x != tmp && x.name != f.name }?.forEach { it.delete() }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Номер страницы, на которой находится место (абзац, символ). */
    fun pageOf(pages: List<List<Slice>>, para: Int, off: Int): Int {
        var lo = 0; var hi = pages.lastIndex; var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val s = pages[mid].first()
            if (s.para < para || (s.para == para && s.start <= off)) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/**
 * Подготовка книги сразу после загрузки: разбор текста и раскладка по страницам
 * сохраняются в памяти приложения (bookcache), поэтому книга открывается мгновенно.
 */
object BookPrep {
    fun dir(ctx: android.content.Context) = File(ctx.filesDir, "bookcache")
    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences("reader", android.content.Context.MODE_PRIVATE)

    /** Запоминаем размер страницы и шрифт — с ними готовим следующие книги. */
    fun rememberStyle(ctx: android.content.Context, st: PageStyle) {
        sp(ctx).edit().putString("style", "${st.width},${st.height},${st.textPx},${st.line},${st.serif},${st.justify}").apply()
    }

    fun lastStyle(ctx: android.content.Context): PageStyle? = runCatching {
        val p = sp(ctx).getString("style", null)!!.split(',')
        PageStyle(p[0].toInt(), p[1].toInt(), p[2].toFloat(), p[3].toFloat(), p[4].toBoolean(), p[5].toBoolean())
    }.getOrNull()

    fun ready(ctx: android.content.Context, bookId: String): Boolean {
        val st = lastStyle(ctx) ?: return false
        return Paginator.cacheFile(dir(ctx), bookId, st).isFile
    }

    private val locks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    fun lock(bookId: String) = locks.getOrPut(bookId) { kotlinx.coroutines.sync.Mutex() }

    /** Долгая работа — вызывать в фоне. Одна книга не готовится дважды одновременно. */
    suspend fun prepare(ctx: android.content.Context, bookId: String, file: File, format: String) = lock(bookId).withLock {
        val st = lastStyle(ctx)
        if (st != null && Paginator.cacheFile(dir(ctx), bookId, st).isFile) return@withLock
        val paras = app.calstats.work.EbookParser.load(file, format, dir(ctx))
        if (st == null || paras.isEmpty()) return@withLock
        Paginator.save(Paginator.cacheFile(dir(ctx), bookId, st), bookId, Paginator.paginate(paras, st) {})
    }

    /** Удалить подготовленные данные книги. */
    fun forget(ctx: android.content.Context, bookId: String, file: File?) {
        File(dir(ctx), "pages").listFiles { x -> x.name.startsWith(bookId + "_") }?.forEach { it.delete() }
        if (file != null) File(dir(ctx), "books").listFiles { x -> x.name.startsWith(file.name + ".") }?.forEach { it.delete() }
    }

    /** Удалить книгу целиком: запись, файл, подготовленные страницы. */
    fun delete(ctx: android.content.Context, vm: app.calstats.MainViewModel, b: app.calstats.work.Book) {
        vm.updateWork { w -> w.copy(readBooks = w.readBooks.filter { it.id != b.id }) }
        val f = b.file?.let { File(ctx.filesDir, it) }
        forget(ctx, b.id, f)
        f?.delete()
        vm.message = "Книга «${b.title}» удалена"
    }
}
