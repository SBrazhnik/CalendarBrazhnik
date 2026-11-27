package app.calstats.work

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToLong

/** Операция, найденная в уведомлении банка; ждёт подтверждения в «Финансах». */
@Serializable
data class PendingTx(
    val id: String,
    val bank: String,
    val amount: Long,
    val income: Boolean,
    val merchant: String,
    val date: String,
    val raw: String,
    val card: String = "",
    /** "tx" — операция, "bill" — счёт к оплате (ещё не оплачен). */
    val kind: String = "tx",
    /** Приложение, откуда пришло уведомление — чтобы открыть банк для оплаты. */
    val pkg: String = "",
) {
    val isBill get() = kind == "bill"
}

/** Разбор уведомлений банковских приложений: «Покупка 350 ₽ Пятёрочка», «Пополнение +5 000 ₽» и т.п. */
object BankNotify {
    val BANKS = mapOf(
        "com.idamob.tinkoff.android" to "Т-Банк", "ru.tinkoff.investing" to "Т-Банк",
        "ru.sberbankmobile" to "Сбер", "ru.sberbank.sbbol" to "Сбер",
        "ru.alfabank.mobile.android" to "Альфа-Банк", "ru.vtb24.mobilebanking.android" to "ВТБ",
        "ru.raiffeisennews" to "Райффайзен", "ru.rosbank.android" to "Росбанк", "ru.gazprombank.android.mobilebank.app" to "Газпромбанк",
        "com.openbank" to "Открытие", "ru.mts.money" to "МТС Банк", "ru.yoo.money" to "ЮMoney", "ru.ozon.app.android" to "Ozon Банк",
        "ru.pochta.bank" to "Почта Банк", "ru.sovcomcard.halva.v1" to "Халва", "ru.nspk.mirpay" to "Mir Pay",
        "com.yandex.bank" to "Яндекс Банк", "ru.psbank.online" to "ПСБ",
    )

    private val EXPENSE = Regex("(?iu)(покупк|оплат|списан|перевод\\s|снят|платеж|платёж|расход)")
    private val INCOME = Regex("(?iu)(зачислен|пополнен|поступлен|возврат|перевод от|входящий|кэшбэк|cashback)")
    private val MONEY = Regex("([+−-]?)\\s?(\\d{1,3}(?:[\\s\\u00A0\\u202F]\\d{3})*(?:[.,]\\d{1,2})?)\\s?(?:₽|р\\b|руб|RUB|RUR)")
    /** Отмена/возврат операции — деньги вернулись. */
    private val REFUND = Regex("(?iu)(отмен[аеиы]|отменен|отменён|возврат|refund|reversal|отказ в операции)")
    /** Счёт к оплате, а не сама оплата. */
    private val BILL = Regex("(?iu)(оплатите|к оплате|выставлен[а-яё]*\\s+сч[её]т|сч[её]т на оплату|сч[её]т на сумму|новый сч[её]т|необходимо оплатить|ожидает оплаты|задолженност|напоминаем.{0,40}оплат|начислен[а-яё]*\\s+(налог|штраф|пени))")
    private val PAID = Regex("(?iu)(успешно|оплачен[оа]?\\b|списан[оа]?\\b|покупка\\b|выполнен)")
    /** SMS от банков в обычном приложении сообщений: отправитель → банк. */
    val MESSAGING = setOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms", "com.android.messaging")
    private val SMS_SENDERS = listOf(
        Regex("^900$") to "Сбер", Regex("(?iu)sber|сбер") to "Сбер", Regex("(?iu)tinkoff|t-bank|т-банк|tbank") to "Т-Банк",
        Regex("(?iu)alfa|альфа") to "Альфа-Банк", Regex("(?iu)vtb|втб") to "ВТБ", Regex("(?iu)raiff|райф") to "Райффайзен",
        Regex("(?iu)gazprom|газпром") to "Газпромбанк", Regex("(?iu)sovcom|совком|halva|халва") to "Совкомбанк", Regex("(?iu)pochta|почта") to "Почта Банк",
        Regex("(?iu)ozon|озон") to "Ozon Банк", Regex("(?iu)mts.?bank|мтс.?банк") to "МТС Банк", Regex("(?iu)psb|псб") to "ПСБ",
        Regex("(?iu)rosbank|росбанк") to "Росбанк", Regex("(?iu)otkritie|открытие") to "Открытие", Regex("(?iu)yandex.?bank|яндекс.?банк") to "Яндекс Банк",
        Regex("(?iu)rshb|россельхоз") to "Россельхозбанк", Regex("(?iu)uralsib|уралсиб") to "Уралсиб", Regex("(?iu)mkb|мкб") to "МКБ",
    )
    fun smsBank(sender: String?): String? = sender?.trim()?.let { s -> SMS_SENDERS.firstOrNull { it.first.containsMatchIn(s) }?.second }

    /** Реклама и акции банков: «Плати 50 ₽ — получи 500 ₽», «Кешбэк до 30%», «Оформите карту». */
    private val AD = Regex("(?iu)(получи(те)?(?![а-яё])|получай|кешб[эе]к до|кэшб[эе]к до|скидк|акци[яиюей]|подар|выгод|предложени|успей|розыгрыш|приз(?![а-яё])|промокод|только сегодня|оформи|подключи|участв|реклам|(?<![а-яё])плати(?![а-яё])|выиграй|дарим|жми|переходи|узнайте|попробуйте|приглаша|до\\s?\\d+\\s?%)")
    /** Признаки настоящей операции — с ними даже «рекламные» слова не мешают. */
    private val REAL = Regex("(?iu)(списан|зачислен|покупка(?![а-яё])|баланс|остаток|доступно|[*•]\\s?\\d{4}|mir-\\d{4}|сч[её]т\\s?[*•]|отмен[аеы]|возврат средств)")

    private val NOISE = Regex("(?iu)(баланс|доступно|остаток|комисси)[^\\n]*")

    /** Последние 4 цифры карты/счёта: «*1234», «•• 1234», «MIR-1234», «карта 1234», «счёт *1234». */
    val CARD = Regex("(?iu)(?:[*•]+\\s?|(?:mir|visa|ecmc|mc|мир|карт[аеуы]?|сч[её]т[аеу]?)\\s*[-*•]?\\s*)(\\d{4})(?!\\d)")

    fun parse(pkg: String, title: String?, text: String?, date: String, id: String, bankName: String? = null): PendingTx? {
        val bank = bankName ?: BANKS[pkg] ?: return null
        val full = listOfNotNull(title, text).joinToString(" ").replace('\n', ' ')
        if (full.isBlank()) return null
        // Код подтверждения, вход и т.п. — не операции
        if (Regex("(?iu)(код|пароль|вход в|никому не сообщ)").containsMatchIn(full)) return null
        // Реклама — не операция
        if (AD.containsMatchIn(full) && !REAL.containsMatchIn(full)) return null
        val refund = REFUND.containsMatchIn(full)
        val bill = !refund && BILL.containsMatchIn(full) && !PAID.containsMatchIn(full)
        val isIncome = refund || (INCOME.containsMatchIn(full) && !Regex("(?iu)покупк|оплат").containsMatchIn(full))
        val isExpense = EXPENSE.containsMatchIn(full)
        val clean = NOISE.replace(full, " ")
        val m = MONEY.find(clean) ?: return null
        if (!isIncome && !isExpense && !bill && m.groupValues[1].isEmpty()) return null
        val amount = BankImport.parseAmount(m.groupValues[2]) ?: return null
        if (amount <= 0) return null
        val income = !bill && (isIncome || m.groupValues[1] == "+")
        val after = clean.substring(m.range.last + 1).trim().trim('.', ',', ' ', '·', '—', '-')
        val merchant = after.split(Regex("[.\\n]")).firstOrNull()?.trim()?.take(60).orEmpty()
            .ifBlank { title?.takeIf { !EXPENSE.containsMatchIn(it) && !INCOME.containsMatchIn(it) }.orEmpty() }
        val card = CARD.find(full)?.groupValues?.get(1).orEmpty()
        return PendingTx(id, bank, amount, income, merchant.replace(CARD, "").trim(), date, full.take(300), card, if (bill) "bill" else "tx", pkg)
    }
}

/** Кредит: аннуитетный платёж и график. */
object LoanMath {
    data class Row(val n: Int, val payment: Long, val interest: Long, val principal: Long, val left: Long)

    /** [amount] в копейках, [yearRate] — % годовых, [months] — срок. */
    fun payment(amount: Long, yearRate: Double, months: Int): Long {
        if (months <= 0) return 0
        val r = yearRate / 100 / 12
        if (r == 0.0) return (amount.toDouble() / months).roundToLong()
        return (amount * r / (1 - (1 + r).pow(-months))).roundToLong()
    }

    fun schedule(amount: Long, yearRate: Double, months: Int): List<Row> {
        val p = payment(amount, yearRate, months)
        val r = yearRate / 100 / 12
        var left = amount.toDouble()
        return (1..months).map { n ->
            val i = left * r
            var pr = p - i
            if (n == months) pr = left
            left -= pr
            Row(n, (pr + i).roundToLong(), i.roundToLong(), pr.roundToLong(), left.coerceAtLeast(0.0).roundToLong())
        }
    }
}
