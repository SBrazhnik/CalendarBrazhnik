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

/** Давление и пульс. */
@Serializable
data class Pressure(val id: String, val at: Long, val sys: Int, val dia: Int, val pulse: Int = 0, val note: String = "")

/** Самочувствие: симптом и сила 1–5. */
@Serializable
data class Symptom(val id: String, val date: String, val name: String, val level: Int = 3, val note: String = "")

/** Результат анализа с нормой. */
@Serializable
data class LabResult(val id: String, val date: String, val name: String, val value: Double, val unit: String = "", val min: Double? = null, val max: Double? = null, val note: String = "") {
    val status: Int get() = when { min != null && value < min -> -1; max != null && value > max -> 1; else -> 0 }
}

object Vitals {
    /** Категория давления по ВОЗ/ESC. */
    fun category(sys: Int, dia: Int): Pair<String, Int> = when {
        sys >= 180 || dia >= 110 -> "очень высокое" to 3
        sys >= 140 || dia >= 90 -> "высокое" to 2
        sys >= 130 || dia >= 85 -> "повышенное" to 1
        sys < 90 || dia < 60 -> "пониженное" to 1
        else -> "норма" to 0
    }

    /** Частые анализы с нормами для взрослых (ориентир; нормы лаборатории могут отличаться). */
    val LABS = listOf(
        LabResult("", "", "Гемоглобин", 0.0, "г/л", 120.0, 160.0), LabResult("", "", "Глюкоза", 0.0, "ммоль/л", 3.9, 5.6),
        LabResult("", "", "Холестерин общий", 0.0, "ммоль/л", null, 5.2), LabResult("", "", "ЛПНП", 0.0, "ммоль/л", null, 3.0),
        LabResult("", "", "ТТГ", 0.0, "мЕд/л", 0.4, 4.0), LabResult("", "", "Витамин D", 0.0, "нг/мл", 30.0, 100.0),
        LabResult("", "", "Ферритин", 0.0, "нг/мл", 15.0, 150.0), LabResult("", "", "Креатинин", 0.0, "мкмоль/л", 62.0, 106.0),
        LabResult("", "", "АЛТ", 0.0, "Ед/л", null, 41.0), LabResult("", "", "СОЭ", 0.0, "мм/ч", null, 15.0),
        LabResult("", "", "Лейкоциты", 0.0, "×10⁹/л", 4.0, 9.0), LabResult("", "", "Гликированный гемоглобин", 0.0, "%", null, 6.0),
    )
    val SYMPTOMS = listOf("Головная боль", "Усталость", "Боль в спине", "Простуда", "Температура", "Аллергия", "Бессонница", "Тревога", "Боль в животе", "Головокружение")
}

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
