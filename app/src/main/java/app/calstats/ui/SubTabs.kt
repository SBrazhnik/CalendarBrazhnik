@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Порядок и скрытые разделы модуля — у каждого пользователя свои. */
object SubTabsPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("subtabs", Context.MODE_PRIVATE)
    fun order(ctx: Context, key: String): List<String> = sp(ctx).getString("o_$key", "")!!.split(',').filter { it.isNotBlank() }
    fun hidden(ctx: Context, key: String): Set<String> = sp(ctx).getString("h_$key", "")!!.split(',').filter { it.isNotBlank() }.toSet()
    fun save(ctx: Context, key: String, order: List<String>, hidden: Set<String>) =
        sp(ctx).edit().putString("o_$key", order.joinToString(",")).putString("h_$key", hidden.joinToString(",")).apply()
}

/**
 * Разделы модуля чипами в несколько строк (без прокрутки вбок).
 * Последний чип «✎» — расставить порядок и скрыть ненужное.
 */
@Composable
fun SubTabs(key: String, items: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    val ids = items.map { it.first }
    val saved = remember(tick, key) { SubTabsPrefs.order(ctx, key) }
    val hidden = remember(tick, key) { SubTabsPrefs.hidden(ctx, key) }
    val ordered = (saved.filter { it in ids } + ids.filter { it !in saved })
    val visible = ordered.filter { it !in hidden }.ifEmpty { ordered }
    val title = items.toMap()
    // Текущий раздел скрыли — переходим на первый видимый
    LaunchedEffect(visible, selected) { if (selected !in visible && visible.isNotEmpty() && selected in ids) onSelect(visible.first()) }
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        visible.forEach { id -> FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(title[id].orEmpty(), maxLines = 1) }) }
        AssistChip(onClick = { edit = true }, label = { Text("✎") })
    }
    if (edit) {
        var order by remember { mutableStateOf(ordered) }
        var hid by remember { mutableStateOf(hidden) }
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Разделы") },
            text = {
                Column {
                    Text("Порядок — стрелками, галочка — показывать.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    order.forEachIndexed { i, id ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(id !in hid, { on -> hid = if (on) hid - id else if (order.count { it !in hid } > 1) hid + id else hid })
                            Text(title[id].orEmpty(), Modifier.weight(1f))
                            TextButton(enabled = i > 0, onClick = { order = order.toMutableList().apply { add(i - 1, removeAt(i)) } }) { Text("↑") }
                            TextButton(enabled = i < order.lastIndex, onClick = { order = order.toMutableList().apply { add(i + 1, removeAt(i)) } }) { Text("↓") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { SubTabsPrefs.save(ctx, key, order, hid); tick++; edit = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { SubTabsPrefs.save(ctx, key, emptyList(), emptySet()); tick++; edit = false }) { Text("Сбросить") } },
        )
    }
}
