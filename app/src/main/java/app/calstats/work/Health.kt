package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Модуль «Здоровье и питание»: профиль и нормы, дневник питания, вода, вес.
 * Шаги, сон, пульс и тренировки читаются из Health Connect (туда их пишет Samsung Health)
 * и не хранятся у нас — только показываются.
 */

enum class Sex(val title: String) { MALE("Мужской"), FEMALE("Женский") }

enum class Activity(val title: String, val factor: Double) {
    LOW("Сидячий образ жизни", 1.2),
    LIGHT("Лёгкая активность 1–3 раза в неделю", 1.375),
    MODERATE("Тренировки 3–5 раз в неделю", 1.55),
    HIGH("Тренировки 6–7 раз в неделю", 1.725),
}

enum class WeightGoal(val title: String, val kcalFactor: Double) {
    LOSE("Снизить вес", 0.85),
    KEEP("Поддерживать", 1.0),
    GAIN("Набрать", 1.1),
}

@Serializable
data class HealthProfile(
    val sex: Sex = Sex.MALE,
    val birthYear: Int = 1990,
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val activity: Activity = Activity.LIGHT,
    val goal: WeightGoal = WeightGoal.KEEP,
    val stepsGoal: Int = 8000,
    val sleepGoalMin: Int = 8 * 60,
)

enum class Meal(val title: String, val emoji: String) {
    BREAKFAST("Завтрак", "🍳"), LUNCH("Обед", "🍲"), DINNER("Ужин", "🍽"), SNACK("Перекус", "🍎"),
}

/** Продукт: значения на 100 г. */
@Serializable
data class FoodItem(
    val name: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val barcode: String? = null,
    val portion: Int = 100,
)

/** Запись в дневнике питания (значения уже пересчитаны на вес порции). */
@Serializable
data class FoodEntry(
    val id: String,
    val date: String,
    val meal: Meal,
    val name: String,
    val grams: Int,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val created: Long = System.currentTimeMillis(),
)

@Serializable
data class WaterEntry(val date: String, val ml: Int)

@Serializable
data class WeightEntry(val date: String, val kg: Double)

data class Norms(val kcal: Int, val protein: Int, val fat: Int, val carbs: Int, val waterMl: Int)

data class DayTotals(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

object HealthMath {
    /** Базовый обмен по формуле Миффлина — Сан Жеора. */
    fun bmr(p: HealthProfile, today: LocalDate): Double {
        val age = (today.year - p.birthYear).coerceIn(14, 100)
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    /**
     * Нормы на день. Дефицит при снижении веса мягкий (15%) и не ниже безопасного минимума;
     * белок 1,6 г/кг, жиры 30% калорий, углеводы — остальное; вода 30 мл/кг.
     */
    fun norms(p: HealthProfile, today: LocalDate): Norms {
        val tdee = bmr(p, today) * p.activity.factor
        val floor = if (p.sex == Sex.MALE) 1500.0 else 1200.0
        val kcal = (tdee * p.goal.kcalFactor).coerceAtLeast(floor)
        val protein = (1.6 * p.weightKg).coerceAtMost(kcal * 0.35 / 4)
        val fat = kcal * 0.30 / 9
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(0.0)
        return Norms(kcal.roundToInt(), protein.roundToInt(), fat.roundToInt(), carbs.roundToInt(), (p.weightKg * 30).roundToInt())
    }

    fun totals(entries: List<FoodEntry>) = DayTotals(
        entries.sumOf { it.kcal }, entries.sumOf { it.protein }, entries.sumOf { it.fat }, entries.sumOf { it.carbs },
    )

    fun portion(f: FoodItem, grams: Int, date: LocalDate, meal: Meal) = FoodEntry(
        id = newId(), date = date.toString(), meal = meal, name = f.name, grams = grams,
        kcal = f.kcal * grams / 100, protein = f.protein * grams / 100, fat = f.fat * grams / 100, carbs = f.carbs * grams / 100,
    )

    fun bmi(p: HealthProfile): Double = p.weightKg / ((p.heightCm / 100.0) * (p.heightCm / 100.0))

    fun bmiText(b: Double) = when {
        b < 18.5 -> "ниже нормы"
        b < 25 -> "в норме"
        b < 30 -> "выше нормы"
        else -> "значительно выше нормы"
    }
}

/** Данные из Health Connect за последние дни (по дням, от старых к новым). */
data class HealthSnapshot(
    val days: List<LocalDate> = emptyList(),
    val steps: List<Long> = emptyList(),
    val activeKcal: List<Double> = emptyList(),
    val sleepMin: List<Long> = emptyList(),
    val restingHr: Int? = null,
    val avgHr: Int? = null,
    val weightKg: Double? = null,
    val workouts: List<Workout> = emptyList(),
    val loadedAt: Long = 0,
) {
    val todaySteps: Long get() = steps.lastOrNull() ?: 0
    val lastSleep: Long get() = sleepMin.lastOrNull() ?: 0
}

data class Workout(val title: String, val date: LocalDate, val minutes: Long)

/** Простые советы по данным. Не медицинские назначения. */
object Advice {
    data class Tip(val emoji: String, val title: String, val text: String)

    fun tips(p: HealthProfile?, n: Norms?, today: DayTotals?, waterMl: Int, hc: HealthSnapshot?, hourNow: Int): List<Tip> {
        val out = ArrayList<Tip>()
        if (p == null) {
            out += Tip("🧭", "Заполните профиль", "Рост, вес и цель нужны, чтобы рассчитать норму калорий, белка и воды.")
            return out
        }
        hc?.let { h ->
            val sleep = h.sleepMin.takeLast(3).filter { it > 0 }
            if (sleep.size >= 2 && sleep.average() < 7 * 60) {
                out += Tip("😴", "Недосып", "Последние ночи в среднем ${(sleep.average() / 60).let { "%.1f".format(it) }} ч. Попробуйте лечь на 30 минут раньше.")
            }
            val steps = h.steps.takeLast(7).dropLast(1)
            if (steps.isNotEmpty() && steps.average() < p.stepsGoal * 0.6) {
                out += Tip("🚶", "Мало движения", "За неделю в среднем ${steps.average().toLong()} шагов. Короткая прогулка после обеда — простой способ добрать.")
            }
            if (h.todaySteps >= p.stepsGoal) out += Tip("🏅", "Цель по шагам выполнена", "Сегодня уже ${h.todaySteps} шагов.")
        }
        if (n != null && today != null) {
            if (hourNow >= 15 && waterMl < n.waterMl / 2) out += Tip("💧", "Пейте воду", "Выпито $waterMl мл из ${n.waterMl}. Поставьте стакан рядом.")
            if (hourNow >= 19 && today.protein < n.protein * 0.6) {
                out += Tip("🥚", "Мало белка", "Сегодня ${today.protein.roundToInt()} г из ${n.protein}. Творог, яйца, курица, бобовые помогут.")
            }
            if (today.kcal > n.kcal * 1.15) out += Tip("⚖", "Калорий больше нормы", "Сегодня ${today.kcal.roundToInt()} из ${n.kcal} ккал.")
        }
        return out
    }
}

object HealthOps {
    fun addFood(w: Workspace, e: FoodEntry, item: FoodItem?): Workspace {
        val recent = if (item == null) w.recentFoods else (listOf(item) + w.recentFoods.filter { it.name != item.name }).take(40)
        return w.copy(foodLog = w.foodLog + e, recentFoods = recent)
    }

    fun deleteFood(w: Workspace, id: String) = w.copy(foodLog = w.foodLog.filter { it.id != id })

    fun water(w: Workspace, date: LocalDate) = w.waterLog.filter { it.date == date.toString() }.sumOf { it.ml }

    fun addWater(w: Workspace, date: LocalDate, ml: Int): Workspace {
        val cur = water(w, date)
        val next = (cur + ml).coerceAtLeast(0)
        return w.copy(waterLog = w.waterLog.filter { it.date != date.toString() } + WaterEntry(date.toString(), next))
    }

    fun addWeight(w: Workspace, date: LocalDate, kg: Double): Workspace {
        val list = w.weightLog.filter { it.date != date.toString() } + WeightEntry(date.toString(), kg)
        val prof = w.healthProfile?.copy(weightKg = kg)
        return w.copy(weightLog = list.sortedBy { it.date }, healthProfile = prof ?: w.healthProfile)
    }

    fun saveCustomFood(w: Workspace, f: FoodItem) = w.copy(customFoods = w.customFoods.filter { it.name != f.name } + f)
}

/** Встроенная база частых продуктов (на 100 г): работает без интернета. */
val FOOD_BASE: List<FoodItem> = listOf(
    FoodItem("Яйцо куриное", 157.0, 12.7, 11.5, 0.7, portion = 55),
    FoodItem("Овсянка на воде", 88.0, 3.0, 1.7, 15.0, portion = 250),
    FoodItem("Овсяные хлопья сухие", 352.0, 12.3, 6.2, 61.8, portion = 50),
    FoodItem("Гречка отварная", 110.0, 4.2, 1.1, 21.3, portion = 200),
    FoodItem("Рис отварной", 116.0, 2.2, 0.5, 24.9, portion = 200),
    FoodItem("Макароны отварные", 112.0, 3.5, 0.4, 23.2, portion = 200),
    FoodItem("Картофель отварной", 82.0, 2.0, 0.4, 16.7, portion = 200),
    FoodItem("Картофельное пюре", 106.0, 2.5, 4.2, 14.7, portion = 200),
    FoodItem("Хлеб белый", 265.0, 7.6, 3.3, 49.0, portion = 30),
    FoodItem("Хлеб ржаной", 210.0, 6.6, 1.2, 40.0, portion = 30),
    FoodItem("Куриная грудка отварная", 137.0, 29.8, 1.8, 0.5, portion = 150),
    FoodItem("Куриное бедро запечённое", 195.0, 24.0, 11.0, 0.0, portion = 150),
    FoodItem("Говядина тушёная", 232.0, 16.8, 18.3, 0.0, portion = 150),
    FoodItem("Свинина запечённая", 263.0, 23.0, 19.0, 0.0, portion = 150),
    FoodItem("Котлета домашняя", 250.0, 15.0, 18.0, 8.0, portion = 100),
    FoodItem("Пельмени", 275.0, 11.9, 12.4, 29.0, portion = 250),
    FoodItem("Сосиски", 266.0, 11.0, 23.9, 1.6, portion = 100),
    FoodItem("Колбаса варёная", 257.0, 12.8, 22.2, 1.5, portion = 50),
    FoodItem("Лосось запечённый", 208.0, 22.0, 13.0, 0.0, portion = 150),
    FoodItem("Минтай отварной", 79.0, 17.6, 1.0, 0.0, portion = 150),
    FoodItem("Тунец консервированный", 116.0, 25.5, 1.0, 0.0, portion = 100),
    FoodItem("Творог 5%", 121.0, 17.2, 5.0, 1.8, portion = 150),
    FoodItem("Творог 0%", 71.0, 16.5, 0.0, 1.3, portion = 150),
    FoodItem("Сыр твёрдый", 360.0, 24.0, 29.0, 0.0, portion = 30),
    FoodItem("Молоко 2,5%", 52.0, 2.8, 2.5, 4.7, portion = 200),
    FoodItem("Кефир 2,5%", 50.0, 2.9, 2.5, 4.0, portion = 250),
    FoodItem("Йогурт натуральный", 66.0, 5.0, 3.2, 3.5, portion = 150),
    FoodItem("Сметана 15%", 162.0, 2.6, 15.0, 3.0, portion = 30),
    FoodItem("Масло сливочное", 748.0, 0.5, 82.5, 0.8, portion = 10),
    FoodItem("Масло подсолнечное", 899.0, 0.0, 99.9, 0.0, portion = 10),
    FoodItem("Огурец", 15.0, 0.8, 0.1, 2.8, portion = 120),
    FoodItem("Помидор", 20.0, 1.1, 0.2, 3.7, portion = 120),
    FoodItem("Салат овощной с маслом", 90.0, 1.2, 7.0, 5.0, portion = 150),
    FoodItem("Капуста свежая", 28.0, 1.8, 0.1, 4.7, portion = 100),
    FoodItem("Морковь", 35.0, 1.3, 0.1, 6.9, portion = 80),
    FoodItem("Брокколи", 34.0, 2.8, 0.4, 6.6, portion = 150),
    FoodItem("Банан", 96.0, 1.5, 0.2, 21.8, portion = 120),
    FoodItem("Яблоко", 47.0, 0.4, 0.4, 9.8, portion = 180),
    FoodItem("Апельсин", 43.0, 0.9, 0.2, 8.1, portion = 180),
    FoodItem("Груша", 47.0, 0.4, 0.3, 10.3, portion = 170),
    FoodItem("Виноград", 72.0, 0.6, 0.6, 15.4, portion = 150),
    FoodItem("Мандарин", 38.0, 0.8, 0.2, 7.5, portion = 80),
    FoodItem("Орехи грецкие", 654.0, 15.2, 65.2, 7.0, portion = 30),
    FoodItem("Миндаль", 609.0, 18.6, 53.7, 13.0, portion = 30),
    FoodItem("Борщ", 49.0, 1.1, 2.2, 6.7, portion = 300),
    FoodItem("Суп куриный с лапшой", 50.0, 3.0, 1.5, 6.0, portion = 300),
    FoodItem("Плов", 190.0, 7.0, 8.5, 21.0, portion = 250),
    FoodItem("Блины", 233.0, 6.1, 12.3, 26.0, portion = 60),
    FoodItem("Сырники", 220.0, 14.0, 10.0, 18.0, portion = 150),
    FoodItem("Пицца", 266.0, 11.0, 10.0, 33.0, portion = 150),
    FoodItem("Бургер", 254.0, 13.0, 12.0, 24.0, portion = 200),
    FoodItem("Шаурма", 230.0, 10.0, 12.0, 21.0, portion = 300),
    FoodItem("Роллы «Филадельфия»", 180.0, 8.0, 6.0, 24.0, portion = 200),
    FoodItem("Шоколад молочный", 535.0, 7.6, 29.7, 59.4, portion = 25),
    FoodItem("Печенье", 417.0, 7.5, 11.8, 74.9, portion = 30),
    FoodItem("Мороженое пломбир", 232.0, 3.2, 15.0, 20.8, portion = 80),
    FoodItem("Сахар", 398.0, 0.0, 0.0, 99.7, portion = 5),
    FoodItem("Мёд", 329.0, 0.8, 0.0, 81.5, portion = 15),
    FoodItem("Кофе с молоком", 40.0, 2.0, 1.5, 4.5, portion = 250),
    FoodItem("Капучино", 45.0, 2.4, 2.2, 3.8, portion = 300),
    FoodItem("Чай без сахара", 1.0, 0.0, 0.0, 0.3, portion = 250),
    FoodItem("Сок апельсиновый", 45.0, 0.7, 0.2, 10.4, portion = 250),
    FoodItem("Кола", 42.0, 0.0, 0.0, 10.6, portion = 330),
    FoodItem("Пиво светлое", 43.0, 0.5, 0.0, 3.6, portion = 500),
    FoodItem("Вино сухое", 68.0, 0.1, 0.0, 0.3, portion = 150),
    FoodItem("Протеиновый коктейль", 380.0, 75.0, 5.0, 8.0, portion = 30),
)

/** Статистика здоровья за период и сравнение с предыдущим таким же периодом. */
object HealthStats {
    /** [cur] и [prev] — средние за день (null — нет данных). [goodUp]: true — рост хорош, false — плох, null — нейтрально. */
    data class Metric(val key: String, val title: String, val unit: String, val cur: Double?, val prev: Double?, val goodUp: Boolean?) {
        val deltaPct: Double? get() = if (cur == null || prev == null || prev == 0.0) null else (cur - prev) / prev * 100
    }

    data class Result(val metrics: List<Metric>, val conclusions: List<String>, val loggedDays: Int, val days: Int)

    private fun avg(xs: List<Double>) = if (xs.isEmpty()) null else xs.average()

    fun compute(w: Workspace, h: HealthSnapshot?, today: LocalDate, days: Int): Result {
        val cur = (0 until days).map { today.minusDays(it.toLong()) }
        val prev = (days until days * 2).map { today.minusDays(it.toLong()) }
        val food = w.foodLog.groupBy { it.date }
        fun kcal(ds: List<LocalDate>) = avg(ds.mapNotNull { d -> food[d.toString()]?.sumOf { it.kcal } })
        fun prot(ds: List<LocalDate>) = avg(ds.mapNotNull { d -> food[d.toString()]?.sumOf { it.protein } })
        val water = w.waterLog.associate { it.date to it.ml }
        fun wat(ds: List<LocalDate>) = avg(ds.mapNotNull { d -> water[d.toString()]?.toDouble()?.takeIf { it > 0 } })
        fun hc(ds: List<LocalDate>, series: List<Number>?) = h?.let { s ->
            avg(ds.mapNotNull { d -> s.days.indexOf(d).takeIf { it >= 0 }?.let { series?.getOrNull(it)?.toDouble() }?.takeIf { it > 0 } })
        }
        val weights = w.weightLog.mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e.kg } }.sortedBy { it.first }
        fun weightAt(ds: List<LocalDate>) = weights.filter { it.first in ds }.lastOrNull()?.second
        val metrics = listOf(
            Metric("kcal", "Калории", "ккал", kcal(cur), kcal(prev), null),
            Metric("protein", "Белок", "г", prot(cur), prot(prev), true),
            Metric("water", "Вода", "мл", wat(cur), wat(prev), true),
            Metric("steps", "Шаги", "", hc(cur.filter { it != today }, h?.steps), hc(prev, h?.steps), true),
            Metric("sleep", "Сон", "ч", hc(cur, h?.sleepMin)?.div(60), hc(prev, h?.sleepMin)?.div(60), true),
            Metric("weight", "Вес", "кг", weightAt(cur), weightAt(prev), null),
            Metric("mood", "Настроение", "из 5", avg(w.moods.filter { runCatching { LocalDate.parse(it.date) in cur }.getOrDefault(false) }.map { it.mood.toDouble() }),
                avg(w.moods.filter { runCatching { LocalDate.parse(it.date) in prev }.getOrDefault(false) }.map { it.mood.toDouble() }), true),
        )
        val logged = cur.count { food.containsKey(it.toString()) }
        return Result(metrics, conclusions(w, metrics, logged, days, today), logged, days)
    }

    private fun conclusions(w: Workspace, m: List<Metric>, logged: Int, days: Int, today: LocalDate): List<String> {
        val out = ArrayList<String>()
        val by = m.associateBy { it.key }
        val p = w.healthProfile
        val n = p?.let { HealthMath.norms(it, today) }
        val period = if (days == 7) "неделю" else "месяц"
        if (logged < days / 2) out += "📝 Питание записано $logged из $days дней — чем полнее дневник, тем точнее выводы."
        by["kcal"]?.cur?.let { k ->
            if (n != null) {
                val diff = (k - n.kcal) / n.kcal * 100
                out += when {
                    diff > 10 -> "🍽 В среднем ${k.roundToInt()} ккал в день — на ${diff.roundToInt()}% выше нормы (${n.kcal})."
                    diff < -20 -> "🍽 В среднем ${k.roundToInt()} ккал — заметно ниже нормы (${n.kcal}). Возможно, записано не всё."
                    else -> "✅ Калорийность в пределах нормы: в среднем ${k.roundToInt()} из ${n.kcal} ккал."
                }
            }
        }
        by["protein"]?.cur?.let { pr -> if (n != null && pr < n.protein * 0.75) out += "🥚 Белка в среднем ${pr.roundToInt()} г из ${n.protein} — стоит добавить." }
        by["water"]?.cur?.let { wa -> if (n != null && wa < n.waterMl * 0.7) out += "💧 Воды в среднем ${wa.roundToInt()} мл при норме ${n.waterMl}." }
        by["steps"]?.let { s ->
            val d = s.deltaPct
            if (s.cur != null && d != null && kotlin.math.abs(d) >= 10) {
                out += if (d > 0) "🚶 Шагов больше на ${d.roundToInt()}%, чем за прошлый $period. Так держать!"
                else "🚶 Шагов меньше на ${(-d).roundToInt()}%, чем за прошлый $period."
            }
            if (s.cur != null && p != null && s.cur >= p.stepsGoal) out += "🏅 Цель по шагам выполняется: в среднем ${s.cur.roundToInt()} в день."
        }
        by["sleep"]?.cur?.let { sl ->
            if (sl < 7) out += "😴 Сон в среднем ${"%.1f".format(sl)} ч — меньше рекомендуемых 7–9 ч."
            else out += "😴 Сон в норме: в среднем ${"%.1f".format(sl)} ч."
        }
        by["weight"]?.let { wt ->
            if (wt.cur != null && wt.prev != null) {
                val d = wt.cur - wt.prev
                if (kotlin.math.abs(d) >= 0.2) {
                    val goodDir = when (p?.goal) { WeightGoal.LOSE -> d < 0; WeightGoal.GAIN -> d > 0; else -> null }
                    out += "⚖ Вес ${if (d > 0) "вырос" else "снизился"} на ${"%.1f".format(kotlin.math.abs(d))} кг" +
                        when (goodDir) { true -> " — в сторону цели 👍"; false -> " — против цели"; null -> "" }
                }
            }
        }
        return out
    }
}
