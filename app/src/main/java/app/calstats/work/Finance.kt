package app.calstats.work

import kotlinx.serialization.Serializable
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/*
 * Модуль «Финансы»: счета, операции, категории, бюджеты, регулярные платежи и цели.
 * Суммы — в копейках (Long), как в «Расходах».
 */

enum class AccountType(val title: String, val emoji: String) {
    CARD("Карта", "💳"),
    CASH("Наличные", "💵"),
    SAVINGS("Накопительный счёт", "🏦"),
    CREDIT("Кредитная карта", "🧾"),
    OTHER("Другое", "👛"),
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType = AccountType.CARD,
    val startBalance: Long = 0,
    val color: Int = PALETTE[1],
    val includeInTotal: Boolean = true,
    val archived: Boolean = false,
    val created: Long = System.currentTimeMillis(),
)

enum class TxType(val title: String) { EXPENSE("Расход"), INCOME("Доход"), TRANSFER("Перевод") }

@Serializable
data class FinCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val income: Boolean = false,
    val color: Int = PALETTE[0],
    val archived: Boolean = false,
)

@Serializable
data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val date: String,
    val note: String = "",
    val source: String = "",
    val importKey: String? = null,
    val created: Long = System.currentTimeMillis(),
) {
    val localDate: LocalDate get() = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
}

@Serializable
data class Budget(val id: String, val categoryId: String, val monthlyLimit: Long)

enum class RepeatPeriod(val title: String) { WEEKLY("Каждую неделю"), MONTHLY("Каждый месяц"), YEARLY("Каждый год") }

/** Регулярный платёж или подписка. */
@Serializable
data class Recurring(
    val id: String,
    val name: String,
    val amount: Long,
    val type: TxType = TxType.EXPENSE,
    val accountId: String? = null,
    val categoryId: String? = null,
    val period: RepeatPeriod = RepeatPeriod.MONTHLY,
    val nextDate: String,
    val subscription: Boolean = true,
    val active: Boolean = true,
    /** Пробный период: до этой даты бесплатно, потом начнутся списания. */
    val trialUntil: String? = null,
) {
    val next: LocalDate get() = runCatching { LocalDate.parse(nextDate) }.getOrDefault(LocalDate.now())
    fun advanced(): Recurring = copy(
        nextDate = when (period) {
            RepeatPeriod.WEEKLY -> next.plusWeeks(1)
            RepeatPeriod.MONTHLY -> next.plusMonths(1)
            RepeatPeriod.YEARLY -> next.plusYears(1)
        }.toString(),
    )
    /** Во что обходится в месяц. */
    val monthly: Long get() = when (period) {
        RepeatPeriod.WEEKLY -> amount * 52 / 12
        RepeatPeriod.MONTHLY -> amount
        RepeatPeriod.YEARLY -> amount / 12
    }
}

@Serializable
data class Goal(
    val id: String,
    val name: String,
    val target: Long,
    val saved: Long = 0,
    val deadline: String? = null,
    val emoji: String = "🎯",
    val color: Int = PALETTE[2],
    val done: Boolean = false,
)

object FinanceDefaults {
    private val expense = listOf(
        "Продукты" to "🛒", "Кафе и рестораны" to "🍽", "Транспорт" to "🚕", "Авто" to "⛽",
        "Дом и ЖКХ" to "🏠", "Связь и интернет" to "📱", "Здоровье" to "💊", "Одежда" to "👕",
        "Развлечения" to "🎉", "Подписки" to "🔁", "Подарки" to "🎁", "Образование" to "📚",
        "Путешествия" to "✈", "Красота" to "💅", "Дети" to "🧸", "Животные" to "🐾", "Спорт" to "🏃", "Другое" to "•",
    )
    private val income = listOf(
        "Зарплата" to "💼", "Подработка" to "🧰", "Подарки" to "🎁", "Проценты и кэшбэк" to "🏦", "Возвраты" to "↩", "Другие доходы" to "•",
    )
    private val colors = listOf(
        0xFF34553F, 0xFFC9963A, 0xFF4F6BD8, 0xFF7C6BD6, 0xFF2E8B6B, 0xFF3FA3B5, 0xFFD9667A, 0xFF8FBF9F, 0xFFE07B39,
        0xFF8E5BBF, 0xFFCF4F6E, 0xFF5B8DEF, 0xFF26A69A, 0xFFEC8FB0, 0xFFF2B84B, 0xFF8D6E63, 0xFF43A047, 0xFF90A4AE,
    ).map { it.toInt() }

    fun categories(): List<FinCategory> =
        expense.mapIndexed { i, (n, e) -> FinCategory("exp_$i", n, e, false, colors[i % colors.size]) } +
            income.mapIndexed { i, (n, e) -> FinCategory("inc_$i", n, e, true, colors[(i + 4) % colors.size]) }

    /** Сопоставление категорий банка нашим (по ключевым словам). */
    val bankMap: List<Pair<List<String>, String>> = listOf(
        listOf("супермаркет", "продукт", "магазины продуктов") to "Продукты",
        listOf("ресторан", "кафе", "фастфуд", "общепит", "кофе") to "Кафе и рестораны",
        listOf("такси", "транспорт", "метро", "каршеринг", "ж/д", "авиа") to "Транспорт",
        listOf("топливо", "азс", "авто", "парков") to "Авто",
        listOf("жкх", "коммунал", "дом", "ремонт", "строител") to "Дом и ЖКХ",
        listOf("связь", "интернет", "мобиль", "телеком") to "Связь и интернет",
        listOf("аптек", "медицин", "здоров", "клиник") to "Здоровье",
        listOf("одежд", "обув") to "Одежда",
        listOf("развлеч", "кино", "театр", "музык", "игр") to "Развлечения",
        listOf("подписк", "сервис", "цифров") to "Подписки",
        listOf("цвет", "подар", "сувенир") to "Подарки",
        listOf("образован", "книг", "курс") to "Образование",
        listOf("отел", "путешеств", "туризм") to "Путешествия",
        listOf("красот", "салон", "косметик") to "Красота",
        listOf("дет", "игрушк") to "Дети",
        listOf("животн", "зоо", "ветерин") to "Животные",
        listOf("спорт", "фитнес") to "Спорт",
    )
}

object FinanceMath {
    fun balance(a: Account, txs: List<Transaction>): Long {
        var b = a.startBalance
        for (t in txs) {
            when (t.type) {
                TxType.EXPENSE -> if (t.accountId == a.id) b -= t.amount
                TxType.INCOME -> if (t.accountId == a.id) b += t.amount
                TxType.TRANSFER -> {
                    if (t.accountId == a.id) b -= t.amount
                    if (t.toAccountId == a.id) b += t.amount
                }
            }
        }
        return b
    }

    fun total(w: Workspace): Long = w.accounts.filter { !it.archived && it.includeInTotal }.sumOf { balance(it, w.transactions) }

    fun inMonth(txs: List<Transaction>, m: YearMonth) = txs.filter { YearMonth.from(it.localDate) == m }

    fun expense(txs: List<Transaction>) = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    fun income(txs: List<Transaction>) = txs.filter { it.type == TxType.INCOME }.sumOf { it.amount }

    /** Расходы по категориям, по убыванию. */
    fun byCategory(txs: List<Transaction>, income: Boolean = false): List<Pair<String?, Long>> =
        txs.filter { it.type == if (income) TxType.INCOME else TxType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (k, v) -> k to v.sumOf { it.amount } }
            .sortedByDescending { it.second }

    /** Расходы по дням месяца (для графика). */
    fun daily(txs: List<Transaction>, m: YearMonth): List<Long> =
        (1..m.lengthOfMonth()).map { d -> txs.filter { it.type == TxType.EXPENSE && it.localDate == m.atDay(d) }.sumOf { it.amount } }
}

object FinanceOps {
    fun ensureDefaults(w: Workspace): Workspace =
        if (w.finCategories.isNotEmpty()) w else w.copy(finCategories = FinanceDefaults.categories())

    fun upsertAccount(w: Workspace, a: Account) =
        if (w.accounts.any { it.id == a.id }) w.copy(accounts = w.accounts.map { if (it.id == a.id) a else it }) else w.copy(accounts = w.accounts + a)

    fun deleteAccount(w: Workspace, id: String) = w.copy(
        accounts = w.accounts.filter { it.id != id },
        transactions = w.transactions.filter { it.accountId != id && it.toAccountId != id },
    )

    fun upsertTx(w: Workspace, t: Transaction) =
        if (w.transactions.any { it.id == t.id }) w.copy(transactions = w.transactions.map { if (it.id == t.id) t else it })
        else w.copy(transactions = w.transactions + t)

    fun deleteTx(w: Workspace, id: String) = w.copy(transactions = w.transactions.filter { it.id != id })

    fun upsertCategory(w: Workspace, c: FinCategory) =
        if (w.finCategories.any { it.id == c.id }) w.copy(finCategories = w.finCategories.map { if (it.id == c.id) c else it })
        else w.copy(finCategories = w.finCategories + c)

    fun setBudget(w: Workspace, categoryId: String, limit: Long?): Workspace {
        val rest = w.budgets.filter { it.categoryId != categoryId }
        return w.copy(budgets = if (limit == null || limit <= 0) rest else rest + Budget(newId(), categoryId, limit))
    }

    fun upsertRecurring(w: Workspace, r: Recurring) =
        if (w.recurrings.any { it.id == r.id }) w.copy(recurrings = w.recurrings.map { if (it.id == r.id) r else it })
        else w.copy(recurrings = w.recurrings + r)

    fun deleteRecurring(w: Workspace, id: String) = w.copy(recurrings = w.recurrings.filter { it.id != id })

    /** «Оплачено»: создаёт операцию и переносит платёж на следующий период. */
    fun payRecurring(w: Workspace, id: String, date: LocalDate): Workspace {
        val r = w.recurrings.firstOrNull { it.id == id } ?: return w
        val acc = r.accountId ?: w.accounts.firstOrNull { !it.archived }?.id ?: return w.copy(recurrings = w.recurrings.map { if (it.id == id) r.advanced() else it })
        val t = Transaction(newId(), r.type, r.amount, acc, categoryId = r.categoryId, date = date.toString(), note = r.name, source = "Регулярный платёж")
        return upsertTx(w, t).copy(recurrings = w.recurrings.map { if (it.id == id) r.advanced() else it })
    }

    fun upsertGoal(w: Workspace, g: Goal) =
        if (w.goals.any { it.id == g.id }) w.copy(goals = w.goals.map { if (it.id == g.id) g else it }) else w.copy(goals = w.goals + g)

    fun deleteGoal(w: Workspace, id: String) = w.copy(goals = w.goals.filter { it.id != id })

    /** Добавляет импортированные операции, пропуская уже загруженные. */
    fun importTxs(w: Workspace, list: List<Transaction>): Pair<Workspace, Int> {
        val keys = w.transactions.mapNotNull { it.importKey }.toHashSet()
        val fresh = list.filter { it.importKey == null || it.importKey !in keys }.distinctBy { it.importKey ?: it.id }
        return w.copy(transactions = w.transactions + fresh) to fresh.size
    }
}

/** Импорт банковских выписок (CSV): Т-Банк и любые таблицы с колонками «дата / сумма / описание». */
object BankImport {
    data class Row(val date: LocalDate, val amount: Long, val description: String, val bankCategory: String)

    fun decode(bytes: ByteArray): String {
        val utf = bytes.toString(Charsets.UTF_8)
        // Выписки банков часто в Windows-1251
        return if (utf.contains('�')) bytes.toString(Charset.forName("windows-1251")) else utf.removePrefix("﻿")
    }

    private fun splitLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == sep && !q -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    private val dateFormats = listOf("dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yy", "dd/MM/yyyy")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(s: String): LocalDate? {
        val t = s.trim()
        for (f in dateFormats) {
            runCatching { return java.time.LocalDateTime.parse(t, f).toLocalDate() }
            runCatching { return LocalDate.parse(t, f) }
        }
        return null
    }

    fun parseAmount(s: String): Long? {
        val t = s.replace(" ", "").replace(" ", "").replace("₽", "").replace("RUB", "").replace(',', '.').trim()
        if (t.isEmpty()) return null
        val v = t.toBigDecimalOrNull() ?: return null
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Разбор CSV: сам находит разделитель, строку заголовка и нужные колонки. */
    fun parse(text: String): List<Row> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sep = listOf(';', '\t', ',').maxBy { s -> lines.first().count { it == s } }
        val headerIdx = lines.indexOfFirst { l ->
            val low = l.lowercase()
            low.contains("дата") && listOf("сумм", "приход", "расход", "списан", "зачисл").any { low.contains(it) }
        }
        if (headerIdx < 0) return emptyList()
        val header = splitLine(lines[headerIdx], sep).map { it.lowercase() }
        fun col(vararg keys: String, avoid: List<String> = emptyList()) =
            header.indexOfFirst { h -> keys.any { h.contains(it) } && avoid.none { h.contains(it) } }
        val iDate = col("дата операции").takeIf { it >= 0 } ?: col("дата")
        val iAmount = col("сумма операции", avoid = listOf("округлен")).takeIf { it >= 0 } ?: col("сумма", avoid = listOf("округлен", "кэшбэк", "бонус"))
        val iDesc = col("описание", "назначение", "контрагент", "комментарий", "получатель")
        val iCat = col("категория")
        val iStatus = col("статус")
        val iIncome = col("приход", "зачислен", "поступлен")
        val iOutcome = col("расход", "списан")
        val out = ArrayList<Row>()
        for (l in lines.drop(headerIdx + 1)) {
            val c = splitLine(l, sep)
            fun g(i: Int) = if (i in c.indices) c[i] else ""
            if (iStatus >= 0 && g(iStatus).uppercase().let { it == "FAILED" || it.contains("ОТКЛОН") }) continue
            val date = parseDate(g(iDate)) ?: continue
            var amount = if (iAmount >= 0) parseAmount(g(iAmount)) else null
            if (amount == null && (iIncome >= 0 || iOutcome >= 0)) {
                val inc = parseAmount(g(iIncome)) ?: 0L
                val outc = parseAmount(g(iOutcome)) ?: 0L
                amount = inc - kotlin.math.abs(outc)
            }
            if (amount == null || amount == 0L) continue
            out += Row(date, amount, g(iDesc), g(iCat))
        }
        return out
    }

    /** Строки выписки → операции на выбранный счёт с подбором категорий. */
    fun toTransactions(rows: List<Row>, accountId: String, categories: List<FinCategory>, source: String): List<Transaction> {
        fun find(name: String, income: Boolean) = categories.firstOrNull { it.name == name && it.income == income }
        val otherExp = categories.firstOrNull { !it.income && it.name == "Другое" }
        val otherInc = categories.firstOrNull { it.income && it.name == "Другие доходы" }
        return rows.map { r ->
            val income = r.amount > 0
            val text = (r.bankCategory + " " + r.description).lowercase()
            val cat = if (income) {
                when {
                    text.contains("зарплат") -> find("Зарплата", true)
                    text.contains("кэшбэк") || text.contains("процент") -> find("Проценты и кэшбэк", true)
                    text.contains("возврат") -> find("Возвраты", true)
                    else -> otherInc
                }
            } else {
                FinanceDefaults.bankMap.firstOrNull { (keys, _) -> keys.any { text.contains(it) } }?.let { find(it.second, false) } ?: otherExp
            }
            Transaction(
                id = newId(),
                type = if (income) TxType.INCOME else TxType.EXPENSE,
                amount = kotlin.math.abs(r.amount),
                accountId = accountId,
                categoryId = cat?.id,
                date = r.date.toString(),
                note = r.description,
                source = source,
                importKey = "${r.date}|${r.amount}|${r.description.trim().lowercase()}",
            )
        }
    }
}
