@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calstats.DevRequests
import app.calstats.MainViewModel
import app.calstats.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val IMG = Regex("""!\[[^\]]*]\(([^)\s]+)\)""")
private fun fmtTime(iso: String) = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM HH:mm"))
}.getOrDefault("")

/** Модуль «Доработка»: свои запросы и вопросы разработчику, со своим паролем. */
@Composable
fun DevTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    // Раздел закрывается при уходе из него и при сворачивании приложения
    var unlocked by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) unlocked = false }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var hasPin by remember { mutableStateOf(DevRequests.Lock.has(ctx)) }
    var setPin by remember { mutableStateOf(false) }
    when {
        !hasPin -> ModuleScaffold("Доработка", snackbar) {
            item {
                Flat {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔒 Закрытый раздел", style = MaterialTheme.typography.titleMedium)
                        Text("Здесь вы отправляете разработчику идеи, ошибки и вопросы по приложению и получаете ответы. Раздел открывается только по своему паролю — задайте его.",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { setPin = true }) { Text("Задать пароль раздела") }
                    }
                }
            }
        }
        !unlocked -> DevLock(onUnlock = { unlocked = true }, onReset = { hasPin = false })
        else -> DevContent(vm, snackbar, onLock = { unlocked = false })
    }
    if (setPin) SetPinDialog(onDismiss = { setPin = false }) { pin ->
        DevRequests.Lock.set(ctx, pin); setPin = false; hasPin = true; unlocked = true
    }
}

@Composable
private fun DevLock(onUnlock: () -> Unit, onReset: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(DevRequests.Lock.waitSeconds(ctx)) }
    var forgot by remember { mutableStateOf(false) }
    val bio = DevRequests.Lock.bio(ctx) && app.calstats.AppLock.bioAvailable(ctx)
    val len = DevRequests.Lock.length(ctx)
    LaunchedEffect(Unit) { if (bio) bioPrompt(ctx, "Раздел «Доработка»", false, onUnlock) }
    LaunchedEffect(wait) {
        while (wait > 0) { delay(1000); wait = DevRequests.Lock.waitSeconds(ctx) }
        if (error?.startsWith("Подождите") == true) error = null
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            PinPad(
                title = "Раздел «Доработка»", subtitle = "Введите пароль раздела",
                error = if (wait > 0) "Подождите $wait с" else error,
                length = len, entered = pin.length,
                bio = if (bio) ({ bioPrompt(ctx, "Раздел «Доработка»", false, onUnlock) }) else null,
                onDigit = { d ->
                    if (wait > 0) return@PinPad
                    if (d == "del") { pin = pin.dropLast(1); return@PinPad }
                    if (pin.length >= len) return@PinPad
                    pin += d; error = null
                    if (pin.length == len) {
                        if (DevRequests.Lock.check(ctx, pin)) { onUnlock(); return@PinPad }
                        pin = ""; wait = DevRequests.Lock.waitSeconds(ctx); error = "Неверный пароль"
                    }
                },
                extra = { TextButton(onClick = { forgot = true }) { Text("Забыли пароль?") } },
            )
        }
    }
    if (forgot) AlertDialog(
        onDismissRequest = { forgot = false },
        title = { Text("Сбросить пароль раздела?") },
        text = { Text("Подтвердите паролем или отпечатком телефона. Пароль раздела и токен GitHub удалятся, запросы на GitHub останутся — после нового пароля введите токен заново.") },
        confirmButton = { TextButton(onClick = { forgot = false; bioPrompt(ctx, "Сброс пароля раздела", true) { DevRequests.Lock.reset(ctx); onReset() } }) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = { forgot = false }) { Text("Отмена") } },
    )
}

@Composable
private fun DevContent(vm: MainViewModel, snackbar: SnackbarHostState, onLock: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(DevRequests.configured(ctx)) }
    var issues by remember { mutableStateOf(DevRequests.cached(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var openN by rememberSaveable { mutableStateOf<Int?>(null) }
    var newReq by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(!configured) }
    var tick by remember { mutableStateOf(0) }
    fun refresh() {
        if (!DevRequests.configured(ctx)) return
        loading = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { DevRequests.list(ctx) } }
            loading = false
            r.onSuccess { issues = it; error = null }.onFailure { error = it.message ?: app.calstats.Net.why(it) }
        }
    }
    LaunchedEffect(configured) { if (configured) refresh() }

    val open = openN?.let { n -> issues.firstOrNull { it.number == n } }
    if (open != null) {
        BackHandler { openN = null; tick++ }
        DevIssueScreen(vm, open, onBack = { openN = null; tick++ }, onChanged = { refresh() })
        return
    }
    val shown = when (filter) { 1 -> issues.filter { it.open }; 2 -> issues.filter { !it.open }; else -> issues }
    ModuleScaffold("Доработка", snackbar, fab = if (configured) "Запрос" to { newReq = true } else null, actions = {
        TextButton(onClick = { refresh() }, enabled = configured && !loading) { Text("⟳") }
        TextButton(onClick = { settings = true }) { Text("⚙") }
        TextButton(onClick = onLock) { Text("🔒") }
    }) {
        item {
            Text("Идеи, ошибки и вопросы по приложению уходят разработчику. Ответы и новые сборки появятся здесь.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!configured) item {
            Flat(onClick = { settings = true }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Подключите GitHub", style = MaterialTheme.typography.titleMedium)
                    Text("Нужен закрытый репозиторий для запросов и ключ доступа. Нажмите — покажу по шагам.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Обновляю…") } }
        error?.let { e -> item { Text("⚠ $e", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        if (configured) item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все · ${issues.size}", "Открытые · ${issues.count { it.open }}", "Готово · ${issues.count { !it.open }}").forEachIndexed { i, t ->
                    FilterChip(filter == i, { filter = i }, label = { Text(t) })
                }
            }
        }
        if (configured && !loading && shown.isEmpty()) item { Text("Запросов пока нет. Нажмите «Запрос», чтобы предложить доработку или задать вопрос.", style = MaterialTheme.typography.bodyMedium) }
        shown.forEach { iss ->
            item(key = iss.number) {
                val fresh = (iss.comments - DevRequests.seen(ctx, iss.number)).coerceAtLeast(0).let { if (tick >= 0) it else it }
                Flat(onClick = { openN = iss.number }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(iss.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${iss.status} · №${iss.number} · ${fmtTime(iss.updated)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (fresh > 0) Text("💬 $fresh нов.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            else if (iss.comments > 0) Text("💬 ${iss.comments}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    if (settings) DevSettingsDialog(onDismiss = { settings = false }, onSaved = { configured = true; settings = false; refresh() })
    if (newReq) NewRequestDialog(vm, onDismiss = { newReq = false }) { created ->
        newReq = false
        issues = listOf(created) + issues
        vm.message = "✉️ Запрос №${created.number} отправлен"
        refresh()
    }
}

@Composable
private fun DevSettingsDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf(DevRequests.repo(ctx)) }
    var token by remember { mutableStateOf(DevRequests.token(ctx).orEmpty()) }
    var bio by remember { mutableStateOf(DevRequests.Lock.bio(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf(false) }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Создайте на GitHub закрытый (Private) репозиторий «zelenyi-ritm-requests».", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { open("https://github.com/new?name=zelenyi-ritm-requests&visibility=private") }) { Text("Открыть github.com/new") }
                Text("2. Создайте ключ: Fine-grained token → Only select repositories → zelenyi-ritm-requests. Права: Issues — Read and write, Contents — Read and write. Срок — 1 год.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { open("https://github.com/settings/personal-access-tokens/new") }) { Text("Создать ключ") }
                Text("3. Вставьте ключ ниже. Он хранится только на этом телефоне и не попадает в резервные копии.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(repo, { repo = it }, Modifier.fillMaxWidth(), label = { Text("Репозиторий") }, singleLine = true)
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Ключ (github_pat_…)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Вход по отпечатку", Modifier.weight(1f))
                    Switch(bio, { bio = it; DevRequests.Lock.setBio(ctx, it) })
                }
                TextButton(onClick = { newPin = true }) { Text("Сменить пароль раздела") }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && token.isNotBlank() && repo.contains('/'), onClick = {
                busy = true; msg = "Проверяю…"
                val old = DevRequests.token(ctx); val oldRepo = DevRequests.repo(ctx); val oldLogin = DevRequests.login(ctx)
                DevRequests.save(ctx, repo, token, null)
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { DevRequests.verify(ctx) } }
                    busy = false
                    r.onSuccess { login -> DevRequests.save(ctx, repo, token, login); msg = "✓ Подключено"; onSaved() }
                        .onFailure { DevRequests.save(ctx, oldRepo, old, oldLogin); msg = "Не получилось: " + (it.message ?: app.calstats.Net.why(it)) }
                }
            }) { Text("Проверить и сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    if (newPin) SetPinDialog(onDismiss = { newPin = false }) { p -> DevRequests.Lock.set(ctx, p); newPin = false; msg = "✓ Пароль раздела изменён" }
}

@Composable
private fun PhotoPicker(photos: List<Uri>, onChange: (List<Uri>) -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { l -> if (l.isNotEmpty()) onChange((photos + l).distinct().take(5)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("📎 Скриншот") }
        if (photos.isNotEmpty()) Text("${photos.size} шт.", style = MaterialTheme.typography.bodySmall)
        if (photos.isNotEmpty()) TextButton(onClick = { onChange(emptyList()) }) { Text("убрать") }
    }
}

@Composable
private fun NewRequestDialog(vm: MainViewModel, onDismiss: () -> Unit, onCreated: (DevRequests.Issue) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(DevRequests.Kind.FEATURE) }
    var module by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("✕") }
                    Text("Новый запрос", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DevRequests.Kind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text("${k.emoji} ${k.title}") }) }
                }
                Text("Модуль", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(module == null, { module = null }, label = { Text("Всё приложение") })
                    Tab.modules.filter { it != Tab.DEV }.forEach { t -> FilterChip(module == t.title, { module = t.title }, label = { Text(t.title) }) }
                }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Коротко, о чём") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text(when (kind) { DevRequests.Kind.BUG -> "Что делали и что пошло не так"; DevRequests.Kind.QUESTION -> "Ваш вопрос"; else -> "Что хотите и зачем" }) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                PhotoPicker(photos) { photos = it }
                Text("К запросу автоматически добавятся версия приложения и модель телефона.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(enabled = !busy && title.isNotBlank() && text.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; err = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { DevRequests.create(ctx, kind, module, title.trim(), text, photos) } }
                        busy = false
                        r.onSuccess { onCreated(it) }.onFailure { err = "Не отправилось: " + (it.message ?: app.calstats.Net.why(it)) + ". Текст сохранён — попробуйте ещё раз." }
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Отправить") }
            }
        }
    }
}

@Composable
private fun DevIssueScreen(vm: MainViewModel, iss: DevRequests.Issue, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<DevRequests.Comment>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var reply by rememberSaveable { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    fun load() = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { DevRequests.comments(ctx, iss.number) } }
        r.onSuccess { comments = it; err = null; DevRequests.markSeen(ctx, iss.number, it.size) }.onFailure { err = it.message ?: app.calstats.Net.why(it) }
    }
    LaunchedEffect(iss.number) { load() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("←") }
                Column(Modifier.weight(1f)) {
                    Text(iss.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${iss.status} · №${iss.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bubble(iss.body.replace("<!-- app -->", "").trim(), fmtTime(iss.created), mine = true)
                err?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (comments == null && err == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                comments?.forEach { c -> Bubble(c.body, fmtTime(c.created), c.mine) }
                if (comments?.isEmpty() == true) Text("Ответа пока нет. Как только разработчик ответит или возьмёт в работу, увидите здесь.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(reply, { reply = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ответить или уточнить…") }, maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PhotoPicker(photos) { photos = it }
                    Button(enabled = !busy && (reply.isNotBlank() || photos.isNotEmpty()), onClick = {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { DevRequests.reply(ctx, iss.number, reply, photos) } }
                            busy = false
                            r.onSuccess { reply = ""; photos = emptyList(); load(); onChanged() }.onFailure { err = "Не отправилось: " + (it.message ?: app.calstats.Net.why(it)) }
                        }
                    }) { Text(if (busy) "…" else "Отправить") }
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { DevRequests.setOpen(ctx, iss.number, !iss.open) } }
                            busy = false
                            r.onSuccess { vm.message = if (iss.open) "Запрос закрыт" else "Запрос открыт снова"; onChanged() }.onFailure { err = it.message }
                        }
                    }) { Text(if (iss.open) "Закрыть запрос" else "Открыть снова") }
                }
            }
        }
    }
}

/** Сообщение: текст + картинки из закрытого репозитория. */
@Composable
private fun Bubble(body: String, time: String, mine: Boolean) {
    val ctx = LocalContext.current
    val imgs = remember(body) { IMG.findAll(body).map { it.groupValues[1] }.toList() }
    val text = remember(body) { body.replace(IMG, "").replace(Regex("\n{3,}"), "\n\n").trim() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(14.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (mine) "Вы" else "Разработчик", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            if (text.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
            imgs.forEach { url ->
                val bmp by produceState<android.graphics.Bitmap?>(null, url) { value = withContext(Dispatchers.IO) { DevRequests.image(ctx, url) } }
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.FillWidth) }
                    ?: Text("🖼 картинка загружается…", style = MaterialTheme.typography.bodySmall)
            }
            // Ссылки на сборки — кнопкой
            Regex("""https://github\.com/\S+/releases/\S+""").find(body)?.let { m ->
                TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.value.trimEnd(')', '.', ',')))) } }) { Text("⬇ Открыть сборку") }
            }
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
