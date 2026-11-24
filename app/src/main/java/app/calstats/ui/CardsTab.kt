@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calstats.MainViewModel
import app.calstats.work.CardsKit
import app.calstats.work.LoyaltyCard
import app.calstats.work.newId
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

// ---------- файлы и штрихкоды ----------

private fun cardsDir(ctx: Context) = File(ctx.filesDir, "cards").apply { mkdirs() }

private fun saveImage(ctx: Context, uri: Uri): String? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / s > 1600) s *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
    val name = newId() + ".jpg"
    File(cardsDir(ctx), name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    name
}.getOrNull()

/** Нарисовать штрихкод или QR. Неподходящий формат (например, не-EAN номер) — Code 128. */
fun barcodeBitmap(text: String, format: String, w: Int, h: Int): Bitmap? {
    fun draw(f: BarcodeFormat): Bitmap {
        val m = MultiFormatWriter().encode(text, f, w, h, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
    return runCatching { draw(BarcodeFormat.valueOf(format)) }.recoverCatching { draw(BarcodeFormat.CODE_128) }.getOrNull()
}

private fun is2d(format: String) = format in setOf("QR_CODE", "AZTEC", "DATA_MATRIX")

/** Импорт карты из файла .pkpass (Apple Wallet, PassWallet и др.). */
fun importPkpass(ctx: Context, uri: Uri): LoyaltyCard? = runCatching {
    var json: String? = null
    var logo: ByteArray? = null
    var logoRank = 0
    ctx.contentResolver.openInputStream(uri)?.use { inp ->
        ZipInputStream(inp).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name.substringAfterLast('/')
                when {
                    n == "pass.json" -> json = z.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
                    n.startsWith("logo") && n.endsWith(".png") -> {
                        val r = when { n.contains("@3x") -> 3; n.contains("@2x") -> 2; else -> 1 }
                        if (r > logoRank) { logo = z.readBytes(); logoRank = r }
                    }
                }
            }
        }
    }
    val card = CardsKit.fromPassJson(json ?: return null, newId()) ?: return null
    val logoName = logo?.let { b -> (newId() + ".png").also { File(cardsDir(ctx), it).writeBytes(b) } }
    card.copy(logo = logoName)
}.getOrNull()

@Composable
private fun FileImage(name: String?, modifier: Modifier, crop: Boolean = true) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, name) {
        value = name?.let { withContext(Dispatchers.IO) { File(cardsDir(ctx), it).takeIf { f -> f.exists() }?.let { f -> BitmapFactory.decodeFile(f.path) } } }
    }
    bmp?.let { Image(it.asImageBitmap(), null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
}

// ---------- модуль ----------

@Composable
fun CardsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<LoyaltyCard?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val cards = vm.work.cards.filter { q.isBlank() || it.store.contains(q.trim(), true) || it.note.contains(q.trim(), true) }
        .sortedWith(compareByDescending<LoyaltyCard> { it.uses }.thenBy { it.store.lowercase() })

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val photo = saveImage(ctx, uri)
        // Ищем штрихкод на фото
        val img = runCatching { com.google.mlkit.vision.common.InputImage.fromFilePath(ctx, uri) }.getOrNull()
        val base = LoyaltyCard(newId(), "", "", photoFront = photo)
        if (img == null) { edit = base; return@rememberLauncherForActivityResult }
        com.google.mlkit.vision.barcode.BarcodeScanning.getClient().process(img)
            .addOnSuccessListener { list ->
                val b = list.firstOrNull { !it.rawValue.isNullOrBlank() }
                edit = if (b != null) base.copy(number = b.rawValue!!, format = CardsKit.fromMlKit(b.format)) else base.also { vm.message = "Штрихкод на фото не найден — введите номер" }
            }
            .addOnFailureListener { edit = base }
    }
    val fileImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val c = importPkpass(ctx, uri)
        if (c == null) vm.message = "Это не карта .pkpass" else edit = c
    }
    LaunchedEffect(vm.pendingCard) { vm.pendingCard?.let { edit = it; vm.pendingCard = null } }
    LaunchedEffect(vm.showCardId) { vm.showCardId?.let { show = it; vm.showCardId = null } }

    fun scan() {
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
            .addOnSuccessListener { b ->
                val raw = b.rawValue ?: return@addOnSuccessListener
                edit = LoyaltyCard(newId(), "", raw, CardsKit.fromMlKit(b.format))
            }
            .addOnFailureListener { vm.message = "Сканер недоступен: нужны сервисы Google Play" }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Карты", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Карта") })
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(text = { Text("📷 Сканировать штрихкод") }, onClick = { addMenu = false; scan() })
                    DropdownMenuItem(text = { Text("🖼 С фото карты") }, onClick = { addMenu = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    DropdownMenuItem(text = { Text("⌨ Ввести номер") }, onClick = { addMenu = false; edit = LoyaltyCard(newId(), "", "") })
                    DropdownMenuItem(text = { Text("📥 Файл .pkpass (Wallet)") }, onClick = { addMenu = false; fileImport.launch(arrayOf("application/vnd.apple.pkpass", "application/octet-stream", "application/zip", "*/*")) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        var part by navView(vm, "cards", "CARDS")
        Column(Modifier.padding(pad).fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(part == "CARDS", { part = "CARDS" }, label = { Text("Карты") })
            androidx.compose.material3.FilterChip(part == "CASHBACK", { part = "CASHBACK" }, label = { Text("Кешбэк") })
        }
        if (part == "CASHBACK") { CashbackPart(vm); return@Column }
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Найти магазин") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
            }
            if (vm.work.cards.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Все бонусные карты в одном месте", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Сканируйте штрихкод карты камерой, добавьте фото карты или импортируйте файл .pkpass. " +
                                "На кассе откройте карту — экран станет ярким, штрихкод крупным. Часто используемые карты поднимаются наверх.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Google Кошелёк и «Кошелёк» не дают другим приложениям читать карты, поэтому перенести их автоматически нельзя. " +
                                "Быстрее всего — открыть карту там и отсканировать её штрихкод с экрана второго телефона или сделать скриншот и добавить «С фото карты».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scan() }) { Text("Сканировать") }
                            OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("С фото") }
                        }
                    }
                }
            }
            items(cards, key = { it.id }) { c -> CardTile(c) { show = c.id } }
            if (vm.work.cards.isNotEmpty()) item(span = { GridItemSpan(2) }) { NearStoreSwitch(vm) }
        }
        }
    }

    show?.let { id ->
        vm.work.cards.firstOrNull { it.id == id }?.let { c ->
            CardShow(vm, c, onEdit = { show = null; edit = c }) { show = null }
        }
    }
    edit?.let { c -> CardEditDialog(vm, c) { edit = null } }
}

@Composable
private fun CardTile(c: LoyaltyCard, onClick: () -> Unit) {
    val bg = Color(c.color)
    val fg = if (bg.luminance() > 0.55f) Color(0xFF121721) else Color.White
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(12.dp),
    ) {
        if (c.logo != null) FileImage(c.logo, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.TopEnd), crop = false)
        Text(c.store.ifBlank { "Карта" }, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(end = if (c.logo != null) 40.dp else 0.dp))
        Text("•• " + c.number.takeLast(4), color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomStart))
    }
}

/** Карта на кассе: крупный штрихкод, яркость на максимум. */
@Composable
private fun CardShow(vm: MainViewModel, c: LoyaltyCard, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(c.id) {
        val win = (ctx as? Activity)?.window
        val old = win?.attributes?.screenBrightness
        win?.attributes = win?.attributes?.apply { screenBrightness = 1f }
        vm.updateWork { w -> w.copy(cards = w.cards.map { if (it.id == c.id) it.copy(uses = it.uses + 1, lastUsed = System.currentTimeMillis()) else it }) }
        // Открыли карту на кассе — тихо запоминаем магазин (если подсказки включены)
        if (app.calstats.CardPlaces.enabled(ctx)) app.calstats.CardPlaces.remember(ctx, vm, c.id, silent = true)
        onDispose { win?.attributes = win?.attributes?.apply { screenBrightness = old ?: -1f } }
    }
    val bmp = remember(c.number, c.format) { if (is2d(c.format)) barcodeBitmap(c.number, c.format, 700, 700) else barcodeBitmap(c.number, c.format, 1200, 380) }
    var photo by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().background(Color(c.color)).padding(18.dp)) {
                val fg = if (Color(c.color).luminance() > 0.55f) Color(0xFF121721) else Color.White
                Text(c.store, color = fg, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(18.dp))
            bmp?.let {
                Image(it.asImageBitmap(), "Штрихкод", Modifier.padding(horizontal = 16.dp).fillMaxWidth().then(if (is2d(c.format)) Modifier.aspectRatio(1f).padding(horizontal = 40.dp) else Modifier.height(130.dp)))
            }
            Text(c.number.chunked(4).joinToString(" "), color = Color(0xFF121721), fontFamily = FontFamily.Monospace, fontSize = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp))
            if (c.note.isNotBlank()) Text(c.note, color = Color(0xFF5E6472), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(c.photoFront, c.photoBack).forEach { p ->
                    FileImage(p, Modifier.size(width = 120.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)).clickable { photo = p })
                }
            }
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.calstats.CardPlaces.enabled(ctx)) TextButton(onClick = { app.calstats.CardPlaces.remember(ctx, vm, c.id, silent = false) }) {
                    Text("📍 Я здесь" + if (c.places.isNotEmpty()) " (${c.places.size})" else "", color = Color(0xFF3F3561))
                }
                TextButton(onClick = onEdit) { Text("Изменить", color = Color(0xFF3F3561)) }
                TextButton(onClick = onDismiss) { Text("Закрыть", color = Color(0xFF3F3561)) }
            }
        }
    }
    photo?.let { p ->
        Dialog(onDismissRequest = { photo = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { photo = null }, contentAlignment = Alignment.Center) {
                FileImage(p, Modifier.fillMaxWidth(), crop = false)
            }
        }
    }
}

@Composable
private fun CardEditDialog(vm: MainViewModel, c: LoyaltyCard, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.cards.any { it.id == c.id }
    var store by remember { mutableStateOf(c.store) }
    var number by remember { mutableStateOf(c.number) }
    var format by remember { mutableStateOf(c.format) }
    var color by remember { mutableStateOf(c.color) }
    var note by remember { mutableStateOf(c.note) }
    var front by remember { mutableStateOf(c.photoFront) }
    var back by remember { mutableStateOf(c.photoBack) }
    var fmtMenu by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var which by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveImage(ctx, uri)?.let { if (which == 0) front = it else back = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Карта" else "Новая карта") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(store, { store = it }, label = { Text("Магазин") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                val hints = CardsKit.STORES.filter { store.isBlank() || it.first.contains(store.trim(), true) }.take(10)
                if (hints.isNotEmpty() && hints.none { it.first == store }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        hints.forEach { (n, col) -> FilterChip(selected = false, onClick = { store = n; color = col.toInt() }, label = { Text(n) }) }
                    }
                }
                OutlinedTextField(number, { number = it.trim(); if (!exists && c.number.isBlank()) format = CardsKit.guess(it) }, label = { Text("Номер / код") }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                Box {
                    OutlinedButton(onClick = { fmtMenu = true }) { Text("Формат: " + (CardsKit.FORMATS.firstOrNull { it.first == format }?.second ?: format)) }
                    DropdownMenu(fmtMenu, { fmtMenu = false }) {
                        CardsKit.FORMATS.forEach { (k, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { format = k; fmtMenu = false }) }
                    }
                }
                if (number.isNotBlank()) {
                    val prev = remember(number, format) { barcodeBitmap(number, format, 600, if (is2d(format)) 600 else 160) }
                    prev?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(if (is2d(format)) 140.dp else 60.dp).background(Color.White)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(color)).clickable { colors = !colors })
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { colors = !colors }) { Text("Цвет карты") }
                }
                if (colors) ColorPalette(color) { color = it }
                OutlinedTextField(note, { note = it }, label = { Text("Заметка: скидка, пин-код, условия") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { which = 0; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (front != null) "Лицо ✓" else "Фото лица") }
                    OutlinedButton(onClick = { which = 1; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (back != null) "Оборот ✓" else "Фото оборота") }
                }
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(cards = w.cards.filter { it.id != c.id }) }; onDismiss() }) {
                    Text("Удалить карту", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = number.isNotBlank() && store.isNotBlank(), onClick = {
                val nc = c.copy(store = store.trim(), number = number.trim(), format = format, color = color, note = note.trim(), photoFront = front, photoBack = back)
                vm.updateWork { w -> w.copy(cards = if (exists) w.cards.map { if (it.id == c.id) nc else it } else w.cards + nc) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


/** Включение подсказки карты у магазина с запросом геолокации (точной, затем «Разрешать всегда»). */
@Composable
private fun NearStoreSwitch(vm: MainViewModel) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(app.calstats.CardPlaces.enabled(ctx)) }
    var askBg by remember { mutableStateOf(false) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.calstats.CardPlaces.sync(ctx, vm.work)
        if (!it) vm.message = "Без «Разрешать всегда» подсказки у магазина не придут, но место будет запоминаться"
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            app.calstats.CardPlaces.setEnabled(ctx, true, vm.work); on = true
            if (!app.calstats.CardPlaces.hasBackground(ctx)) askBg = true
        } else vm.message = "Нужен доступ к геолокации"
    }
    androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📍 Подсказывать карту у магазина", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (on) "Открывайте карту на кассе — приложение запомнит магазин и в следующий раз напомнит о ней, когда вы будете рядом. Места хранятся только в телефоне."
                        else "Когда вы рядом с магазином, придёт уведомление с его картой.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(on, { v ->
                    if (!v) { app.calstats.CardPlaces.setEnabled(ctx, false, vm.work); on = false }
                    else if (app.calstats.CardPlaces.hasFine(ctx)) {
                        app.calstats.CardPlaces.setEnabled(ctx, true, vm.work); on = true
                        if (!app.calstats.CardPlaces.hasBackground(ctx)) askBg = true
                    } else fineLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                })
            }
            if (on && !app.calstats.CardPlaces.hasBackground(ctx)) TextButton(onClick = { askBg = true }) { Text("Разрешить геолокацию «всегда»") }
        }
    }
    if (askBg) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askBg = false },
        title = { Text("Геолокация в фоне") },
        text = { Text("Чтобы уведомление пришло, когда приложение закрыто, выберите в следующем окне «Разрешать всегда». Приложение не следит за маршрутом — Android сам сообщает только о приходе к запомненным магазинам.") },
        confirmButton = { TextButton(onClick = {
            askBg = false
            if (android.os.Build.VERSION.SDK_INT >= 29) bgLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { askBg = false }) { Text("Позже") } },
    )
}

/** Кешбэк по банкам на месяц + подсказка, какой картой платить. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CashbackPart(vm: MainViewModel) {
    val month = app.calstats.work.CashbackKit.month(vm.today)
    var add by remember { mutableStateOf(false) }
    val all = vm.work.cashback
    val cur = all.filter { it.month == month }
    val prev = all.filter { it.month == app.calstats.work.CashbackKit.month(vm.today.minusMonths(1)) }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Кешбэк · ${app.calstats.Fmt.monthTitle(vm.today)}", style = MaterialTheme.typography.titleLarge)
            Text("Запишите категории, выбранные в банках. Приложение напомнит выбрать их 1-го числа и подскажет при записи трат, какой картой платить выгоднее.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (cur.isEmpty() && prev.isNotEmpty()) item {
            OutlinedButton(onClick = {
                vm.updateWork { w -> w.copy(cashback = w.cashback + prev.map { it.copy(id = newId(), month = month) }) }
            }) { Text("Перенести категории с прошлого месяца") }
        }
        // Лучшее по категориям
        if (cur.isNotEmpty()) item {
            androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Где чем платить", style = MaterialTheme.typography.titleSmall)
                    cur.groupBy { it.category }.map { (cat, l) -> cat to l.maxBy { it.percent } }.sortedByDescending { it.second.percent }.forEach { (cat, b) ->
                        Text("$cat — ${b.bank} ${fmtPct(b.percent)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        cur.groupBy { it.bank }.forEach { (bank, list) ->
            item(key = "b$bank") {
                androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(bank, style = MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.sortedByDescending { it.percent }.forEach { cb ->
                                androidx.compose.material3.InputChip(selected = false, onClick = {}, label = { Text("${cb.category} ${fmtPct(cb.percent)}") },
                                    trailingIcon = { Text("✕", Modifier.clickable { vm.updateWork { w -> w.copy(cashback = w.cashback.filter { it.id != cb.id }) } }) })
                            }
                        }
                    }
                }
            }
        }
        item { Button(onClick = { add = true }) { Text("+ Добавить категорию") } }
    }
    if (add) CashbackDialog(vm, month) { add = false }
}

private fun fmtPct(p: Double) = (if (p % 1.0 == 0.0) p.toInt().toString() else p.toString().replace('.', ',')) + "%"

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CashbackDialog(vm: MainViewModel, month: String, onDismiss: () -> Unit) {
    val banks = (vm.work.cashback.map { it.bank } + app.calstats.work.CashbackKit.BANKS).distinct()
    var bank by remember { mutableStateOf(vm.work.cashback.lastOrNull()?.bank ?: "") }
    var cats by remember { mutableStateOf(setOf<String>()) }
    var pct by remember { mutableStateOf("5") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кешбэк на месяц") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(bank, { bank = it }, label = { Text("Банк") }, singleLine = true)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    banks.take(10).forEach { b -> androidx.compose.material3.FilterChip(bank == b, { bank = b }, label = { Text(b) }) }
                }
                OutlinedTextField(pct, { pct = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("Процент") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                Text("Категории (можно несколько)", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.calstats.work.CashbackKit.CATEGORIES.forEach { c ->
                        androidx.compose.material3.FilterChip(c in cats, { cats = if (c in cats) cats - c else cats + c }, label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = {
            val p = pct.replace(',', '.').toDoubleOrNull()
            TextButton(enabled = bank.isNotBlank() && cats.isNotEmpty() && p != null && p > 0, onClick = {
                vm.updateWork { w -> w.copy(cashback = w.cashback.filterNot { it.month == month && it.bank == bank.trim() && it.category in cats } +
                    cats.map { app.calstats.work.Cashback(newId(), bank.trim(), month, it, p!!) }) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
