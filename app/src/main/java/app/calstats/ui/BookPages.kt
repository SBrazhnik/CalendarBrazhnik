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
    fun key() = "${width}x${height}_${textPx.toInt()}_${(line * 100).toInt()}_${serif}_${justify}_v2"

    fun paint(heading: Boolean) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = if (heading) textPx * 1.25f else textPx
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (heading) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Текст абзаца так, как он выводится (с красной строкой). */
    fun text(p: Para): String = if (p.heading) p.text else " " + p.text

    /** Строки абзаца, начиная с символа from (смещения строк — от from). */
    fun layout(text: String, from: Int, heading: Boolean, paint: TextPaint): StaticLayout {
        val t = if (from == 0) text else text.substring(from)
        val b = StaticLayout.Builder.obtain(t, 0, t.length, paint, width)
            .setAlignment(if (heading) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, if (heading) 1.15f else line)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        if (justify && !heading && Build.VERSION.SDK_INT >= 26) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return b.build()
    }
}

/** Рисует одну страницу на Canvas — теми же правилами, что и разбивка. */
class PageRenderer(private val st: PageStyle) {
    private val body = st.paint(false); private val head = st.paint(true)
    fun draw(c: android.graphics.Canvas, paras: List<Para>, page: List<Slice>, color: Int) {
        body.color = color; head.color = color
        var y = 0
        for (s in page) {
            val p = paras.getOrNull(s.para) ?: continue
            val text = st.text(p)
            val l = st.layout(text, s.start, p.heading, if (p.heading) head else body)
            val top = if (p.heading && y > 0 && s.start == 0) st.headTop else 0
            var lines = 0
            while (lines < l.lineCount && s.start + l.getLineEnd(lines) <= s.end) lines++
            if (lines == 0) lines = 1
            val h = l.getLineBottom(lines - 1)
            c.save()
            c.translate(0f, (y + top).toFloat())
            c.clipRect(0, 0, st.width, h)
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
    fun paginate(paras: List<Para>, st: PageStyle, onBatch: (List<List<Slice>>) -> Unit): List<List<Slice>> {
        val body = st.paint(false); val head = st.paint(true)
        val pages = ArrayList<List<Slice>>()
        var cur = ArrayList<Slice>()
        var y = 0
        var lastSent = 0
        fun flush() { if (cur.isNotEmpty()) { pages += cur; cur = ArrayList() }; y = 0 }
        for ((i, p) in paras.withIndex()) {
            // Глава начинается с новой страницы
            if (p.heading && cur.isNotEmpty() && !(paras.getOrNull(i - 1)?.heading ?: false)) flush()
            val text = st.text(p)
            var start = 0
            while (start < text.length) {
                val top = if (p.heading && y > 0 && start == 0) st.headTop else 0
                val l = st.layout(text, start, p.heading, if (p.heading) head else body)
                val avail = st.height - y - top
                var fit = 0
                while (fit < l.lineCount && l.getLineBottom(fit) <= avail) fit++
                if (fit == l.lineCount) {
                    cur += Slice(i, start, text.length)
                    y += top + l.height + if (p.heading) st.headGap else st.gap
                    break
                }
                if (fit == 0) {
                    if (cur.isEmpty()) fit = 1 else { flush(); continue }
                }
                val end = start + l.getLineEnd(fit - 1)
                cur += Slice(i, start, end)
                flush()
                start = end
            }
            if (pages.size - lastSent >= 40) { onBatch(pages.subList(lastSent, pages.size).toList()); lastSent = pages.size }
        }
        flush()
        if (pages.size > lastSent) onBatch(pages.subList(lastSent, pages.size).toList())
        return pages
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
        f.parentFile?.listFiles { x -> x.name.startsWith(bookId + "_") }?.forEach { it.delete() }
        DataOutputStream(f.outputStream().buffered()).use { o ->
            o.writeInt(pages.size)
            for (pg in pages) { o.writeInt(pg.size); for (s in pg) { o.writeInt(s.para); o.writeInt(s.start); o.writeInt(s.end) } }
        }
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
