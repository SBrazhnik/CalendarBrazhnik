package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ======================= МЕНЮ И РЕЦЕПТЫ =======================

@Serializable
data class RecipeIng(val name: String, val qty: String = "")

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val emoji: String = "🍲",
    val minutes: Int? = null,
    val servings: Int = 2,
    val ingredients: List<RecipeIng> = emptyList(),
    val steps: String = "",
    val tags: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
)

enum class MealSlot(val title: String, val emoji: String) { BREAKFAST("Завтрак", "🍳"), LUNCH("Обед", "🍲"), DINNER("Ужин", "🍽"), SNACK("Перекус", "🍎") }

@Serializable
data class MealPlan(val id: String, val date: String, val slot: MealSlot, val recipeId: String? = null, val text: String = "")

object Meals {
    /** Ингредиенты блюд на дни [from, to] — одним списком, одинаковые объединены. */
    fun shopping(w: Workspace, from: LocalDate, to: LocalDate): List<Pair<String, String>> {
        val plans = w.mealPlans.filter { p -> runCatching { LocalDate.parse(p.date) }.getOrNull()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
        val ings = plans.mapNotNull { p -> w.recipes.firstOrNull { it.id == p.recipeId } }.flatMap { it.ingredients }
        return ings.groupBy { it.name.trim().lowercase() }.map { (_, l) ->
            l.first().name.trim().replaceFirstChar { it.uppercase() } to l.map { it.qty }.filter { it.isNotBlank() }.joinToString(" + ")
        }
    }

    val STARTERS = listOf(
        Recipe("s1", "Омлет с помидорами", "🍳", 10, 1, listOf(RecipeIng("Яйца", "3 шт"), RecipeIng("Помидоры", "1 шт"), RecipeIng("Молоко", "50 мл"), RecipeIng("Сыр", "30 г")),
            "Взбить яйца с молоком, добавить нарезанные помидоры, жарить под крышкой 5–7 минут, посыпать сыром.", listOf("завтрак")),
        Recipe("s2", "Овсянка с ягодами", "🥣", 7, 1, listOf(RecipeIng("Овсяные хлопья", "60 г"), RecipeIng("Молоко", "200 мл"), RecipeIng("Ягоды", "80 г"), RecipeIng("Мёд", "1 ч. л.")),
            "Залить хлопья молоком, варить 5 минут, добавить ягоды и мёд.", listOf("завтрак")),
        Recipe("s3", "Куриный суп", "🍲", 60, 4, listOf(RecipeIng("Курица", "500 г"), RecipeIng("Картофель", "4 шт"), RecipeIng("Морковь", "1 шт"), RecipeIng("Лук", "1 шт"), RecipeIng("Вермишель", "80 г")),
            "Сварить бульон 40 минут, добавить картофель, затем зажарку из лука и моркови, в конце вермишель. Посолить.", listOf("обед")),
        Recipe("s4", "Паста с курицей и сливками", "🍝", 25, 2, listOf(RecipeIng("Паста", "200 г"), RecipeIng("Куриное филе", "300 г"), RecipeIng("Сливки", "200 мл"), RecipeIng("Чеснок", "2 зубчика"), RecipeIng("Пармезан", "40 г")),
            "Отварить пасту. Обжарить филе с чесноком, влить сливки, потушить 5 минут, смешать с пастой, посыпать сыром.", listOf("ужин")),
        Recipe("s5", "Гречка с овощами", "🥘", 25, 2, listOf(RecipeIng("Гречка", "160 г"), RecipeIng("Кабачок", "1 шт"), RecipeIng("Перец болгарский", "1 шт"), RecipeIng("Лук", "1 шт")),
            "Отварить гречку. Обжарить овощи 10 минут, смешать с гречкой.", listOf("обед", "ужин")),
        Recipe("s6", "Салат греческий", "🥗", 15, 2, listOf(RecipeIng("Огурцы", "2 шт"), RecipeIng("Помидоры", "2 шт"), RecipeIng("Сыр фета", "150 г"), RecipeIng("Оливки", "70 г"), RecipeIng("Оливковое масло", "2 ст. л.")),
            "Нарезать овощи крупно, добавить фету и оливки, заправить маслом.", listOf("ужин")),
    )
}

// ======================= ТРЕНИРОВКИ =======================

@Serializable
data class Exercise(val id: String, val name: String, val group: String = "", val cardio: Boolean = false)

@Serializable
data class WorkSet(val exerciseId: String, val weight: Double = 0.0, val reps: Int = 0, val minutes: Int = 0)

@Serializable
data class WorkoutLog(val id: String, val date: String, val title: String = "", val sets: List<WorkSet> = emptyList(), val minutes: Int = 0, val note: String = "")

object Training {
    val STARTERS = listOf(
        Exercise("e1", "Приседания", "Ноги"), Exercise("e2", "Жим лёжа", "Грудь"), Exercise("e3", "Становая тяга", "Спина"),
        Exercise("e4", "Подтягивания", "Спина"), Exercise("e5", "Отжимания", "Грудь"), Exercise("e6", "Жим гантелей сидя", "Плечи"),
        Exercise("e7", "Выпады", "Ноги"), Exercise("e8", "Планка", "Пресс"), Exercise("e9", "Бег", "Кардио", cardio = true),
        Exercise("e10", "Велотренажёр", "Кардио", cardio = true),
    )

    /** Лучший вес в упражнении (по 1 повторному максимуму по Эпли). */
    fun best(w: Workspace, exId: String): WorkSet? = w.workouts.flatMap { it.sets }.filter { it.exerciseId == exId && it.weight > 0 }
        .maxByOrNull { it.weight * (1 + it.reps / 30.0) }

    fun last(w: Workspace, exId: String): WorkSet? = w.workouts.sortedByDescending { it.date }.firstNotNullOfOrNull { l -> l.sets.lastOrNull { it.exerciseId == exId } }

    fun volume(l: WorkoutLog) = l.sets.sumOf { it.weight * it.reps }

    fun weekCount(w: Workspace, today: LocalDate): Int {
        val from = today.minusDays(today.dayOfWeek.value - 1L)
        return w.workouts.count { runCatching { !LocalDate.parse(it.date).isBefore(from) }.getOrDefault(false) }
    }
}

// ======================= ПОЕЗДКИ =======================

@Serializable
data class TripItem(val id: String, val text: String, val done: Boolean = false)

@Serializable
data class TripCost(val id: String, val title: String, val amount: Long)

@Serializable
data class Trip(
    val id: String,
    val title: String,
    val place: String = "",
    val start: String,
    val end: String,
    val abroad: Boolean = false,
    val budget: Long = 0,
    val packing: List<TripItem> = emptyList(),
    val bookings: List<TripItem> = emptyList(),
    val costs: List<TripCost> = emptyList(),
    val note: String = "",
) {
    val startDate: LocalDate get() = LocalDate.parse(start)
    val endDate: LocalDate get() = LocalDate.parse(end)
}

object Trips {
    val PACKING = listOf("Документы и билеты", "Банковские карты, наличные", "Телефон, зарядка, пауэрбанк", "Лекарства", "Одежда по погоде",
        "Бельё и носки", "Туалетные принадлежности", "Солнцезащитные очки", "Наушники", "Ключи от дома")
    val ABROAD = listOf("Загранпаспорт", "Страховка", "Виза (если нужна)", "Переходник для розетки", "Валюта")

    /** Предупреждения: загранпаспорт истекает меньше чем через полгода после возвращения и т.п. */
    fun warnings(t: Trip, w: Workspace): List<String> {
        val out = mutableListOf<String>()
        if (t.abroad) {
            val p = w.documents.filter { it.kind == DocKind.INTL_PASSPORT && !it.archived }
            if (p.isEmpty()) out += "Нет загранпаспорта в «Документах»"
            p.forEach { d -> d.expires?.let { LocalDate.parse(it) }?.let { if (it.isBefore(t.endDate.plusMonths(6))) out += "Загранпаспорт «${d.owner.ifBlank { d.title }}» истекает ${it} — многие страны требуют запас 6 месяцев" } }
        }
        w.documents.filter { it.kind == DocKind.PASSPORT && !it.archived }.forEach { d ->
            d.expires?.let { LocalDate.parse(it) }?.let { if (it.isBefore(t.endDate)) out += "Паспорт «${d.owner.ifBlank { d.title }}» истекает до конца поездки" }
        }
        return out
    }

    fun daysLeft(t: Trip, today: LocalDate) = ChronoUnit.DAYS.between(today, t.startDate)
}

// ======================= ЧТЕНИЕ И ОБУЧЕНИЕ =======================

enum class BookKind(val title: String, val emoji: String, val unit: String) { BOOK("Книга", "📖", "стр."), AUDIO("Аудиокнига", "🎧", "ч"), COURSE("Курс", "🎓", "уроков") }
enum class BookStatus(val title: String) { PLAN("Хочу"), ACTIVE("Читаю"), DONE("Прочитано"), DROPPED("Бросил") }

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val kind: BookKind = BookKind.BOOK,
    val total: Int = 0,
    val current: Int = 0,
    val status: BookStatus = BookStatus.PLAN,
    val started: String? = null,
    val finished: String? = null,
    val rating: Int = 0,
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (status == BookStatus.DONE) 1f else if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
}

@Serializable
data class ReadLog(val date: String, val bookId: String, val amount: Int)

object Reading {
    fun doneThisYear(w: Workspace, year: Int) = w.books.count { it.status == BookStatus.DONE && it.finished?.startsWith("$year") == true }
    fun streak(w: Workspace, today: LocalDate): Int {
        val days = w.readLogs.map { it.date }.toHashSet()
        var d = if (today.toString() in days) today else today.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }
}

// ======================= ДЕЛА ПО ДОМУ =======================

@Serializable
data class Chore(
    val id: String,
    val title: String,
    val room: String = "",
    val everyDays: Int = 7,
    val lastDone: String? = null,
    /** Очередь исполнителей: после отметки ход переходит к следующему. */
    val people: List<String> = emptyList(),
    val turn: Int = 0,
) {
    fun due(today: LocalDate): LocalDate = lastDone?.let { LocalDate.parse(it).plusDays(everyDays.toLong()) } ?: today
    fun daysLeft(today: LocalDate) = ChronoUnit.DAYS.between(today, due(today))
    fun whoseTurn(): String? = people.getOrNull(turn % maxOf(1, people.size))
}

object Chores {
    val ROOMS = listOf("Кухня", "Ванная", "Спальня", "Гостиная", "Прихожая", "Балкон", "Вся квартира")
    val STARTERS = listOf(
        Chore("c1", "Пропылесосить", "Вся квартира", 7), Chore("c2", "Помыть полы", "Вся квартира", 7),
        Chore("c3", "Сменить постельное бельё", "Спальня", 14), Chore("c4", "Почистить сантехнику", "Ванная", 7),
        Chore("c5", "Разморозить и помыть холодильник", "Кухня", 90), Chore("c6", "Почистить фильтр вытяжки", "Кухня", 90),
        Chore("c7", "Помыть окна", "Вся квартира", 180), Chore("c8", "Вынести мусор", "Кухня", 2),
        Chore("c9", "Заменить фильтр для воды", "Кухня", 60), Chore("c10", "Почистить стиральную машину", "Ванная", 30),
    )

    fun done(c: Chore, today: LocalDate) = c.copy(lastDone = today.toString(), turn = if (c.people.isEmpty()) 0 else (c.turn + 1) % c.people.size)
}

// ======================= РАСТЕНИЯ =======================

@Serializable
data class Plant(
    val id: String,
    val name: String,
    val place: String = "",
    val emoji: String = "🪴",
    val waterDays: Int = 7,
    val feedDays: Int? = null,
    val lastWater: String? = null,
    val lastFeed: String? = null,
    val note: String = "",
) {
    fun waterIn(today: LocalDate) = lastWater?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it).plusDays(waterDays.toLong())) } ?: 0
    fun feedIn(today: LocalDate) = feedDays?.let { fd -> lastFeed?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it).plusDays(fd.toLong())) } ?: 0 }
}

object Plants {
    /** Популярные растения и как часто поливать. */
    val PRESETS = listOf(
        "Монстера" to 7, "Фикус" to 7, "Замиокулькас" to 14, "Сансевиерия" to 21, "Орхидея" to 7, "Спатифиллум" to 4,
        "Кактус" to 21, "Алоэ" to 14, "Хлорофитум" to 5, "Драцена" to 10, "Пальма" to 5, "Фиалка" to 4,
    )
}
