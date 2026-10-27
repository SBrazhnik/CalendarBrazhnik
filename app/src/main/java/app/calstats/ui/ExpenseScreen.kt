@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.work.Category
import app.calstats.work.Expense
import app.calstats.work.ME
import app.calstats.work.ReceiptItem
import app.calstats.work.SplitMath
import app.calstats.work.SplitMode
import app.calstats.work.WorkOps
import app.calstats.work.newId

private class ItemDraft(val id: String, name: String, price: String, people: Set<String>) {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
    var people by mutableStateOf(people)
}

/** Чек: сумма, кто платил и как делить — поровну, долями, суммами или по позициям. */
@Composable
fun ExpenseScreen(vm: MainViewModel, bookId: String, expenseId: String?, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val w = vm.work
    val book = w.books.firstOrNull { it.id == bookId }
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { vm.pop() }) { Text("Счёт удалён — назад") } }
        return
    }
    val existing = w.expenses.firstOrNull { it.id == expenseId }
    val members = book.memberIds.mapNotNull { w.person(it) }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var amountText by remember { mutableStateOf(existing?.let { SplitMath.plain(it.amount) } ?: "") }
    var payer by remember { mutableStateOf(existing?.payerId ?: if (ME in book.memberIds) ME else book.memberIds.first()) }
    var date by remember { mutableStateOf(existing?.localDate ?: vm.today) }
    var category by remember { mutableStateOf(existing?.category ?: Category.FOOD) }
    var mode by remember { mutableStateOf(existing?.mode ?: SplitMode.EQUAL) }
    val isTransfer = existing?.isTransfer == true
    var recipient by remember { mutableStateOf(existing?.shares?.keys?.firstOrNull() ?: book.memberIds.firstOrNull { it != payer }) }

    val equalSet = remember {
        mutableStateMapOf<String, Boolean>().apply {
            book.memberIds.forEach { put(it, existing == null || existing.mode != SplitMode.EQUAL || it in existing.shares.keys) }
        }
    }
    val weights = remember {
        mutableStateMapOf<String, Long>().apply {
            book.memberIds.forEach { put(it, if (existing?.mode == SplitMode.SHARES) existing.shares[it] ?: 0L else 1L) }
        }
    }
    val exact = remember {
        mutableStateMapOf<String, String>().apply {
            book.memberIds.forEach { put(it, if (existing?.mode == SplitMode.EXACT) existing.shares[it]?.let { v -> SplitMath.plain(v) } ?: "" else "") }
        }
    }
    val items = remember {
        mutableStateListOf<ItemDraft>().apply {
            existing?.items?.forEach { add(ItemDraft(it.id, it.name, SplitMath.plain(it.amount), it.personIds.toSet())) }
        }
    }
    var service by remember { mutableStateOf(existing?.servicePercent?.takeIf { it > 0 }?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val receiptItems = items.mapNotNull { d ->
        val a = SplitMath.parse(d.price) ?: return@mapNotNull null
        if (d.people.isEmpty()) null else ReceiptItem(d.id, d.name.ifBlank { "Позиция" }, a, book.memberIds.filter { it in d.people })
    }
    val servicePct = service.toIntOrNull()?.coerceIn(0, 100) ?: 0
    val amount: Long? = if (mode == SplitMode.ITEMS && !isTransfer) SplitMath.itemsTotal(receiptItems, servicePct).takeIf { it > 0 } else SplitMath.parse(amountText)

    fun build(): Expense? {
        val a = amount ?: return null
        if (a <= 0) return null
        if (isTransfer) {
            val to = recipient ?: return null
            return existing!!.copy(amount = a, payerId = payer, shares = mapOf(to to a), date = date.toString())
        }
        val shares: Map<String, Long> = when (mode) {
            SplitMode.EQUAL -> book.memberIds.filter { equalSet[it] == true }.associateWith { 1L }
            SplitMode.SHARES -> book.memberIds.associateWith { weights[it] ?: 0L }.filterValues { it > 0 }
            SplitMode.EXACT -> book.memberIds.associateWith { SplitMath.parse(exact[it] ?: "") ?: 0L }.filterValues { it != 0L }
            SplitMode.ITEMS -> emptyMap()
        }
        if (mode != SplitMode.ITEMS && shares.isEmpty()) return null
        if (mode == SplitMode.EXACT && shares.values.sum() != a) return null
        if (mode == SplitMode.ITEMS && receiptItems.isEmpty()) return null
        return Expense(
            id = existing?.id ?: newId(),
            bookId = bookId,
            title = title.trim().ifEmpty { category.title },
            amount = a,
            payerId = payer,
            date = date.toString(),
            mode = mode,
            shares = shares,
            items = if (mode == SplitMode.ITEMS) receiptItems else emptyList(),
            servicePercent = if (mode == SplitMode.ITEMS) servicePct else 0,
            category = category,
            created = existing?.created ?: System.currentTimeMillis(),
        )
    }
    val draft = build()
    val preview = draft?.let { SplitMath.owed(it) } ?: emptyMap()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.Filled.Close, "Закрыть") } },
                title = { Text(if (isTransfer) "Перевод" else if (existing == null) "Новый чек" else "Чек") },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                    Button(
                        enabled = draft != null,
                        onClick = {
                            val e = draft ?: return@Button
                            vm.updateWork { WorkOps.upsertExpense(it, e) }
                            vm.pop()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Сохранить") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isTransfer) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("За что: ужин, плитка, такси") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (mode != SplitMode.ITEMS || isTransfer) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Итого по чеку: ${SplitMath.money(amount ?: 0)}", style = MaterialTheme.typography.headlineSmall)
            }

            Text(if (isTransfer) "Кто перевёл" else "Кто платил", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                members.forEach { p ->
                    FilterChip(selected = payer == p.id, onClick = { payer = p.id }, label = { Text(p.name) }, leadingIcon = { Avatar(p, 20.dp) })
                }
            }
            if (isTransfer) {
                Text("Кому", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.filter { it.id != payer }.forEach { p ->
                        FilterChip(selected = recipient == p.id, onClick = { recipient = p.id }, label = { Text(p.name) }, leadingIcon = { Avatar(p, 20.dp) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Дата", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dateWithDow(date)) }
            }

            if (!isTransfer) {
                Text("Категория", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text("${c.emoji} ${c.title}") })
                    }
                }

                Text("Как делить", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, SplitMode.entries.size),
                            label = { Text(m.title, maxLines = 1) },
                        )
                    }
                }

                when (mode) {
                    SplitMode.EQUAL -> members.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = equalSet[p.id] == true, onCheckedChange = { equalSet[p.id] = it })
                            Avatar(p, 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, Modifier.weight(1f))
                            Text(preview[p.id]?.let { SplitMath.money(it) } ?: "—", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SplitMode.SHARES -> members.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p, 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                Text(preview[p.id]?.let { SplitMath.money(it) } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { weights[p.id] = ((weights[p.id] ?: 0L) - 1).coerceAtLeast(0) }) { Text("−") }
                            Text((weights[p.id] ?: 0L).toString(), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { weights[p.id] = (weights[p.id] ?: 0L) + 1 }) { Text("+") }
                        }
                    }
                    SplitMode.EXACT -> {
                        members.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p, 26.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(p.name, Modifier.weight(1f))
                                OutlinedTextField(
                                    value = exact[p.id] ?: "",
                                    onValueChange = { v -> exact[p.id] = v.filter { it.isDigit() || it == ',' || it == '.' } },
                                    singleLine = true,
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(130.dp),
                                )
                            }
                        }
                        val sum = book.memberIds.sumOf { SplitMath.parse(exact[it] ?: "") ?: 0L }
                        val left = (amount ?: 0L) - sum
                        Text(
                            if (left == 0L) "Распределено полностью" else if (left > 0) "Осталось распределить ${SplitMath.money(left)}" else "Лишние ${SplitMath.money(-left)}",
                            color = if (left == 0L) Color(0xFF22A06B) else MaterialTheme.colorScheme.error,
                        )
                    }
                    SplitMode.ITEMS -> {
                        items.forEach { d ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = d.name,
                                            onValueChange = { d.name = it },
                                            placeholder = { Text("Позиция") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        OutlinedTextField(
                                            value = d.price,
                                            onValueChange = { v -> d.price = v.filter { it.isDigit() || it == ',' || it == '.' } },
                                            placeholder = { Text("₽") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.width(100.dp),
                                        )
                                        IconButton(onClick = { items.remove(d) }) { Icon(Icons.Filled.Close, "Убрать") }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        members.forEach { p ->
                                            FilterChip(
                                                selected = p.id in d.people,
                                                onClick = { d.people = if (p.id in d.people) d.people - p.id else d.people + p.id },
                                                label = { Text(p.name) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { items.add(ItemDraft(newId(), "", "", book.memberIds.toSet())) }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Позиция чека")
                        }
                        OutlinedTextField(
                            value = service,
                            onValueChange = { v -> service = v.filter { it.isDigit() }.take(3) },
                            label = { Text("Обслуживание / чаевые, %") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(240.dp),
                        )
                        if (preview.isNotEmpty()) {
                            Text("Кто сколько должен по чеку", style = MaterialTheme.typography.titleSmall)
                            members.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(p, 22.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.name, Modifier.weight(1f))
                                    Text(preview[p.id]?.let { SplitMath.money(it) } ?: "—", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить чек?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.updateWork { WorkOps.deleteExpense(it, existing.id) }
                    vm.pop()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
