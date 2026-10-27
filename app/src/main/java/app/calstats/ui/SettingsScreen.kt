@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.net.Uri
import app.calstats.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Синхронизация с Google") {
                Text(
                    "Приложение работает с календарём телефона, который Android синхронизирует с Google: " +
                        "события из Google Календаря появляются здесь, а добавленные здесь — в Google Календаре.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.syncNow() }, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать сейчас") }
            }

            Section("Какие календари считать") {
                if (vm.calendars.isEmpty()) {
                    Text("Календари не найдены. Проверьте, что на телефоне добавлен аккаунт Google.")
                }
                vm.calendars.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = c.key !in vm.data.excludedCalendars,
                            onCheckedChange = { vm.setCalendarIncluded(c, it) },
                        )
                        ColorDot(c.color)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name)
                            Text(
                                c.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Section("Подсчёт") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Учитывать будущие события")
                        Text(
                            "Если выключено, считаются только прошедшие события (до текущего момента).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.data.includeFuture, onCheckedChange = { vm.setIncludeFuture(it) })
                }
                Text(
                    "Дни — сколько разных дней было хотя бы одно событие направления. " +
                        "Часы считаются по событиям со временем; события «весь день» дают дни, но не часы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Резервная копия") {
                Text(
                    "Сами события хранятся в Google Календаре: на новом телефоне они появятся после входа " +
                        "в тот же аккаунт Google. Названия направлений и настройки Android автоматически " +
                        "сохраняет в резервную копию на Google Диске. Дополнительно можно сохранить их в файл.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { exportLauncher.launch(vm.backupFileName()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить копию в файл")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Восстановить из файла") }
                HorizontalDivider()
                Text(
                    "Выгрузка всех событий с направлениями в таблицу (открывается в Excel и Google Таблицах):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { csvLauncher.launch(vm.csvFileName()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выгрузить события в CSV")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Восстановить настройки?") },
            text = { Text("Текущие названия направлений и настройки будут заменены данными из файла.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.importBackup(uri)
                }) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
