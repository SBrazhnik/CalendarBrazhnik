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
)

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

    private val EXPENSE = Regex("(?i)(покупк|оплат|списан|перевод\\s|снят|платеж|платёж|расход)")
    private val INCOME = Regex("(?i)(зачислен|пополнен|поступлен|возврат|перевод от|входящий|кэшбэк|cashback)")
    private val MONEY = Regex("([+−-]?)\\s?(\\d{1,3}(?:[\\s\\u00A0\\u202F]\\d{3})*(?:[.,]\\d{1,2})?)\\s?(?:₽|р\\b|руб|RUB|RUR)")
    private val NOISE = Regex("(?i)(баланс|доступно|остаток|комисси)[^\\n]*")

    fun parse(pkg: String, title: String?, text: String?, date: String, id: String): PendingTx? {
        val bank = BANKS[pkg] ?: return null
        val full = listOfNotNull(title, text).joinToString(" ").replace('\n', ' ')
        if (full.isBlank()) return null
        // Код подтверждения, вход и т.п. — не операции
        if (Regex("(?i)(код|пароль|вход в|никому не сообщ)").containsMatchIn(full)) return null
        val isIncome = INCOME.containsMatchIn(full) && !Regex("(?i)покупк|оплат").containsMatchIn(full)
        val isExpense = EXPENSE.containsMatchIn(full)
        val clean = NOISE.replace(full, " ")
        val m = MONEY.find(clean) ?: return null
        if (!isIncome && !isExpense && m.groupValues[1].isEmpty()) return null
        val amount = BankImport.parseAmount(m.groupValues[2]) ?: return null
        if (amount <= 0) return null
        val income = isIncome || m.groupValues[1] == "+"
        val after = clean.substring(m.range.last + 1).trim().trim('.', ',', ' ', '·', '—', '-')
        val merchant = after.split(Regex("[.\\n]")).firstOrNull()?.trim()?.take(60).orEmpty()
            .ifBlank { title?.takeIf { !EXPENSE.containsMatchIn(it) && !INCOME.containsMatchIn(it) }.orEmpty() }
        return PendingTx(id, bank, amount, income, merchant, date, full.take(300))
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
