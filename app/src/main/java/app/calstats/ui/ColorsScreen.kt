@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel

private class ColorInfo(val color: Int, val count: Int, val samples: List<String>)

@Composable
fun ColorsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val infos = remember(vm.instances, vm.calendars, vm.data.excludedCalendars) {
        val included = vm.includedCalendarIds()
        val counts = HashMap<Int, Int>()
        val titles = HashMap<Int, HashMap<String, Int>>()
        for (i in vm.instances) {
            if (i.calendarId !in included) continue
            counts[i.color] = (counts[i.color] ?: 0) + 1
            val t = titles.getOrPut(i.color) { HashMap() }
            t[i.title] = (t[i.title] ?: 0) + 1
        }
        for (c in vm.data.directions.keys) if (c !in counts) counts[c] = 0
        counts.entries
            .sortedByDescending { it.value }
            .map { (color, n) ->
                val samples = titles[color]?.entries?.sortedByDescending { it.value }?.take(3)?.map { it.key } ?: emptyList()
                ColorInfo(color, n, samples)
            }
    }

    var editCustom by remember { mutableStateOf<app.calstats.CustomDirection?>(null) }
    editCustom?.let { c -> CustomDirectionDialog(vm, c, infos.map { it.color }) { editCustom = null } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Цвета и направления") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Дайте каждому цвету название направления. Оттенки здесь могут немного отличаться от " +
                        "приложения Google Календарь — ориентируйтесь на примеры событий. Если нескольким " +
                        "цветам дать одинаковое название, они объединятся в одно направление.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Свои направления", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = {
                        editCustom = app.calstats.CustomDirection(java.util.UUID.randomUUID().toString(), "", PALETTE_COLORS[7])
                    }) { Text("+ Направление") }
                }
                Text(
                    "Любое название и любой цвет. Событие попадает в направление по цвету в Google Календаре или по словам в названии (например, «тренировка, бассейн»).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(vm.data.custom, key = { "x" + it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { editCustom = c }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(c.display, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    c.eventColor?.let { "цвет Google" },
                                    c.keywords.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "слова: "),
                                    if (c.hidden) "не в статистике" else null,
                                ).joinToString(" · ").ifBlank { "нет правил — добавьте цвет или слова" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        c.eventColor?.let { ColorDot(it, 16.dp) }
                    }
                }
            }
            item { Text("Цвета Google Календаря", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(infos, key = { it.color }) { info ->
                ColorItem(info, vm)
            }
        }
    }
}

@Composable
private fun ColorItem(info: ColorInfo, vm: MainViewModel) {
    val setting = vm.data.directions[info.color]
    var name by remember(info.color) { mutableStateOf(setting?.name ?: "") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            var pick by remember { mutableStateOf(false) }
            if (pick) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pick = false },
                    title = { Text("Цвет в приложении") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ColorPalette(setting?.display ?: info.color) { vm.setDirectionDisplay(info.color, it) }
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { pick = false }) { Text("Готово") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.setDirectionDisplay(info.color, null); pick = false }) { Text("Как в Google") } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { pick = true }) { ColorDot(setting?.display ?: info.color, 32.dp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        vm.setDirectionName(info.color, it)
                    },
                    label = { Text("Направление") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            val countText = "${info.count} ${Fmt.plural(info.count.toLong(), "событие", "события", "событий")}"
            Text(
                if (info.samples.isEmpty()) countText else "$countText · например: ${info.samples.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = setting?.hidden == true,
                    onCheckedChange = { vm.setDirectionHidden(info.color, it) },
                )
                Text("Не учитывать в статистике", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


/** Своё направление: название, любой цвет, цвет Google и слова. */
@Composable
private fun CustomDirectionDialog(vm: MainViewModel, c: app.calstats.CustomDirection, seen: List<Int>, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val exists = vm.data.custom.any { it.id == c.id }
    var name by remember { mutableStateOf(c.name) }
    var display by remember { mutableStateOf(c.display) }
    var eventColor by remember { mutableStateOf(c.eventColor) }
    var words by remember { mutableStateOf(c.keywords.joinToString(", ")) }
    var hidden by remember { mutableStateOf(c.hidden) }
    // Цвета, которые понимает Google Календарь: из аккаунта + уже встречавшиеся в событиях.
    val google = remember(vm.calendars) {
        val cal = vm.calendars.firstOrNull { it.accountType == "com.google" && it.primary } ?: vm.calendars.firstOrNull { it.accountType == "com.google" }
        val opts = cal?.let { runCatching { app.calstats.CalendarRepository(ctx).eventColors(it.accountName, it.accountType).map { o -> o.color } }.getOrNull() }.orEmpty()
        (opts + seen).distinct()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Направление" else "Новое направление") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                Text("Цвет в приложении", style = MaterialTheme.typography.labelLarge)
                ColorPalette(display) { display = it }
                Text("Цвет события в Google Календаре", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Google поддерживает только свои 11 цветов — выберите, каким цветом будут события этого направления. Можно не выбирать и определять направление только по словам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(selected = eventColor == null, onClick = { eventColor = null }, label = { Text("Нет") })
                    google.forEach { g ->
                        Box(
                            Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .then(if (eventColor == g) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                .clickable { eventColor = g },
                            contentAlignment = Alignment.Center,
                        ) { ColorDot(g, 24.dp) }
                    }
                }
                OutlinedTextField(words, { words = it }, label = { Text("Слова в названии события") }, placeholder = { Text("тренировка, бассейн, зал") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hidden, { hidden = it })
                    Text("Не учитывать в статистике")
                }
                if (exists) androidx.compose.material3.TextButton(onClick = { vm.deleteCustomDirection(c.id); onDismiss() }) {
                    Text("Удалить направление", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = {
                vm.upsertCustomDirection(c.copy(name = name.trim(), display = display, eventColor = eventColor,
                    keywords = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }, hidden = hidden))
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
