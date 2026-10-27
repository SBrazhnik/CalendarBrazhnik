@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.layout.Arrangement
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(info.color, 32.dp)
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
