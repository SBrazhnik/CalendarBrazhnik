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
                        s.display?.let { put("display", colorToHex(it)) }
                    })
                }
            })
            put("excludedCalendars", JSONArray().apply { d.excludedCalendars.sorted().forEach { put(it) } })
            put("includeFuture", d.includeFuture)
            d.lastCalendarKey?.let { put("lastCalendarKey", it) }
            put("custom", JSONArray().apply {
                d.custom.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("display", colorToHex(c.display))
                        c.eventColor?.let { put("eventColor", colorToHex(it)) }
                        put("keywords", JSONArray().apply { c.keywords.forEach { put(it) } })
                        put("hidden", c.hidden)
                    })
                }
            })
        }

        fun fromJson(o: JSONObject): AppData {
            val dirs = HashMap<Int, DirectionSetting>()
            o.optJSONArray("directions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val color = hexToColor(item.getString("color"))
                    dirs[color] = DirectionSetting(
                        color, item.optString("name", ""), item.optBoolean("hidden", false),
                        item.optString("display", "").takeIf { it.isNotEmpty() }?.let { hexToColor(it) },
                    )
                }
            }
            val excluded = HashSet<String>()
            o.optJSONArray("excludedCalendars")?.let { arr ->
                for (i in 0 until arr.length()) excluded += arr.getString(i)
            }
            val custom = ArrayList<CustomDirection>()
            o.optJSONArray("custom")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val kw = c.optJSONArray("keywords")?.let { k -> (0 until k.length()).map { k.getString(it) } }.orEmpty()
                    custom += CustomDirection(
                        c.getString("id"), c.optString("name", ""), hexToColor(c.getString("display")),
                        c.optString("eventColor", "").takeIf { it.isNotEmpty() }?.let { hexToColor(it) }, kw, c.optBoolean("hidden", false),
                    )
                }
            }
            return AppData(
                custom = custom,
                directions = dirs,
                excludedCalendars = excluded,
                includeFuture = o.optBoolean("includeFuture", false),
                lastCalendarKey = if (o.has("lastCalendarKey")) o.getString("lastCalendarKey") else null,
            )
        }
    }
}
