@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.TelegramBot
import app.calstats.Updater
import app.calstats.health.HealthLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun avatarFile(ctx: Context) = File(ctx.filesDir, "avatar.jpg")

/** Круглый аватар: фото из профиля или первая буква имени. */
@Composable
fun Avatar(vm: MainViewModel, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, vm.avatarVersion) {
        value = withContext(Dispatchers.IO) { avatarFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                vm.work.meName.trim().takeIf { it.isNotEmpty() && it != "Я" }?.take(1)?.uppercase() ?: "🌿",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun saveAvatar(ctx: Context, uri: Uri): Boolean = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (minOf(opts.outWidth, opts.outHeight) / sample > 600) sample *= 2
    val src = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    val side = minOf(src.width, src.height)
    val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    avatarFile(ctx).outputStream().use { sq.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    true
}.getOrDefault(false)

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String? = null, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
    }
}

/** Профиль: имя и фото, тариф, обновления, скачивание, Telegram-бот. */
@Composable
fun ProfileScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(vm.work.meName.takeIf { it != "Я" } ?: "") }
    var editBmp by remember { mutableStateOf<Bitmap?>(null) }
    var avatarMenu by remember { mutableStateOf(false) }
    var emojiSheet by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editBmp = AvatarFiles.importPhoto(ctx, uri) ?: run { vm.message = "Не удалось открыть фото"; null }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    editBmp?.let { b ->
        AvatarEditorDialog(b, onDismiss = { editBmp = null }) { out ->
            AvatarFiles.save(ctx, out)
            vm.avatarVersion++
            editBmp = null
        }
    }
    if (emojiSheet) EmojiAvatarSheet(onDismiss = { emojiSheet = false }) { out ->
        AvatarFiles.save(ctx, out)
        AvatarFiles.src(ctx).delete()
        vm.avatarVersion++
        emojiSheet = false
    }
    if (avatarMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { avatarMenu = false },
            title = { Text("Фото профиля") },
            text = {
                Column {
                    listOfNotNull(
                        "📷  Выбрать фото" to { pick() },
                        "😊  Аватар из эмодзи" to { emojiSheet = true },
                        if (AvatarFiles.src(ctx).exists()) "✂  Изменить текущее" to { editBmp = AvatarFiles.loadSrc(ctx) } else null,
                        if (AvatarFiles.out(ctx).exists()) "🗑  Удалить" to { AvatarFiles.delete(ctx); vm.avatarVersion++ } else null,
                    ).forEach { (label, action) ->
                        Text(
                            label,
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { avatarMenu = false; action() }.padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { avatarMenu = false }) { Text("Закрыть") } },
        )
    }
    LaunchedEffect(Unit) { vm.checkUpdate(manual = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.updateWork { it.copy(meName = name.trim().ifBlank { "Я" }) }
                        vm.pop()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).clickable { avatarMenu = true }) {
                        Avatar(vm, 88.dp)
                    }
                    TextButton(onClick = { avatarMenu = true }) { Text("Изменить фото") }
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text("Как вас зовут") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Text(
                        "Тариф: бесплатный (бета). Данные хранятся на этом телефоне; события — в Google Календаре.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            UpdateCard(vm)

            SectionCard("Скачать приложение") {
                Text(
                    "Ссылка на последнюю версию для другого телефона, близких или коллег.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Updater.shareDownloadLink(ctx) }) { Text("Поделиться ссылкой") }
                    OutlinedButton(onClick = { openUrl(ctx, Updater.RELEASES_PAGE) }) { Text("Открыть") }
                }
            }

            TelegramCard(vm)

            SectionCard("Ещё") {
                ProfileLink("Подключения", "Samsung Health, Google, Яндекс, Telegram") {
                    if (!vm.isEnabled(Tab.ACCOUNTS)) vm.setModuleEnabled(Tab.ACCOUNTS, true)
                    vm.pop(); vm.tab = Tab.ACCOUNTS
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileLink("Настройки", "Тема, резервная копия, напоминания") { vm.push(Screen.Settings) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileLink("Модули", "Включить, выключить, порядок в меню") { vm.push(Screen.Modules) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileLink("О приложении", "Что нового, история версий, статистика") { vm.push(Screen.About) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileLink(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun UpdateCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val cur = remember { Updater.currentVersion(ctx) }
    SectionCard("Обновления") {
        Text("Установлена версия $cur", style = MaterialTheme.typography.bodyMedium)
        val u = vm.update
        if (u != null) {
            Text("Доступна версия ${u.version}" + if (u.size > 0) " · ${u.size / 1_048_576} МБ" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            val p = vm.updateProgress
            if (p != null) {
                LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = { vm.installUpdate(ctx) }) { Text("Скачать и установить") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.checkUpdate(manual = true) }, enabled = !vm.updateChecking) { Text("Проверить обновления") }
                if (vm.updateChecking) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
        SwitchRow("Автообновление", "Проверять новые версии и предлагать установить", vm.autoUpdate) { vm.changeAutoUpdate(it) }
        Text(
            "Android всегда спрашивает подтверждение установки — это защита системы. В первый раз разрешите установку из «Зелёного ритма».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Подключение личного Telegram-бота. */
@Composable
fun TelegramCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = vm.telegramState
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }

    // Ждём, пока пользователь нажмёт «Старт» в боте.
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        repeat(40) {
            if (runCatching { vm.telegram.findChat() }.getOrDefault(false)) {
                vm.refreshTelegram()
                // Сразу «на связи» — иначе бот отвечает, только когда открыто приложение
                app.calstats.TelegramService.setEnabled(ctx, true)
                vm.message = "Telegram подключён"
                waiting = false
                return@LaunchedEffect
            }
            delay(3000)
        }
        waiting = false
    }

    SectionCard("Telegram-бот") {
        when {
            st.connected -> {
                Text("✅ Подключён: @${st.botName}", style = MaterialTheme.typography.bodyMedium)
                var live by remember { mutableStateOf(app.calstats.TelegramService.enabled(ctx)) }
                SwitchRow("⚡ Бот на связи", "Отвечает за 1–2 секунды, даже когда приложение закрыто. В шторке будет тихое уведомление", live) { on ->
                    live = on
                    app.calstats.TelegramService.setEnabled(ctx, on)
                    if (on) {
                        // Samsung усыпляет фоновую работу — просим не экономить батарею на приложении
                        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                        if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) == false) runCatching {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName)))
                        }
                        vm.message = "Бот на связи. Напишите ему «что сегодня?»"
                    }
                }
                if (!live) Text("Без этого бот отвечает, только когда вы открываете приложение — ответ может идти до часа.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (live) {
                    var fast by remember { mutableStateOf(app.calstats.TelegramService.fast(ctx)) }
                    SwitchRow("🚀 Без задержек", "Телефон не усыпляет бота: ответ за секунды даже на заблокированном экране. Батарея — чуть больше расход", fast) { fast = it; app.calstats.TelegramService.setFast(ctx, it) }
                    val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                    if (pm?.isIgnoringBatteryOptimizations(ctx.packageName) == false) {
                        Text("⚠ Телефон экономит батарею на приложении и может останавливать бота. Разрешите работу без ограничений. На Samsung также: Настройки → Батарея → Ограничения фона → «Никогда не спящие приложения» → добавить «Зелёный ритм».",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName))) }
                        }) { Text("Разрешить работу в фоне") }
                    }
                    if (!app.calstats.TelegramService.running) OutlinedButton(onClick = { app.calstats.TelegramService.start(ctx) }) { Text("Запустить бота сейчас") }
                }
                // Диагностика — чтобы было видно, что не так
                var tick by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
                val err = remember(tick) { vm.telegram.lastError() }
                val okAt = remember(tick) { vm.telegram.lastOk() }
                Text(
                    "Состояние: " + (if (live) (if (app.calstats.TelegramService.running) "служба работает" + app.calstats.TelegramService.lastPoll.takeIf { it > 0 }?.let { " · ждёт сообщений с ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}" }.orEmpty() else "служба не запущена") else "ответы при открытии приложения") +
                        (if (okAt > 0) " · связь с Telegram ${java.time.Instant.ofEpochMilli(okAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}" else "") +
                        (err?.let { "\n⚠ Ошибка: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow("Утренняя сводка", "Каждый день в 10:00: задачи, сроки, списания", st.daily) { vm.telegram.setFlag("daily", it); vm.refreshTelegram() }
                SwitchRow("Напоминания", "Документы, подписки, пробные периоды", st.reminders) { vm.telegram.setFlag("reminders", it); vm.refreshTelegram() }
                SwitchRow("Новые версии", "Сообщить о выходе обновления", st.updates) { vm.telegram.setFlag("updates", it); vm.refreshTelegram() }
                SwitchRow("Принимать сообщения", "Кнопки меню, вопросы («сколько потратил?»), записи («кофе 250», «купить хлеб»)", st.inbox) { vm.telegram.setFlag("inbox", it); vm.refreshTelegram() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { vm.telegram.setupMenu(); vm.message = if (vm.telegram.send("👋 Проверка связи. Меню — кнопки внизу и «/» слева.\n\n" + app.calstats.BotBrain.HELP, keyboard = true)) "Отправлено — в боте появилось меню" else "Не удалось отправить" }
                    }) { Text("Проверить и показать меню") }
                    OutlinedButton(onClick = { openUrl(ctx, "https://t.me/${st.botName}") }) { Text("Открыть бот") }
                    TextButton(onClick = { app.calstats.TelegramService.setEnabled(ctx, false); vm.telegram.disconnect(); vm.refreshTelegram() }) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
                }
            }
            st.hasToken -> {
                Text("Бот @${st.botName} найден. Откройте его и нажмите «Старт» (или напишите любое сообщение).", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { waiting = true; openUrl(ctx, "https://t.me/${st.botName}?start=go") }) { Text("Открыть бот") }
                    if (waiting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { waiting = true }) { Text("Я нажал «Старт»") }
                }
                TextButton(onClick = { vm.telegram.disconnect(); vm.refreshTelegram() }) { Text("Другой бот") }
            }
            else -> {
                Text(
                    "Напоминания, утренняя сводка и быстрые записи через Telegram. Бот ваш личный, данные идут напрямую из телефона.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "1. Откройте @BotFather → /newbot, придумайте имя.\n2. Скопируйте токен и вставьте ниже.\n3. Нажмите «Старт» в своём боте.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { openUrl(ctx, "https://t.me/BotFather") }) { Text("Открыть @BotFather") }
                OutlinedTextField(token, { token = it.trim() }, label = { Text("Токен бота") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    busy = true
                    scope.launch {
                        runCatching { vm.telegram.connect(token) }
                            .onSuccess { vm.refreshTelegram(); token = "" }
                            .onFailure { vm.message = it.message ?: "Не удалось подключить" }
                        busy = false
                    }
                }, enabled = token.contains(':') && !busy) { Text(if (busy) "Проверяю…" else "Подключить") }
            }
        }
    }
}

// ======================= ПОДКЛЮЧЕНИЯ =======================

private data class Conn(val emoji: String, val title: String, val status: String, val ok: Boolean?, val action: String?, val onAction: (() -> Unit)?)

/** Модуль «Подключения»: учётные записи и сервисы, из которых приложение берёт данные. */
@Composable
fun AccountsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val hcLauncher = rememberLauncherForActivityResult(HealthLink.permissionContract()) { vm.refreshHealth(true) }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.loadBirthdays() }
    LaunchedEffect(Unit) { vm.refreshHealth(true); vm.refreshTelegram() }
    var telegramOpen by remember { mutableStateOf(false) }

    val accounts = vm.calendars.groupBy { it.accountType to it.accountName }.keys
    fun accs(pred: (String) -> Boolean) = accounts.filter { pred(it.first) }.map { it.second }.distinct()
    val google = accs { it == "com.google" }
    val samsung = accs { it.contains("osp") || it.contains("samsung") }
    val dav = accounts.filter { it.first.contains("davx5") || it.first.contains("caldav") || it.second.contains("yandex", true) }.map { it.second }.distinct()
    val yandex = dav.filter { it.contains("yandex", true) || it.contains("ya.ru", true) }
    val others = accounts.filter { (t, _) -> t != "com.google" && !t.contains("osp") && !t.contains("samsung") && t !in setOf("LOCAL", "local") }
        .filter { (_, n) -> n !in yandex }.map { it.second }.distinct()

    fun open(intent: Intent) = try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) { vm.message = "Не удалось открыть" }
    val syncSettings = { open(Intent(Settings.ACTION_SYNC_SETTINGS)) }

    val hc = when {
        vm.healthStatus != HealthLink.Status.AVAILABLE -> Conn("❤", "Samsung Health", "Нужен Health Connect", false, "Установить") { open(HealthLink.installIntent()) }
        vm.healthGranted == 0 -> Conn("❤", "Samsung Health", "Не подключено", false, "Подключить") {
            runCatching { hcLauncher.launch(HealthLink.permissions) }.onFailure { vm.message = "Не удалось открыть Health Connect" }
        }
        else -> Conn("❤", "Samsung Health", "Шаги, сон, пульс, вес · ${vm.healthGranted} из ${HealthLink.permissions.size} разрешений", true, "Настроить") { open(HealthLink.settingsIntent()) }
    }
    val list = listOf(
        hc,
        Conn("G", "Google", if (google.isEmpty()) "Нет аккаунта в календаре телефона" else "Календарь: " + google.joinToString(), google.isNotEmpty(), "Аккаунты", syncSettings),
        Conn("S", "Samsung", if (samsung.isEmpty()) "Календарь Samsung не найден" else "Календарь: " + samsung.joinToString(), samsung.isNotEmpty().takeIf { it }, "Аккаунты", syncSettings),
        Conn(
            "Я", "Яндекс",
            if (yandex.isNotEmpty()) "Календарь: " + yandex.joinToString() else "Календарь Яндекса подключается через DAVx⁵ (CalDAV, пароль приложения Яндекса)",
            yandex.isNotEmpty().takeIf { it },
            if (yandex.isEmpty()) "DAVx⁵" else "Аккаунты",
        ) { if (yandex.isEmpty()) openUrl(ctx, "https://www.davx5.com/") else syncSettings() },
        Conn("✈", "Telegram", vm.telegramState.let { if (it.connected) "Бот @${it.botName}" else "Напоминания и быстрые записи" }, vm.telegramState.connected.takeIf { it }, if (vm.telegramState.connected) "Настроить" else "Подключить") { telegramOpen = !telegramOpen },
        Conn(
            "🎂", "Контакты",
            if (app.calstats.Birthdays.allowed(ctx)) "Дни рождения: ${vm.birthdays.size}" else "Дни рождения из контактов — в календаре и утренней сводке",
            app.calstats.Birthdays.allowed(ctx).takeIf { it },
            if (app.calstats.Birthdays.allowed(ctx)) null else "Подключить",
        ) { contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
        Conn("₽", "Банк", "Т-Банк, Сбер, Альфа — импорт выписки CSV в Финансы", null, "Финансы") { vm.tab = Tab.FINANCE },
        Conn("📝", "Google Keep, Samsung Notes", "Импорт архива Keep и «Поделиться» из любых заметок", null, "Заметки") { vm.tab = Tab.NOTES },
        Conn("🥫", "Open Food Facts", "Поиск продуктов и штрихкодов — без регистрации", true, null, null),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Подключения", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Сервисы и учётные записи, откуда приложение берёт данные. Пароли сюда не вводятся — вход делается в самих сервисах или в настройках Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            list.forEach { c ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(c.emoji, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                                if (c.ok != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (c.ok) Color(0xFF4F9A6A) else Color(0xFFC27B44)))
                                }
                            }
                            Text(c.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.action != null && c.onAction != null) TextButton(onClick = c.onAction) { Text(c.action) }
                    }
                }
                if (c.title == "Telegram" && telegramOpen) TelegramCard(vm)
            }
            if (others.isNotEmpty()) {
                SectionCard("Другие календари на телефоне") {
                    others.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Text(
                "Скоро: вход в аккаунт «Зелёного ритма» и синхронизация между телефоном и веб-версией.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
