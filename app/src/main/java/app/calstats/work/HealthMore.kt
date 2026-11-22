package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/* Здоровье: голодание, лекарства, настроение, цикл, рецепты. */

@Serializable
data class Fast(val id: String, val start: Long, val end: Long? = null, val goalH: Int = 16)

@Serializable
data class Med(
    val id: String,
    val name: String,
    val dose: String = "",
    /** Время приёма «ЧЧ:ММ». */
    val times: List<String> = listOf("09:00"),
    /** Дни недели 1..7. */
    val days: List<Int> = (1..7).toList(),
    val until: String? = null,
    val active: Boolean = true,
)

@Serializable
data class MedLog(val medId: String, val date: String, val time: String, val taken: Boolean = true)

@Serializable
data class Mood(val date: String, val mood: Int, val energy: Int = 0, val note: String = "", val tags: List<String> = emptyList())

@Serializable
data class CycleLog(val start: String, val length: Int = 5)

/** Ингредиент рецепта: продукт и вес в граммах. */
@Serializable
data class Ingredient(val item: FoodItem, val grams: Int)

object HealthMore {
    val MOODS = listOf("😣", "😕", "😐", "🙂", "😄")
    val MOOD_TAGS = listOf("работа", "семья", "спорт", "сон", "погода", "здоровье", "друзья", "отдых", "стресс", "учёба")
    val FAST_PRESETS = listOf(12, 14, 16, 18, 20)

    fun activeFast(w: Workspace) = w.fasts.lastOrNull { it.end == null }

    fun medsOn(w: Workspace, d: LocalDate): List<Pair<Med, String>> = w.meds
        .filter { m -> m.active && d.dayOfWeek.value in m.days && (m.until == null || !d.isAfter(LocalDate.parse(m.until))) }
        .flatMap { m -> m.times.sorted().map { m to it } }

    fun taken(w: Workspace, medId: String, d: LocalDate, time: String) =
        w.medLogs.any { it.medId == medId && it.date == d.toString() && it.time == time && it.taken }

    fun toggleMed(w: Workspace, medId: String, d: LocalDate, time: String): Workspace {
        val has = taken(w, medId, d, time)
        val rest = w.medLogs.filter { !(it.medId == medId && it.date == d.toString() && it.time == time) }
        return w.copy(medLogs = if (has) rest else rest + MedLog(medId, d.toString(), time))
    }

    fun setMood(w: Workspace, m: Mood) = w.copy(moods = w.moods.filter { it.date != m.date } + m)

    /** Рецепт → продукт на 100 г; [cooked] — вес готового блюда (если упарилось/разварилось). */
    fun recipe(name: String, ing: List<Ingredient>, cooked: Int?): FoodItem {
        val raw = ing.sumOf { it.grams }.coerceAtLeast(1)
        val total = (cooked ?: raw).coerceAtLeast(1)
        fun per100(f: (FoodItem) -> Double) = ing.sumOf { f(it.item) * it.grams / 100 } * 100 / total
        return FoodItem(name, per100 { it.kcal }, per100 { it.protein }, per100 { it.fat }, per100 { it.carbs }, portion = minOf(total, 400))
    }

    data class CycleInfo(val avgLen: Int, val next: LocalDate, val ovulation: LocalDate, val dayOfCycle: Int, val inPeriod: Boolean)

    /** Прогноз цикла по последним записям (средняя длина, по умолчанию 28). */
    fun cycle(w: Workspace, today: LocalDate): CycleInfo? {
        val starts = w.cycles.mapNotNull { runCatching { LocalDate.parse(it.start) to it.length }.getOrNull() }.sortedBy { it.first }
        val last = starts.lastOrNull() ?: return null
        val gaps = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first).toInt() }.filter { it in 20..45 }.takeLast(6)
        val avg = if (gaps.isEmpty()) 28 else gaps.average().toInt()
        var next = last.first.plusDays(avg.toLong())
        while (next.isBefore(today)) next = next.plusDays(avg.toLong())
        val day = (ChronoUnit.DAYS.between(last.first, today) % avg).toInt() + 1
        return CycleInfo(avg, next, next.minusDays(14), day, ChronoUnit.DAYS.between(last.first, today) < last.second)
    }
}
