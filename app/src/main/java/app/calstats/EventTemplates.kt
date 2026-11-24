package app.calstats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Шаблоны событий: название, длительность, напоминание (например, «выйти заранее» за дорогу). */
object EventTemplates {
    data class T(val title: String, val minutes: Int, val remind: Int)

    val DEFAULTS = listOf(T("Созвон", 30, 10), T("Встреча", 60, 30), T("Тренировка", 60, 30), T("Врач", 60, 60), T("Обед", 60, 15), T("Фокус-работа", 90, 5))

    private fun sp(ctx: Context) = ctx.getSharedPreferences("event_tpl", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<T> {
        val raw = sp(ctx).getString("list", null) ?: return DEFAULTS
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> T(o.getString("t"), o.getInt("m"), o.optInt("r", 15)) } }
        }.getOrDefault(DEFAULTS)
    }

    fun save(ctx: Context, l: List<T>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.title).put("m", it.minutes).put("r", it.remind)) }
        sp(ctx).edit().putString("list", a.toString()).apply()
    }

    fun add(ctx: Context, t: T) = save(ctx, (listOf(t) + list(ctx).filter { it.title != t.title }).take(12))
    fun remove(ctx: Context, title: String) = save(ctx, list(ctx).filter { it.title != title })
}
