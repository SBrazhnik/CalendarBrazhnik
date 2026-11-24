package app.calstats.work

import java.io.File
import java.util.zip.ZipFile

/** Текст книги: абзацы; heading — заголовок главы (для оглавления). */
data class Para(val text: String, val heading: Boolean = false)

/** Читает EPUB, FB2 и TXT в список абзацев — без сторонних библиотек. */
object EbookParser {
    fun parse(f: File, format: String): List<Para> = when (format.lowercase()) {
        "epub" -> epub(f)
        "fb2" -> fb2(f.readBytes())
        "fb2.zip" -> ZipFile(f).use { z -> z.entries().asSequence().firstOrNull { it.name.endsWith(".fb2") }?.let { fb2(z.getInputStream(it).readBytes()) } ?: emptyList() }
        else -> txt(f.readBytes())
    }.filter { it.text.isNotBlank() }

    private fun decode(b: ByteArray): String {
        val head = String(b, 0, minOf(b.size, 300), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([\\w-]+)").find(head)?.groupValues?.get(1)
        if (enc != null) return runCatching { String(b, charset(enc)) }.getOrElse { String(b, Charsets.UTF_8) }
        val utf = String(b, Charsets.UTF_8)
        // Русские .txt часто в windows-1251
        return if (utf.count { it == '\uFFFD' } > 20) String(b, charset("windows-1251")) else utf
    }

    private fun unescape(s: String) = s.replace("&nbsp;", " ").replace("&#160;", " ").replace("&quot;", "\"").replace("&laquo;", "«").replace("&raquo;", "»")
        .replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…").replace("&lt;", "<").replace("&gt;", ">").replace("&#8212;", "—").replace("&amp;", "&")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }

    private fun clean(s: String) = unescape(s.replace(Regex("<[^>]+>"), "")).replace(Regex("[ \\t\\r\\n]+"), " ").trim()

    fun txt(b: ByteArray): List<Para> {
        val t = decode(b).replace("\r\n", "\n")
        // Абзацы: пустая строка или отступ; длинные строки текста без переносов — как есть
        val blocks = if (t.contains("\n\n")) t.split(Regex("\n\\s*\n")) else t.split('\n')
        return blocks.map { it.replace(Regex("\\s*\n\\s*"), " ").trim() }.filter { it.isNotBlank() }.map { p ->
            Para(p, heading = p.length < 60 && (p == p.uppercase() && p.any { it.isLetter() } || Regex("^(глава|часть|chapter|part)\\b", RegexOption.IGNORE_CASE).containsMatchIn(p)))
        }
    }

    fun fb2(b: ByteArray): List<Para> {
        val x = decode(b)
        val body = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).findAll(x).firstOrNull { !it.value.contains("name=\"notes\"") }?.groupValues?.get(1) ?: x
        val out = mutableListOf<Para>()
        Regex("<title[^>]*>(.*?)</title>|<subtitle[^>]*>(.*?)</subtitle>|<p[^>]*>(.*?)</p>|<v[^>]*>(.*?)</v>|<empty-line\\s*/>", RegexOption.DOT_MATCHES_ALL).findAll(body).forEach { m ->
            when {
                m.groupValues[1].isNotEmpty() -> out += Para(clean(m.groupValues[1].replace("</p>", " ")), true)
                m.groupValues[2].isNotEmpty() -> out += Para(clean(m.groupValues[2]), true)
                m.groupValues[3].isNotEmpty() -> out += Para(clean(m.groupValues[3]))
                m.groupValues[4].isNotEmpty() -> out += Para(clean(m.groupValues[4]))
            }
        }
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

    fun epub(f: File): List<Para> = ZipFile(f).use { z ->
        val opf = opfPath(z) ?: return@use emptyList()
        val base = opf.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val x = String(z.getInputStream(z.getEntry(opf)).readBytes(), Charsets.UTF_8)
        val manifest = Regex("<item\\s[^>]*>").findAll(x).associate { m ->
            val id = Regex("id=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1).orEmpty()
            val href = Regex("href=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1).orEmpty()
            id to java.net.URLDecoder.decode(href, "UTF-8")
        }
        val spine = Regex("<itemref\\s[^>]*idref=\"([^\"]+)\"").findAll(x).map { it.groupValues[1] }.toList()
        val out = mutableListOf<Para>()
        for (id in spine) {
            val href = manifest[id] ?: continue
            val e = z.getEntry(base + href) ?: z.getEntry(href) ?: continue
            val html = String(z.getInputStream(e).readBytes(), Charsets.UTF_8)
            val body = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: html
            val noScript = body.replace(Regex("<(script|style)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), "")
            Regex("<(h[1-6]|p|div|li|blockquote)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(noScript).forEach { m ->
                val inner = m.groupValues[2]
                if (m.groupValues[1] == "div" && Regex("<(p|div|h[1-6])[\\s>]").containsMatchIn(inner)) {
                    // вложенные блоки — разберём их содержимое отдельно
                    Regex("<(h[1-6]|p)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(inner).forEach { n -> out += Para(clean(n.groupValues[2]), n.groupValues[1].startsWith("h")) }
                } else out += Para(clean(inner.replace(Regex("<br\\s*/?>"), " ")), m.groupValues[1].startsWith("h"))
            }
        }
        out
    }
}
