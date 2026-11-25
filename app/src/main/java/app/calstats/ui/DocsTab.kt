@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.work.DocKind
import app.calstats.work.DocState
import app.calstats.work.DocsMath
import app.calstats.work.DocsOps
import app.calstats.work.Document
import app.calstats.work.Recurring
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import app.calstats.work.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class DView(val title: String) { DOCS("Документы"), SUBS("Подписки") }

private val OK_C = Color(0xFF4F7A5A)
private val SOON_C = Color(0xFFC27B44)
private val EXP_C = Color(0xFFB04A3F)

/** Модуль «Документы и подписки». */
@Composable
fun DocsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var view by navView(vm, "docs", DView.DOCS)
    var addSub by remember { mutableStateOf(false) }
    val locked = vm.docsLock && !vm.docsUnlocked
    // Паспорта и полисы под защитой: нельзя сделать снимок экрана, и не видно в списке недавних приложений.
    val act = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    if (vm.docsLock) androidx.compose.runtime.DisposableEffect(Unit) {
        act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Документы", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            )
        },
        floatingActionButton = {
            if (!locked) {
                ExtendedFloatingActionButton(
                    onClick = { if (view == DView.DOCS) vm.push(Screen.DocEdit(null)) else addSub = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(if (view == DView.DOCS) "Документ" else "Подписка") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DView.entries.forEach { v -> FilterChip(selected = view == v, onClick = { view = v }, label = { Text(v.title) }) }
            }
            when {
                view == DView.SUBS -> SubsView(vm)
                locked -> LockedView(vm)
                else -> DocsList(vm)
            }
        }
    }
    if (addSub) {
        val cat = vm.work.finCategories.firstOrNull { it.name == "Подписки" }?.id
        RecurringDialog(vm, Recurring(newId(), "", 0, nextDate = vm.today.toString(), categoryId = cat, subscription = true)) { addSub = false }
    }
}

// ======================= ЗАЩИТА =======================

fun canLock(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= 30) {
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        @Suppress("DEPRECATION")
        bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/** Отпечаток, лицо или PIN телефона — системным окном Android. */
fun unlock(ctx: Context, onOk: () -> Unit) {
    if (Build.VERSION.SDK_INT < 29) {
        onOk()
        return
    }
    val b = BiometricPrompt.Builder(ctx).setTitle("Документы").setSubtitle("Подтвердите, что это вы")
    if (Build.VERSION.SDK_INT >= 30) {
        b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        b.setDeviceCredentialAllowed(true)
    }
    b.build().authenticate(
        CancellationSignal(),
        ctx.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
        },
    )
}

@Composable
private fun LockedView(vm: MainViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { unlock(ctx) { vm.docsUnlocked = true } }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Документы защищены", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { unlock(ctx) { vm.docsUnlocked = true } }) { Text("Открыть") }
    }
}

// ======================= СПИСОК ДОКУМЕНТОВ =======================

@Composable
private fun DocsList(vm: MainViewModel) {
    val ctx = LocalContext.current
    val today = vm.today
    // Семья: фильтр по владельцу
    var who by navView(vm, "docsOwner", "")
    val owners = vm.work.documents.filter { !it.archived }.map { it.owner.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    val docs = vm.work.documents.filter { !it.archived && (who.isEmpty() || it.owner.trim() == who) }
    val attention = DocsMath.attention(docs, today)
    val rest = docs.filter { it !in attention }.sortedWith(compareBy({ it.owner }, { it.kind.ordinal }))
    val archived = vm.work.documents.filter { it.archived }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Сроки документов", style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            docs.isEmpty() -> "Документов пока нет"
                            attention.isEmpty() -> "Всё в порядке"
                            else -> "${attention.size} " + Fmt.plural(attention.size.toLong(), "требует", "требуют", "требуют") + " внимания"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Напомню заранее, за неделю и накануне. Номера и фото хранятся только на телефоне.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (owners.size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = who.isEmpty(), onClick = { who = "" }, label = { Text("Вся семья") })
                owners.forEach { o -> FilterChip(selected = who == o, onClick = { who = if (who == o) "" else o }, label = { Text(o) }) }
            }
        }
        if (docs.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Быстро добавить", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(DocKind.PASSPORT, DocKind.INTL_PASSPORT, DocKind.DRIVER, DocKind.OSAGO, DocKind.TECH, DocKind.DMS, DocKind.WARRANTY).forEach { k ->
                            FilterChip(selected = false, onClick = {
                                val d = Document(newId(), k, remindDays = k.remindDays.coerceAtLeast(7))
                                vm.updateWork { DocsOps.upsert(it, d) }
                                vm.push(Screen.DocEdit(d.id))
                            }, label = { Text("${k.emoji} ${k.title}") })
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { scanReceipt(ctx, vm) }, modifier = Modifier.fillMaxWidth()) { Text("🧾 Гарантия по QR-коду чека") }
        }
        if (attention.isNotEmpty()) {
            item { Text("Внимание", style = MaterialTheme.typography.titleMedium, color = SOON_C) }
            items(attention, key = { it.id }) { d -> DocCard(vm, d) }
        }
        if (rest.isNotEmpty()) {
            if (attention.isNotEmpty()) item { Text("Остальные", style = MaterialTheme.typography.titleMedium) }
            items(rest, key = { it.id }) { d -> DocCard(vm, d) }
        }
        if (archived.isNotEmpty()) {
            item { Text("Архив", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(archived, key = { "a" + it.id }) { d -> DocCard(vm, d) }
        }
        if (canLock(ctx) || vm.docsLock) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Защита отпечатком или PIN", style = MaterialTheme.typography.bodyMedium)
                        Text("Спрашивать при открытии документов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.docsLock, onCheckedChange = { on ->
                        if (on) unlock(ctx) { vm.changeDocsLock(true) } else unlock(ctx) { vm.changeDocsLock(false) }
                    })
                }
            }
        }
    }
}

@Composable
private fun DocCard(vm: MainViewModel, d: Document) {
    val today = vm.today
    val state = DocsMath.state(d, today)
    val left = DocsMath.daysLeft(d, today)
    Card(
        onClick = { vm.push(Screen.DocEdit(d.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(stateColor(state).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(d.kind.emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(d.owner.takeIf { it.isNotBlank() }, d.number.takeIf { it.isNotBlank() }?.let { DocsMath.masked(it) })
                        .joinToString(" · ").ifBlank { d.kind.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (left != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            left < 0 -> "истёк"
                            left == 0L -> "сегодня"
                            left <= 60 -> Fmt.days(left)
                            else -> "до ${Fmt.monthShort(d.expiry!!.monthValue)} ${d.expiry!!.year}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = stateColor(state),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (d.photos.isNotEmpty()) Text("📷 ${d.photos.size}", style = MaterialTheme.typography.labelSmall)
                }
            } else if (d.photos.isNotEmpty()) {
                Text("📷 ${d.photos.size}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun stateColor(s: DocState) = when (s) {
    DocState.OK -> OK_C
    DocState.SOON -> SOON_C
    DocState.EXPIRED -> EXP_C
    DocState.NONE -> Color(0xFF5A6B7D)
}

// ======================= ПОДПИСКИ =======================

@Composable
private fun SubsView(vm: MainViewModel) {
    val today = vm.today
    val subs = DocsMath.subscriptions(vm.work)
    val active = subs.filter { it.active && it.type == TxType.EXPENSE }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Подписки", style = MaterialTheme.typography.labelLarge)
                    Text("${SplitMath.money(active.sumOf { it.monthly })} в месяц", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${SplitMath.money(active.sumOf { it.monthly } * 12)} в год · активных ${active.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val week = active.count { ChronoUnit.DAYS.between(today, it.next) in 0..7 }
                    if (week > 0) Text("На этой неделе списаний: $week", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (subs.isEmpty()) {
            item {
                Text(
                    "Добавьте подписки — напомню накануне списания и за 2 дня до конца пробного периода. " +
                        "Те же подписки видны в «Финансах» → «Платежи».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(subs, key = { it.id }) { r ->
            var edit by remember { mutableStateOf(false) }
            Card(onClick = { edit = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    PaymentLine(vm, r)
                    DocsMath.trialLeft(r, today)?.let { t ->
                        Text(
                            "⏳ Пробный период: " + if (t == 0L) "заканчивается сегодня" else "ещё ${Fmt.days(t)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SOON_C,
                            modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                        )
                    }
                }
            }
            if (edit) RecurringDialog(vm, r) { edit = false }
        }
        if (subs.isNotEmpty()) {
            item {
                Text(
                    "Отменили подписку? Откройте её и снимите «Активен» — она перестанет учитываться в сумме.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ======================= КАРТОЧКА ДОКУМЕНТА =======================

@Composable
fun DocEditScreen(vm: MainViewModel, id: String?, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val original = remember(id) { vm.work.documents.firstOrNull { it.id == id } }
    val docId = remember(id) { id ?: newId() }
    var kind by remember { mutableStateOf(original?.kind ?: DocKind.PASSPORT) }
    var title by remember { mutableStateOf(original?.title ?: "") }
    var owner by remember { mutableStateOf(original?.owner ?: "") }
    var number by remember { mutableStateOf(original?.number ?: "") }
    var issuer by remember { mutableStateOf(original?.issuer ?: "") }
    var issued by remember { mutableStateOf(original?.issued?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var expires by remember { mutableStateOf(original?.expiry) }
    var remind by remember { mutableStateOf(original?.remindDays ?: kind.remindDays.coerceAtLeast(7)) }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var photos by remember { mutableStateOf(original?.photos ?: emptyList()) }
    var archived by remember { mutableStateOf(original?.archived ?: false) }
    var showNumber by remember { mutableStateOf(original == null) }
    var deleted by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<app.calstats.work.DocScan.Found?>(null) }

    if (vm.docsLock && !vm.docsUnlocked) {
        LaunchedEffect(Unit) { unlock(ctx) { vm.docsUnlocked = true } }
        Scaffold(topBar = {
            TopAppBar(title = { Text("Документ") }, navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
        }) { pad -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = { unlock(ctx) { vm.docsUnlocked = true } }) { Text("Открыть") } } }
        return
    }

    fun build() = Document(
        id = docId, kind = kind, title = title.trim(), owner = owner.trim(), number = number.trim(), issuer = issuer.trim(),
        issued = issued?.toString(), expires = expires?.toString(), remindDays = remind, note = note.trim(), photos = photos,
        archived = archived, created = original?.created ?: System.currentTimeMillis(),
    )

    // Автосохранение при выходе с экрана.
    DisposableEffect(Unit) {
        onDispose {
            if (!deleted) {
                val d = build()
                val empty = d.title.isEmpty() && d.number.isEmpty() && d.expires == null && d.photos.isEmpty() && d.note.isEmpty() && original == null
                if (!empty && d != original) vm.updateWork { DocsOps.upsert(it, d) }
            }
        }
    }

    // Сканер документов Google: обрезка по краям, выравнивание, несколько страниц
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        vm.keepDocsUnlocked = false
        val r = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(res.data) ?: return@rememberLauncherForActivityResult
        val added = r.pages.orEmpty().mapNotNull { copyPhoto(ctx, it.imageUri) }
        photos = photos + added
        if (added.isNotEmpty()) vm.message = "Отсканировано страниц: ${added.size}"
    }
    fun scan() {
        val act = ctx as? android.app.Activity ?: return
        val opts = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(10)
            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        vm.keepDocsUnlocked = true
        com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(opts).getStartScanIntent(act)
            .addOnSuccessListener { scanner.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.keepDocsUnlocked = false; vm.message = "Сканер недоступен: нужны сервисы Google Play" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        vm.keepDocsUnlocked = false
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val added = uris.mapNotNull { copyPhoto(ctx, it) }
        photos = photos + added
        if (added.size < uris.size) vm.message = "Часть фото не удалось сохранить"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Новый документ" else kind.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Тип", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DocKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = {
                        kind = k
                        if (original == null) remind = k.remindDays.coerceAtLeast(7)
                    }, label = { Text("${k.emoji} ${k.title}") })
                }
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название (если нужно своё)") }, placeholder = { Text(kind.title) },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

            Text("Чей документ", style = MaterialTheme.typography.labelLarge)
            val names = (listOf(vm.work.meName) + vm.work.people.map { it.name } + vm.work.documents.map { it.owner })
                .filter { it.isNotBlank() }.distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                names.take(10).forEach { n -> FilterChip(selected = owner == n, onClick = { owner = if (owner == n) "" else n }, label = { Text(n) }) }
                ContactChip(label = "📇 Контакт") { c -> owner = c.name.ifBlank { c.phone } }
            }
            OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), label = { Text("Владелец") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    if (showNumber) number else DocsMath.masked(number),
                    { if (showNumber) number = it },
                    Modifier.weight(1f),
                    label = { Text("Серия и номер") },
                    singleLine = true,
                    readOnly = !showNumber,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                TextButton(onClick = { showNumber = !showNumber }) { Text(if (showNumber) "Скрыть" else "Показать") }
            }
            if (number.isNotBlank()) {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText(kind.title, number.trim()))
                    vm.message = "Номер скопирован"
                }, contentPadding = PaddingValues(0.dp)) { Text("Скопировать номер") }
            }
            OutlinedTextField(issuer, { issuer = it }, Modifier.fillMaxWidth(), label = { Text("Кем выдан / компания") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(ctx, issued ?: vm.today) { issued = it } }, Modifier.weight(1f)) {
                    Text(issued?.let { "Выдан ${Fmt.date(it)}" } ?: "Дата выдачи")
                }
                OutlinedButton(onClick = { pickDate(ctx, expires ?: vm.today.plusYears(1)) { expires = it } }, Modifier.weight(1f)) {
                    Text(expires?.let { "До ${Fmt.date(it)}" } ?: "Действует до")
                }
            }
            if (kind == DocKind.PASSPORT && expires == null) {
                TextButton(onClick = {
                    pickDate(ctx, vm.today.minusYears(30), "Дата рождения владельца") { b ->
                        val e = DocsMath.passportExpiry(b, vm.today)
                        if (e != null) expires = e else vm.message = "После 45 лет паспорт бессрочный"
                    }
                }, contentPadding = PaddingValues(0.dp)) { Text("Рассчитать срок по дате рождения (замена в 20 и 45 лет)") }
            }
            if (expires != null) {
                Text("Напомнить заранее", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 14, 30, 60, 90, 180).forEach { r ->
                        FilterChip(selected = remind == r, onClick = { remind = r }, label = { Text(if (r < 30) "за $r дн." else "за ${r / 30} мес.") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.push(Screen.Edit(presetDate = expires, presetAllDay = true, presetTitle = "Истекает: ${title.ifBlank { kind.title }}" + owner.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()))
                    }) { Text("📅 В календарь") }
                    TextButton(onClick = { expires = null }) { Text("Бессрочный") }
                }
            }

            Text("Фото", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEach { p ->
                    Box {
                        Thumb(ctx, p, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { viewPhoto = p })
                        IconButton(
                            onClick = { photos = photos - p },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)),
                        ) { Icon(Icons.Filled.Close, "Убрать", Modifier.size(16.dp), tint = Color.White) }
                    }
                }
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable {
                        vm.keepDocsUnlocked = true
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("+ фото", color = MaterialTheme.colorScheme.primary) }
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { scan() },
                    contentAlignment = Alignment.Center,
                ) { Text("📄 скан", color = MaterialTheme.colorScheme.primary) }
            }
            if (photos.isNotEmpty()) OutlinedButton(onClick = {
                runCatching {
                    val f = docPdf(ctx, title.ifBlank { kind.title }, photos)
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "PDF документа"))
                }.onFailure { vm.message = "Не удалось собрать PDF" }
            }) { Text("📤 Отправить PDF (${photos.size} стр.)") }
            if (photos.isNotEmpty()) {
                OutlinedButton(onClick = {
                    scanning = true
                    val img = com.google.mlkit.vision.common.InputImage.fromFilePath(ctx, android.net.Uri.fromFile(photoFile(ctx, photos.first())))
                    com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(img)
                        .addOnSuccessListener { t -> scanning = false; found = app.calstats.work.DocScan.parse(t.text) }
                        .addOnFailureListener { scanning = false; vm.message = "Не удалось распознать: нужны сервисы Google Play" }
                }, enabled = !scanning) { Text(if (scanning) "Распознаю…" else "🔍 Распознать номер и даты с фото") }
            }
            Text(
                "Сфотографируйте разворот камерой телефона и выберите снимок. Фото хранятся внутри приложения и не попадают в облачную копию.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Заметка") }, minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            if (original != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(archived, { archived = it })
                    Spacer(Modifier.width(10.dp))
                    Text("В архиве (заменён, утерян, не нужен)")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    found?.let { f ->
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Найдено на фото") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (f.numbers.isEmpty() && f.dates.isEmpty()) Text("Ничего не нашлось. Попробуйте более чёткое фото.")
                    if (f.numbers.isNotEmpty()) Text("Номер — нажмите, чтобы вставить", style = MaterialTheme.typography.labelLarge)
                    f.numbers.forEach { n -> OutlinedButton(onClick = { number = n; showNumber = true }) { Text(n) } }
                    if (f.dates.isNotEmpty()) Text("Даты", style = MaterialTheme.typography.labelLarge)
                    f.dates.sorted().forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.date(d), Modifier.weight(1f))
                            TextButton(onClick = { issued = d }) { Text("выдан") }
                            TextButton(onClick = { expires = d }) { Text("до") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { found = null }) { Text("Готово") } },
        )
    }
    viewPhoto?.let { p ->
        Dialog(onDismissRequest = { viewPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewPhoto = null }, contentAlignment = Alignment.Center) {
                Thumb(ctx, p, Modifier.fillMaxWidth(), full = true)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить документ?") },
            text = { Text("Данные и фото документа будут удалены с телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    deleted = true
                    (original?.photos.orEmpty() + photos).distinct().forEach { photoFile(ctx, it).delete() }
                    vm.updateWork { DocsOps.delete(it, docId) }
                    confirmDelete = false
                    vm.pop()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private fun photoFile(ctx: Context, name: String) = File(File(ctx.filesDir, "docs"), name)

/** Копирует выбранное фото в папку приложения, уменьшая до 2000 px. */
private fun copyPhoto(ctx: Context, uri: Uri): String? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > 2000) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
    val name = newId() + ".jpg"
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    bmp.recycle()
    name
}.getOrNull()

@Composable
private fun Thumb(ctx: Context, name: String, modifier: Modifier, full: Boolean = false) {
    val bmp by produceState<Bitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            val f = photoFile(ctx, name)
            if (!f.exists()) null
            else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = if (full) 1 else 4 })
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier, contentScale = if (full) ContentScale.Fit else ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

// ======================= НА ГЛАВНОЙ =======================

@Composable
fun DocsSummary(vm: MainViewModel) {
    val today = vm.today
    val w = vm.work
    val attention = DocsMath.attention(w.documents, today)
    val soonSubs = DocsMath.subscriptions(w).filter { it.active && ChronoUnit.DAYS.between(today, it.next) in 0..3 }
    val docs = w.documents.count { !it.archived }
    Card(onClick = { vm.tab = Tab.DOCS }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(Tab.DOCS)
                Spacer(Modifier.width(8.dp))
                Text("Документы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (attention.isEmpty()) "всё в порядке" else "внимание: ${attention.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (attention.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else SOON_C,
                )
            }
            if (docs == 0 && soonSubs.isEmpty()) {
                Text("Добавьте паспорт, права, ОСАГО — напомню о сроках", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Номера на Главной не показываем — только названия и сроки.
            attention.take(3).forEach { d ->
                val left = DocsMath.daysLeft(d, today) ?: 0
                Text(
                    "${d.kind.emoji} ${d.name}" + d.owner.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                        " — " + if (left < 0) "истёк" else if (left == 0L) "истекает сегодня" else "через ${Fmt.days(left)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            soonSubs.take(3).forEach { r ->
                val d = ChronoUnit.DAYS.between(today, r.next)
                Text(
                    "🔁 ${r.name} — ${SplitMath.money(r.amount)} " + when (d) { 0L -> "сегодня"; 1L -> "завтра"; else -> "через ${Fmt.days(d)}" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Сканировать QR-код кассового чека: гарантия на год + (по желанию) расход в «Финансах». */
fun scanReceipt(ctx: Context, vm: MainViewModel) {
    val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
    com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan()
        .addOnSuccessListener { b ->
            val r = app.calstats.work.DocScan.receipt(b.rawValue.orEmpty())
            if (r == null) {
                vm.message = "Это не QR-код кассового чека"
                return@addOnSuccessListener
            }
            val d = Document(
                newId(), DocKind.WARRANTY, title = "Покупка на ${SplitMath.money(r.sum)}",
                issued = r.time.toLocalDate().toString(), expires = r.time.toLocalDate().plusYears(1).toString(), remindDays = 14,
                note = "Чек от ${Fmt.date(r.time.toLocalDate())}, сумма ${SplitMath.money(r.sum)}" + (r.fn?.let { ", ФН $it" } ?: ""),
            )
            vm.updateWork { DocsOps.upsert(it, d) }
            vm.push(Screen.DocEdit(d.id))
            vm.message = "Гарантия на год добавлена — укажите, что купили"
        }
        .addOnFailureListener { vm.message = "Сканер недоступен: нужны сервисы Google Play" }
}


/** Собрать фото документа в один PDF (страницы A4). */
private fun docPdf(ctx: Context, title: String, photos: List<String>): File {
    val doc = android.graphics.pdf.PdfDocument()
    photos.forEachIndexed { i, name ->
        val bmp = BitmapFactory.decodeFile(photoFile(ctx, name).path) ?: return@forEachIndexed
        val pw = 595; val ph = 842
        val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
        val scale = minOf((pw - 40f) / bmp.width, (ph - 40f) / bmp.height)
        val w = bmp.width * scale; val h = bmp.height * scale
        val l = (pw - w) / 2; val t = (ph - h) / 2
        page.canvas.drawBitmap(bmp, null, android.graphics.RectF(l, t, l + w, t + h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)
        bmp.recycle()
    }
    val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
    val out = File(dir, title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60) + ".pdf")
    out.outputStream().use { doc.writeTo(it) }
    doc.close()
    return out
}
