package app.calstats.work

import android.content.Context
import app.calstats.TaskStore
import kotlinx.serialization.json.Json
import java.io.File

/** Хранение рабочего пространства в файле work.json (входит в резервную копию Android). */
class WorkStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "work.json")

    private val bak: File get() = File(context.filesDir, "work.json.bak")
    private fun decode(f: File): Workspace? = runCatching { json.decodeFromString(Workspace.serializer(), f.readText()) }.getOrNull()

    /** Время последней записи файла — чтобы не перечитывать его без нужды. */
    fun stamp(): Long = file.lastModified()

    fun load(): Workspace {
        val f = file
        if (f.exists()) {
            decode(f)?.let { return it }
            // Файл повреждён: сохраняем его копию для разбора и берём предыдущую версию, а не пустые данные.
            runCatching { f.copyTo(File(context.filesDir, "work.broken-${System.currentTimeMillis()}.json"), overwrite = true) }
            bak.takeIf { it.exists() }?.let(::decode)?.let { return it }
            return Workspace()
        }
        val migrated = migrateOldTasks()
        save(migrated)
        return migrated
    }

    @Synchronized
    fun save(w: Workspace) {
        val tmp = File(context.filesDir, "work.json.tmp")
        tmp.writeText(json.encodeToString(Workspace.serializer(), w))
        // Предыдущая версия — запасная копия на случай сбоя
        runCatching { if (file.exists() && file.length() > 0) file.copyTo(bak, overwrite = true) }
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Простые задачи из прошлых версий → проект «Личное». */
    private fun migrateOldTasks(): Workspace {
        val old = TaskStore(context).load()
        val project = Project(id = newId(), key = "ЛИЧ", name = "Личное", color = PALETTE[0])
        var n = 1
        val issues = old.map { t ->
            Issue(
                id = newId(),
                projectId = project.id,
                number = n++,
                title = t.title,
                description = t.notes,
                status = if (t.done) Status.DONE else Status.TODO,
                assigneeId = ME,
                due = t.due?.toString(),
                color = t.color,
                created = t.created,
                updated = t.doneAt ?: t.created,
                resolvedAt = t.doneAt,
                order = n.toDouble(),
            )
        }
        return Workspace(projects = listOf(project.copy(nextNumber = n)), issues = issues)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
