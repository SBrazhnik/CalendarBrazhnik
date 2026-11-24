package app.calstats.work

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Бонусная/скидочная карта магазина. [format] — имя формата ZXing (EAN_13, CODE_128, QR_CODE…). */
@Serializable
data class LoyaltyCard(
    val id: String,
    val store: String,
    val number: String,
    val format: String = "CODE_128",
    val color: Int = 0xFF2F5D46.toInt(),
    val note: String = "",
    val photoFront: String? = null,
    val photoBack: String? = null,
    /** Логотип из .pkpass. */
    val logo: String? = null,
    val uses: Int = 0,
    val lastUsed: Long = 0,
    val created: Long = System.currentTimeMillis(),
    /** Где пользовались картой — для подсказки у магазина. */
    val places: List<CardPlace> = emptyList(),
)

@Serializable
data class CardPlace(val lat: Double, val lon: Double)

/** Кешбэк банка в категории на месяц («2026-11»). */
@Serializable
data class Cashback(
    val id: String,
    val bank: String,
    val month: String,
    val category: String,
    val percent: Double,
)

object CashbackKit {
    val BANKS = listOf("Т-Банк", "Сбер", "Альфа", "ВТБ", "Газпромбанк", "Озон Банк", "Яндекс Пэй", "Совкомбанк", "Райффайзен", "МТС Банк")
    val CATEGORIES = listOf(
        "Супермаркеты", "Кафе и рестораны", "Фастфуд", "Такси", "Транспорт", "АЗС", "Аптеки", "Одежда и обувь", "Красота",
        "Развлечения", "Кино", "Спорт", "Дом и ремонт", "Электроника", "Маркетплейсы", "Животные", "Детские товары",
        "Путешествия", "Ж/д билеты", "Авиабилеты", "Книги", "Цветы", "Связь", "ЖКХ", "Всё остальное",
    )

    /** Категория расходов приложения → категории кешбэка. */
    private val MAP = mapOf(
        "Продукты" to listOf("Супермаркеты"), "Кафе и рестораны" to listOf("Кафе и рестораны", "Фастфуд"),
        "Транспорт" to listOf("Такси", "Транспорт"), "Авто" to listOf("АЗС"), "Здоровье" to listOf("Аптеки"),
        "Одежда" to listOf("Одежда и обувь"), "Красота" to listOf("Красота"), "Развлечения" to listOf("Развлечения", "Кино"),
        "Спорт" to listOf("Спорт"), "Дом и ЖКХ" to listOf("Дом и ремонт", "ЖКХ"), "Животные" to listOf("Животные"),
        "Дети" to listOf("Детские товары"), "Путешествия" to listOf("Путешествия", "Ж/д билеты", "Авиабилеты"),
        "Образование" to listOf("Книги"), "Подарки" to listOf("Цветы"), "Связь и интернет" to listOf("Связь"),
    )

    fun month(d: java.time.LocalDate) = d.toString().take(7)

    /** Лучший кешбэк в этом месяце для категории расходов (или категории кешбэка). */
    fun best(all: List<Cashback>, month: String, category: String?): Cashback? {
        if (category == null) return null
        val cats = (MAP[category] ?: listOf(category)).map { it.lowercase() }
        val m = all.filter { it.month == month }
        return m.filter { it.category.lowercase() in cats }.maxByOrNull { it.percent }
            ?: m.filter { it.category == "Всё остальное" }.maxByOrNull { it.percent }
    }
}

object CardsKit {
    /** Частые магазины и их фирменные цвета (только для оформления карточки). */
    val STORES: List<Pair<String, Long>> = listOf(
        "Пятёрочка" to 0xFFE31E24, "Перекрёсток" to 0xFF0B6E33, "Магнит" to 0xFFD71A21, "Лента" to 0xFF003C96,
        "Ашан" to 0xFFE2001A, "ВкусВилл" to 0xFF2B9C3B, "Дикси" to 0xFFF39200, "Окей" to 0xFFE30613,
        "Спортмастер" to 0xFF1D1D1B, "Декатлон" to 0xFF0082C3, "М.Видео" to 0xFFE30613, "Эльдорадо" to 0xFF96C11F,
        "DNS" to 0xFFF07C00, "Леруа Мерлен" to 0xFF78BE20, "OBI" to 0xFFF07D00, "Детский мир" to 0xFF0065B3,
        "Л'Этуаль" to 0xFF1A1A1A, "Золотое яблоко" to 0xFFC5A46D, "Рив Гош" to 0xFF000000, "Улыбка радуги" to 0xFFE5007E,
        "Аптека Ригла" to 0xFF0B9444, "Азбука вкуса" to 0xFF1F5139, "Fix Price" to 0xFF00A0E3, "Gloria Jeans" to 0xFF1E3A8A,
        "Familia" to 0xFFE30613, "Твой Дом" to 0xFFE30613, "Лукойл" to 0xFFE30613, "Роснефть" to 0xFFFFCC00, "Газпромнефть" to 0xFF0079C2,
    )

    /** Формат ML Kit (Barcode.FORMAT_*) → имя ZXing. */
    fun fromMlKit(f: Int): String = when (f) {
        32 -> "EAN_13"; 64 -> "EAN_8"; 512 -> "UPC_A"; 1024 -> "UPC_E"; 1 -> "CODE_128"; 2 -> "CODE_39"; 4 -> "CODE_93"
        8 -> "CODABAR"; 128 -> "ITF"; 256 -> "QR_CODE"; 2048 -> "PDF_417"; 4096 -> "AZTEC"; 16 -> "DATA_MATRIX"; else -> "CODE_128"
    }

    /** Формат для номера, введённого вручную. */
    fun guess(number: String): String {
        val n = number.trim()
        return when {
            n.length == 13 && n.all { it.isDigit() } && ean13ok(n) -> "EAN_13"
            n.length == 8 && n.all { it.isDigit() } -> "EAN_8"
            else -> "CODE_128"
        }
    }

    fun ean13ok(n: String): Boolean {
        if (n.length != 13 || !n.all { it.isDigit() }) return false
        val sum = n.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == n.last() - '0'
    }

    val FORMATS = listOf("CODE_128" to "Штрихкод (Code 128)", "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "CODE_39" to "Code 39", "QR_CODE" to "QR-код", "PDF_417" to "PDF417", "AZTEC" to "Aztec", "ITF" to "ITF")

    /** Разбор pass.json из файла .pkpass (Apple Wallet / PassWallet). */
    fun fromPassJson(json: String, id: String): LoyaltyCard? {
        val o = Json.parseToJsonElement(json).jsonObject
        fun str(obj: JsonObject?, k: String) = (obj?.get(k) as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        val bc = (o["barcodes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: o["barcode"] as? JsonObject
        val number = str(bc, "message") ?: run {
            // Без штрихкода: ищем номер в полях карты.
            listOf("storeCard", "generic", "coupon", "eventTicket", "boardingPass").firstNotNullOfOrNull { t ->
                (o[t] as? JsonObject)?.let { s ->
                    listOf("primaryFields", "secondaryFields", "auxiliaryFields", "headerFields").flatMap { f -> (s[f] as? JsonArray).orEmpty().map { it as JsonObject } }
                        .firstNotNullOfOrNull { f -> str(f, "value")?.takeIf { v -> v.count { it.isDigit() } >= 6 } }
                }
            }
        } ?: return null
        val format = when (str(bc, "format")) {
            "PKBarcodeFormatQR" -> "QR_CODE"
            "PKBarcodeFormatPDF417" -> "PDF_417"
            "PKBarcodeFormatAztec" -> "AZTEC"
            else -> "CODE_128"
        }
        val color = str(o, "backgroundColor")?.let { parseRgb(it) } ?: 0xFF2F5D46.toInt()
        val store = str(o, "organizationName") ?: str(o, "logoText") ?: str(o, "description") ?: "Карта"
        return LoyaltyCard(id, store, number, format, color, note = str(o, "description").orEmpty().takeIf { it != store }.orEmpty())
    }

    /** «rgb(227, 30, 36)» или «#E31E24». */
    fun parseRgb(s: String): Int? {
        Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""").find(s)?.let { m ->
            val (r, g, b) = m.destructured
            return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
        return s.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    }

    /** Текст из «Поделиться» (Кошелёк и др.): ищем номер карты. */
    fun numberFromText(t: String): String? = Regex("""\b\d[\d ]{6,22}\d\b""").find(t)?.value?.replace(" ", "")
}
