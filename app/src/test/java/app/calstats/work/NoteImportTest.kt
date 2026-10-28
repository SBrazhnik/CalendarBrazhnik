package app.calstats.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NoteImportTest {
    private val keepText = """
        {"color":"YELLOW","isTrashed":false,"isPinned":true,"isArchived":false,
         "textContent":"Купить краску\nи валик","title":"Ремонт",
         "labels":[{"name":"дом"}],
         "userEditedTimestampUsec":1700000000000000,"createdTimestampUsec":1690000000000000}
    """.trimIndent()

    private val keepList = """
        {"isTrashed":false,"title":"Продукты","listContent":[{"text":"Молоко","isChecked":true},{"text":"Хлеб","isChecked":false}],
         "createdTimestampUsec":1690000000000000}
    """.trimIndent()

    @Test fun parsesTextNote() {
        val n = NoteImport.parseKeepJson(keepText)!!
        assertEquals("Ремонт", n.title)
        assertEquals("Купить краску\nи валик", n.body)
        assertTrue(n.pinned)
        assertEquals(listOf("дом"), n.labels)
        assertEquals(1690000000000L, n.created)
    }

    @Test fun parsesChecklist() {
        val n = NoteImport.parseKeepJson(keepList)!!
        assertTrue(n.isChecklist)
        assertEquals(2, n.checklist.size)
        assertTrue(n.checklist[0].checked)
    }

    @Test fun skipsTrash() {
        assertNull(NoteImport.parseKeepJson("""{"isTrashed":true,"title":"x","textContent":"y"}"""))
    }

    @Test fun readsZipAndDedupes() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("Takeout/Keep/a.json")); z.write(keepText.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("Takeout/Keep/b.json")); z.write(keepList.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("Takeout/Keep/a.html")); z.write("<html/>".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val notes = NoteImport.readKeepZip(ByteArrayInputStream(bytes))
        assertEquals(2, notes.size)
        val (w1, n1) = WorkOps.importNotes(Workspace(), notes)
        assertEquals(2, n1)
        val (_, n2) = WorkOps.importNotes(w1, NoteImport.readKeepZip(ByteArrayInputStream(bytes)))
        assertEquals(0, n2)
    }

    @Test fun sharedListBecomesChecklist() {
        val n = NoteImport.fromSharedText("Покупки\n☐ Молоко\n☑ Хлеб", null, "test")
        assertTrue(n.isChecklist)
        assertEquals("Покупки", n.title)
        assertEquals(2, n.checklist.size)
        assertTrue(n.checklist[1].checked)
    }
}
