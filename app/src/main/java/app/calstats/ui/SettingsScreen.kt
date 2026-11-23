@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.dp
import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import app.calstats.DrinkReminder
import app.calstats.Fmt
import java.time.LocalTime
import app.calstats.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val ctx = LocalContext.current
    var permTick by remember { mutableStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permTick++
        if (!granted) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
        }
    }
    val notifAllowed = remember(permTick, vm.now) { DrinkReminder.canNotify(ctx) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    var settingsTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.headlineSmall) },
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
          androidx.compose.runtime.CompositionLocalProvider(LocalSettingsTab provides settingsTab) {
            // Тема — сверху, как на corpdance: карточки-превью.
            Text("Тема", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                app.calstats.ThemeMode.entries.forEach { t ->
                    val sw = when (t) {
                        app.calstats.ThemeMode.SYSTEM -> listOf(0xFFF3F2F7, 0xFF0C1913, 0xFFD4A960)
                        app.calstats.ThemeMode.LIGHT -> listOf(0xFFF3F2F7, 0xFFFFFFFF, 0xFF121721)
                        app.calstats.ThemeMode.DARK -> listOf(0xFF121721, 0xFF1D222A, 0xFFC9B8FF)
                        app.calstats.ThemeMode.FOREST -> listOf(0xFF0C1913, 0xFF172A20, 0xFFD4A960)
                    }
                    val selected = vm.themeMode == t
                    Column(
                        Modifier.weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .clickable { vm.changeTheme(t) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Мини-превью экрана: фон, карточка, акцент
                        Box(
                            Modifier.fillMaxWidth().height(54.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(androidx.compose.ui.graphics.Color(sw[0])).padding(6.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().height(16.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(sw[1])))
                                Box(Modifier.width(26.dp).height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(sw[2])))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (t == app.calstats.ThemeMode.SYSTEM) "Авто" else t.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null)
                    }
                }
            }
            // Разделы — сеткой «пилюль», ниже содержимое выбранного (как на corpdance).
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                SETTINGS_TABS.forEachIndexed { i, title ->
                    val sel = settingsTab == i
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { settingsTab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Section("Модули") {
                Text(
                    "Включено: " + vm.enabledModules.joinToString(", ") { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { vm.push(app.calstats.Screen.Modules) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Включить, выключить, порядок")
                }
            }

            Section("Синхронизация с Google") {
                Text(
                    "Приложение работает с календарём телефона, который Android синхронизирует с Google: " +
                        "события из Google Календаря появляются здесь, а добавленные здесь — в Google Календаре.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.syncNow() }, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать сейчас") }
            }

            Section("Даты из модулей в календаре") {
                val es = remember { app.calstats.CalendarExport.Settings(ctx) }
                var ver by remember { mutableStateOf(0) }
                val writable = vm.calendars.filter { it.writable }
                val target = remember(ver, vm.calendars) { app.calstats.CalendarExport.target(ctx, es) }
                val colors = remember(target) {
                    target?.let { runCatching { app.calstats.CalendarRepository(ctx).eventColors(it.accountName, it.accountType) }.getOrNull() }.orEmpty()
                }
                Text(
                    "Сроки долгов, документов, платежи, прививки, ТО появятся событиями на весь день с напоминанием. " +
                        "Изменили или закрыли в приложении — событие обновится или исчезнет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var calMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { calMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Календарь: " + (target?.let { it.name + " · " + it.accountName } ?: "нет доступного"))
                    }
                    androidx.compose.material3.DropdownMenu(calMenu, { calMenu = false }) {
                        writable.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(c.name + " · " + c.accountName) }, onClick = {
                                es.calendarKey = c.key; calMenu = false; ver++; vm.exportDates(now = true)
                            })
                        }
                    }
                }
                app.calstats.CalendarExport.Source.entries.forEach { src ->
                    var on by remember(ver) { mutableStateOf(es.on(src)) }
                    var key by remember(ver) { mutableStateOf(es.colorKey(src)) }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.title)
                                Text(src.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = on, onCheckedChange = { on = it; es.setOn(src, it); vm.exportDates() })
                        }
                        if (on && colors.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = key == null, onClick = { key = null; es.setColorKey(src, null); vm.exportDates() }, label = { Text("цвет календаря") })
                                colors.forEach { o ->
                                    FilterChip(
                                        selected = key == o.key,
                                        onClick = { key = o.key; es.setColorKey(src, o.key); vm.exportDates() },
                                        leadingIcon = { ColorDot(vm.directionDisplay(o.color), 14.dp) },
                                        label = { Text(vm.directionName(o.color) ?: "без названия", maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.exportDates(now = true) }) { Text("Обновить сейчас") }
                    TextButton(onClick = {
                        app.calstats.CalendarExport.Source.entries.forEach { es.setOn(it, false) }
                        ver++
                        Thread { app.calstats.CalendarExport.removeAll(ctx) }.start()
                        vm.message = "События из модулей убраны из календаря"
                    }) { Text("Убрать все") }
                }
            }

            Section("Уведомления") {
                val ns = remember { app.calstats.Notify.Settings(ctx) }
                var morning by remember { mutableStateOf(ns.morning) }
                var mTime by remember { mutableStateOf(ns.morningTime) }
                var evening by remember { mutableStateOf(ns.evening) }
                var eTime by remember { mutableStateOf(ns.eveningTime) }
                var events by remember { mutableStateOf(ns.events) }
                var weekly by remember { mutableStateOf(ns.weekly) }
                var before by remember { mutableStateOf(ns.before) }
                fun apply() { app.calstats.Notify.scheduleAll(ctx) }
                @Composable
                fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title)
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = on, onCheckedChange = {
                            set(it)
                            apply()
                            if (it && !DrinkReminder.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        })
                    }
                }
                Toggle("Утренняя сводка", "События дня, задачи, платежи, долги, документы, привычки", morning) { morning = it; ns.morning = it }
                if (morning) OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> mTime = LocalTime.of(h, m); ns.morningTime = mTime; apply() }, mTime.hour, mTime.minute, true).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Утром в " + Fmt.time(mTime)) }
                Toggle("Напоминать о событиях", "Перед началом каждого события календаря", events) { events = it; ns.events = it }
                if (events) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(selected = before == m, onClick = { before = m; ns.before = m; apply() }, label = { Text(if (m < 60) "за $m мин" else "за 1 ч") })
                        }
                    }
                }
                Toggle("Вечерний итог", "Неотмеченные привычки, задачи и платежи на завтра", evening) { evening = it; ns.evening = it }
                if (evening) OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> eTime = LocalTime.of(h, m); ns.eveningTime = eTime; apply() }, eTime.hour, eTime.minute, true).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Вечером в " + Fmt.time(eTime)) }
                Toggle("Итог недели", "В воскресенье в 19:00: события, задачи, траты, привычки", weekly) { weekly = it; ns.weekly = it }
                TextButton(onClick = { app.calstats.Notify.handle(ctx, android.content.Intent(app.calstats.Notify.ACTION_MORNING)) }) { Text("Показать утреннюю сводку сейчас") }
                Text(
                    "Если события дублируются с календарём Samsung или Google, выключите напоминания там или здесь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Учёт алкоголя") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ежедневный вопрос «Пили вчера?»")
                        Text(
                            "Уведомление с кнопками «Не пил» и «Пил» — отвечать можно не открывая приложение.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.reminderEnabled, onCheckedChange = {
                        vm.changeReminderEnabled(it)
                        if (it && !DrinkReminder.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
                if (vm.reminderEnabled) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(ctx, { _, h, m -> vm.changeReminderTime(LocalTime.of(h, m)) },
                                vm.reminderTime.hour, vm.reminderTime.minute, true).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Время напоминания: " + Fmt.time(vm.reminderTime)) }
                    if (!notifAllowed) {
                        Text(
                            "Уведомления для приложения выключены.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else ctx.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Разрешить уведомления") }
                    }
                    TextButton(onClick = { vm.testReminder() }) { Text("Показать уведомление сейчас") }
                    Text(
                        "На Samsung, если вопрос не приходит: Настройки → Приложения → Зелёный ритм → " +
                            "Батарея → «Без ограничений».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    "В копию попадает всё: направления и цвета, все модули (задачи, финансы, долги, здоровье, привычки, " +
                        "документы, карты, покупки, цели, дневник, авто, питомцы), настройки главной и меню, " +
                        "а также фото документов, бонусных карт и аватар. Один файл .zip. Токен Telegram-бота не сохраняется. " +
                        "События остаются в Google Календаре.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { exportLauncher.launch(vm.backupFileName()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить копию…")
                }
                Text(
                    "В окне выбора слева в меню будут «Google Диск» и «Яндекс Диск» (если приложения установлены) — выберите нужную папку там.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Восстановить из копии (.zip или старый .json)") }
                HorizontalDivider()
                Text("Автокопия раз в день", style = MaterialTheme.typography.titleSmall)
                val folder = vm.backupFolder
                if (folder == null) {
                    Text(
                        "Выберите папку — приложение будет само сохранять туда копию раз в сутки и хранить последние ${app.calstats.Backup.KEEP}. " +
                            "Работает с Яндекс Диском, памятью телефона и картой памяти. Google Диск постоянный доступ к папке не даёт — " +
                            "для него сохраняйте копию кнопкой выше.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать папку для автокопии") }
                } else {
                    val last = vm.backupLast
                    Text(
                        "Папка: $folder\n" + (if (last > 0) "Последняя копия: " + java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.forLanguageTag("ru"))) else "Копий ещё не было") +
                            (vm.backupError?.let { "\nОшибка: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.backupToFolder(silent = false) }, modifier = Modifier.weight(1f)) { Text("Сейчас") }
                        OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) { Text("Сменить") }
                        TextButton(onClick = { vm.setBackupFolder(null) }) { Text("Откл.") }
                    }
                }
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

private val SETTINGS_TABS = listOf("Общие", "Уведомления", "Календарь", "Резервные копии")
private val SECTION_TAB = mapOf(
    "Модули" to 0, "Синхронизация с Google" to 2, "Какие календари считать" to 2, "Подсчёт" to 2,
    "Даты из модулей в календаре" to 2, "Уведомления" to 1, "Учёт алкоголя" to 1, "Резервная копия" to 3,
)
private val LocalSettingsTab = androidx.compose.runtime.compositionLocalOf { 0 }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    if ((SECTION_TAB[title] ?: 0) != LocalSettingsTab.current) return
    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
