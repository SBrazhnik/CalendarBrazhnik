package app.calstats.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceTest {
    private val tbank = """
        "Дата операции";"Дата платежа";"Номер карты";"Статус";"Сумма операции";"Валюта операции";"Сумма платежа";"Валюта платежа";"Кэшбэк";"Категория";"MCC";"Описание";"Бонусы (включая кэшбэк)";"Округление на инвесткопилку";"Сумма операции с округлением"
        "15.11.2026 19:02:11";"15.11.2026";"*1234";"OK";"-1234,50";"RUB";"-1234,50";"RUB";"";"Супермаркеты";"5411";"Пятёрочка";"12,00";"0,00";"1234,50"
        "14.11.2026 09:00:00";"14.11.2026";"*1234";"OK";"85000,00";"RUB";"85000,00";"RUB";"";"Пополнения";"";"Зарплата ООО Ромашка";"0,00";"0,00";"85000,00"
        "13.11.2026 12:00:00";"13.11.2026";"*1234";"FAILED";"-500,00";"RUB";"-500,00";"RUB";"";"Такси";"4121";"Яндекс Такси";"0,00";"0,00";"500,00"
        "12.11.2026 12:00:00";"12.11.2026";"*1234";"OK";"-350,00";"RUB";"-350,00";"RUB";"";"Фастфуд";"5814";"Вкусно и точка";"0,00";"0,00";"350,00"
    """.trimIndent()

    @Test fun parsesTbankCsv() {
        val rows = BankImport.parse(tbank)
        assertEquals(3, rows.size) // FAILED пропущена
        assertEquals(-123450L, rows[0].amount)
        assertEquals(LocalDate.of(2026, 11, 15), rows[0].date)
        val cats = FinanceDefaults.categories()
        val txs = BankImport.toTransactions(rows, "acc", cats, "Т-Банк")
        assertEquals("Продукты", cats.first { it.id == txs[0].categoryId }.name)
        assertEquals(TxType.INCOME, txs[1].type)
        assertEquals("Зарплата", cats.first { it.id == txs[1].categoryId }.name)
        assertEquals("Кафе и рестораны", cats.first { it.id == txs[2].categoryId }.name)
    }

    @Test fun reimportSkipsDuplicates() {
        val cats = FinanceDefaults.categories()
        val txs = BankImport.toTransactions(BankImport.parse(tbank), "acc", cats, "Т-Банк")
        val (w1, n1) = FinanceOps.importTxs(Workspace(), txs)
        assertEquals(3, n1)
        val again = BankImport.toTransactions(BankImport.parse(tbank), "acc", cats, "Т-Банк")
        val (_, n2) = FinanceOps.importTxs(w1, again)
        assertEquals(0, n2)
    }

    @Test fun genericCsvWithIncomeOutcomeColumns() {
        val csv = "Дата,Описание,Приход,Расход\n01.11.2026,Кофе,,250.00\n02.11.2026,Возврат,100,\n"
        val rows = BankImport.parse(csv)
        assertEquals(2, rows.size)
        assertEquals(-25000L, rows[0].amount)
        assertEquals(10000L, rows[1].amount)
    }

    @Test fun balancesAndTransfers() {
        val card = Account("c", "Карта", startBalance = 100000)
        val cash = Account("h", "Наличные", AccountType.CASH)
        val txs = listOf(
            Transaction("1", TxType.EXPENSE, 30000, "c", date = "2026-11-01"),
            Transaction("2", TxType.INCOME, 50000, "c", date = "2026-11-02"),
            Transaction("3", TxType.TRANSFER, 20000, "c", toAccountId = "h", date = "2026-11-03"),
        )
        assertEquals(100000L, FinanceMath.balance(card, txs))
        assertEquals(20000L, FinanceMath.balance(cash, txs))
        val w = Workspace(accounts = listOf(card, cash), transactions = txs)
        assertEquals(120000L, FinanceMath.total(w))
        assertEquals(30000L, FinanceMath.expense(FinanceMath.inMonth(txs, YearMonth.of(2026, 11))))
    }

    @Test fun payRecurringAdvancesAndCreatesTx() {
        val w = Workspace(
            accounts = listOf(Account("c", "Карта")),
            recurrings = listOf(Recurring("r", "Музыка", 29900, nextDate = "2026-11-20")),
        )
        val w2 = FinanceOps.payRecurring(w, "r", LocalDate.of(2026, 11, 20))
        assertEquals(1, w2.transactions.size)
        assertEquals("2026-12-20", w2.recurrings.single().nextDate)
        assertTrue(FinanceMath.balance(w2.accounts.single(), w2.transactions) == -29900L)
    }
}
