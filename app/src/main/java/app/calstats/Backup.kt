package app.calstats

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Полная резервная копия в одном .zip:
 *  backup.json — направления, все модули (work), алкоголь, настройки;
 *  files/docs/…, files/cards/…, files/avatar*.jpg — фото документов, карт, аватар.
 * Токен Telegram-бота в копию не попадает.
 */
object Backup {
    private val PREFS = listOf("calstats_ui", "notify", "cal_export", "usage")
    private val DIRS = listOf("docs", "cards", "notes", "receipts", "journal")
    private val FILES = listOf("avatar.jpg", "avatar_src.jpg")
    const val KEEP = 7

    fun fileName() = "zelenyi-ritm-${LocalDate.now()}.zip"

    fun prefsJson(ctx: Context): JSONObject {
        val out = JSONObject()
        for (name in PREFS) {
            val o = JSONObject()
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                val t = when (v) {
                    is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"
                    is String -> "s"; is Set<*> -> "S"; else -> null
                } ?: return@forEach
                o.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
            }
            out.put(name, o)
        }
        return out
    }

    fun restorePrefs(ctx: Context, json: JSONObject) {
        for (name in PREFS) {
            val o = json.optJSONObject(name) ?: continue
            val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            o.keys().forEach { k ->
                val x = o.getJSONObject(k)
                when (x.getString("t")) {
                    "b" -> e.putBoolean(k, x.getBoolean("v"))
                    "i" -> e.putInt(k, x.getInt("v"))
                    "l" -> e.putLong(k, x.getLong("v"))
                    "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                    "s" -> e.putString(k, x.getString("v"))
                    "S" -> { val a = x.getJSONArray("v"); e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                }
            }
            e.apply()
        }
    }

    /** Пишет zip. Возвращает число вложенных файлов. */
    fun writeZip(ctx: Context, json: JSONObject, out: OutputStream): Int {
        var n = 0
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(json.toString(2).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            val base = ctx.filesDir
            val files = DIRS.flatMap { d -> File(base, d).walkTopDown().filter { it.isFile }.toList() } +
                FILES.map { File(base, it) }.filter { it.isFile }
            for (f in files) {
                z.putNextEntry(ZipEntry("files/" + f.relativeTo(base).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
                n++
            }
        }
        return n
    }

    /** Читает zip или старый .json. Файлы раскладывает по местам, возвращает backup.json. */
    fun read(ctx: Context, input: InputStream): JSONObject {
        val bytes = input.readBytes()
        if (bytes.size > 1 && bytes[0] == '{'.code.toByte()) return JSONObject(bytes.toString(Charsets.UTF_8))
        if (!(bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())) error("неизвестный формат файла")
        var json: JSONObject? = null
        val base = ctx.filesDir.canonicalFile
        val staged = mutableListOf<Pair<File, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val data = z.readBytes()
                when {
                    e.name == "backup.json" -> json = JSONObject(data.toString(Charsets.UTF_8))
                    e.name.startsWith("files/") -> {
                        val f = File(base, e.name.removePrefix("files/")).canonicalFile
                        // защита от путей вида ../../
                        if (f.path.startsWith(base.path + File.separator)) staged += f to data
                    }
                }
            }
        }
        val j = json ?: error("в архиве нет backup.json")
        if (j.optString("app") != DataStore.APP_MARK) error("это не копия этого приложения")
        // Заменяем папки целиком, только когда архив прочитан полностью
        DIRS.forEach { File(base, it).deleteRecursively() }
        for ((f, data) in staged) { f.parentFile?.mkdirs(); f.writeBytes(data) }
        return j
    }

    // ---------- автокопия в выбранную папку (Яндекс Диск, SD, «Файлы») ----------

    private fun sp(ctx: Context) = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    fun folder(ctx: Context): Uri? = sp(ctx).getString("tree", null)?.let(Uri::parse)
    fun folderName(ctx: Context): String? = sp(ctx).getString("tree_name", null)
    fun lastAuto(ctx: Context): Long = sp(ctx).getLong("last", 0)
    fun lastError(ctx: Context): String? = sp(ctx).getString("err", null)

    fun setFolder(ctx: Context, tree: Uri?) {
        val old = folder(ctx)
        if (old != null && old != tree) runCatching {
            ctx.contentResolver.releasePersistableUriPermission(old, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (tree == null) { sp(ctx).edit().remove("tree").remove("tree_name").apply(); return }
        ctx.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val name = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        sp(ctx).edit().putString("tree", tree.toString()).putString("tree_name", name ?: "выбранная папка").apply()
    }

    fun autoDue(ctx: Context): Boolean = folder(ctx) != null &&
        System.currentTimeMillis() - lastAuto(ctx) > 20 * 3600_000L

    /** Создаёт файл в папке, удаляет старые копии сверх KEEP. */
    fun writeToFolder(ctx: Context, json: JSONObject): String {
        val tree = folder(ctx) ?: error("папка не выбрана")
        val r = ctx.contentResolver
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = fileName()
            // та же дата — перезаписываем
            val existing = list(ctx, tree)
            existing.filter { it.second == name }.forEach { DocumentsContract.deleteDocument(r, it.first) }
            val doc = DocumentsContract.createDocument(r, parent, "application/zip", name) ?: error("папка не даёт создать файл")
            r.openOutputStream(doc)?.use { writeZip(ctx, json, it) } ?: error("файл недоступен")
            list(ctx, tree).filter { it.second.startsWith("zelenyi-ritm-") && it.second.endsWith(".zip") }
                .sortedByDescending { it.second }.drop(KEEP)
                .forEach { runCatching { DocumentsContract.deleteDocument(r, it.first) } }
            sp(ctx).edit().putLong("last", System.currentTimeMillis()).remove("err").apply()
            return name
        } catch (e: Exception) {
            sp(ctx).edit().putString("err", e.message ?: e.javaClass.simpleName).apply()
            throw e
        }
    }

    private fun list(ctx: Context, tree: Uri): List<Pair<Uri, String>> {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = mutableListOf<Pair<Uri, String>>()
        ctx.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to (c.getString(1) ?: "")
        }
        return out
    }
}
