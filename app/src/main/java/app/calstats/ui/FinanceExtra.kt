@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.BankQueue
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.work.BankImport
import app.calstats.work.FinanceMath
import app.calstats.work.LoanMath
import app.calstats.work.Recurring
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import app.calstats.work.newId
import java.time.LocalDate
import java.time.YearMonth

private fun money(k: Long) = SplitMath.money(k)
private val INC = Color(0xFF3F8A5A)
private val EXP = Color(0xFFC0583F)

/** Операции из уведомлений банков + предложение включить автоучёт. */
fun LazyListScope.bankInbox(vm: MainViewModel) {
    item(key = "bank_inbox") {
        val ctx = LocalContext.current
        val list = vm.bankPending
        if (list.isEmpty()) {
            if (!BankQueue.enabled(ctx) && !vm.bankHintHidden) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Траты без ручного ввода", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Приложение может замечать уведомления банков (Т-Банк, Сбер, Альфа, ВТБ и др.) и предлагать записать операцию одной кнопкой. Другие уведомления не читаются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Включить") }
                            TextButton(onClick = { vm.hideBankHint() }) { Text("Не нужно") }
                        }
                    }
                }
            }
            return@item
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Из уведомлений банка: ${list.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.acceptBank(list.map { it.id }.toSet()) }) { Text("Добавить все") }
                }
                list.takeLast(8).reversed().forEach { t ->
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.merchant.ifBlank { if (t.income) "Поступление" else "Списание" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.bank} · ${Fmt.dateNoYear(LocalDate.parse(t.date))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text((if (t.income) "+" else "−") + money(t.amount), color = if (t.income) INC else EXP, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { vm.acceptBank(setOf(t.id)) }) { Text("✓") }
                        TextButton(onClick = { vm.dropBank(setOf(t.id)) }) { Text("✕") }
                    }
                }
            }
        }
    }
}

/** Расходы и доходы по месяцам + прогноз до конца месяца / до зарплаты. */
fun LazyListScope.trendsCard(vm: MainViewModel) {
    item(key = "trends") {
        val w = vm.work
        val today = vm.today
        val months = (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val exp = months.map { FinanceMath.expense(FinanceMath.inMonth(w.transactions, it)) }
        val inc = months.map { FinanceMath.income(FinanceMath.inMonth(w.transactions, it)) }
        if (exp.all { it == 0L } && inc.all { it == 0L }) return@item
        val max = (exp + inc).maxOrNull()?.coerceAtLeast(1) ?: 1
        // Прогноз: средние траты в день за текущий месяц
        val spent = exp.last()
        val day = today.dayOfMonth
        val perDay = spent / day
        val payday = vm.payday
        val until = if (payday != null) {
            var d = today.withDayOfMonth(minOf(payday, today.lengthOfMonth()))
            if (!d.isAfter(today)) d = today.plusMonths(1).let { it.withDayOfMonth(minOf(payday, it.lengthOfMonth())) }
            d
        } else today.withDayOfMonth(today.lengthOfMonth())
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, until)
        val need = perDay * daysLeft
        val balance = FinanceMath.total(w)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("По месяцам", style = MaterialTheme.typography.titleSmall)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val slot = size.width / months.size
                    val bw = slot * 0.3f
                    months.indices.forEach { i ->
                        val hi = (inc[i].toFloat() / max * size.height)
                        val he = (exp[i].toFloat() / max * size.height)
                        drawRoundRect(INC, Offset(i * slot + slot * 0.18f, size.height - hi), Size(bw, hi), CornerRadius(4f))
                        drawRoundRect(EXP, Offset(i * slot + slot * 0.52f, size.height - he), Size(bw, he), CornerRadius(4f))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    months.forEach { Text(Fmt.monthShort(it.monthValue), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("● доходы  ● расходы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "В среднем ${money(perDay)} в день. " + (if (payday != null) "До зарплаты ($daysLeft дн.)" else "До конца месяца ($daysLeft дн.)") +
                        " уйдёт ещё ≈ ${money(need)}; останется ≈ ${money(balance - need)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (balance - need < 0) EXP else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("День зарплаты:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    listOf(null, 5, 10, 15, 20, 25).forEach { d ->
                        TextButton(onClick = { vm.changePayday(d) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                            Text(d?.toString() ?: "—", fontWeight = if (payday == d) FontWeight.Bold else FontWeight.Normal,
                                color = if (payday == d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Кредитный калькулятор: платёж, переплата, график; можно добавить платёж в «Платежи». */
@Composable
fun LoanSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("12") }
    val a = BankImport.parseAmount(amount) ?: 0
    val r = rate.replace(',', '.').toDoubleOrNull() ?: 0.0
    val m = months.toIntOrNull() ?: 0
    val ok = a > 0 && m in 1..600
    val sched = remember(a, r, m) { if (ok) LoanMath.schedule(a, r, m) else emptyList() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Кредитный калькулятор", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, Modifier.weight(1.4f), label = { Text("Сумма, ₽") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(rate, { rate = it }, Modifier.weight(1f), label = { Text("% годовых") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(months, { months = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Месяцев") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(12, 24, 36, 60, 120, 240).forEach { x -> FilterChip(selected = m == x, onClick = { months = x.toString() }, label = { Text(if (x < 12 || x % 12 != 0) "$x мес" else "${x / 12} г") }) }
            }
            if (ok) {
                val pay = sched.first().payment
                val total = sched.sumOf { it.payment }
                Text("Платёж: ${money(pay)} в месяц", style = MaterialTheme.typography.titleMedium)
                Text("Всего выплатите ${money(total)}, переплата ${money(total - a)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    vm.updateWork {
                        it.copy(recurrings = it.recurrings + Recurring(newId(), "Кредит", pay, nextDate = vm.today.plusMonths(1).toString(), subscription = false))
                    }
                    vm.message = "Платёж по кредиту добавлен в «Платежи»"
                    onDismiss()
                }) { Text("Добавить ежемесячный платёж в «Платежи»") }
                Text("График", style = MaterialTheme.typography.titleSmall)
                Row { listOf("№", "Платёж", "Проценты", "Долг", "Остаток").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(sched) { row ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            listOf("${row.n}", money(row.payment), money(row.interest), money(row.principal), money(row.left)).forEach {
                                Text(it, Modifier.weight(1f), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val unusedTx = TxType.EXPENSE
