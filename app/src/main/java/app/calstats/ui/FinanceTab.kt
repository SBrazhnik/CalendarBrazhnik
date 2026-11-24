@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.work.Account
import app.calstats.work.AccountType
import app.calstats.work.BankImport
import app.calstats.work.FinCategory
import app.calstats.work.FinanceMath
import app.calstats.work.FinanceOps
import app.calstats.work.Goal
import app.calstats.work.PALETTE
import app.calstats.work.Recurring
import app.calstats.work.RepeatPeriod
import app.calstats.work.SplitMath
import app.calstats.work.Transaction
import app.calstats.work.TxType
import app.calstats.work.Workspace
import app.calstats.work.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private val INCOME_C = Color(0xFF2E8B6B)
private val EXPENSE_C = Color(0xFFD9667A)
private val FIN_VIEWS = listOf("OVERVIEW" to "Обзор", "TXS" to "Операции", "DEBTS" to "Долги", "BUDGETS" to "Бюджеты", "PAYMENTS" to "Платежи", "GOALS" to "Цели", "ACCOUNTS" to "Счета")

private fun money(k: Long) = SplitMath.money(k)

/** Модуль «Финансы». */
@Composable
fun FinanceTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (vm.work.finCategories.isEmpty()) vm.updateWork { FinanceOps.ensureDefaults(it) } }
    val w = vm.work
    var view by navView(vm, "fin", "OVERVIEW")
    var month by remember { mutableStateOf(YearMonth.now()) }
    var txSheet by remember { mutableStateOf<Transaction?>(null) }
    var accountDialog by remember { mutableStateOf<Account?>(null) }
    var menu by remember { mutableStateOf(false) }
    var importRows by remember { mutableStateOf<List<BankImport.Row>?>(null) }
    var debtDialog by remember { mutableStateOf<app.calstats.work.Debt?>(null) }
    var debtSheet by remember { mutableStateOf<String?>(null) }
    var partyDialog by remember { mutableStateOf<app.calstats.work.Counterparty?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val rows: List<BankImport.Row>? = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { s -> BankImport.parse(BankImport.decode(s.readBytes())) }
                } catch (e: Exception) {
                    null
                }
            }
            when {
                rows == null -> vm.message = "Не удалось прочитать файл"
                rows.isEmpty() -> vm.message = "В файле не найдено операций. Нужна таблица CSV с колонками «Дата» и «Сумма»."
                else -> importRows = rows
            }
        }
    }

    fun newTx(type: TxType = TxType.EXPENSE) {
        val acc = w.accounts.firstOrNull { !it.archived }
        if (acc == null) {
            accountDialog = Account(newId(), "Карта")
            vm.message = "Сначала добавьте счёт"
            return
        }
        txSheet = Transaction(newId(), type, 0, acc.id, date = vm.today.toString())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Финансы", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("🧾 Сканировать QR-код чека") }, onClick = { menu = false; vm.scanReceipt(ctx) })
                            DropdownMenuItem(text = { Text("Импорт выписки банка (CSV)") }, onClick = {
                                menu = false
                                importer.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/octet-stream", "application/vnd.ms-excel"))
                            })
                            DropdownMenuItem(text = { Text("Новый счёт") }, onClick = {
                                menu = false
                                accountDialog = Account(newId(), "", color = PALETTE[w.accounts.size % PALETTE.size])
                            })
                            if (vm.isEnabled(Tab.MONEY)) {
                                DropdownMenuItem(text = { Text("Общие расходы с друзьями") }, onClick = {
                                    menu = false
                                    vm.tab = Tab.MONEY
                                })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (view == "DEBTS") {
                ExtendedFloatingActionButton(
                    onClick = { debtDialog = app.calstats.work.Debt(newId(), "", date = vm.today.toString()) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Долг") },
                )
            } else {
                ExtendedFloatingActionButton(onClick = { newTx() }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Операция") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FIN_VIEWS.forEach { (k, t) -> FilterChip(selected = view == k, onClick = { view = k }, label = { Text(t) }) }
            }
            if (view in listOf("OVERVIEW", "TXS", "BUDGETS")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") }
                    Text(
                        Fmt.monthName(month.monthValue) + if (month.year != vm.today.year) " ${month.year}" else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (w.accounts.none { !it.archived }) {
                    item { StartCard(vm) { accountDialog = it } }
                }
                when (view) {
                    "OVERVIEW" -> {
                        overview(vm, month, onTx = { txSheet = it }, onView = { view = it })
                    }
                    "TXS" -> transactions(vm, month) { txSheet = it }
                    "DEBTS" -> debtsSection(vm, onDebt = { debtDialog = it }, onParty = { partyDialog = it }, onOpen = { debtSheet = it.id })
                    "BUDGETS" -> budgets(vm, month)
                    "PAYMENTS" -> payments(vm)
                    "GOALS" -> goals(vm)
                    "ACCOUNTS" -> accounts(vm) { accountDialog = it }
                }
            }
        }
    }

    txSheet?.let { t -> TxSheet(vm, t) { txSheet = null } }
    debtDialog?.let { d -> DebtDialog(vm, d) { debtDialog = null } }
    LaunchedEffect(vm.openDebt) { vm.openDebt?.let { debtSheet = it; vm.openDebt = null } }
    LaunchedEffect(vm.newDebt) { if (vm.newDebt) { debtDialog = app.calstats.work.Debt(newId(), "", date = vm.today.toString()); vm.newDebt = false } }
    debtSheet?.let { id -> DebtSheet(vm, id, onEdit = { debtSheet = null; debtDialog = it }) { debtSheet = null } }
    partyDialog?.let { p -> PartyDialog(vm, p) { partyDialog = null } }
    if (vm.loanCalc) LoanSheet(vm) { vm.loanCalc = false }
    accountDialog?.let { a -> AccountDialog(vm, a) { accountDialog = null } }
    importRows?.let { rows -> ImportDialog(vm, rows) { importRows = null } }
}

@Composable
private fun StartCard(vm: MainViewModel, onAccount: (Account) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("С чего начать", style = MaterialTheme.typography.titleMedium)
            Text(
                "Добавьте счета — карту, наличные, накопительный. Потом записывайте траты кнопкой «Операция» " +
                    "или загрузите выписку банка: меню ⋮ → «Импорт выписки».",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAccount(Account(newId(), "Карта", AccountType.CARD, color = PALETTE[0])) }) { Text("💳 Карта") }
                OutlinedButton(onClick = { onAccount(Account(newId(), "Наличные", AccountType.CASH, color = PALETTE[1])) }) { Text("💵 Наличные") }
            }
        }
    }
}

// ======================= ОБЗОР =======================

private fun LazyListScope.overview(vm: MainViewModel, month: YearMonth, onTx: (Transaction) -> Unit, onView: (String) -> Unit) {
    val w = vm.work
    val txs = FinanceMath.inMonth(w.transactions, month)
    val inc = FinanceMath.income(txs)
    val exp = FinanceMath.expense(txs)
    item(key = "hero") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Все счета", style = MaterialTheme.typography.labelLarge)
                Text(money(FinanceMath.total(w)), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Доходы", style = MaterialTheme.typography.labelMedium)
                        Text("+" + money(inc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Расходы", style = MaterialTheme.typography.labelMedium)
                        Text("−" + money(exp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Итог месяца", style = MaterialTheme.typography.labelMedium)
                        Text(SplitMath.money(inc - exp, sign = true), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    bankInbox(vm)
    savingsCard(vm) { onView("DEBTS") }
    debtsOverview(vm, onDebts = { onView("DEBTS") }, onOpen = { vm.openDebt = it.id }, onNew = { onView("DEBTS"); vm.newDebt = true })
    trendsCard(vm)
    val byCat = FinanceMath.byCategory(txs)
    if (byCat.isNotEmpty()) {
        item(key = "donut") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(byCat.map { (id, v) -> (w.category(id)?.color?.let { Color(it) } ?: Color.Gray) to v }, exp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        byCat.take(6).forEach { (id, v) ->
                            val c = w.category(id)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(c?.color?.let { Color(it) } ?: Color.Gray))
                                Spacer(Modifier.width(6.dp))
                                Text("${c?.emoji ?: ""} ${c?.name ?: "Без категории"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${(v * 100 / exp.coerceAtLeast(1))}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (byCat.size > 6) Text("и ещё ${byCat.size - 6}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    val daily = FinanceMath.daily(w.transactions, month)
    if (daily.any { it > 0 }) {
        item(key = "daily") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Траты по дням", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val max = daily.max().coerceAtLeast(1)
                    val bar = MaterialTheme.colorScheme.primary
                    val todayIdx = if (YearMonth.from(vm.today) == month) vm.today.dayOfMonth - 1 else -1
                    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                        val bw = size.width / daily.size
                        daily.forEachIndexed { i, v ->
                            val h = size.height * v / max
                            drawRoundRect(
                                color = if (i == todayIdx) EXPENSE_C else bar.copy(alpha = 0.85f),
                                topLeft = Offset(i * bw + bw * 0.15f, size.height - h),
                                size = Size(bw * 0.7f, h.coerceAtLeast(if (v > 0) 2f else 0f)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                            )
                        }
                    }
                    val days = if (YearMonth.from(vm.today) == month) vm.today.dayOfMonth else month.lengthOfMonth()
                    Text(
                        "В среднем ${money(daily.take(days).sum() / days.coerceAtLeast(1))} в день",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
    val budgets = w.budgets
    if (budgets.isNotEmpty()) {
        item(key = "budgets") {
            Card(onClick = { onView("BUDGETS") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Бюджеты", style = MaterialTheme.typography.titleSmall)
                    budgets.sortedByDescending { b -> txs.filter { it.type == TxType.EXPENSE && it.categoryId == b.categoryId }.sumOf { it.amount } * 100 / b.monthlyLimit.coerceAtLeast(1) }
                        .take(3).forEach { b -> BudgetRow(vm, b.categoryId, b.monthlyLimit, txs) }
                }
            }
        }
    }
    val upcoming = w.recurrings.filter { it.active && !it.next.isAfter(vm.today.plusDays(14)) }.sortedBy { it.next }
    if (upcoming.isNotEmpty()) {
        item(key = "upcoming") {
            Card(onClick = { onView("PAYMENTS") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ближайшие платежи", style = MaterialTheme.typography.titleSmall)
                    upcoming.take(4).forEach { r -> PaymentLine(vm, r) }
                }
            }
        }
    }
    val recent = w.transactions.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.created }).take(5)
    if (recent.isNotEmpty()) {
        item(key = "recent_h") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Последние операции", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onView("TXS") }) { Text("Все") }
            }
        }
        items(recent, key = { "r" + it.id }) { t -> TxRow(vm, t) { onTx(t) } }
    }
}

@Composable
private fun Donut(parts: List<Pair<Color, Long>>, total: Long) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            parts.forEach { (c, v) ->
                val sweep = 360f * v / total.coerceAtLeast(1)
                drawArc(c, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("расходы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ======================= ОПЕРАЦИИ =======================

private fun LazyListScope.transactions(vm: MainViewModel, month: YearMonth, onTx: (Transaction) -> Unit) {
    val w = vm.work
    val txs = FinanceMath.inMonth(w.transactions, month)
    if (txs.isEmpty()) {
        item { Text("Операций за этот месяц нет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        return
    }
    txs.groupBy { it.localDate }.toSortedMap(compareByDescending<LocalDate> { it }).forEach { (d, list) ->
        item(key = "d$d") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayTitle(d, vm.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                val e = FinanceMath.expense(list)
                if (e > 0) Text("−" + money(e), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list.sortedByDescending { it.created }, key = { it.id }) { t -> TxRow(vm, t) { onTx(t) } }
    }
}

@Composable
private fun TxRow(vm: MainViewModel, t: Transaction, onClick: () -> Unit) {
    val w = vm.work
    val c = w.category(t.categoryId)
    val acc = w.account(t.accountId)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background((c?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Text(if (t.type == TxType.TRANSFER) "⇄" else c?.emoji ?: "•", fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (t.type) {
                        TxType.TRANSFER -> "${acc?.name} → ${w.account(t.toAccountId)?.name}"
                        else -> c?.name ?: "Без категории"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(t.note.takeIf { it.isNotBlank() }, acc?.name?.takeIf { t.type != TxType.TRANSFER }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (t.type) {
                    TxType.EXPENSE -> "−" + money(t.amount)
                    TxType.INCOME -> "+" + money(t.amount)
                    TxType.TRANSFER -> money(t.amount)
                },
                fontWeight = FontWeight.Bold,
                color = when (t.type) {
                    TxType.INCOME -> INCOME_C
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

// ======================= БЮДЖЕТЫ =======================

private fun LazyListScope.budgets(vm: MainViewModel, month: YearMonth) {
    val w = vm.work
    val txs = FinanceMath.inMonth(w.transactions, month)
    val totalLimit = w.budgets.sumOf { it.monthlyLimit }
    val totalSpent = w.budgets.sumOf { b -> txs.filter { it.type == TxType.EXPENSE && it.categoryId == b.categoryId }.sumOf { it.amount } }
    item(key = "bh") {
        Text(
            "Лимит трат на месяц по категориям. Полоса краснеет, когда трат больше лимита.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (w.budgets.isNotEmpty()) {
        item(key = "btotal") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Всего по бюджетам: ${money(totalSpent)} из ${money(totalLimit)}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Progress(totalSpent, totalLimit)
                }
            }
        }
    }
    items(w.finCategories.filter { !it.income && !it.archived }, key = { "b" + it.id }) { c ->
        var edit by remember { mutableStateOf(false) }
        val limit = w.budgets.firstOrNull { it.categoryId == c.id }?.monthlyLimit
        Card(onClick = { edit = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                if (limit != null) BudgetRow(vm, c.id, limit, txs)
                else {
                    val spent = txs.filter { it.type == TxType.EXPENSE && it.categoryId == c.id }.sumOf { it.amount }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji} ${c.name}", Modifier.weight(1f))
                        Text(if (spent > 0) money(spent) else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("+ лимит", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (edit) {
            var text by remember { mutableStateOf(limit?.let { SplitMath.plain(it) } ?: "") }
            AlertDialog(
                onDismissRequest = { edit = false },
                title = { Text("${c.emoji} ${c.name}") },
                text = {
                    OutlinedTextField(
                        text, { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' } },
                        label = { Text("Лимит в месяц, ₽") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.updateWork { FinanceOps.setBudget(it, c.id, SplitMath.parse(text)) }
                        edit = false
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.updateWork { FinanceOps.setBudget(it, c.id, null) }
                        edit = false
                    }) { Text(if (limit != null) "Убрать лимит" else "Отмена") }
                },
            )
        }
    }
}

@Composable
private fun BudgetRow(vm: MainViewModel, categoryId: String, limit: Long, monthTxs: List<Transaction>) {
    val c = vm.work.category(categoryId)
    val spent = monthTxs.filter { it.type == TxType.EXPENSE && it.categoryId == categoryId }.sumOf { it.amount }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c?.emoji ?: ""} ${c?.name ?: "?"}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${money(spent)} / ${money(limit)}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Progress(spent, limit)
        val left = limit - spent
        Text(
            if (left >= 0) "осталось ${money(left)}" else "перерасход ${money(-left)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Progress(value: Long, max: Long) {
    val f = if (max <= 0) 0f else value.toFloat() / max
    LinearProgressIndicator(
        progress = { f.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = when {
            f > 1f -> MaterialTheme.colorScheme.error
            f > 0.85f -> Color(0xFFE5A100)
            else -> MaterialTheme.colorScheme.primary
        },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

// ======================= ПЛАТЕЖИ И ПОДПИСКИ =======================

private fun LazyListScope.payments(vm: MainViewModel) {
    val w = vm.work
    val list = w.recurrings.sortedWith(compareBy({ !it.active }, { it.next }))
    item(key = "ph") {
        var add by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Подписки и регулярные платежи", style = MaterialTheme.typography.labelLarge)
                Text("${money(list.filter { it.active && it.type == TxType.EXPENSE }.sumOf { it.monthly })} в месяц", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${money(list.filter { it.active && it.type == TxType.EXPENSE }.sumOf { it.monthly } * 12)} в год",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { add = true }) { Text("+ Добавить платёж", color = MaterialTheme.colorScheme.onSecondary) }
            }
        }
        if (add) RecurringDialog(vm, Recurring(newId(), "", 0, nextDate = vm.today.toString())) { add = false }
    }
    if (list.isEmpty()) {
        item { Text("Добавьте подписки (музыка, кино, облако), ЖКХ, кредит, аренду — приложение напомнит о них на Главной.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    items(list, key = { "p" + it.id }) { r ->
        var edit by remember { mutableStateOf(false) }
        Card(onClick = { edit = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Box(Modifier.padding(12.dp)) { PaymentLine(vm, r) }
        }
        if (edit) RecurringDialog(vm, r) { edit = false }
    }
}

@Composable
internal fun PaymentLine(vm: MainViewModel, r: Recurring) {
    val days = ChronoUnit.DAYS.between(vm.today, r.next)
    val c = vm.work.category(r.categoryId)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c?.emoji ?: if (r.subscription) "🔁" else "📅", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !r.active -> "приостановлен"
                    days < 0 -> "просрочен на ${Fmt.days(-days)}"
                    days == 0L -> "сегодня"
                    days == 1L -> "завтра"
                    else -> "${Fmt.dateNoYear(r.next)} · через ${Fmt.days(days)}"
                } + " · " + r.period.title.lowercase(),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.active && days <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(r.amount), fontWeight = FontWeight.Bold)
            if (r.active && days <= 3) {
                TextButton(onClick = {
                    vm.updateWork { FinanceOps.payRecurring(it, r.id, vm.today) }
                    vm.message = "«${r.name}» оплачен, следующий — ${Fmt.dateNoYear(r.advanced().next)}"
                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Оплачено") }
            }
        }
    }
}

@Composable
internal fun RecurringDialog(vm: MainViewModel, r: Recurring, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = vm.work
    val exists = w.recurrings.any { it.id == r.id }
    var name by remember { mutableStateOf(r.name) }
    var amount by remember { mutableStateOf(if (r.amount > 0) SplitMath.plain(r.amount) else "") }
    var period by remember { mutableStateOf(r.period) }
    var next by remember { mutableStateOf(r.next) }
    var account by remember { mutableStateOf(r.accountId ?: w.accounts.firstOrNull { !it.archived }?.id) }
    var category by remember { mutableStateOf(r.categoryId ?: w.finCategories.firstOrNull { it.name == "Подписки" }?.id) }
    var subscription by remember { mutableStateOf(r.subscription) }
    var active by remember { mutableStateOf(r.active) }
    var trial by remember { mutableStateOf(r.trialUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Платёж" else "Новый платёж") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название: Яндекс Плюс, ЖКХ, кредит") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                if (!exists && subscription && name.isBlank()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        app.calstats.work.SUB_CATALOG.forEach { n -> FilterChip(selected = false, onClick = { name = n }, label = { Text(n) }) }
                    }
                }
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Сумма, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatPeriod.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.title) }) }
                }
                OutlinedButton(onClick = { pickDate(ctx, next) { next = it } }) { Text("Следующий платёж: ${Fmt.date(next)}") }
                Text("Счёт", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    w.accounts.filter { !it.archived }.forEach { a -> FilterChip(selected = account == a.id, onClick = { account = a.id }, label = { Text(a.name) }) }
                }
                Text("Категория", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    w.finCategories.filter { !it.income && !it.archived }.forEach { c ->
                        FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(subscription, { subscription = it })
                    Text("Подписка")
                }
                if (subscription) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, trial ?: vm.today.plusDays(7)) { trial = it; next = it } }) {
                        Text(trial?.let { "Пробный период до ${Fmt.dateNoYear(it)}" } ?: "Есть пробный период")
                    }
                    if (trial != null) TextButton(onClick = { trial = null }) { Text("Убрать") }
                }
                if (exists) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(active, { active = it })
                    Text("Активен")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (SplitMath.parse(amount) ?: 0) > 0, onClick = {
                vm.updateWork {
                    FinanceOps.upsertRecurring(it, r.copy(name = name.trim(), amount = SplitMath.parse(amount) ?: 0, period = period,
                        nextDate = next.toString(), accountId = account, categoryId = category, subscription = subscription, active = active,
                        trialUntil = trial?.takeIf { subscription }?.toString()))
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = {
                    vm.updateWork { FinanceOps.deleteRecurring(it, r.id) }
                    onDismiss()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ======================= ЦЕЛИ =======================

private fun LazyListScope.goals(vm: MainViewModel) {
    val w = vm.work
    item(key = "gh") {
        var add by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Копите на отпуск, машину или подушку безопасности.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { add = true }) { Text("+ Цель") }
        }
        if (add) GoalDialog(vm, Goal(newId(), "", 0, color = PALETTE[w.goals.size % PALETTE.size])) { add = false }
    }
    items(w.goals.sortedBy { it.done }, key = { "g" + it.id }) { g ->
        var edit by remember { mutableStateOf(false) }
        var topUp by remember { mutableStateOf(false) }
        Card(onClick = { edit = true }, colors = CardDefaults.cardColors(containerColor = Color(g.color).copy(alpha = 0.14f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        Text("${money(g.saved)} из ${money(g.target)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!g.done) Button(onClick = { topUp = true }) { Text("Пополнить") }
                }
                Spacer(Modifier.height(10.dp))
                Progress(g.saved, g.target)
                g.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { d ->
                    val months = ChronoUnit.MONTHS.between(YearMonth.from(vm.today), YearMonth.from(d)).coerceAtLeast(1)
                    val left = (g.target - g.saved).coerceAtLeast(0)
                    Text(
                        "до ${Fmt.date(d)} · откладывать по ${money(left / months)} в месяц",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (edit) GoalDialog(vm, g) { edit = false }
        if (topUp) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { topUp = false },
                title = { Text("Пополнить «${g.name}»") },
                text = {
                    OutlinedTextField(text, { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Сумма, ₽") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                },
                confirmButton = {
                    TextButton(onClick = {
                        val a = SplitMath.parse(text) ?: 0
                        if (a > 0) vm.updateWork { FinanceOps.upsertGoal(it, g.copy(saved = g.saved + a, done = g.saved + a >= g.target)) }
                        topUp = false
                    }) { Text("Добавить") }
                },
                dismissButton = { TextButton(onClick = { topUp = false }) { Text("Отмена") } },
            )
        }
    }
}

@Composable
private fun GoalDialog(vm: MainViewModel, g: Goal, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.goals.any { it.id == g.id }
    var name by remember { mutableStateOf(g.name) }
    var target by remember { mutableStateOf(if (g.target > 0) SplitMath.plain(g.target) else "") }
    var saved by remember { mutableStateOf(if (g.saved > 0) SplitMath.plain(g.saved) else "") }
    var deadline by remember { mutableStateOf(g.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var emoji by remember { mutableStateOf(g.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Цель" else "Новая цель") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("На что копим") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(target, { v -> target = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Нужно, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(saved, { v -> saved = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Уже есть, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, deadline ?: vm.today.plusMonths(6)) { deadline = it } }) {
                        Text(deadline?.let { "К дате: " + Fmt.date(it) } ?: "Срок (необязательно)")
                    }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Убрать") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("🎯", "✈", "🚗", "🏠", "🛡", "💻", "📱", "🎓", "💍", "🏖").forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (SplitMath.parse(target) ?: 0) > 0, onClick = {
                val t = SplitMath.parse(target) ?: 0
                val s = SplitMath.parse(saved) ?: 0
                vm.updateWork { FinanceOps.upsertGoal(it, g.copy(name = name.trim(), target = t, saved = s, deadline = deadline?.toString(), emoji = emoji, done = s >= t)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = {
                    vm.updateWork { FinanceOps.deleteGoal(it, g.id) }
                    onDismiss()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ======================= СЧЕТА =======================

private fun LazyListScope.accounts(vm: MainViewModel, onEdit: (Account) -> Unit) {
    val w = vm.work
    item(key = "atotal") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Всего: ${money(FinanceMath.total(w))}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEdit(Account(newId(), "", color = PALETTE[w.accounts.size % PALETTE.size])) }) { Text("+ Счёт") }
        }
    }
    items(w.accounts.sortedBy { it.archived }, key = { "a" + it.id }) { a ->
        val bal = FinanceMath.balance(a, w.transactions)
        Card(onClick = { onEdit(a) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(a.color)), contentAlignment = Alignment.Center) {
                    Text(a.type.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name + if (a.archived) " (архив)" else "", style = MaterialTheme.typography.titleMedium)
                    Text(a.type.title + if (!a.includeInTotal) " · не в общем балансе" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(bal), fontWeight = FontWeight.Bold, color = if (bal < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AccountDialog(vm: MainViewModel, a: Account, onDismiss: () -> Unit) {
    val w = vm.work
    val exists = w.accounts.any { it.id == a.id }
    var name by remember { mutableStateOf(a.name) }
    var type by remember { mutableStateOf(a.type) }
    var start by remember { mutableStateOf(if (a.startBalance != 0L) SplitMath.plain(a.startBalance) else "") }
    var inTotal by remember { mutableStateOf(a.includeInTotal) }
    var archived by remember { mutableStateOf(a.archived) }
    var color by remember { mutableStateOf(a.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Счёт" else "Новый счёт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название: Т-Банк, Сбер, кошелёк") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.emoji} ${t.title}") }) }
                }
                OutlinedTextField(
                    start, { v -> start = v.filter { it.isDigit() || it == ',' || it == '.' || it == '-' } },
                    label = { Text(if (exists) "Начальный остаток, ₽" else "Сколько сейчас на счёте, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ColorPicker(color) { color = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(inTotal, { inTotal = it })
                    Text("Учитывать в общем балансе")
                }
                if (exists) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(archived, { archived = it })
                    Text("В архиве")
                }
                if (exists) TextButton(onClick = { confirmDelete = true }) { Text("Удалить счёт", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                vm.updateWork {
                    FinanceOps.upsertAccount(FinanceOps.ensureDefaults(it), a.copy(name = name.trim(), type = type, startBalance = SplitMath.parse(start) ?: 0,
                        includeInTotal = inTotal, archived = archived, color = color))
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (confirmDelete) {
        val n = w.transactions.count { it.accountId == a.id || it.toAccountId == a.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${a.name}»?") },
            text = { Text("Вместе с операциями этого счёта ($n). Чтобы сохранить историю, лучше отправить счёт в архив.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork { FinanceOps.deleteAccount(it, a.id) }
                    confirmDelete = false
                    onDismiss()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

// ======================= ОПЕРАЦИЯ =======================

/** Добавление и изменение операции; закрыли окно — введённое сохраняется. */
@Composable
fun TxSheet(vm: MainViewModel, t: Transaction, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = vm.work
    val exists = w.transactions.any { it.id == t.id }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(t.type) }
    var amount by remember { mutableStateOf(if (t.amount > 0) SplitMath.plain(t.amount) else "") }
    var account by remember { mutableStateOf(t.accountId) }
    var toAccount by remember { mutableStateOf(t.toAccountId ?: w.accounts.firstOrNull { it.id != t.accountId && !it.archived }?.id) }
    var category by remember { mutableStateOf(t.categoryId) }
    var date by remember { mutableStateOf(t.localDate) }
    var note by remember { mutableStateOf(t.note) }
    var done by remember { mutableStateOf(false) }
    val accounts = w.accounts.filter { !it.archived || it.id == t.accountId }

    fun save(): Boolean {
        if (done) return false
        val a = SplitMath.parse(amount) ?: return false
        if (a <= 0) return false
        if (type == TxType.TRANSFER && (toAccount == null || toAccount == account)) return false
        done = true
        vm.updateWork {
            FinanceOps.upsertTx(
                it,
                t.copy(
                    type = type, amount = a, accountId = account,
                    toAccountId = if (type == TxType.TRANSFER) toAccount else null,
                    categoryId = if (type == TxType.TRANSFER) null else category,
                    date = date.toString(), note = note.trim(),
                ),
            )
        }
        return true
    }

    ModalBottomSheet(onDismissRequest = {
        if (save() && !exists) vm.message = "Операция сохранена"
        onDismiss()
    }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { i, tt ->
                    SegmentedButton(
                        selected = type == tt,
                        onClick = {
                            type = tt
                            if (w.category(category)?.income != (tt == TxType.INCOME)) category = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, TxType.entries.size),
                        label = { Text(tt.title) },
                    )
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text("Сумма, ₽") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = when (type) {
                        TxType.INCOME -> INCOME_C
                        TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                        TxType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                    },
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(if (type == TxType.TRANSFER) "Откуда" else "Счёт", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                accounts.forEach { a -> FilterChip(selected = account == a.id, onClick = { account = a.id }, label = { Text("${a.type.emoji} ${a.name}") }) }
            }
            if (type == TxType.TRANSFER) {
                Text("Куда", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.filter { it.id != account }.forEach { a -> FilterChip(selected = toAccount == a.id, onClick = { toAccount = a.id }, label = { Text("${a.type.emoji} ${a.name}") }) }
                }
            } else {
                Text("Категория", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    w.finCategories.filter { it.income == (type == TxType.INCOME) && !it.archived }.forEach { c ->
                        FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = date == vm.today, onClick = { date = vm.today }, label = { Text("Сегодня") })
                FilterChip(selected = date == vm.today.minusDays(1), onClick = { date = vm.today.minusDays(1) }, label = { Text("Вчера") })
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dateNoYear(date)) }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (exists) OutlinedButton(onClick = {
                    done = true
                    vm.updateWork { FinanceOps.deleteTx(it, t.id) }
                    onDismiss()
                }, modifier = Modifier.height(52.dp)) { Icon(Icons.Filled.Delete, "Удалить") }
                Button(onClick = { if (save()) onDismiss() }, enabled = (SplitMath.parse(amount) ?: 0) > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(if (exists) "Сохранить" else "Добавить")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ImportDialog(vm: MainViewModel, rows: List<BankImport.Row>, onDismiss: () -> Unit) {
    val w = vm.work
    var account by remember { mutableStateOf(w.accounts.firstOrNull { !it.archived }?.id) }
    val from = rows.minOf { it.date }
    val to = rows.maxOf { it.date }
    val exp = rows.filter { it.amount < 0 }.sumOf { -it.amount }
    val inc = rows.filter { it.amount > 0 }.sumOf { it.amount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт выписки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Найдено операций: ${rows.size}\n${Fmt.range(from, to)}\nРасходы ${money(exp)} · доходы ${money(inc)}")
                Text("На какой счёт загрузить", style = MaterialTheme.typography.titleSmall)
                if (w.accounts.none { !it.archived }) Text("Сначала создайте счёт", color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    w.accounts.filter { !it.archived }.forEach { a -> FilterChip(selected = account == a.id, onClick = { account = a.id }, label = { Text(a.name) }) }
                }
                Text(
                    "Категории подберутся автоматически, их можно поправить. Повторная загрузка той же выписки дублей не создаст.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = account != null, onClick = {
                val acc = account ?: return@TextButton
                var added = 0
                vm.updateWork { ws: Workspace ->
                    val base = FinanceOps.ensureDefaults(ws)
                    val r = FinanceOps.importTxs(base, BankImport.toTransactions(rows, acc, base.finCategories, "Выписка"))
                    added = r.second
                    r.first
                }
                vm.message = "Загружено операций: $added"
                onDismiss()
            }) { Text("Загрузить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Suppress("unused")
private fun keepTypes(c: FinCategory) = c
