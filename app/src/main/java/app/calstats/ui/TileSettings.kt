@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.TileSize

/** Что может показывать плитка модуля на Главной: ключ → подпись. Первый — по умолчанию. */
val TILE_VARIANTS: Map<Tab, List<Pair<String, String>>> = mapOf(
    Tab.FINANCE to listOf(
        "today" to "Траты сегодня", "month" to "Траты за месяц", "balance" to "Баланс счетов", "savings" to "Накопления",
        "debts" to "Долги: итог", "owed" to "Мне должны", "iowe" to "Я должен",
    ),
    Tab.CALENDAR to listOf("today" to "События сегодня", "next" to "Следующее событие", "week" to "События за неделю"),
    Tab.TASKS to listOf("open" to "Открытые задачи", "today" to "Задачи на сегодня", "overdue" to "Просроченные"),
    Tab.HEALTH to listOf("kcal" to "Калории", "water" to "Вода", "steps" to "Шаги", "sleep" to "Сон"),
)

/** Блоки большой карточки «Финансы». */
val FIN_SECTIONS = listOf("spent" to "Траты сегодня и за месяц", "budget" to "Остаток бюджета", "savings" to "Накопления и капитал", "debts" to "Долги", "payments" to "Ближайшие платежи")

/** Настройка плитки: размер, что показывать, порядок, скрыть. */
@Composable
fun TileSettingsSheet(vm: MainViewModel, t: Tab) {
    ModalBottomSheet(onDismissRequest = { vm.tileSettings = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleBadge(t, 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(t.title, style = MaterialTheme.typography.titleLarge)
            }
            Text("Размер", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TileSize.entries.forEach { z ->
                    FilterChip(selected = vm.tileSize(t) == z, onClick = { vm.setTileSize(t, z) }, label = {
                        Text(when (z) { TileSize.SMALL -> "Маленькая"; TileSize.MEDIUM -> "Средняя"; TileSize.LARGE -> "Большая" }, maxLines = 1)
                    })
                }
            }
            Text(when (vm.tileSize(t)) { TileSize.SMALL -> "3 плитки в ряд: только главное число"; TileSize.MEDIUM -> "2 в ряд: число, подпись и строка подробностей"; TileSize.LARGE -> "Во всю ширину: подробная сводка" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TILE_VARIANTS[t]?.let { vars ->
                Text(if (vm.tileSize(t) == TileSize.LARGE && t == Tab.FINANCE) "Что показывать в маленькой и средней плитке" else "Что показывать", style = MaterialTheme.typography.labelLarge)
                val cur = vm.tileVariant(t) ?: vars.first().first
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    vars.forEach { (k, title) -> FilterChip(selected = cur == k, onClick = { vm.setTileVariant(t, k) }, label = { Text(title) }) }
                }
            }
            if (t == Tab.FINANCE) {
                Text("Блоки большой карточки", style = MaterialTheme.typography.labelLarge)
                FIN_SECTIONS.forEach { (k, title) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(k in vm.finSections, { vm.toggleFinSection(k) })
                        Text(title, Modifier.weight(1f))
                    }
                }
            }
            Text("Порядок", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val i = vm.homeTiles.indexOf(t)
                OutlinedButton(onClick = { vm.moveTile(t, -1) }, enabled = i > 0) { Text("← Раньше") }
                OutlinedButton(onClick = { vm.moveTile(t, 1) }, enabled = i in 0 until vm.homeTiles.lastIndex) { Text("Позже →") }
            }
            Text("Или зажмите плитку и перетащите.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.setTileHidden(t, true); vm.tileSettings = null }) { Text("Скрыть с Главной", color = MaterialTheme.colorScheme.error) }
        }
    }
}
