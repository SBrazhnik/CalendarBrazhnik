package app.calstats.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.applySmart
import app.calstats.work.SmartInput
import app.calstats.work.SmartInput.Kind
import app.calstats.work.SplitMath
import java.time.LocalDateTime

/** Как открыть быстрый ввод: null — закрыт, false — с клавиатурой, true — сразу голосом. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickInputSheet(vm: MainViewModel, voice: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var forced by rememberSaveable { mutableStateOf<Kind?>(null) }
    val habits = vm.work.habits
    val r = remember(text, forced, habits) {
        val now = LocalDateTime.now()
        if (text.isBlank()) null else forced?.let { SmartInput.asKind(it, text, now, habits) } ?: SmartInput.parse(text, now, habits)
    }
    val focus = remember { FocusRequester() }
    val ctxQ = androidx.compose.ui.platform.LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = it; forced = null }
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите, что записать")
        try { speech.launch(i) } catch (_: ActivityNotFoundException) {
            vm.message = "На телефоне нет распознавания речи — установите приложение Google"
        }
    }
    LaunchedEffect(Unit) { if (voice) listen() else runCatching { focus.requestFocus() } }
    fun close() { vm.quickInput = null }
    fun save() {
        val x = r ?: return
        vm.message = "✓ " + vm.applySmart(x)
        text = ""; forced = null
        close()
    }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Быстрая запись", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("потратил 450 на такси") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    maxLines = 3,
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(onClick = { listen() }, modifier = Modifier.size(52.dp)) { Text("🎤", style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.size(6.dp))
                androidx.compose.material3.FilledTonalIconButton(onClick = { close(); vm.scanReceipt(ctxQ) }, modifier = Modifier.size(52.dp)) { Text("🧾", style = MaterialTheme.typography.titleLarge) }
            }
            if (r == null) {
                Text(
                    "Скажите или напишите как обычно:\n" +
                        "• «потратил 450 на такси», «кофе 250 руб»\n" +
                        "• «получил зарплату 85 тыс»\n" +
                        "• «завтра в 15 встреча с Иваном»\n" +
                        "• «напомни в пятницу позвонить в банк»\n" +
                        "• «выпил 2 стакана воды», «вес 82,5», «настроение хорошее»\n" +
                        "• «купить молоко, хлеб и яйца»\n" +
                        "• «сделал зарядку», «заметка …», «дневник …»",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${r.kind.emoji} ${r.kind.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(describe(r, vm), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text("Не то? Выберите, куда записать:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kind.entries.filter { it != Kind.HABIT || habits.isNotEmpty() }.forEach { k ->
                        FilterChip(selected = r.kind == k, onClick = { forced = k }, label = { Text("${k.emoji} ${k.title}") })
                    }
                }
            }
            Button(onClick = { save() }, enabled = r != null && valid(r), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (r?.kind == Kind.EVENT) "Дальше" else "Записать")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun valid(r: SmartInput.Result) = when (r.kind) {
    Kind.EXPENSE, Kind.INCOME -> r.amount > 0
    Kind.WATER -> r.ml > 0
    Kind.WEIGHT -> r.kg > 0
    Kind.MOOD -> r.mood in 1..5
    Kind.SHOP -> r.items.isNotEmpty()
    Kind.HABIT -> r.habitId != null
    else -> r.text.isNotBlank()
}

private fun describe(r: SmartInput.Result, vm: MainViewModel): String = when (r.kind) {
    Kind.EXPENSE, Kind.INCOME -> if (r.amount <= 0) "Не нашёл сумму — скажите, например, «450 рублей»"
        else SplitMath.money(r.amount) + (r.category?.let { " · $it" } ?: "") + (if (r.text.isNotBlank()) " · ${r.text}" else "") + " · сегодня" +
            (if (r.kind == Kind.EXPENSE) app.calstats.work.CashbackKit.best(vm.work.cashback, app.calstats.work.CashbackKit.month(vm.today), r.category)
                ?.let { "\n💳 Выгоднее картой ${it.bank}: ${it.category} ${it.percent}%" } ?: "" else "")
    Kind.TASK -> r.text + (r.date?.let { " · до ${Fmt.dayTitle(it, vm.today)}" } ?: "")
    Kind.EVENT -> r.text + " · " + Fmt.dayTitle(r.date ?: vm.today, vm.today) + (r.time?.let { ", ${Fmt.time(it)}" } ?: ", весь день")
    Kind.WATER -> "+${r.ml} мл за сегодня"
    Kind.WEIGHT -> if (r.kg > 0) "${r.kg} кг" else "Скажите вес числом"
    Kind.MOOD -> "${r.mood} из 5"
    Kind.SHOP -> r.items.joinToString(", ") { it.first + if (it.second.isNotBlank()) " ${it.second}" else "" }
    Kind.HABIT -> if (r.habitId != null) r.text else "Не нашёл такую привычку"
    else -> r.text
}
