package app.calstats.work

import java.io.File
import java.util.zip.ZipFile

/** Текст книги: абзацы; heading — заголовок главы (для оглавления). */
data class Para(val text: String, val heading: Boolean = false)

/** Читает EPUB, FB2 и TXT в список абзацев — без сторонних библиотек. Разбор однопроходный, результат кэшируется. */
object EbookParser {
    /** С кэшем: первый раз разбираем файл, дальше читаем готовые абзацы — книга открывается мгновенно. */
    fun load(f: File, format: String, cacheDir: File): List<Para> {
        val c = File(cacheDir, "books/${f.name}.${f.length()}.paras")
        if (c.isFile) runCatching { return readCache(c) }
        val out = parse(f, format)
        runCatching {
            c.parentFile?.mkdirs()
            c.parentFile?.listFiles { x -> x.name.startsWith(f.name + ".") }?.forEach { it.delete() }
            java.io.DataOutputStream(c.outputStream().buffered()).use { o ->
                o.writeInt(out.size)
                for (p in out) { val b = p.text.toByteArray(Charsets.UTF_8); o.writeBoolean(p.heading); o.writeInt(b.size); o.write(b) }
            }
        }
        return out
    }

    private fun readCache(c: File): List<Para> = java.io.DataInputStream(c.inputStream().buffered()).use { i ->
        val n = i.readInt()
        List(n) { val h = i.readBoolean(); val b = ByteArray(i.readInt()); i.readFully(b); Para(String(b, Charsets.UTF_8), h) }
    }

    fun parse(f: File, format: String): List<Para> = when (format.lowercase()) {
        "epub" -> epub(f)
        "fb2" -> fb2(f.readBytes())
        "fb2.zip" -> ZipFile(f).use { z -> z.entries().asSequence().firstOrNull { it.name.endsWith(".fb2") }?.let { fb2(z.getInputStream(it).readBytes()) } ?: emptyList() }
        else -> txt(f.readBytes())
    }.filter { it.text.isNotBlank() }

    private val ENC = Regex("encoding=[\"']([\\w-]+)")
    private fun decode(b: ByteArray): String {
        val head = String(b, 0, minOf(b.size, 300), Charsets.ISO_8859_1)
        val enc = ENC.find(head)?.groupValues?.get(1)
        if (enc != null) return runCatching { String(b, charset(enc)) }.getOrElse { String(b, Charsets.UTF_8) }
        val utf = String(b, Charsets.UTF_8)
        // Русские .txt часто в windows-1251
        return if (utf.count { it == '\uFFFD' } > 20) String(b, charset("windows-1251")) else utf
    }

    private val ENTITIES = mapOf("nbsp" to " ", "quot" to "\"", "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–", "hellip" to "…", "lt" to "<", "gt" to ">",
        "amp" to "&", "apos" to "'", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„", "shy" to "", "thinsp" to " ", "emsp" to " ", "ensp" to " ")

    /** Сущности (&amp; &#8212; &#x2014;) и пробелы — одним проходом. */
    private fun norm(s: CharSequence): String {
        val sb = StringBuilder(s.length)
        var i = 0; var space = true
        while (i < s.length) {
            var ch = s[i]
            if (ch == '&') {
                val semi = s.indexOf(';', i).let { if (it in (i + 2)..(i + 10)) it else -1 }
                if (semi > 0) {
                    val name = s.substring(i + 1, semi)
                    val rep = if (name.startsWith("#")) runCatching {
                        val code = if (name.startsWith("#x") || name.startsWith("#X")) name.substring(2).toInt(16) else name.substring(1).toInt()
                        String(Character.toChars(code))
                    }.getOrNull() else ENTITIES[name]
                    if (rep != null) {
                        for (c in rep) { if (c.isWhitespace()) { if (!space) { sb.append(' '); space = true } } else { sb.append(c); space = false } }
                        i = semi + 1; continue
                    }
                }
            }
            if (ch == '\u00A0') ch = ' '
            if (ch.isWhitespace()) { if (!space) { sb.append(' '); space = true } } else { sb.append(ch); space = false }
            i++
        }
        return sb.toString().trim()
    }

    private val STRIP = Regex("<[^>]+>")
    private fun clean(s: String) = norm(s.replace(STRIP, " "))

    private val TAG = Regex("<!--.*?-->|<!\\[CDATA\\[|\\]\\]>|<([/?!]?)([a-zA-Z][a-zA-Z0-9:_-]*)[^>]*?(/?)>", RegexOption.DOT_MATCHES_ALL)

    /** Однопроходный разбор разметки в абзацы: блочные теги разделяют абзацы, заголовочные помечают главы. */
    private fun blocks(src: String, block: Set<String>, head: Set<String>, skip: Set<String>, out: MutableList<Para>) {
        val sb = StringBuilder()
        var headDepth = 0; var skipDepth = 0; var pos = 0
        fun flush() { val t = norm(sb); sb.setLength(0); if (t.isNotEmpty()) out += Para(t, headDepth > 0) }
        for (m in TAG.findAll(src)) {
            if (skipDepth == 0) sb.append(src, pos, m.range.first)
            pos = m.range.last + 1
            val name = m.groupValues[2].lowercase().substringAfter(':')
            if (name.isEmpty() || m.groupValues[1] == "?" || m.groupValues[1] == "!") continue
            val close = m.groupValues[1] == "/"
            val selfClose = m.groupValues[3] == "/"
            when {
                name in skip -> if (!selfClose) skipDepth = if (close) maxOf(0, skipDepth - 1) else skipDepth + 1
                skipDepth > 0 -> {}
                name == "br" -> flush()
                name in head -> { flush(); if (!selfClose) headDepth = if (close) maxOf(0, headDepth - 1) else headDepth + 1 }
                name in block -> flush()
            }
        }
        if (skipDepth == 0) sb.append(src, pos, src.length)
        flush()
    }

    private val SPLIT2 = Regex("\n\\s*\n")
    private val NL = Regex("\\s*\n\\s*")
    private val CHAPTER = Regex("^(глава|часть|книга|chapter|part|book)[\\s.]", RegexOption.IGNORE_CASE)

    fun txt(b: ByteArray): List<Para> {
        val t = decode(b).replace("\r\n", "\n")
        // Абзацы: пустая строка; если пустых строк нет — каждая строка
        val blocks = if (t.contains("\n\n")) t.split(SPLIT2) else t.split('\n')
        return blocks.map { it.replace(NL, " ").trim() }.filter { it.isNotBlank() }.map { p ->
            Para(p, heading = p.length < 60 && (p == p.uppercase() && p.any { it.isLetter() } || CHAPTER.containsMatchIn(p)))
        }
    }

    private val FB2_BLOCK = setOf("p", "v", "empty-line", "stanza", "text-author", "epigraph", "cite", "poem", "section", "table", "tr")
    private val FB2_HEAD = setOf("title", "subtitle")
    private val FB2_SKIP = setOf("binary", "description", "image")

    fun fb2(b: ByteArray): List<Para> {
        val x = decode(b)
        // Основной текст — первое <body> (во втором обычно сноски)
        val s = x.indexOf("<body").let { if (it < 0) 0 else x.indexOf('>', it) + 1 }
        val e = x.indexOf("</body>", s).let { if (it < 0) x.length else it }
        val out = ArrayList<Para>()
        blocks(x.substring(s, e), FB2_BLOCK, FB2_HEAD, FB2_SKIP, out)
        return out
    }

    /** Название и автор из FB2/EPUB — чтобы не вводить вручную. */
    fun meta(f: File, format: String): Pair<String, String> = runCatching {
        when (format) {
            "epub" -> ZipFile(f).use { z ->
                val opf = opfPath(z) ?: return@use "" to ""
                val x = String(z.getInputStream(z.getEntry(opf)).readBytes(), Charsets.UTF_8)
                clean(Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1).orEmpty()) to
                    clean(Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1).orEmpty())
            }
            "fb2", "fb2.zip" -> {
                val x = if (format == "fb2") decode(f.readBytes()) else ZipFile(f).use { z -> z.entries().asSequence().first { it.name.endsWith(".fb2") }.let { decode(z.getInputStream(it).readBytes()) } }
                val ti = Regex("<title-info>(.*?)</title-info>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1).orEmpty()
                val a = Regex("<author>(.*?)</author>", RegexOption.DOT_MATCHES_ALL).find(ti)?.groupValues?.get(1).orEmpty()
                val name = listOf("first-name", "last-name").mapNotNull { k -> Regex("<$k>(.*?)</$k>").find(a)?.groupValues?.get(1) }.joinToString(" ")
                clean(Regex("<book-title>(.*?)</book-title>").find(ti)?.groupValues?.get(1).orEmpty()) to clean(name)
            }
            else -> "" to ""
        }
    }.getOrDefault("" to "")

    private fun opfPath(z: ZipFile): String? {
        val c = z.getEntry("META-INF/container.xml") ?: return z.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name
        return Regex("full-path=\"([^\"]+)\"").find(String(z.getInputStream(c).readBytes()))?.groupValues?.get(1)
    }

    private val HTML_BLOCK = setOf("p", "div", "li", "blockquote", "tr", "dd", "dt", "section", "article", "pre", "table", "ul", "ol", "hr", "aside", "figure", "figcaption", "center")
    private val HTML_HEAD = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val HTML_SKIP = setOf("head", "script", "style", "svg", "math", "nav")
    private val ITEM = Regex("<item\\s[^>]*>")
    private val ID = Regex("\\sid=\"([^\"]+)\"")
    private val HREF = Regex("href=\"([^\"]+)\"")
    private val ITEMREF = Regex("<itemref\\s[^>]*idref=\"([^\"]+)\"")

    fun epub(f: File): List<Para> = ZipFile(f).use { z ->
        val opf = opfPath(z) ?: return@use emptyList()
        val base = opf.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val x = String(z.getInputStream(z.getEntry(opf)).readBytes(), Charsets.UTF_8)
        val manifest = ITEM.findAll(x).associate { m ->
            val id = ID.find(m.value)?.groupValues?.get(1).orEmpty()
            val href = HREF.find(m.value)?.groupValues?.get(1).orEmpty()
            id to runCatching { java.net.URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
        }
        val spine = ITEMREF.findAll(x).map { it.groupValues[1] }.toList()
        val out = ArrayList<Para>()
        for (id in spine) {
            val href = manifest[id] ?: continue
            val e = z.getEntry(base + href) ?: z.getEntry(href) ?: continue
            blocks(String(z.getInputStream(e).readBytes(), Charsets.UTF_8), HTML_BLOCK, HTML_HEAD, HTML_SKIP, out)
        }
        out
    }
}
