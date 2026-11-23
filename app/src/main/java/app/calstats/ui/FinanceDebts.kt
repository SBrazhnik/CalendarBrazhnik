@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.work.BankImport
import app.calstats.work.Counterparty
import app.calstats.work.Debt
import app.calstats.work.DebtDir
import app.calstats.work.DebtMath
import app.calstats.work.DebtOps
import app.calstats.work.DebtPayment
import app.calstats.work.SplitMath
import app.calstats.work.newId
import java.time.LocalDate

private val PLUS = Color(0xFF3F8A5A)
private val MINUS = Color(0xFFC0583F)

private fun money(k: Long) = SplitMath.money(k)

/** Карточка «Накопления и капитал» для обзора Финансов. */
fun LazyListScope.savingsCard(vm: MainViewModel, onDebts: () -> Unit) {
    item(key = "savings") {
        val w = vm.work
        val sav = DebtMath.savings(w)
        val sum = DebtMath.summary(w, vm.today)
        val credit = w.accounts.filter { !it.archived && it.type == app.calstats.work.AccountType.CREDIT }
            .sumOf { app.calstats.work.FinanceMath.balance(it, w.transactions) }.coerceAtMost(0)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Накопления", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(sav), style = MaterialTheme.typography.displaySmall)
                Text(
                    "Счета без кредиток + отложено на цели (${money(w.goals.filter { !it.done }.sumOf { it.saved })})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sum.owedToMe != 0L || sum.iOwe != 0L || credit != 0L) {
                    Text(
                        "Капитал с учётом долгов и кредиток: " + money(sav + sum.owedToMe - sum.iOwe + credit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDebts),
                    )
                }
            }
        }
    }
}

/** Блок «Долги» в обзоре Финансов. */
fun LazyListScope.debtsOverview(vm: MainViewModel, onDebts: () -> Unit, onOpen: (Debt) -> Unit, onNew: () -> Unit) {
    item(key = "debts_overview") {
        val w = vm.work
        val today = vm.today
        val sum = DebtMath.summary(w, today)
        val open = w.debts.filter { !it.closed }
        Card(onClick = onDebts, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Долги", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (sum.overdue > 0) Text("просрочено: ${sum.overdue}", style = MaterialTheme.typography.labelMedium, color = MINUS)
                }
                if (open.isEmpty()) {
                    Text("Нет открытых долгов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onNew, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+ Записать долг или заём") }
                    return@Column
                }
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Мне должны", style = MaterialTheme.typography.labelMedium)
                        Text(money(sum.owedToMe), color = PLUS, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Я должен", style = MaterialTheme.typography.labelMedium)
                        Text(money(sum.iOwe), color = MINUS, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Итог", style = MaterialTheme.typography.labelMedium)
                        Text(SplitMath.money(sum.net, sign = true), style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (sum.monthlyIn > 0 || sum.monthlyOut > 0) {
                    Text(
                        "Проценты в месяц: +${money(sum.monthlyIn)} / −${money(sum.monthlyOut)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                open.sortedWith(compareBy({ it.dueDate ?: LocalDate.MAX }, { -DebtMath.status(it, today).totalDue })).take(3).forEach { d ->
                    val s = DebtMath.status(d, today)
                    val who = w.counterparties.firstOrNull { it.id == d.partyId }?.name ?: "?"
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(d) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (d.dir == DebtDir.I_OWE) "↗ " else "↙ ") + who + d.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            d.dueDate?.let {
                                Text(if (s.overdueDays > 0) "просрочен ${s.overdueDays} дн." else "до ${Fmt.dateNoYear(it)}", style = MaterialTheme.typography.labelSmall,
                                    color = if (s.overdueDays > 0) MINUS else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(money(s.totalDue), color = if (d.dir == DebtDir.OWED_TO_ME) PLUS else MINUS, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (open.size > 3) Text("и ещё ${open.size - 3} → все долги", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Раздел «Долги»: люди и компании, кто кому сколько и за что, займы под процент. */
fun LazyListScope.debtsSection(vm: MainViewModel, onDebt: (Debt) -> Unit, onParty: (Counterparty?) -> Unit, onOpen: (Debt) -> Unit) {
    val w = vm.work
    val today = vm.today
    val sum = DebtMath.summary(w, today)
    item(key = "debts_hero") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Мне должны", style = MaterialTheme.typography.labelLarge)
                        Text(money(sum.owedToMe), style = MaterialTheme.typography.headlineSmall, color = PLUS)
                        if (sum.monthlyIn > 0) Text("+${money(sum.monthlyIn)} в месяц проценты", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Я должен", style = MaterialTheme.typography.labelLarge)
                        Text(money(sum.iOwe), style = MaterialTheme.typography.headlineSmall, color = MINUS)
                        if (sum.monthlyOut > 0) Text("+${money(sum.monthlyOut)} в месяц проценты", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Итог: " + SplitMath.money(sum.net, sign = true) + if (sum.overdue > 0) " · просрочено: ${sum.overdue}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sum.overdue > 0) MINUS else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDebt(Debt(newId(), "", DebtDir.OWED_TO_ME, date = today.toString())) }) { Text("+ Мне должны") }
                    OutlinedButton(onClick = { onDebt(Debt(newId(), "", DebtDir.I_OWE, date = today.toString())) }) { Text("+ Я должен") }
                }
                TextButton(onClick = { vm.loanCalc = true }) { Text("🏦 Кредитный калькулятор и график платежей") }
            }
        }
    }
    if (w.debts.isEmpty()) {
        item(key = "debts_empty") {
            Text(
                "Записывайте, кто сколько и за что должен — людям и компаниям. Для займов укажите процент в месяц: " +
                    "приложение посчитает, сколько набежало, и напомнит о сроке возврата.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val parties = w.counterparties.filter { p -> w.debts.any { it.partyId == p.id } }
        .sortedByDescending { kotlin.math.abs(DebtMath.partyBalance(w, it.id, today)) }
    parties.forEach { p ->
        item(key = "party_" + p.id) {
            val bal = DebtMath.partyBalance(w, p.id, today)
            val list = w.debts.filter { it.partyId == p.id }.sortedWith(compareBy({ it.closed }, { it.dueDate ?: LocalDate.MAX }))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onParty(p) }.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (p.company) "🏢" else p.name.take(1).uppercase()) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    bal > 0 -> "должен вам"
                                    bal < 0 -> "вы должны"
                                    else -> "в расчёте"
                                } + if (p.company) " · компания" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            money(kotlin.math.abs(bal)),
                            color = if (bal > 0) PLUS else if (bal < 0) MINUS else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    list.forEach { d ->
                        val s = DebtMath.status(d, today)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(d) }.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (d.dir == DebtDir.I_OWE) "↗ " else "↙ ") + d.title.ifBlank { d.dir.title },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        "с ${Fmt.dateNoYear(d.start)}",
                                        d.ratePerMonth.takeIf { it > 0 }?.let { "${fmtRate(it)}%/мес" },
                                        d.dueDate?.let { if (s.overdueDays > 0) "просрочен ${s.overdueDays} дн." else "до ${Fmt.dateNoYear(it)}" },
                                        if (d.closed) "закрыт" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (s.overdueDays > 0 && !d.closed) MINUS else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (d.closed) "✓" else money(s.totalDue),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (d.closed) MaterialTheme.colorScheme.onSurfaceVariant else if (d.dir == DebtDir.OWED_TO_ME) PLUS else MINUS,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun fmtRate(r: Double): String = if (r == r.toLong().toDouble()) r.toLong().toString() else "%.2f".format(r).trimEnd('0').trimEnd(',', '.')

/** Новый долг или его изменение. */
@Composable
fun DebtDialog(vm: MainViewModel, d0: Debt, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = vm.work
    val exists = w.debts.any { it.id == d0.id }
    var dir by remember { mutableStateOf(d0.dir) }
    var partyId by remember { mutableStateOf(d0.partyId) }
    var newParty by remember { mutableStateOf("") }
    var company by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(d0.title) }
    var amount by remember { mutableStateOf(if (d0.principal > 0) (d0.principal / 100.0).toString().removeSuffix(".0") else "") }
    var rate by remember { mutableStateOf(if (d0.ratePerMonth > 0) fmtRate(d0.ratePerMonth) else "") }
    var date by remember { mutableStateOf(d0.start) }
    var due by remember { mutableStateOf(d0.dueDate) }
    var confirmDelete by remember { mutableStateOf(false) }

    val kop = BankImport.parseAmount(amount) ?: 0
    val r = rate.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parties = w.counterparties
    // Быстрый выбор: участники «Задач»/«Расходов», которых ещё нет в списке.
    val people = w.people.map { it.name }.filter { n -> n.isNotBlank() && parties.none { it.name.equals(n, true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Долг" else "Новый долг") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DebtDir.entries.forEach { x -> FilterChip(selected = dir == x, onClick = { dir = x }, label = { Text(x.title) }) }
                }
                Text(if (dir == DebtDir.I_OWE) "Кому" else "Кто должен", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parties.forEach { p ->
                        FilterChip(selected = partyId == p.id, onClick = { partyId = p.id; newParty = "" }, label = { Text((if (p.company) "🏢 " else "") + p.name) })
                    }
                    people.take(6).forEach { n -> FilterChip(selected = newParty == n, onClick = { newParty = n; partyId = "" }, label = { Text("+ $n") }) }
                }
                OutlinedTextField(
                    newParty, { newParty = it; if (it.isNotBlank()) partyId = "" },
                    label = { Text("Новый человек или компания") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                if (newParty.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !company, onClick = { company = false }, label = { Text("Человек") })
                        FilterChip(selected = company, onClick = { company = true }, label = { Text("Компания") })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("За что") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(rate, { rate = it }, label = { Text("Процент в месяц (если заём)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text("% / мес") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }, Modifier.weight(1f)) { Text("С ${Fmt.date(date)}") }
                    OutlinedButton(onClick = { pickDate(ctx, due ?: vm.today.plusMonths(1)) { due = it } }, Modifier.weight(1f)) {
                        Text(due?.let { "До ${Fmt.date(it)}" } ?: "Срок возврата")
                    }
                }
                if (due != null) {
                    TextButton(onClick = { due = null }) { Text("Без срока") }
                    val ctx2 = LocalContext.current
                    val on = remember { app.calstats.CalendarExport.Settings(ctx2).on(app.calstats.CalendarExport.Source.DEBTS) }
                    Text(
                        if (on) "📅 Дата возврата попадёт в календарь с напоминанием накануне и за 3 дня"
                        else "📅 Добавлять сроки долгов в календарь можно в Настройках → «Даты из модулей в календаре»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (kop > 0 && r > 0) {
                    val preview = DebtMath.status(Debt("p", "", dir, principal = kop, date = vm.today.toString(), due = due?.toString(), ratePerMonth = r), vm.today)
                    val months = due?.let { java.time.temporal.ChronoUnit.DAYS.between(date, it) / DebtMath.DAYS_IN_MONTH }
                    val atDue = due?.let { DebtMath.status(Debt("p", "", dir, principal = kop, date = date.toString(), ratePerMonth = r), it).totalDue }
                    Text(
                        "Проценты: ${money(preview.monthlyInterest)} в месяц, ${money(preview.monthlyInterest * 12)} в год" +
                            (if (atDue != null && months != null) "\nК сроку (${"%.1f".format(months)} мес.): ${money(atDue)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = kop > 0 && (partyId.isNotBlank() || newParty.isNotBlank()),
                onClick = {
                    vm.updateWork { w0 ->
                        var ww = w0
                        var pid = partyId
                        if (pid.isBlank()) {
                            val existing = ww.counterparties.firstOrNull { it.name.equals(newParty.trim(), true) }
                            pid = existing?.id ?: newId().also { ww = DebtOps.upsertParty(ww, Counterparty(it, newParty.trim(), company)) }
                        }
                        DebtOps.upsert(
                            ww,
                            d0.copy(partyId = pid, dir = dir, title = title.trim(), principal = kop, ratePerMonth = r, date = date.toString(), due = due?.toString()),
                        )
                    }
                    onDismiss()
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить долг?") },
            text = { Text("Запись и все платежи по ней будут удалены.") },
            confirmButton = { TextButton(onClick = { vm.updateWork { DebtOps.delete(it, d0.id) }; confirmDelete = false; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

/** Подробности долга: расчёт, платежи, напоминание. */
@Composable
fun DebtSheet(vm: MainViewModel, id: String, onEdit: (Debt) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val d = vm.work.debts.firstOrNull { it.id == id } ?: run { onDismiss(); return }
    val party = vm.work.counterparties.firstOrNull { it.id == d.partyId }
    val s = DebtMath.status(d, vm.today)
    var pay by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((party?.name ?: "?") + " · " + d.dir.title.lowercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(d.title.ifBlank { d.dir.title }, style = MaterialTheme.typography.titleLarge)
            Text(money(s.totalDue), style = MaterialTheme.typography.displaySmall, color = if (d.dir == DebtDir.OWED_TO_ME) PLUS else MINUS)
            Text(if (d.closed) "Долг закрыт" else "к возврату на сегодня", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Line("Сумма долга", money(d.principal))
            Line("Остаток долга", money(s.principalLeft))
            if (d.ratePerMonth > 0) {
                Line("Ставка", "${fmtRate(d.ratePerMonth)}% в месяц")
                Line("Начислено процентов", money(s.interestTotal))
                Line("Из них не оплачено", money(s.interestUnpaid))
                Line("Набегает в месяц", money(s.monthlyInterest))
            }
            Line("Выплачено", money(s.paid))
            Line("Дата", Fmt.date(d.start))
            d.dueDate?.let { Line("Вернуть до", Fmt.date(it) + if (s.overdueDays > 0) " (просрочен ${s.overdueDays} дн.)" else "") }
            s.atDue?.let { Line("К сроку будет", money(it)) }
            if (d.payments.isNotEmpty()) {
                Text("Платежи", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                d.payments.sortedByDescending { it.date }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Fmt.date(LocalDate.parse(p.date))}  ${p.note}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(money(p.amount), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = {
                            vm.updateWork { DebtOps.upsert(it, d.copy(payments = d.payments - p, closed = false)) }
                        }) { Text("✕") }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!d.closed) Button(onClick = { pay = true }) { Text(if (d.dir == DebtDir.I_OWE) "Я заплатил" else "Мне вернули") }
                OutlinedButton(onClick = { onEdit(d) }) { Text("Изменить") }
                if (!d.closed && d.dir == DebtDir.OWED_TO_ME) {
                    OutlinedButton(onClick = {
                        val text = "Привет! Напоминаю про долг" + (d.title.takeIf { it.isNotBlank() }?.let { " за «$it»" } ?: "") +
                            ": ${money(s.totalDue)}" + (d.dueDate?.let { ", срок — ${Fmt.date(it)}" } ?: "") + "."
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Напомнить"))
                    }) { Text("Напомнить") }
                }
                OutlinedButton(onClick = { vm.updateWork { DebtOps.upsert(it, d.copy(closed = !d.closed)) } }) {
                    Text(if (d.closed) "Открыть снова" else "Закрыть")
                }
            }
        }
    }
    if (pay) PaymentDialog(vm, d, s.totalDue) { pay = false }
}

@Composable
private fun Line(k: String, v: String) {
    Row {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PaymentDialog(vm: MainViewModel, d: Debt, due: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var amount by remember { mutableStateOf((due / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it) }) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(vm.today) }
    val kop = BankImport.parseAmount(amount) ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Платёж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Сначала гасятся проценты, потом сам долг. Весь остаток: ${money(due)}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий") }, singleLine = true)
                OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.date(date)) }
            }
        },
        confirmButton = {
            TextButton(enabled = kop > 0, onClick = {
                vm.updateWork { DebtOps.pay(it, d.id, DebtPayment(newId(), date.toString(), kop, note.trim()), vm.today) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Человек или компания: имя, телефон, заметка. */
@Composable
fun PartyDialog(vm: MainViewModel, p: Counterparty, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var company by remember { mutableStateOf(p.company) }
    var phone by remember { mutableStateOf(p.phone) }
    var note by remember { mutableStateOf(p.note) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (company) "Компания" else "Человек") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !company, onClick = { company = false }, label = { Text("Человек") })
                    FilterChip(selected = company, onClick = { company = true }, label = { Text("Компания") })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Имя / название") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(note, { note = it }, label = { Text("Заметка") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                vm.updateWork { DebtOps.upsertParty(it, p.copy(name = name.trim(), company = company, phone = phone.trim(), note = note.trim())) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirm = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Удалить «${p.name}»?") },
            text = { Text("Будут удалены и все долги с этим человеком или компанией.") },
            confirmButton = { TextButton(onClick = { vm.updateWork { DebtOps.deleteParty(it, p.id) }; confirm = false; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}
