package app.calstats

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Учёт алкоголя: для каждого дня — пил (true) / не пил (false) / не отмечено.
 * Хранится в тех же настройках, что и остальное, поэтому входит в автоматическую копию.
 */
class SobrietyStore(context: Context) {
    private val prefs = context.getSharedPreferences("calstats", Context.MODE_PRIVATE)

    fun load(): Map<LocalDate, Boolean> {
        val raw = prefs.getString(KEY_DAYS, null) ?: return emptyMap()
        return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(emptyMap())
    }

    fun save(map: Map<LocalDate, Boolean>) {
        prefs.edit().putString(KEY_DAYS, toJson(map).toString()).apply()
    }

    fun set(date: LocalDate, drank: Boolean?): Map<LocalDate, Boolean> {
        val map = load().toMutableMap()
        if (drank == null) map.remove(date) else map[date] = drank
        save(map)
        return map
    }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER, true)
        set(v) { prefs.edit().putBoolean(KEY_REMINDER, v).apply() }

    var reminderTime: LocalTime
        get() = runCatching { LocalTime.parse(prefs.getString(KEY_TIME, null)) }.getOrDefault(LocalTime.of(10, 0))
        set(v) { prefs.edit().putString(KEY_TIME, v.toString()).apply() }

    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(v) { prefs.edit().putBoolean(KEY_ASKED, v).apply() }

    /** Для файла резервной копии. */
    fun backupJson(): JSONObject = JSONObject().apply {
        put("days", toJson(load()))
        put("reminder", reminderEnabled)
        put("time", reminderTime.toString())
    }

    fun restore(o: JSONObject) {
        o.optJSONObject("days")?.let { save(fromJson(it)) }
        if (o.has("reminder")) reminderEnabled = o.getBoolean("reminder")
        if (o.has("time")) runCatching { reminderTime = LocalTime.parse(o.getString("time")) }
    }

    companion object {
        private const val KEY_DAYS = "drinks"
        private const val KEY_REMINDER = "drink_reminder"
        private const val KEY_TIME = "drink_time"
        private const val KEY_ASKED = "notif_asked"

        fun toJson(map: Map<LocalDate, Boolean>): JSONObject = JSONObject().apply {
            map.toSortedMap().forEach { (d, v) -> put(d.toString(), v) }
        }

        fun fromJson(o: JSONObject): Map<LocalDate, Boolean> {
            val out = HashMap<LocalDate, Boolean>()
            o.keys().forEach { k -> runCatching { out[LocalDate.parse(k)] = o.getBoolean(k) } }
            return out
        }
    }
}

/** Счётчики «без алкоголя». */
object Sobriety {
    class Summary(
        /** Сколько дней подряд без алкоголя на сегодня (null — отметок ещё нет). */
        val streak: Int?,
        /** С какого дня идёт текущая серия. */
        val since: LocalDate?,
        val lastDrank: LocalDate?,
        val longest: Int,
    )

    fun summary(map: Map<LocalDate, Boolean>, today: LocalDate): Summary {
        if (map.isEmpty()) return Summary(null, null, null, 0)
        val drinkDays = map.filter { it.value && !it.key.isAfter(today) }.keys.sorted()
        val first = map.keys.min()
        val lastDrank = drinkDays.lastOrNull()
        val streak: Int
        val since: LocalDate?
        when {
            map[today] == true -> {
                streak = 0
                since = null
            }
            lastDrank != null -> {
                streak = ChronoUnit.DAYS.between(lastDrank, today).toInt()
                since = lastDrank.plusDays(1)
            }
            else -> {
                streak = ChronoUnit.DAYS.between(first, today).toInt() + 1
                since = first
            }
        }
        var longest = streak
        if (drinkDays.isNotEmpty()) {
            if (first.isBefore(drinkDays.first())) {
                longest = maxOf(longest, ChronoUnit.DAYS.between(first, drinkDays.first()).toInt())
            }
            for (i in 0 until drinkDays.size - 1) {
                longest = maxOf(longest, ChronoUnit.DAYS.between(drinkDays[i], drinkDays[i + 1]).toInt() - 1)
            }
        }
        return Summary(streak, since, lastDrank, longest)
    }

    /** «23 дня без алкоголя» и т.п. */
    fun streakText(s: Summary): String = when (s.streak) {
        null -> "Отметок пока нет"
        0 -> "Сегодня отмечено: пил"
        else -> "${s.streak} ${Fmt.plural(s.streak.toLong(), "день", "дня", "дней")} без алкоголя"
    }
}
