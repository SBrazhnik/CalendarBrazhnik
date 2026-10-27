package app.calstats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Хранит названия направлений и настройки. Файл настроек входит в
 * автоматическую резервную копию Android и выгружается вручную в JSON.
 */
class DataStore(context: Context) {
    private val prefs = context.getSharedPreferences("calstats", Context.MODE_PRIVATE)

    fun load(): AppData {
        val raw = prefs.getString(KEY, null) ?: return AppData()
        return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(AppData())
    }

    fun save(data: AppData) {
        prefs.edit().putString(KEY, toJson(data).toString()).apply()
    }

    companion object {
        private const val KEY = "data"
        const val APP_MARK = "calendar-brazhnik"

        fun colorToHex(color: Int): String = String.format("#%08X", color)
        fun hexToColor(hex: String): Int = java.lang.Long.parseLong(hex.removePrefix("#"), 16).toInt()

        fun toJson(d: AppData): JSONObject = JSONObject().apply {
            put("app", APP_MARK)
            put("version", 1)
            put("created", LocalDateTime.now().toString())
            put("directions", JSONArray().apply {
                d.directions.values.sortedBy { it.name }.forEach { s ->
                    put(JSONObject().apply {
                        put("color", colorToHex(s.color))
                        put("name", s.name)
                        put("hidden", s.hidden)
                    })
                }
            })
            put("excludedCalendars", JSONArray().apply { d.excludedCalendars.sorted().forEach { put(it) } })
            put("includeFuture", d.includeFuture)
            d.lastCalendarKey?.let { put("lastCalendarKey", it) }
        }

        fun fromJson(o: JSONObject): AppData {
            val dirs = HashMap<Int, DirectionSetting>()
            o.optJSONArray("directions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val color = hexToColor(item.getString("color"))
                    dirs[color] = DirectionSetting(color, item.optString("name", ""), item.optBoolean("hidden", false))
                }
            }
            val excluded = HashSet<String>()
            o.optJSONArray("excludedCalendars")?.let { arr ->
                for (i in 0 until arr.length()) excluded += arr.getString(i)
            }
            return AppData(
                directions = dirs,
                excludedCalendars = excluded,
                includeFuture = o.optBoolean("includeFuture", false),
                lastCalendarKey = if (o.has("lastCalendarKey")) o.getString("lastCalendarKey") else null,
            )
        }
    }
}
