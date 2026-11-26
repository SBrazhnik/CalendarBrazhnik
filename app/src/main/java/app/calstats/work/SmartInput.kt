package app.calstats.work

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Единый быстрый ввод (голосом или текстом): «потратил 450 на такси», «завтра в 10 позвонить в банк»,
 * «выпил 2 стакана воды», «купить молоко, хлеб», «вес 82,5», «настроение 4», «заметка …».
 * Только разбор — без побочных эффектов, поэтому легко проверяется тестами.
 */
object SmartInput {
    enum class Kind(val title: String, val emoji: String) {
        EXPENSE("Расход", "💸"), INCOME("Доход", "💰"), TASK("Задача", "✅"), EVENT("Событие", "📅"),
        WATER("Вода", "💧"), WEIGHT("Вес", "⚖"), MOOD("Настроение", "🙂"), SHOP("Покупки", "🛒"),
        NOTE("Заметка", "📝"), HABIT("Привычка", "🔁"), JOURNAL("Дневник", "📔"),
    }

    data class Result(
        val kind: Kind,
        val text: String,
        val amount: Long = 0,
        val category: String? = null,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val ml: Int = 0,
        val kg: Double = 0.0,
        val mood: Int = 0,
        val items: List<Pair<String, String>> = emptyList(),
        val habitId: String? = null,
    )

    /** Ключевые слова → категория расходов (по названиям стандартных категорий). */
    val CATEGORY_WORDS: List<Pair<String, List<String>>> = listOf(
        "Транспорт" to listOf("такси", "метро", "автобус", "электричк", "проезд", "каршеринг", "самокат", "яндекс го", "трамва"),
        "Авто" to listOf("бензин", "заправ", "топлив", "дизел", "парковк", "мойк", "шиномонтаж", "осаго", "каско", "штраф"),
        "Кафе и рестораны" to listOf("кафе", "ресторан", "кофе", "обед", "ужин", "завтрак", "бар", "пицц", "суши", "доставк", "шаурм", "бургер", "столов"),
        "Продукты" to listOf("продукт", "магазин", "пятёрочк", "пятерочк", "магнит", "перекрёст", "перекрест", "лента", "ашан", "вкусвилл", "самокат", "молоко", "хлеб", "овощ"),
        "Дом и ЖКХ" to listOf("жкх", "коммунал", "квартплат", "электричеств", "свет", "газ", "аренд", "ремонт", "икеа", "хозтовар"),
        "Связь и интернет" to listOf("связь", "телефон", "интернет", "мтс", "билайн", "мегафон", "теле2", "tele2"),
        "Здоровье" to listOf("аптек", "лекарств", "врач", "клиник", "анализ", "стоматолог", "таблетк"),
        "Одежда" to listOf("одежд", "обув", "куртк", "кроссовк", "джинс", "футболк", "платье"),
        "Развлечения" to listOf("кино", "театр", "концерт", "игр", "боулинг", "музей", "квест"),
        "Подписки" to listOf("подписк", "кинопоиск", "окко", "яндекс плюс", "spotify", "youtube"),
        "Подарки" to listOf("подар", "цветы", "букет"),
        "Образование" to listOf("курс", "книг", "учеб", "репетитор", "школ"),
        "Путешествия" to listOf("отел", "гостиниц", "билет", "самолёт", "самолет", "поезд", "ржд", "тур"),
        "Красота" to listOf("парикмах", "стрижк", "маникюр", "косметик", "салон"),
        "Дети" to listOf("детск", "игрушк", "садик", "кружок"),
        "Животные" to listOf("корм", "ветеринар", "зоомагазин", "наполнител"),
        "Спорт" to listOf("спортзал", "фитнес", "бассейн", "тренировк", "абонемент"),
    )

    fun guessCategory(text: String): String? {
        val t = text.lowercase()
        return CATEGORY_WORDS.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first
    }

    private val MONEY = Regex("""(?iu)(\d[\d\s]*(?:[.,]\d{1,2})?)\s*(тыс(?:яч[аи]?|\.)?|к(?![а-яё]))?\s*(₽|р(?![а-яё])\.?|руб[а-яёa-z]*)?""")
    private fun amountOf(text: String): Pair<Long, IntRange>? {
        val m = MONEY.findAll(text).firstOrNull { it.groupValues[1].isNotBlank() } ?: return null
        val n = m.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        val mult = if (m.groupValues[2].isNotBlank()) 1000 else 1
        return (n * mult * 100).toLong() to m.range
    }

    private fun clean(s: String) = s.replace(Regex("""(?iu)^\s*(на|за|в|во)\s+"""), "")
        .replace(Regex("\\s+"), " ").trim().trimEnd('.', ',').replaceFirstChar { it.uppercase() }

    private fun starts(t: String, vararg w: String) = w.firstOrNull { t.startsWith(it) }

    fun parse(raw: String, now: LocalDateTime, habits: List<Habit> = emptyList()): Result? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isBlank()) return null
        val t = text.lowercase()

        // Явные префиксы
        starts(t, "заметка", "запиши заметку", "запомни")?.let { return Result(Kind.NOTE, clean(text.drop(it.length))) }
        starts(t, "дневник", "в дневник")?.let { return Result(Kind.JOURNAL, clean(text.drop(it.length))) }
        starts(t, "купить", "надо купить", "нужно купить", "в список покупок", "список покупок")?.let {
            val items = Shop.parse(text.drop(it.length).replace(Regex("(?iu)\\s+и\\s+"), ", "))
            if (items.isNotEmpty()) return Result(Kind.SHOP, items.joinToString(", ") { p -> p.first }, items = items)
        }

        // Вода: «выпил 2 стакана воды», «вода 300 мл», «500 мл воды»
        if (Regex("(?iu)вод[аыу]|стакан").containsMatchIn(t)) {
            val glasses = Regex("""(?iu)(\d+|один|два|три|четыре|пять)\s*стакан""").find(t)
            val ml = Regex("""(?iu)(\d+(?:[.,]\d+)?)\s*(мл|л(?![а-яё])|литр)""").find(t)
            val v = when {
                ml != null -> { val n = ml.groupValues[1].replace(',', '.').toDouble(); if (ml.groupValues[2].startsWith("мл")) n.toInt() else (n * 1000).toInt() }
                glasses != null -> words(glasses.groupValues[1]) * 250
                t.contains("стакан") -> 250
                else -> 0
            }
            if (v in 50..5000) return Result(Kind.WATER, "$v мл", ml = v)
        }
        // Вес: «вес 82,5», «вешу 80 кг»
        Regex("""(?iu)(?:вес|вешу|взвесил[а-яёa-z]*)\s*(\d{2,3}(?:[.,]\d)?)""").find(t)?.let {
            val kg = it.groupValues[1].replace(',', '.').toDouble()
            if (kg in 20.0..300.0) return Result(Kind.WEIGHT, "$kg кг", kg = kg)
        }
        // Настроение: «настроение 4», «настроение отличное»
        if (t.startsWith("настроение")) {
            val n = Regex("""[1-5]""").find(t)?.value?.toInt() ?: when {
                Regex("отличн|супер|прекрасн").containsMatchIn(t) -> 5
                Regex("хорош").containsMatchIn(t) -> 4
                Regex("нормальн|так себе|средн").containsMatchIn(t) -> 3
                Regex("плох").containsMatchIn(t) -> 2
                Regex("ужасн|отвратит").containsMatchIn(t) -> 1
                else -> 3
            }
            return Result(Kind.MOOD, "$n из 5", mood = n)
        }
        // Привычка: «сделал зарядку», «отметь медитацию»
        if (Regex("(?iu)^(сделал[а-яёa-z]*|выполнил[а-яёa-z]*|отметь|отметить|была?|был)(?![а-яё])").containsMatchIn(t)) {
            habits.filter { !it.archived }.firstOrNull { h ->
                val stem = h.name.lowercase().take(maxOf(4, h.name.length - 2))
                stem.isNotBlank() && t.contains(stem)
            }?.let { return Result(Kind.HABIT, it.name, habitId = it.id) }
        }

        // Доход
        if (Regex("(?iu)^(получил[а-яёa-z]*|пришл[аио]|зарплат|аванс|доход|заработал[а-яёa-z]*|премия|вернули)").containsMatchIn(t)) {
            amountOf(text)?.let { (kop, r) -> return Result(Kind.INCOME, clean(text.removeRange(r).replace(Regex("(?iu)^(получил[а-яёa-z]*|пришл[аио]|доход|заработал[а-яёa-z]*)"), "")), amount = kop) }
        }
        // Расход: глагол траты или сумма с валютой
        val spendVerb = Regex("(?iu)^(потратил[а-яёa-z]*|заплатил[а-яёa-z]*|купил[а-яёa-z]*|оплатил[а-яёa-z]*|трата|расход|отдал[а-яёa-z]*)").find(t)
        val withCurrency = Regex("""(?iu)\d\s*(₽|р(?![а-яё])|руб|тыс)""").containsMatchIn(t)
        if (spendVerb != null || withCurrency) {
            amountOf(text)?.let { (kop, r) ->
                var rest = text.removeRange(r)
                spendVerb?.let { v -> rest = rest.replaceFirst(Regex("(?iu)^\\s*" + Regex.escape(v.value)), "") }
                val note = clean(rest)
                return Result(Kind.EXPENSE, note, amount = kop, category = guessCategory(note))
            }
        }

        // «продукты 500», «такси 350» — слово траты и сумма без даты/времени → расход
        val cat = guessCategory(t)
        if (cat != null && !Regex("""(?iu)\d{1,2}[:.]\d{2}|\d{1,2}\s*(январ|феврал|март|апрел|ма[яй]|июн|июл|август|сентябр|октябр|ноябр|декабр)|завтра|сегодня в|послезавтра|понедельник|вторник|сред[уа]|четверг|пятниц|суббот|воскресень""").containsMatchIn(t)) {
            amountOf(text)?.takeIf { it.first >= 1000 }?.let { (kop, r) ->
                return Result(Kind.EXPENSE, clean(text.removeRange(r)), amount = kop, category = cat)
            }
        }

        // Событие или задача — по дате/времени
        val q = QuickParse.parse(text, now)
        val title = clean((q?.title ?: text).replace(Regex("(?iu)^(напомни(?:ть)?(?: мне)?|задача|надо|нужно|сделать)\\s+"), ""))
        val isMeeting = Regex("(?iu)встреч|созвон|при[её]м|врач|день рождени|концерт|кино|ужин с|обед с|собрани|тренировк").containsMatchIn(t)
        if (q?.time != null && (isMeeting || !Regex("(?iu)^(напомни|задача|надо|нужно|сделать)").containsMatchIn(t))) {
            return Result(Kind.EVENT, title, date = q.date, time = q.time)
        }
        return Result(Kind.TASK, title, date = if (q != null && q.date != now.toLocalDate() || q?.time != null) q?.date else null, time = q?.time)
    }

    /** Принудительно как запись выбранного типа (если угадали неверно). */
    fun asKind(kind: Kind, raw: String, now: LocalDateTime, habits: List<Habit> = emptyList()): Result {
        val prefix = when (kind) {
            Kind.EXPENSE -> "трата "; Kind.INCOME -> "получил "; Kind.SHOP -> "купить "; Kind.WATER -> "вода "
            Kind.WEIGHT -> "вес "; Kind.MOOD -> "настроение "; Kind.NOTE -> "заметка "; Kind.JOURNAL -> "дневник "
            Kind.HABIT -> "сделал "; Kind.TASK -> "задача "; Kind.EVENT -> ""
        }
        val r = parse(prefix + raw.trim(), now, habits)
        if (r != null && r.kind == kind) return r
        val q = QuickParse.parse(raw, now)
        return when (kind) {
            Kind.EVENT -> Result(Kind.EVENT, clean(q?.title ?: raw), date = q?.date ?: now.toLocalDate(), time = q?.time)
            Kind.TASK -> Result(Kind.TASK, clean(q?.title ?: raw), date = q?.date?.takeIf { it != now.toLocalDate() || q.time != null }, time = q?.time)
            else -> Result(kind, clean(raw))
        }
    }

    private fun words(s: String) = when (s.lowercase()) {
        "один" -> 1; "два" -> 2; "три" -> 3; "четыре" -> 4; "пять" -> 5; else -> s.toIntOrNull() ?: 1
    }
}
