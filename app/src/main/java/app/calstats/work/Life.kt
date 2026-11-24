package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/* Модули «Покупки», «Цели», «Дневник», «Авто», «Дом», «Питомцы». */

// ---------- Покупки ----------
@Serializable
data class ShopList(val id: String, val name: String, val emoji: String = "🛒", val archived: Boolean = false)

@Serializable
data class ShopItem(
    val id: String,
    val listId: String,
    val name: String,
    val qty: String = "",
    val done: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val price: Long? = null,
    val store: String = "",
)

/** Цена товара в магазине — для подсказки «где дешевле». */
@Serializable
data class ShopPrice(val name: String, val store: String, val price: Long, val date: String)

object Shop {
    /** Отделы магазина — список идёт в порядке обхода зала. */
    val AISLES: List<Pair<String, List<String>>> = listOf(
        "🥦 Овощи и фрукты" to listOf("яблок", "банан", "помидор", "огур", "картоф", "лук", "морков", "капуст", "лимон", "апельсин", "зелень", "салат", "перец", "чеснок", "ягод", "груш", "мандарин", "кабач", "авокадо", "грибы", "шампиньон"),
        "🍞 Хлеб и выпечка" to listOf("хлеб", "батон", "лаваш", "булк", "багет", "пирож", "лепёшк"),
        "🥛 Молочное и яйца" to listOf("молок", "кефир", "сметан", "творог", "сыр", "йогурт", "масло сливоч", "сливки", "ряженк", "яйц"),
        "🥩 Мясо и рыба" to listOf("курин", "куриц", "филе", "говяд", "свинин", "фарш", "колбас", "сосиск", "ветчин", "рыб", "лосос", "креветк", "индейк", "бекон"),
        "🥫 Бакалея" to listOf("макарон", "паст", "рис", "греч", "овсян", "мука", "сахар", "соль", "масло подсол", "оливков", "консерв", "кофе", "чай", "крупа", "соус", "кетчуп", "майонез", "специ", "хлопья"),
        "🧃 Напитки" to listOf("вода", "сок", "газиров", "пиво", "вино", "лимонад", "морс", "квас"),
        "🍫 Сладкое" to listOf("шоколад", "печенье", "конфет", "торт", "мороженое", "зефир", "вафл", "мёд", "мед ", "варенье"),
        "🧊 Заморозка" to listOf("пельмен", "заморож", "вареник", "наггетс"),
        "🧴 Бытовое и гигиена" to listOf("порошок", "средство", "губк", "бумаг", "салфет", "мыло", "шампун", "зубн", "пакет", "фольг", "гель", "дезодор", "прокладк", "подгузн"),
        "💊 Аптека" to listOf("таблет", "пластыр", "витамин", "капли", "бинт", "мазь", "сироп"),
    )
    const val OTHER = "🛍 Прочее"
    fun aisle(name: String): String { val n = name.lowercase(); return AISLES.firstOrNull { (_, k) -> k.any { n.contains(it) } }?.first ?: OTHER }
    fun aisleOrder(a: String) = AISLES.indexOfFirst { it.first == a }.let { if (it < 0) 99 else it }

    /** Где дешевле: самая низкая известная цена в другом магазине. */
    fun cheaper(prices: List<ShopPrice>, name: String, store: String, price: Long?): ShopPrice? {
        val n = name.trim().lowercase()
        val best = prices.filter { it.name == n }.groupBy { it.store }.mapValues { it.value.maxBy { p -> p.date } }.values.minByOrNull { it.price } ?: return null
        return if (best.store != store && (price == null || best.price < price)) best else null
    }
    /** «молоко 2, хлеб, яйца 10 шт» → отдельные позиции. */
    fun parse(text: String): List<Pair<String, String>> = text.split(',', '\n', ';')
        .map { it.trim() }.filter { it.isNotEmpty() }
        .map { s ->
            val m = Regex("""^(.*?)\s+(\d+(?:[.,]\d+)?\s*(?:шт|кг|г|л|мл|уп|пач)?\.?)$""").find(s)
            if (m != null) m.groupValues[1].trim().replaceFirstChar { it.uppercase() } to m.groupValues[2].trim()
            else s.replaceFirstChar { it.uppercase() } to ""
        }

    fun shareText(l: ShopList, items: List<ShopItem>) =
        "${l.emoji} ${l.name}\n" + items.filter { !it.done }.joinToString("\n") { "• ${it.name}" + if (it.qty.isNotBlank()) " — ${it.qty}" else "" }
}

// ---------- Цели ----------
@Serializable
data class GoalStep(val id: String, val title: String, val done: Boolean = false)

@Serializable
data class LifeGoal(
    val id: String,
    val title: String,
    val area: String = "Личное",
    val emoji: String = "🎯",
    val year: Int = LocalDate.now().year,
    val deadline: String? = null,
    val steps: List<GoalStep> = emptyList(),
    val why: String = "",
    val done: Boolean = false,
    /** Автопрогресс: "save:<id>", "savings", "habit:<id>", "weight", "debts", "journal", "project:<id>". */
    val link: String? = null,
    val target: Double? = null,
    /** Стартовое значение (вес, долг) на момент привязки. */
    val start: Double? = null,
) {
    val progress: Float get() = when {
        done -> 1f
        steps.isEmpty() -> 0f
        else -> steps.count { it.done }.toFloat() / steps.size
    }
}

/** Прогресс цели из данных других модулей. */
object GoalAuto {
    data class P(val value: Float, val label: String)

    data class Option(val link: String, val title: String, val needsTarget: String?)

    fun options(w: Workspace): List<Option> =
        w.goals.filter { !it.done }.map { Option("save:${it.id}", "${it.emoji} Накопление «${it.name}»", null) } +
            Option("savings", "💰 Сумма накоплений", "Сколько накопить, ₽") +
            w.habits.filter { !it.archived && it.kind == HabitKind.DO }.map { Option("habit:${it.id}", "${it.emoji} Привычка «${it.name}»", "Сколько раз за год") } +
            Option("weight", "⚖ Вес", "Целевой вес, кг") +
            Option("debts", "🤝 Закрыть свои долги", null) +
            Option("journal", "📔 Дни с записью в дневнике", "Сколько дней за год") +
            Option("books", "📖 Прочитать книг за год", "Сколько книг") +
            Option("workouts", "🏋 Тренировок за год", "Сколько тренировок") +
            w.projects.filter { !it.archived }.map { Option("project:${it.id}", "✅ Задачи проекта «${it.name}»", null) }

    /** Стартовое значение при привязке (для веса и долгов). */
    fun startValue(link: String, w: Workspace, today: LocalDate): Double? = when (link) {
        "weight" -> w.weightLog.maxByOrNull { it.date }?.kg
        "debts" -> DebtMath.summary(w, today).iOwe / 100.0
        else -> null
    }

    fun progress(g: LifeGoal, w: Workspace, today: LocalDate): P? {
        val link = g.link ?: return null
        val money = { k: Long -> SplitMath.money(k) }
        return when {
            link.startsWith("save:") -> w.goals.firstOrNull { it.id == link.removePrefix("save:") }?.let {
                P(if (it.target > 0) it.saved.toFloat() / it.target else 0f, "${money(it.saved)} из ${money(it.target)}")
            }
            link == "savings" -> g.target?.let { t ->
                val s = DebtMath.savings(w); P((s / 100.0 / t).toFloat(), "${money(s)} из ${money((t * 100).toLong())}")
            }
            link.startsWith("habit:") -> w.habits.firstOrNull { it.id == link.removePrefix("habit:") }?.let { h ->
                val n = w.habitLogs.count { it.habitId == h.id && it.date.startsWith("${g.year}") && it.value >= h.target }
                val t = g.target ?: 100.0
                P((n / t).toFloat(), "$n из ${t.toInt()} раз")
            }
            link == "weight" -> {
                val cur = w.weightLog.maxByOrNull { it.date }?.kg ?: return null
                val st = g.start ?: return null; val t = g.target ?: return null
                val f = if (st == t) 1f else ((st - cur) / (st - t)).toFloat()
                P(f, "сейчас ${"%.1f".format(cur)} кг, цель ${"%.1f".format(t)}")
            }
            link == "debts" -> {
                val st = g.start ?: return null
                val left = DebtMath.summary(w, today).iOwe / 100.0
                P(if (st <= 0) 1f else ((st - left) / st).toFloat(), "осталось ${money((left * 100).toLong())}")
            }
            link == "journal" -> {
                val n = w.journal.count { it.date.startsWith("${g.year}") && (it.text.isNotBlank() || it.best.isNotBlank() || it.gratitude.isNotEmpty()) }
                val t = g.target ?: 200.0
                P((n / t).toFloat(), "$n из ${t.toInt()} дней")
            }
            link == "books" -> {
                val n = Reading.doneThisYear(w, g.year); val t = g.target ?: w.readingGoal.toDouble()
                P((n / t).toFloat(), "$n из ${t.toInt()} книг")
            }
            link == "workouts" -> {
                val n = w.workouts.count { it.date.startsWith("${g.year}") }; val t = g.target ?: 100.0
                P((n / t).toFloat(), "$n из ${t.toInt()} тренировок")
            }
            link.startsWith("project:") -> {
                val l = w.issues.filter { it.projectId == link.removePrefix("project:") }
                if (l.isEmpty()) P(0f, "задач пока нет") else P(l.count { it.done }.toFloat() / l.size, "${l.count { it.done }} из ${l.size} задач")
            }
            else -> null
        }?.let { it.copy(value = it.value.coerceIn(0f, 1f)) }
    }
}

/** Прогресс с учётом автопривязки (если есть — по данным, иначе по шагам). */
fun LifeGoal.progressIn(w: Workspace, today: LocalDate): Float = if (done) 1f else GoalAuto.progress(this, w, today)?.value ?: progress

val GOAL_AREAS = listOf("Здоровье" to "💪", "Финансы" to "💰", "Карьера" to "💼", "Семья" to "👨‍👩‍👧", "Учёба" to "📚", "Путешествия" to "✈", "Дом" to "🏠", "Личное" to "🌱")

// ---------- Дневник ----------
@Serializable
data class JournalEntry(
    val date: String,
    val text: String = "",
    val gratitude: List<String> = emptyList(),
    val best: String = "",
    val updated: Long = System.currentTimeMillis(),
)

object Journal {
    val PROMPTS = listOf(
        "Что сегодня получилось лучше всего?", "Чему я сегодня научился?", "Что бы я сделал иначе?",
        "Кто сегодня меня порадовал?", "Что меня беспокоит и что я могу с этим сделать?",
        "Какой маленький шаг к цели я сделал?", "За что я могу себя похвалить?",
    )

    fun prompt(d: LocalDate) = PROMPTS[(d.dayOfYear) % PROMPTS.size]

    fun streak(entries: List<JournalEntry>, today: LocalDate): Int {
        val days = entries.filter { it.text.isNotBlank() || it.gratitude.any { g -> g.isNotBlank() } }.map { it.date }.toSet()
        var d = if (today.toString() in days) today else today.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }
}

// ---------- Авто ----------
@Serializable
data class Car(val id: String, val name: String, val plate: String = "", val year: Int? = null, val serviceKm: Int = 10_000, val serviceMonths: Int = 12)

enum class CarEventType(val title: String, val emoji: String) {
    FUEL("Заправка", "⛽"), SERVICE("ТО", "🔧"), REPAIR("Ремонт", "🛠"), TIRES("Шины", "🛞"), WASH("Мойка", "🧽"),
    INSURANCE("Страховка", "🛡"), FINE("Штраф", "🚓"), PARKING("Парковка", "🅿"), OTHER("Другое", "🚗"),
}

@Serializable
data class CarEvent(
    val id: String,
    val carId: String,
    val type: CarEventType,
    val date: String,
    val km: Int? = null,
    val cost: Long = 0,
    val liters: Double? = null,
    val note: String = "",
)

object CarMath {
    fun lastKm(events: List<CarEvent>, carId: String) = events.filter { it.carId == carId }.mapNotNull { it.km }.maxOrNull()

    /** Средний расход л/100 км по заправкам «до полного бака» (по всем заправкам с пробегом). */
    fun consumption(events: List<CarEvent>, carId: String): Double? {
        val f = events.filter { it.carId == carId && it.type == CarEventType.FUEL && it.km != null && it.liters != null }.sortedBy { it.km }
        if (f.size < 2) return null
        val km = f.last().km!! - f.first().km!!
        val l = f.drop(1).sumOf { it.liters!! }
        return if (km > 0) l / km * 100 else null
    }

    data class NextService(val km: Int?, val date: LocalDate?, val kmLeft: Int?, val daysLeft: Long?)

    fun nextService(c: Car, events: List<CarEvent>, today: LocalDate): NextService? {
        val last = events.filter { it.carId == c.id && it.type == CarEventType.SERVICE }.maxByOrNull { it.date } ?: return null
        val km = last.km?.plus(c.serviceKm)
        val date = LocalDate.parse(last.date).plusMonths(c.serviceMonths.toLong())
        val cur = lastKm(events, c.id)
        return NextService(km, date, if (km != null && cur != null) km - cur else null, ChronoUnit.DAYS.between(today, date))
    }

    fun monthCost(events: List<CarEvent>, carId: String, ym: java.time.YearMonth) =
        events.filter { it.carId == carId && runCatching { java.time.YearMonth.from(LocalDate.parse(it.date)) == ym }.getOrDefault(false) }.sumOf { it.cost }
}

// ---------- Дом ----------
@Serializable
data class Meter(val id: String, val name: String, val emoji: String = "💧", val unit: String = "м³", val tariff: Long = 0)

@Serializable
data class MeterReading(val meterId: String, val date: String, val value: Double)

val METER_TEMPLATES = listOf(
    Meter("t1", "Холодная вода", "🚰", "м³"), Meter("t2", "Горячая вода", "♨", "м³"),
    Meter("t3", "Электричество", "⚡", "кВт·ч"), Meter("t4", "Газ", "🔥", "м³"), Meter("t5", "Отопление", "🌡", "Гкал"),
)

object Meters {
    /** Расход между двумя последними показаниями. */
    fun lastUse(readings: List<MeterReading>, id: String): Double? {
        val r = readings.filter { it.meterId == id }.sortedBy { it.date }
        if (r.size < 2) return null
        return r.last().value - r[r.size - 2].value
    }
}

// ---------- Питомцы ----------
@Serializable
data class Pet(val id: String, val name: String, val kind: String = "🐶", val breed: String = "", val birth: String? = null, val chip: String = "")

enum class PetEventType(val title: String, val emoji: String, val repeatMonths: Int?) {
    VACCINE("Прививка", "💉", 12), PARASITES("От паразитов", "🪲", 3), VET("Ветеринар", "🩺", null),
    GROOMING("Груминг", "✂", 2), WEIGHT("Вес", "⚖", null), MEDS("Лекарство", "💊", null), OTHER("Другое", "🐾", null),
}

@Serializable
data class PetEvent(
    val id: String,
    val petId: String,
    val type: PetEventType,
    val date: String,
    val next: String? = null,
    val weight: Double? = null,
    val note: String = "",
)

object Pets {
    fun upcoming(events: List<PetEvent>, today: LocalDate, days: Long = 14): List<PetEvent> = events
        .filter { e -> e.next != null && events.none { o -> o.petId == e.petId && o.type == e.type && o.date > e.date } }
        .filter { val d = LocalDate.parse(it.next); !d.isAfter(today.plusDays(days)) }
        .sortedBy { it.next }

    fun ageText(birth: String?, today: LocalDate): String? {
        val b = birth?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val m = ChronoUnit.MONTHS.between(b, today)
        return if (m < 12) "$m мес." else "${m / 12} г." + if (m % 12 > 0) " ${m % 12} мес." else ""
    }
}
