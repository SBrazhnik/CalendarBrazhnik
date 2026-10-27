package app.calstats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Задачи в SharedPreferences: попадают в автоматическую копию Android и в файл копии. */
class TaskStore(context: Context) {
    private val prefs = context.getSharedPreferences("calstats", Context.MODE_PRIVATE)

    fun load(): List<TaskItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { fromJson(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    fun save(tasks: List<TaskItem>) {
        prefs.edit().putString(KEY, toJson(tasks).toString()).apply()
    }

    companion object {
        private const val KEY = "tasks"

        fun toJson(tasks: List<TaskItem>): JSONArray = JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("notes", t.notes)
                    t.due?.let { put("due", it.toString()) }
                    t.color?.let { put("color", DataStore.colorToHex(it)) }
                    put("done", t.done)
                    t.doneAt?.let { put("doneAt", it) }
                    put("created", t.created)
                })
            }
        }

        fun fromJson(arr: JSONArray): List<TaskItem> {
            val out = ArrayList<TaskItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += TaskItem(
                    id = o.getLong("id"),
                    title = o.optString("title", ""),
                    notes = o.optString("notes", ""),
                    due = if (o.has("due")) LocalDate.parse(o.getString("due")) else null,
                    color = if (o.has("color")) DataStore.hexToColor(o.getString("color")) else null,
                    done = o.optBoolean("done", false),
                    doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
                    created = o.optLong("created", System.currentTimeMillis()),
                )
            }
            return out
        }
    }
}
