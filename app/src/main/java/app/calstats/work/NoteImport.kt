package app.calstats.work

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Импорт заметок из других сервисов. */
object NoteImport {
    private val json = Json { ignoreUnknownKeys = true }

    /** Цвета Google Keep → цвета заметок. */
    private val keepColors = mapOf(
        "RED" to 0xFFF28B82, "ORANGE" to 0xFFFBBC04, "YELLOW" to 0xFFFFF475, "GREEN" to 0xFFCCFF90,
        "TEAL" to 0xFFA7FFEB, "BLUE" to 0xFFCBF0F8, "CERULEAN" to 0xFFAECBFA, "PURPLE" to 0xFFD7AEFB,
        "PINK" to 0xFFFDCFE8, "BROWN" to 0xFFE6C9A8, "GRAY" to 0xFFE8EAED,
    ).mapValues { it.value.toInt() }

    /** Одна заметка Google Keep из архива Google Takeout (Keep/*.json). Корзина пропускается. */
    fun parseKeepJson(text: String): Note? {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""
        fun bool(k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
        fun usec(k: String) = o[k]?.jsonPrimitive?.longOrNull?.let { it / 1000 }
        if (bool("isTrashed")) return null
        val list = (o["listContent"] as? JsonArray)?.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            CheckItem(
                newId(),
                item["text"]?.jsonPrimitive?.contentOrNull ?: "",
                item["isChecked"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }.orEmpty()
        val labels = (o["labels"] as? JsonArray)?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        val created = usec("createdTimestampUsec") ?: usec("userEditedTimestampUsec") ?: System.currentTimeMillis()
        val note = Note(
            id = newId(),
            title = str("title"),
            body = str("textContent"),
            checklist = list,
            isChecklist = list.isNotEmpty(),
            color = keepColors[str("color")],
            pinned = bool("isPinned"),
            archived = bool("isArchived"),
            labels = labels,
            source = "Google Keep",
            created = created,
            updated = usec("userEditedTimestampUsec") ?: created,
        )
        return if (note.isEmpty) null else note
    }

    /** Архив Google Takeout (zip): берём все Keep/*.json. */
    fun readKeepZip(input: InputStream): List<Note> {
        val out = ArrayList<Note>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (!e.isDirectory && name.endsWith(".json", ignoreCase = true) && name.contains("Keep", ignoreCase = true)) {
                    parseKeepJson(zip.readBytes().toString(Charsets.UTF_8))?.let { out += it }
                }
            }
        }
        return out
    }

    /** Заметка из текста, присланного другим приложением через «Поделиться». */
    fun fromSharedText(text: String, subject: String?, source: String): Note {
        val t = text.trim()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() && !t.startsWith(it) } ?: ""
        val lines = t.lines()
        // Если пришёл список «☐ / ☑ / - [ ] / • », превращаем его в чек-лист.
        val marks = Regex("""^\s*(☐|☑|✓|✔|- \[ ]|- \[x]|\* \[ ]|\* \[x]|•|-)\s+(.*)$""", RegexOption.IGNORE_CASE)
        val listLines = lines.filter { it.isNotBlank() }
        val isList = listLines.size >= 2 && listLines.drop(if (marks.matches(listLines.first())) 0 else 1).all { marks.matches(it) }
        if (isList) {
            val head = if (marks.matches(listLines.first())) title else listLines.first()
            val items = listLines.drop(if (marks.matches(listLines.first())) 0 else 1).map { l ->
                val m = marks.find(l)!!
                val mark = m.groupValues[1].lowercase()
                CheckItem(newId(), m.groupValues[2], mark == "☑" || mark == "✓" || mark == "✔" || mark.contains("[x]"))
            }
            return Note(newId(), title = head, checklist = items, isChecklist = true, source = source)
        }
        return Note(newId(), title = title, body = t, source = source)
    }
}
