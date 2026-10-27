@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Screen
import app.calstats.work.Expense
import app.calstats.work.ME
import app.calstats.work.PALETTE
import app.calstats.work.SplitBook
import app.calstats.work.SplitMath
import app.calstats.work.SplitMode
import app.calstats.work.WorkOps
import app.calstats.work.Workspace
import app.calstats.work.newId

private val PLUS = Color(0xFF22A06B)
private val MINUS = Color(0xFFD64545)

/** Вкладка «Расходы»: общие траты с близкими, как в Kittysplit. */
@Composable
fun MoneyTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var creating by remember { mutableStateOf(false) }
    val books = w.books.filter { !it.archived }.sortedByDescending { b -> w.expenses.filter { it.bookId == b.id }.maxOfOrNull { it.created } ?: b.created }
    val myNet = remember(w.expenses) {
        w.books.sumOf { b -> SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L }
    }
    val owedToMe = remember(w.expenses) {
        w.books.sumOf { b -> (SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L).coerceAtLeast(0) }
    }
    val iOwe = remember(w.expenses) {
        w.books.sumOf { b -> (-(SplitMath.balances(w.expenses.filter { it.bookId == b.id })[ME] ?: 0L)).coerceAtLeast(0) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Расходы", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { vm.push(Screen.People) }) { Icon(painterResource(R.drawable.ic_group), "Люди и группы") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Новый счёт") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Итого по всем счетам", style = MaterialTheme.typography.labelLarge)
                        Text(
                            when {
                                myNet > 0 -> "Вам должны ${SplitMath.money(myNet)}"
                                myNet < 0 -> "Вы должны ${SplitMath.money(-myNet)}"
                                else -> "Все в расчёте"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (owedToMe > 0 && iOwe > 0) {
                            Text(
                                "Вам должны ${SplitMath.money(owedToMe)} · вы должны ${SplitMath.money(iOwe)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (books.isEmpty()) {
                item {
                    Text(
                        "Создайте счёт — например «Ресторан 27 октября», «Ремонт квартиры» или «Поездка в Казань». " +
                            "Добавляйте чеки, а приложение посчитает, кто кому сколько должен.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(books, key = { it.id }) { b -> BookCard(vm, b) }
            val archived = w.books.filter { it.archived }
            if (archived.isNotEmpty()) {
                item { Text("Архив", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(archived, key = { it.id }) { b -> BookCard(vm, b) }
            }
        }
    }

    if (creating) BookDialog(vm, null) { creating = false }
}

@Composable
private fun BookCard(vm: MainViewModel, b: SplitBook) {
    val w = vm.work
    val ex = w.expenses.filter { it.bookId == b.id }
    val total = ex.filter { !it.isTransfer }.sumOf { it.amount }
    val my = SplitMath.balances(ex)[ME] ?: 0L
    Card(onClick = { vm.push(Screen.Book(b.id)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(b.color)), contentAlignment = Alignment.Center) {
                Text(b.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Потрачено ${SplitMath.money(total)} · ${ex.count { !it.isTransfer }} ${Fmt.plural(ex.count { !it.isTransfer }.toLong(), "чек", "чека", "чеков")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                AvatarStack(b.memberIds.mapNotNull { w.person(it) }, 22.dp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        my > 0 -> "вам должны"
                        my < 0 -> "вы должны"
                        else -> "в расчёте"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (my != 0L) {
                    Text(SplitMath.money(kotlin.math.abs(my)), color = if (my > 0) PLUS else MINUS, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Создание/редактирование счёта: название и участники (по одному или группой). */
@Composable
fun BookDialog(vm: MainViewModel, book: SplitBook?, onDismiss: () -> Unit) {
    val w = vm.work
    var name by remember { mutableStateOf(book?.name ?: "") }
    var members by remember { mutableStateOf(book?.memberIds?.toSet() ?: setOf(ME)) }
    var groupId by remember { mutableStateOf(book?.groupId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (book == null) "Новый счёт" else "Счёт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Название: ресторан, ремонт, поездка") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (w.groups.isNotEmpty()) {
                    Text("Взять участников из группы", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        w.groups.forEach { g ->
                            FilterChip(selected = groupId == g.id, onClick = {
                                groupId = g.id
                                members = (g.memberIds + ME).toSet()
                            }, label = { Text(g.name) })
                        }
                    }
                }
                Text("Участники", style = MaterialTheme.typography.titleSmall)
                w.everyone.forEach { p ->
                    val used = book != null && w.expenses.any { e -> e.bookId == book.id && (e.payerId == p.id || p.id in SplitMath.owed(e).keys) }
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !used) { members = if (p.id in members) members - p.id else members + p.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = p.id in members, enabled = !used, onCheckedChange = { members = if (it) members + p.id else members - p.id })
                        Avatar(p, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(p.name)
                    }
                }
                TextButton(onClick = { vm.push(Screen.People) }) { Text("+ Добавить человека") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && members.size >= 2, onClick = {
                val ordered = w.everyone.map { it.id }.filter { it in members }
                val b = (book ?: SplitBook(newId(), name, ordered, color = PALETTE[w.books.size % PALETTE.size]))
                    .copy(name = name.trim(), memberIds = ordered, groupId = groupId)
                vm.updateWork { WorkOps.upsertBook(it, b) }
                onDismiss()
                if (book == null) vm.push(Screen.Book(b.id))
            }) { Text(if (book == null) "Создать" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Экран счёта: чеки и балансы. */
@Composable
fun BookScreen(vm: MainViewModel, id: String, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val w = vm.work
    val book = w.books.firstOrNull { it.id == id }
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { vm.pop() }) { Text("Счёт удалён — назад") } }
        return
    }
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var settleTarget by remember { mutableStateOf<SplitMath.Transfer?>(null) }
    val expenses = w.expenses.filter { it.bookId == id }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.created })
    val net = remember(expenses) { SplitMath.balances(expenses) }
    val transfers = remember(net) { SplitMath.settle(net) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { Text(book.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { share(ctx, summary(w, book, expenses)) }) { Icon(painterResource(R.drawable.ic_share), "Поделиться") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Название и участники") }, onClick = {
                                menu = false
                                edit = true
                            })
                            DropdownMenuItem(text = { Text(if (book.archived) "Вернуть из архива" else "В архив") }, onClick = {
                                menu = false
                                vm.updateWork { WorkOps.upsertBook(it, book.copy(archived = !book.archived)) }
                            })
                            DropdownMenuItem(text = { Text("Удалить счёт") }, onClick = {
                                menu = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.push(Screen.ExpenseEdit(id, null)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Чек") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Чеки") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Кто кому должен") })
            }
            if (tab == 0) {
                ExpensesList(vm, book, expenses)
            } else {
                BalancesList(vm, book, expenses, net, transfers) { settleTarget = it }
            }
        }
    }

    if (edit) BookDialog(vm, book) { edit = false }

    settleTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = { Text("Отметить оплату") },
            text = { Text("${w.person(t.from)?.name} перевёл(а) ${w.person(t.to)?.name} ${SplitMath.money(t.amount)}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork {
                        WorkOps.upsertExpense(
                            it,
                            Expense(
                                id = newId(), bookId = id, title = "Возврат долга", amount = t.amount, payerId = t.from,
                                date = vm.today.toString(), mode = SplitMode.EXACT, shares = mapOf(t.to to t.amount), isTransfer = true,
                            ),
                        )
                    }
                    settleTarget = null
                }) { Text("Да, оплачено") }
            },
            dismissButton = { TextButton(onClick = { settleTarget = null }) { Text("Отмена") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${book.name}»?") },
            text = { Text("Все чеки этого счёта будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.pop()
                    vm.updateWork { WorkOps.deleteBook(it, id) }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ExpensesList(vm: MainViewModel, book: SplitBook, expenses: List<Expense>) {
    val w = vm.work
    val real = expenses.filter { !it.isTransfer }
    val total = real.sumOf { it.amount }
    val byDate = expenses.groupBy { it.localDate }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                MiniStat("Всего", SplitMath.money(total), Modifier.weight(1f))
                MiniStat("На человека", SplitMath.money(if (book.memberIds.isEmpty()) 0 else total / book.memberIds.size), Modifier.weight(1f))
                val myShare = real.sumOf { SplitMath.owed(it)[ME] ?: 0L }
                MiniStat("Ваша доля", SplitMath.money(myShare), Modifier.weight(1f))
            }
        }
        if (expenses.isEmpty()) {
            item {
                Text(
                    "Чеков пока нет. Нажмите «Чек»: укажите сумму, кто платил и как делить — поровну, долями, суммами или по позициям чека.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        byDate.toSortedMap(compareByDescending<java.time.LocalDate> { it }).forEach { (date, list) ->
            item(key = "d$date") {
                Text(
                    Fmt.dayTitle(date, vm.today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(list, key = { it.id }) { e ->
                val payer = w.person(e.payerId)
                val mine = SplitMath.owed(e)[ME] ?: 0L
                Card(onClick = { vm.push(Screen.ExpenseEdit(book.id, e.id)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (e.isTransfer) "⇄" else e.category.emoji, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (e.isTransfer) {
                                val to = e.shares.keys.firstOrNull()
                                Text("${payer?.name} → ${w.person(to)?.name}", style = MaterialTheme.typography.titleSmall)
                                Text("Возврат долга", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "Платил(а) ${payer?.name ?: "?"} · ${e.mode.title.lowercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(SplitMath.money(e.amount), fontWeight = FontWeight.Bold)
                            if (!e.isTransfer && mine > 0) {
                                Text("ваша доля ${SplitMath.money(mine)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalancesList(
    vm: MainViewModel,
    book: SplitBook,
    expenses: List<Expense>,
    net: Map<String, Long>,
    transfers: List<SplitMath.Transfer>,
    onSettle: (SplitMath.Transfer) -> Unit,
) {
    val w = vm.work
    val real = expenses.filter { !it.isTransfer }
    val paid = book.memberIds.associateWith { id -> real.filter { it.payerId == id }.sumOf { it.amount } }
    val share = book.memberIds.associateWith { id -> real.sumOf { SplitMath.owed(it)[id] ?: 0L } }
    val maxAbs = (net.values.maxOfOrNull { kotlin.math.abs(it) } ?: 1L).coerceAtLeast(1L)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Переводы, чтобы рассчитаться", style = MaterialTheme.typography.titleMedium) }
        if (transfers.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = PLUS.copy(alpha = 0.12f))) {
                    Text("Все в расчёте 🎉", Modifier.padding(16.dp), color = PLUS, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(transfers, key = { "${it.from}>${it.to}" }) { t ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(w.person(t.from), 30.dp)
                    Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Avatar(w.person(t.to), 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${w.person(t.from)?.name} → ${w.person(t.to)?.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SplitMath.money(t.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = { onSettle(t) }) { Text("Оплачено") }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { Text("Балансы", style = MaterialTheme.typography.titleMedium) }
        items(book.memberIds, key = { "b$it" }) { pid ->
            val p = w.person(pid)
            val n = net[pid] ?: 0L
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: "?", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "заплатил(а) ${SplitMath.money(paid[pid] ?: 0)} · доля ${SplitMath.money(share[pid] ?: 0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(SplitMath.money(n, sign = true), color = if (n > 0) PLUS else if (n < 0) MINUS else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().padding(start = 38.dp, top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxWidth((kotlin.math.abs(n).toFloat() / maxAbs).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(if (n >= 0) PLUS else MINUS),
                    )
                }
            }
        }
    }
}

private fun summary(w: Workspace, book: SplitBook, expenses: List<Expense>): String {
    val real = expenses.filter { !it.isTransfer }
    val net = SplitMath.balances(expenses)
    val t = SplitMath.settle(net)
    return buildString {
        append("«${book.name}»\n")
        append("Потрачено: ${SplitMath.money(real.sumOf { it.amount })}\n\n")
        real.sortedBy { it.date }.forEach { e ->
            append("• ${e.title} — ${SplitMath.money(e.amount)} (платил(а) ${w.person(e.payerId)?.name})\n")
        }
        append("\nКто кому должен:\n")
        if (t.isEmpty()) append("Все в расчёте\n")
        t.forEach { append("${w.person(it.from)?.name} → ${w.person(it.to)?.name}: ${SplitMath.money(it.amount)}\n") }
    }
}

private fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Отправить итог"))
}

