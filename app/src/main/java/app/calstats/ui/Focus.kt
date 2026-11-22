package app.calstats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calstats.MainViewModel
import kotlinx.coroutines.delay

/** Таймер фокуса (помидор): 25 минут работы — время записывается в задачу, затем 5 минут перерыва. */
@Composable
fun FocusDialog(vm: MainViewModel, f: MainViewModel.Focus) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(f) {
        while (true) {
            now = System.currentTimeMillis()
            if (f.pausedLeft == null && now >= f.endAt) {
                if (f.breakMode) vm.focus = null else vm.stopFocus(completed = true)
                break
            }
            delay(500)
        }
    }
    val left = (f.pausedLeft ?: (f.endAt - now)).coerceAtLeast(0)
    val total = f.minutes * 60_000f
    val color = if (f.breakMode) moduleColor(app.calstats.Tab.HEALTH) else moduleColor(app.calstats.Tab.TASKS)
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (f.breakMode) "Перерыв" else "Фокус", style = MaterialTheme.typography.headlineSmall)
                Text(f.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
                Box(contentAlignment = Alignment.Center) {
                    val track = color.copy(alpha = 0.18f)
                    Canvas(Modifier.size(200.dp)) {
                        val sw = 12.dp.toPx()
                        drawArc(track, 0f, 360f, false, style = Stroke(sw))
                        drawArc(color, -90f, 360f * (left / total), false, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    val s = left / 1000
                    Text("%02d:%02d".format(s / 60, s % 60), style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp))
                }
                if (f.pausedLeft != null) Text("Пауза", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                if (f.breakMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.focus = null }) { Text("Закончить") }
                        Button(onClick = { vm.startFocus(null, "Следующий помидор") }) { Text("Ещё 25 мин") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.pauseFocus() }) { Text(if (f.pausedLeft != null) "Продолжить" else "Пауза") }
                        Button(onClick = { vm.stopFocus(completed = false) }) { Text("Готово") }
                    }
                    TextButton(onClick = { vm.focus = null; vm.message = "Фокус отменён" }) { Text("Отменить без записи") }
                    Text("Время запишется в задачу. Приложение можно свернуть — по окончании придёт уведомление.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
