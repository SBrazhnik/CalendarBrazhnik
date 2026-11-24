package app.calstats

import app.calstats.work.EbookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EbookTest {
    @Test fun fb2() {
        val x = """<?xml version="1.0" encoding="utf-8"?><FictionBook><description><title-info><book-title>Тест</book-title></title-info></description>
            <body><title><p>Глава 1</p></title><section><p>Первый &amp; абзац&nbsp;текста.</p><p>Второй <emphasis>абзац</emphasis>&#8212;да.</p>
            <poem><stanza><v>Строка один</v><v>Строка два</v></stanza></poem></section></body><body name="notes"><p>сноска</p></body>
            <binary id="x">QUJD</binary></FictionBook>"""
        val p = EbookParser.fb2(x.toByteArray())
        assertEquals(listOf("Глава 1", "Первый & абзац текста.", "Второй абзац—да.", "Строка один", "Строка два"), p.map { it.text })
        assertTrue(p[0].heading); assertTrue(!p[1].heading)
    }

    @Test fun epub() {
        val f = File.createTempFile("book", ".epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/c.opf"/></rootfiles></container>""")
            put("OEBPS/c.opf", """<package><manifest><item id="a" href="ch%201.xhtml" media-type="x"/></manifest><spine><itemref idref="a"/></spine></package>""")
            put("OEBPS/ch 1.xhtml", """<html><head><title>X</title><style>p{}</style></head><body><div><h2>Часть <b>I</b></h2><div><p>Один</p><p>Два<br/>три</p></div>хвост</div></body></html>""")
        }
        val p = EbookParser.epub(f)
        assertEquals(listOf("Часть I", "Один", "Два", "три", "хвост"), p.map { it.text })
        assertTrue(p[0].heading)
        // кэш читается так же
        val cache = File(System.getProperty("java.io.tmpdir"), "ebtest" + System.nanoTime())
        assertEquals(p, EbookParser.load(f, "epub", cache))
        assertEquals(p, EbookParser.load(f, "epub", cache))
    }

    @Test fun bigBookIsFast() {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><FictionBook><body>")
        repeat(150) { c -> sb.append("<section><title><p>Глава $c</p></title>"); repeat(100) { sb.append("<p>Абзац &mdash; с <emphasis>выделением</emphasis> и &laquo;кавычками&raquo;, ").append("слово ".repeat(40)).append("</p>\n") }; sb.append("</section>") }
        sb.append("</body></FictionBook>")
        val b = sb.toString().toByteArray()
        EbookParser.fb2(b)
        val t = System.currentTimeMillis()
        val p = EbookParser.fb2(b)
        val ms = System.currentTimeMillis() - t
        println("EBOOK_BENCH ${b.size / 1000} KB → ${p.size} абзацев за $ms мс")
        assertEquals(150 * 101, p.size)
        assertTrue("разбор слишком медленный: $ms мс", ms < 4000)
    }
}
