@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.calstats.ui

import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import app.calstats.work.ShopPrice
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.calstats.work.progressIn
import app.calstats.work.BankImport
import app.calstats.work.foodDaysLeft
import app.calstats.work.Car
import app.calstats.work.CarEvent
import app.calstats.work.CarEventType
import app.calstats.work.CarMath
import app.calstats.work.GOAL_AREAS
import app.calstats.work.GoalStep
import app.calstats.work.Journal
import app.calstats.work.JournalEntry
import app.calstats.work.LifeGoal
import app.calstats.work.METER_TEMPLATES
import app.calstats.work.Meter
import app.calstats.work.MeterReading
import app.calstats.work.Meters
import app.calstats.work.Pet
import app.calstats.work.PetEvent
import app.calstats.work.PetEventType
import app.calstats.work.Pets
import app.calstats.work.Shop
import app.calstats.work.ShopItem
import app.calstats.work.ShopList
import app.calstats.work.SplitMath
import app.calstats.work.newId
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ---------- общий каркас ----------

@Composable
internal fun ModuleScaffold(
    title: String,
    snackbar: SnackbarHostState,
    fab: Pair<String, () -> Unit>? = null,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                actions = { actions() },
            )
        },
        floatingActionButton = { fab?.let { (t, a) -> ExtendedFloatingActionButton(onClick = a, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(t) }) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun Flat(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { content() }
    else Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ======================= ПОКУПКИ =======================

@Composable
fun ShoppingTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val w = vm.work
    val lists = w.shopLists.filter { !it.archived }
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val cur = lists.firstOrNull { it.id == sel } ?: lists.firstOrNull()
    var input by remember { mutableStateOf("") }
    var newList by remember { mutableStateOf(false) }
    var priceFor by remember { mutableStateOf<ShopItem?>(null) }
    if (lists.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            vm.updateWork { it.copy(shopLists = listOf(ShopList(newId(), "Продукты", "🛒"), ShopList(newId(), "Аптека", "💊"), ShopList(newId(), "Для дома", "🧴"))) }
        }
    }
    fun add() {
        val l = cur ?: return
        val items = Shop.parse(input).map { (n, q) -> ShopItem(newId(), l.id, n, q) }
        if (items.isNotEmpty()) vm.updateWork { it.copy(shopItems = it.shopItems + items) }
        input = ""
    }
    ModuleScaffold(
        "Покупки", snackbar,
        actions = {
            if (cur != null) IconButton(onClick = {
                val t = Shop.shareText(cur, w.shopItems.filter { it.listId == cur.id })
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Отправить список"))
            }) { Icon(Icons.Filled.Share, "Поделиться") }
        },
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lists.forEach { l ->
                    val left = w.shopItems.count { it.listId == l.id && !it.done }
                    FilterChip(selected = cur?.id == l.id, onClick = { sel = l.id }, label = { Text("${l.emoji} ${l.name}" + if (left > 0) " · $left" else "") })
                }
                FilterChip(selected = false, onClick = { newList = true }, label = { Text("+ список") })
            }
        }
        if (cur != null) {
            item {
                OutlinedTextField(
                    input, { input = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("молоко 2, хлеб, яйца 10 шт") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { if (input.isNotBlank()) IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, "Добавить") } },
                )
            }
            val all = w.shopItems.filter { it.listId == cur.id }
            // Не купленное — по отделам магазина в порядке обхода, купленное — внизу
            val open = all.filter { !it.done }.sortedWith(compareBy({ Shop.aisleOrder(Shop.aisle(it.name)) }, { -it.created }))
            val items = open + all.filter { it.done }.sortedByDescending { it.created }
            val byAisle = open.size >= 5
            if (all.isEmpty()) item { Hint("Пишите через запятую — каждая позиция добавится отдельно. Список сам разложится по отделам магазина. Долгое нажатие на товар — цена и магазин: приложение подскажет, где дешевле.") }
            val total = open.mapNotNull { it.price }.sum()
            if (total > 0) item { Hint("Примерно на ${SplitMath.money(total)} по известным ценам") }
            items(items, key = { it.id }) { i -> Column {
                if (byAisle && !i.done) {
                    val a = Shop.aisle(i.name)
                    val idx = open.indexOf(i)
                    if (idx == 0 || Shop.aisle(open[idx - 1].name) != a) Text(a, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(
                            onClick = { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } },
                            onLongClick = { priceFor = i },
                        )
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(i.done, { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } })
                    Text(
                        i.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (i.done) TextDecoration.LineThrough else null,
                        color = if (i.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        if (i.qty.isNotBlank() || i.price != null) Text(listOfNotNull(i.qty.takeIf { it.isNotBlank() }, i.price?.let { SplitMath.money(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!i.done) Shop.cheaper(w.shopPrices, i.name, i.store, i.price)?.let { c ->
                            Text("дешевле: ${c.store} ${SplitMath.money(c.price)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3F8A5A))
                        }
                    }
                }
            } }
            if (items.any { it.done }) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.filter { !(it.listId == cur.id && it.done) }) } }) { Text("Убрать купленное") }
                        TextButton(onClick = { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.map { if (it.listId == cur.id) it.copy(done = false) else it }) } }) { Text("Купить снова") }
                    }
                }
            }
        }
    }
    priceFor?.let { i ->
        var qty by remember { mutableStateOf(i.qty) }
        var price by remember { mutableStateOf(i.price?.let { SplitMath.plain(it) } ?: "") }
        val stores = w.shopPrices.map { it.store }.distinct().take(8)
        var store by remember { mutableStateOf(i.store.ifBlank { stores.firstOrNull().orEmpty() }) }
        AlertDialog(
            onDismissRequest = { priceFor = null },
            title = { Text(i.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(qty, { qty = it }, label = { Text("Количество") }, singleLine = true)
                    OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("Цена, ₽") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(store, { store = it }, label = { Text("Магазин") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (stores + listOf("Пятёрочка", "Магнит", "Перекрёсток", "Лента", "Ашан", "ВкусВилл")).distinct().take(10).forEach { s -> FilterChip(store == s, { store = s }, label = { Text(s) }) }
                    }
                    val hist = w.shopPrices.filter { it.name == i.name.trim().lowercase() }.sortedByDescending { it.date }.take(5)
                    if (hist.isNotEmpty()) Hint("Раньше: " + hist.joinToString { "${it.store} ${SplitMath.money(it.price)}" })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = price.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) }
                    vm.updateWork { w0 ->
                        w0.copy(
                            shopItems = w0.shopItems.map { if (it.id == i.id) it.copy(qty = qty.trim(), price = p, store = store.trim()) else it },
                            shopPrices = if (p != null && store.isNotBlank()) (w0.shopPrices + ShopPrice(i.name.trim().lowercase(), store.trim(), p, vm.today.toString())).takeLast(1000) else w0.shopPrices,
                        )
                    }
                    priceFor = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { priceFor = null }) { Text("Отмена") } },
        )
    }
    if (newList) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("🛒") }
        AlertDialog(
            onDismissRequest = { newList = false },
            title = { Text("Новый список") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🛒", "💊", "🧴", "🎁", "🏠", "👕", "🐾", "🧒", "🔧", "🌱").forEach { e ->
                            FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                        }
                    }
                    if (cur != null) TextButton(onClick = {
                        vm.updateWork { w0 -> w0.copy(shopLists = w0.shopLists.map { if (it.id == cur.id) it.copy(archived = true) else it }) }
                        newList = false
                    }) { Text("Удалить список «${cur.name}»", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val l = ShopList(newId(), name.trim(), emoji)
                    vm.updateWork { it.copy(shopLists = it.shopLists + l) }
                    sel = l.id
                    newList = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { newList = false }) { Text("Отмена") } },
        )
    }
}

// ======================= ЦЕЛИ =======================

@Composable
fun GoalsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var year by rememberSaveable { mutableStateOf(vm.today.year) }
    var edit by remember { mutableStateOf<LifeGoal?>(null) }
    var review by remember { mutableStateOf(false) }
    val goals = vm.work.lifeGoals.filter { it.year == year }
    ModuleScaffold("Цели", snackbar, fab = "Цель" to { edit = LifeGoal(newId(), "", year = year) }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                Text("$year", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            }
        }
        if (goals.isNotEmpty()) {
            item {
                val avg = goals.map { it.progressIn(vm.work, vm.today) }.average().toFloat()
                Flat {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Прогресс года", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(avg * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
                        Text("Выполнено целей: ${goals.count { it.done }} из ${goals.size}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Bar(avg, moduleColor(Tab.GOALS))
                    }
                }
            }
            item {
                val ym = vm.today.toString().take(7)
                val done = goals.count { ym in it.reviews }
                Flat(onClick = { review = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📝 Разбор месяца", style = MaterialTheme.typography.titleSmall)
                            Hint(if (done == goals.count { !it.done }) "Готово за ${Fmt.monthName(vm.today.monthValue).lowercase()}" else "5 минут: что сделано по каждой цели и какой следующий шаг")
                        }
                        Text("$done/${goals.count { !it.done }}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            item { Hint("Поставьте 3–7 целей на год по разным сферам: здоровье, финансы, семья, карьера. Разбейте каждую на шаги — прогресс посчитается сам.") }
        }
        goals.groupBy { it.area }.forEach { (area, list) ->
            item { Text((GOAL_AREAS.firstOrNull { it.first == area }?.second ?: "🎯") + " " + area, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            items(list, key = { it.id }) { g ->
                Flat(onClick = { edit = g }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${g.emoji} ${g.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                                textDecoration = if (g.done) TextDecoration.LineThrough else null)
                            Text("${(g.progressIn(vm.work, vm.today) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Bar(g.progressIn(vm.work, vm.today), moduleColor(Tab.GOALS))
                        app.calstats.work.GoalAuto.progress(g, vm.work, vm.today)?.let {
                            Text("🔗 " + it.label + if (it.value >= 1f && !g.done) " · 🎉 цель достигнута!" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        g.steps.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.updateWork { w -> w.copy(lifeGoals = w.lifeGoals.map { x -> if (x.id == g.id) x.copy(steps = x.steps.map { if (it.id == s.id) it.copy(done = !it.done) else it }) else x }) }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (s.done) "☑" else "☐", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, textDecoration = if (s.done) TextDecoration.LineThrough else null)
                            }
                        }
                        g.deadline?.let { Text("до ${Fmt.date(LocalDate.parse(it))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    if (review) {
        val ym = vm.today.toString().take(7)
        val active = goals.filter { !it.done }
        val texts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { active.forEach { put(it.id, it.reviews[ym].orEmpty()) } } }
        AlertDialog(
            onDismissRequest = { review = false },
            title = { Text("Разбор: ${Fmt.monthName(vm.today.monthValue).lowercase()}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    active.forEach { g ->
                        Text("${g.emoji} ${g.title} · ${(g.progressIn(vm.work, vm.today) * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                        g.reviews.entries.filter { it.key != ym }.maxByOrNull { it.key }?.let { Hint("Прошлый раз: ${it.value}") }
                        OutlinedTextField(texts[g.id].orEmpty(), { texts[g.id] = it }, Modifier.fillMaxWidth(), placeholder = { Text("Что сделал · что дальше") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        TextButton(onClick = { vm.push(app.calstats.Screen.Edit(presetDate = vm.today.plusDays(1), presetTitle = "🎯 ${g.title}")) }) { Text("📅 Запланировать время на цель") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                vm.updateWork { w -> w.copy(lifeGoals = w.lifeGoals.map { g -> texts[g.id]?.takeIf { it.isNotBlank() }?.let { g.copy(reviews = g.reviews + (ym to it.trim())) } ?: g }) }
                review = false; vm.message = "Разбор сохранён"
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { review = false }) { Text("Отмена") } },
        )
    }
    edit?.let { g -> GoalDialog(vm, g) { edit = null } }
}

@Composable
internal fun Bar(f: Float, c: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c))
    }
}

@Composable
private fun GoalDialog(vm: MainViewModel, g: LifeGoal, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.lifeGoals.any { it.id == g.id }
    var title by remember { mutableStateOf(g.title) }
    var area by remember { mutableStateOf(g.area) }
    var why by remember { mutableStateOf(g.why) }
    var steps by remember { mutableStateOf(g.steps) }
    var step by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf(g.deadline?.let { LocalDate.parse(it) }) }
    var done by remember { mutableStateOf(g.done) }
    var link by remember { mutableStateOf(g.link) }
    var target by remember { mutableStateOf(g.target?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var linkMenu by remember { mutableStateOf(false) }
    val options = remember(vm.work) { app.calstats.work.GoalAuto.options(vm.work) }
    val opt = options.firstOrNull { it.link == link }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Цель" else "Новая цель") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Цель") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GOAL_AREAS.forEach { (a, e) -> FilterChip(selected = area == a, onClick = { area = a }, label = { Text("$e $a") }) }
                }
                OutlinedTextField(why, { why = it }, label = { Text("Зачем мне это") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Text("Прогресс из данных", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { linkMenu = true }) { Text(opt?.title ?: "🔗 Не привязана — считать по шагам", maxLines = 1) }
                    DropdownMenu(linkMenu, { linkMenu = false }) {
                        DropdownMenuItem(text = { Text("Не привязывать — по шагам") }, onClick = { link = null; linkMenu = false })
                        options.forEach { o -> DropdownMenuItem(text = { Text(o.title) }, onClick = { link = o.link; linkMenu = false }) }
                    }
                }
                opt?.needsTarget?.let { hint ->
                    OutlinedTextField(target, { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text(hint) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                Text("Шаги", style = MaterialTheme.typography.labelLarge)
                steps.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${s.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { steps = steps - s }) { Text("✕") }
                    }
                }
                OutlinedTextField(
                    step, { step = it }, label = { Text("+ шаг") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (step.isNotBlank()) { steps = steps + GoalStep(newId(), step.trim()); step = "" } }),
                    trailingIcon = { if (step.isNotBlank()) IconButton(onClick = { steps = steps + GoalStep(newId(), step.trim()); step = "" }) { Icon(Icons.Filled.Add, null) } },
                )
                OutlinedButton(onClick = { pickDate(ctx, deadline ?: LocalDate.of(g.year, 12, 31)) { deadline = it } }) { Text(deadline?.let { "Срок: ${Fmt.date(it)}" } ?: "Срок") }
                if (exists) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(done, { done = it }); Text("Цель достигнута") }
                    TextButton(onClick = { vm.updateWork { w -> w.copy(lifeGoals = w.lifeGoals.filter { it.id != g.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val ng = g.copy(title = title.trim(), area = area, why = why.trim(), emoji = GOAL_AREAS.firstOrNull { it.first == area }?.second ?: "🎯",
                    steps = steps + listOfNotNull(step.takeIf { it.isNotBlank() }?.let { GoalStep(newId(), it.trim()) }), deadline = deadline?.toString(), done = done,
                    link = link, target = if (opt?.needsTarget != null) target.replace(',', '.').toDoubleOrNull() else null,
                    start = if (link == g.link && g.start != null) g.start else link?.let { app.calstats.work.GoalAuto.startValue(it, vm.work, vm.today) })
                vm.updateWork { w -> w.copy(lifeGoals = if (exists) w.lifeGoals.map { if (it.id == g.id) ng else it } else w.lifeGoals + ng) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ДНЕВНИК =======================

@Composable
fun JournalTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var day by rememberSaveable { mutableStateOf(vm.today.toString()) }
    val d = LocalDate.parse(day)
    val e = vm.work.journal.firstOrNull { it.date == day }
    var text by remember(day, e?.updated) { mutableStateOf(e?.text.orEmpty()) }
    var g by remember(day, e?.updated) { mutableStateOf((e?.gratitude.orEmpty() + listOf("", "", "")).take(3)) }
    var best by remember(day, e?.updated) { mutableStateOf(e?.best.orEmpty()) }
    val changed = text != e?.text.orEmpty() || g.filter { it.isNotBlank() } != e?.gratitude.orEmpty() || best != e?.best.orEmpty()
    var promptShift by remember(day) { mutableStateOf(0) }
    fun save() = vm.updateWork { w ->
        val old = w.journal.firstOrNull { it.date == day }
        val ne = JournalEntry(day, text.trim(), g.map { it.trim() }.filter { it.isNotEmpty() }, best.trim(), photos = old?.photos.orEmpty(), tags = old?.tags.orEmpty())
        w.copy(journal = w.journal.filter { it.date != day } + ne)
    }
    fun updEntry(f: (JournalEntry) -> JournalEntry) = vm.updateWork { w ->
        val old = w.journal.firstOrNull { it.date == day } ?: JournalEntry(day)
        w.copy(journal = w.journal.filter { it.date != day } + f(old).copy(updated = System.currentTimeMillis()))
    }
    val pickPhoto = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        val names = uris.mapNotNull { u -> runCatching {
            val dir = java.io.File(ctx.filesDir, "journal").apply { mkdirs() }
            val f = java.io.File(dir, "$day-${newId()}.jpg")
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
            var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 1600) s *= 2
            val bmp = ctx.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching null
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            "journal/" + f.name
        }.getOrNull() }
        if (names.isNotEmpty()) { if (changed) save(); updEntry { it.copy(photos = it.photos + names) } }
    }
    val streak = Journal.streak(vm.work.journal, vm.today)
    ModuleScaffold("Дневник", snackbar) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (changed) save(); day = d.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                Text(
                    "📅 " + Fmt.dayTitle(d, vm.today),
                    Modifier.weight(1f).clickable { pickDate(ctx, d) { if (changed) save(); day = it.toString() } },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { if (changed) save(); day = d.plusDays(1).toString() }, enabled = d < vm.today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            }
        }
        if (streak > 0) item { Hint("✍ Записи $streak ${Fmt.plural(streak.toLong(), "день", "дня", "дней")} подряд") }
        item {
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Journal.PROMPTS[(d.dayOfYear + promptShift) % Journal.PROMPTS.size], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = moduleColor(Tab.JOURNAL))
                        TextButton(onClick = { promptShift++ }) { Text("🎲") }
                    }
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5, placeholder = { Text("Что было сегодня…") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
        }
        item {
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🙏 Три благодарности", style = MaterialTheme.typography.titleSmall)
                    g.forEachIndexed { i, s ->
                        OutlinedTextField(s, { v -> g = g.toMutableList().also { it[i] = v } }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("${i + 1}.") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    }
                    Text("✨ Лучший момент дня", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(best, { best = it }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
        }
        // Метки и фото дня
        item {
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Метки дня", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (Journal.TAGS + vm.work.journal.flatMap { it.tags }).distinct().forEach { t ->
                            val on = t in e?.tags.orEmpty()
                            FilterChip(on, { if (changed) save(); updEntry { x -> x.copy(tags = if (on) x.tags - t else x.tags + t) } }, label = { Text(t) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📷 Фото дня", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("+ фото") }
                    }
                    if (e?.photos.orEmpty().isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        e!!.photos.forEach { p -> JournalPhoto(p, Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))) { updEntry { x -> x.copy(photos = x.photos - p) }; java.io.File(ctx.filesDir, p).delete() } }
                    }
                }
            }
        }
        if (changed) item { Button(onClick = { save(); vm.message = "Сохранено" }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") } }
        // Год назад и месяц назад в этот день
        listOf(1L to "Год назад", 0L to "Месяц назад").forEach { (yrs, label) ->
            val ago = if (yrs == 1L) d.minusYears(1) else d.minusMonths(1)
            vm.work.journal.firstOrNull { it.date == ago.toString() && (it.text.isNotBlank() || it.best.isNotBlank() || it.photos.isNotEmpty()) }?.let { old ->
                item(key = "ago$label") {
                    Flat(onClick = { if (changed) save(); day = old.date }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("🕰 $label · ${Fmt.date(ago)}", style = MaterialTheme.typography.labelLarge, color = moduleColor(Tab.JOURNAL))
                            Text(old.best.ifBlank { old.text }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        val past = vm.work.journal.filter { it.date != day && (it.text.isNotBlank() || it.gratitude.isNotEmpty()) }.sortedByDescending { it.date }.take(20)
        if (past.isNotEmpty()) item { Text("Прошлые записи", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(past, key = { it.date }) { p ->
            Flat(onClick = { if (changed) save(); day = p.date }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(Fmt.dayTitle(LocalDate.parse(p.date), vm.today) + (if (p.tags.isNotEmpty()) " · " + p.tags.joinToString(", ") else "") + if (p.photos.isNotEmpty()) " · 📷${p.photos.size}" else "",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.text.ifBlank { "🙏 " + p.gratitude.joinToString(", ") }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ======================= АВТО =======================

@Composable
fun CarTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val car = w.cars.firstOrNull { it.id == sel } ?: w.cars.firstOrNull()
    var carDialog by remember { mutableStateOf<Car?>(null) }
    var event by remember { mutableStateOf<CarEvent?>(null) }
    ModuleScaffold("Авто", snackbar, fab = if (car != null) "Запись" to { event = CarEvent(newId(), car.id, CarEventType.FUEL, vm.today.toString(), km = CarMath.lastKm(w.carEvents, car.id)) } else null) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                w.cars.forEach { c -> FilterChip(selected = car?.id == c.id, onClick = { sel = c.id }, label = { Text("🚗 ${c.name}") }) }
                FilterChip(selected = false, onClick = { carDialog = Car(newId(), "") }, label = { Text("+ машина") })
            }
        }
        if (car == null) {
            item { Hint("Добавьте машину: приложение будет считать расход топлива, траты в месяц и напомнит о ТО по пробегу или дате.") }
            return@ModuleScaffold
        }
        val ev = w.carEvents.filter { it.carId == car.id }.sortedByDescending { it.date }
        item {
            Flat(onClick = { carDialog = car }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Пробег", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CarMath.lastKm(w.carEvents, car.id)?.let { "%,d км".format(it).replace(',', ' ') } ?: "—", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Расход", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CarMath.consumption(w.carEvents, car.id)?.let { "%.1f л/100".format(it) } ?: "—", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("За месяц", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(SplitMath.money(CarMath.monthCost(w.carEvents, car.id, YearMonth.from(vm.today))), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    CarMath.nextService(car, w.carEvents, vm.today)?.let { n ->
                        val warn = (n.kmLeft ?: Int.MAX_VALUE) < 1000 || (n.daysLeft ?: Long.MAX_VALUE) < 30
                        Text(
                            "🔧 Следующее ТО: " + listOfNotNull(n.km?.let { "на ${it} км" + (n.kmLeft?.let { l -> " (осталось $l)" } ?: "") }, n.date?.let { "до ${Fmt.date(it)}" }).joinToString(" или "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    } ?: Hint("Запишите последнее ТО — напомню о следующем.")
                    car.osagoUntil?.let { LocalDate.parse(it) }?.let { d ->
                        val left = java.time.temporal.ChronoUnit.DAYS.between(vm.today, d)
                        Text("🛡 ОСАГО до ${Fmt.date(d)}" + if (left in 0..30) " — осталось $left дн." else if (left < 0) " — просрочено!" else "",
                            style = MaterialTheme.typography.bodySmall, color = if (left <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    car.kaskoUntil?.let { LocalDate.parse(it) }?.let { d -> Text("🛡 КАСКО до ${Fmt.date(d)}", style = MaterialTheme.typography.bodySmall) }
                    app.calstats.work.CarHints.tires(w.carEvents, car.id, vm.today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = moduleColor(Tab.CAR)) }
                    w.carEvents.filter { it.carId == car.id && it.type == CarEventType.FINE && it.unpaid }.forEach { f ->
                        val until = app.calstats.work.CarHints.discountUntil(f)
                        val left = java.time.temporal.ChronoUnit.DAYS.between(vm.today, until)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚓 Штраф ${SplitMath.money(f.cost)}" + if (left >= 0) " — со скидкой 50% (${SplitMath.money(f.cost / 2)}) до ${Fmt.dateNoYear(until)}" else " — не оплачен",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { vm.updateWork { w0 -> w0.copy(carEvents = w0.carEvents.map { if (it.id == f.id) it.copy(unpaid = false) else it }) } }) { Text("Оплачен") }
                        }
                    }
                    val st = app.calstats.work.CarHints.stations(w.carEvents, car.id)
                    if (st.size >= 2) Text("⛽ Дешевле всего: " + st.take(3).joinToString { "${it.first} ${"%.2f".format(it.second).replace('.', ',')} ₽/л" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(ev, key = { it.id }) { e ->
            Flat(onClick = { event = e }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.type.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.type.title + (e.liters?.let { " · %.1f л".format(it) } ?: "") + if (e.note.isNotBlank()) " · ${e.note}" else "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.date(LocalDate.parse(e.date)) + (e.km?.let { " · $it км" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.cost > 0) Text(SplitMath.money(e.cost), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    carDialog?.let { c ->
        var name by remember { mutableStateOf(c.name) }
        var plate by remember { mutableStateOf(c.plate) }
        var km by remember { mutableStateOf(c.serviceKm.toString()) }
        var months by remember { mutableStateOf(c.serviceMonths.toString()) }
        var osago by remember { mutableStateOf(c.osagoUntil?.let { LocalDate.parse(it) }) }
        var kasko by remember { mutableStateOf(c.kaskoUntil?.let { LocalDate.parse(it) }) }
        val cctx = LocalContext.current
        val exists = w.cars.any { it.id == c.id }
        AlertDialog(
            onDismissRequest = { carDialog = null },
            title = { Text(if (exists) "Машина" else "Новая машина") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Марка и модель") }, singleLine = true)
                    OutlinedTextField(plate, { plate = it.uppercase() }, label = { Text("Госномер") }, singleLine = true)
                    Text("ТО каждые", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(km, { km = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("км") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(months, { months = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("месяцев") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickDate(cctx, osago ?: vm.today.plusYears(1)) { osago = it } }) { Text("ОСАГО " + (osago?.let { "до " + Fmt.dateNoYear(it) } ?: "—")) }
                        OutlinedButton(onClick = { pickDate(cctx, kasko ?: vm.today.plusYears(1)) { kasko = it } }) { Text("КАСКО " + (kasko?.let { "до " + Fmt.dateNoYear(it) } ?: "—")) }
                    }
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(cars = w0.cars.filter { it.id != c.id }, carEvents = w0.carEvents.filter { it.carId != c.id }) }; carDialog = null }) {
                        Text("Удалить машину и записи", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val nc = c.copy(name = name.trim(), plate = plate.trim(), serviceKm = km.toIntOrNull() ?: 10_000, serviceMonths = months.toIntOrNull() ?: 12,
                        osagoUntil = osago?.toString(), kaskoUntil = kasko?.toString())
                    vm.updateWork { w0 -> w0.copy(cars = if (exists) w0.cars.map { if (it.id == c.id) nc else it } else w0.cars + nc) }
                    sel = nc.id
                    carDialog = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { carDialog = null }) { Text("Отмена") } },
        )
    }
    event?.let { e ->
        val ctx = LocalContext.current
        val exists = w.carEvents.any { it.id == e.id }
        var type by remember { mutableStateOf(e.type) }
        var date by remember { mutableStateOf(LocalDate.parse(e.date)) }
        var km by remember { mutableStateOf(e.km?.toString().orEmpty()) }
        var cost by remember { mutableStateOf(if (e.cost > 0) (e.cost / 100).toString() else "") }
        var liters by remember { mutableStateOf(e.liters?.toString().orEmpty()) }
        var note by remember { mutableStateOf(e.note) }
        var toFinance by remember { mutableStateOf(!exists) }
        var unpaid by remember { mutableStateOf(e.unpaid) }
        AlertDialog(
            onDismissRequest = { event = null },
            title = { Text(if (exists) "Запись" else "Новая запись") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CarEventType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.emoji} ${t.title}") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(cost, { cost = it }, Modifier.weight(1f), label = { Text("Сумма, ₽") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(km, { km = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Пробег, км") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (type == CarEventType.FUEL) OutlinedTextField(liters, { liters = it }, label = { Text("Литров") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(note, { note = it }, label = { Text(if (type == CarEventType.FUEL) "АЗС (Лукойл, Газпром…)" else "Комментарий") }, singleLine = true)
                    if (type == CarEventType.FINE) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(unpaid, { unpaid = it }); Text("Ещё не оплачен — напомнить о скидке 50%") }
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.date(date)) }
                    if (!exists && vm.isEnabled(Tab.FINANCE)) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(toFinance, { toFinance = it }); Text("Записать расход в «Финансы»") }
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(carEvents = w0.carEvents.filter { it.id != e.id }) }; event = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ne = e.copy(type = type, date = date.toString(), km = km.toIntOrNull(), cost = BankImport.parseAmount(cost) ?: 0,
                        liters = liters.replace(',', '.').toDoubleOrNull(), note = note.trim(), unpaid = type == CarEventType.FINE && unpaid)
                    vm.updateWork { w0 ->
                        var out = w0.copy(carEvents = if (exists) w0.carEvents.map { if (it.id == e.id) ne else it } else w0.carEvents + ne)
                        if (toFinance && !exists && ne.cost > 0) {
                            val acc = out.accounts.firstOrNull { !it.archived }
                            val cat = out.finCategories.firstOrNull { !it.income && (it.name.contains("Транспорт") || it.name.contains("Авто")) }
                            if (acc != null) out = out.copy(transactions = out.transactions + app.calstats.work.Transaction(newId(), app.calstats.work.TxType.EXPENSE, ne.cost, acc.id,
                                categoryId = cat?.id, date = ne.date, note = "${type.title}" + (ne.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""), source = "Авто"))
                        }
                        out
                    }
                    event = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { event = null }) { Text("Отмена") } },
        )
    }
}

// ======================= ДОМ =======================

@Composable
fun HomeUtilTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var reading by remember { mutableStateOf<Meter?>(null) }
    var addMeter by remember { mutableStateOf(false) }
    ModuleScaffold("Дом", snackbar, fab = "Счётчик" to { addMeter = true }) {
        item {
            Hint("Передавайте показания в одном месте: приложение покажет расход за месяц" + if (w.meters.any { it.tariff > 0 }) " и сумму к оплате." else ". Укажите тариф — посчитает и сумму.")
        }
        if (w.meters.isEmpty()) {
            item {
                OutlinedButton(onClick = { vm.updateWork { it.copy(meters = METER_TEMPLATES.take(3).map { m -> m.copy(id = newId()) }) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить воду и электричество")
                }
            }
        }
        if (w.meters.isNotEmpty()) item {
            val hctx = LocalContext.current
            val sp = remember { hctx.getSharedPreferences("home_util", android.content.Context.MODE_PRIVATE) }
            var addr by remember { mutableStateOf(sp.getString("addr", "").orEmpty()) }
            var account by remember { mutableStateOf(sp.getString("acc", "").orEmpty()) }
            var open by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (addr.isBlank()) { open = true; return@Button }
                    val lines = w.meters.mapNotNull { m ->
                        w.meterReadings.filter { it.meterId == m.id }.maxByOrNull { it.date }?.let { r ->
                            "${m.name}" + (if (m.serial.isNotBlank()) " (№ ${m.serial})" else "") + ": " + "%.3f".format(r.value).trimEnd('0').trimEnd(',', '.') + " ${m.unit}"
                        }
                    }
                    val text = "Показания счётчиков на ${Fmt.date(vm.today)}\nАдрес: $addr" + (if (account.isNotBlank()) "\nЛицевой счёт: $account" else "") + "\n\n" + lines.joinToString("\n")
                    hctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Показания счётчиков, $addr").putExtra(Intent.EXTRA_TEXT, text), "Отправить в УК"))
                }) { Text("📤 Отправить показания в УК") }
                TextButton(onClick = { open = true }) { Text("Адрес") }
            }
            if (open) AlertDialog(
                onDismissRequest = { open = false }, title = { Text("Для письма в УК") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(addr, { addr = it }, label = { Text("Адрес, квартира") }, singleLine = true)
                    OutlinedTextField(account, { account = it }, label = { Text("Лицевой счёт (необязательно)") }, singleLine = true)
                } },
                confirmButton = { TextButton(onClick = { sp.edit().putString("addr", addr.trim()).putString("acc", account.trim()).apply(); open = false }) { Text("Сохранить") } },
            )
        }
        items(w.meters, key = { it.id }) { m ->
            val rs = w.meterReadings.filter { it.meterId == m.id }.sortedByDescending { it.date }
            val use = Meters.lastUse(w.meterReadings, m.id)
            Flat(onClick = { reading = m }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.emoji} ${m.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(rs.firstOrNull()?.let { "%.2f".format(it.value).trimEnd('0').trimEnd(',', '.') + " ${m.unit}" } ?: "нет показаний", style = MaterialTheme.typography.labelLarge)
                    }
                    rs.firstOrNull()?.let { Text("Последнее: ${Fmt.date(LocalDate.parse(it.date))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    use?.let {
                        Text(
                            "Расход: %.2f %s".format(it, m.unit) + if (m.tariff > 0) " · ${SplitMath.money((it * m.tariff).toLong())}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val thisMonth = rs.firstOrNull()?.let { YearMonth.from(LocalDate.parse(it.date)) == YearMonth.from(vm.today) } == true
                    if (!thisMonth && vm.today.dayOfMonth >= 15) Text("Пора передать показания за ${Fmt.monthName(vm.today.monthValue).lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    m.verifyUntil?.let { LocalDate.parse(it) }?.let { d ->
                        val left = java.time.temporal.ChronoUnit.DAYS.between(vm.today, d)
                        Text("🔍 Поверка до ${Fmt.date(d)}" + if (left in 0..60) " — осталось $left дн." else if (left < 0) " — просрочена" else "",
                            style = MaterialTheme.typography.bodySmall, color = if (left <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    reading?.let { m ->
        var v by remember { mutableStateOf("") }
        var tariff by remember { mutableStateOf(if (m.tariff > 0) (m.tariff / 100.0).toString() else "") }
        var serial by remember { mutableStateOf(m.serial) }
        var verify by remember { mutableStateOf(m.verifyUntil?.let { LocalDate.parse(it) }) }
        val mctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { reading = null },
            title = { Text("${m.emoji} ${m.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(v, { v = it }, label = { Text("Показание, ${m.unit}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(tariff, { tariff = it }, label = { Text("Тариф, ₽ за ${m.unit}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(serial, { serial = it }, label = { Text("Номер счётчика") }, singleLine = true)
                    OutlinedButton(onClick = { pickDate(mctx, verify ?: vm.today.plusYears(4)) { verify = it } }) { Text("Поверка до: " + (verify?.let { Fmt.date(it) } ?: "не указано")) }
                    w.meterReadings.filter { it.meterId == m.id }.sortedByDescending { it.date }.take(6).forEach { r ->
                        Text("${Fmt.date(LocalDate.parse(r.date))} — ${r.value}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.updateWork { w0 -> w0.copy(meters = w0.meters.filter { it.id != m.id }, meterReadings = w0.meterReadings.filter { it.meterId != m.id }) }; reading = null }) {
                        Text("Удалить счётчик", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = v.replace(',', '.').toDoubleOrNull()
                    val t = BankImport.parseAmount(tariff) ?: 0
                    vm.updateWork { w0 ->
                        w0.copy(
                            meters = w0.meters.map { if (it.id == m.id) it.copy(tariff = t, serial = serial.trim(), verifyUntil = verify?.toString()) else it },
                            meterReadings = if (value != null) w0.meterReadings.filter { !(it.meterId == m.id && it.date == vm.today.toString()) } + MeterReading(m.id, vm.today.toString(), value) else w0.meterReadings,
                        )
                    }
                    reading = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { reading = null }) { Text("Отмена") } },
        )
    }
    if (addMeter) {
        AlertDialog(
            onDismissRequest = { addMeter = false },
            title = { Text("Какой счётчик") },
            text = {
                Column {
                    METER_TEMPLATES.forEach { t ->
                        Text("${t.emoji} ${t.name}", Modifier.fillMaxWidth().clickable {
                            vm.updateWork { it.copy(meters = it.meters + t.copy(id = newId())) }
                            addMeter = false
                        }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addMeter = false }) { Text("Закрыть") } },
        )
    }
}

// ======================= ПИТОМЦЫ =======================

@Composable
fun PetsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var petDialog by remember { mutableStateOf<Pet?>(null) }
    var event by remember { mutableStateOf<PetEvent?>(null) }
    var foodFor by remember { mutableStateOf<Pet?>(null) }
    val upcoming = Pets.upcoming(w.petEvents, vm.today, 30)
    ModuleScaffold("Питомцы", snackbar, fab = "Питомец" to { petDialog = Pet(newId(), "") }) {
        if (w.pets.isEmpty()) item { Hint("Добавьте питомца — напомню о прививках, обработке от паразитов и визитах к ветеринару.") }
        if (upcoming.isNotEmpty()) {
            item {
                Flat {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Скоро", style = MaterialTheme.typography.titleSmall)
                        upcoming.forEach { e ->
                            val p = w.pets.firstOrNull { it.id == e.petId }
                            val d = ChronoUnit.DAYS.between(vm.today, LocalDate.parse(e.next))
                            Text(
                                "${e.type.emoji} ${p?.name ?: ""}: ${e.type.title.lowercase()} — " + when { d < 0 -> "просрочено на ${-d} дн."; d == 0L -> "сегодня"; else -> "через ${Fmt.days(d)}" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        items(w.pets, key = { it.id }) { p ->
            val ev = w.petEvents.filter { it.petId == p.id }.sortedByDescending { it.date }
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.clickable { petDialog = p }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(moduleColor(Tab.PETS).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(p.kind, fontSize = 24.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(p.breed.takeIf { it.isNotBlank() }, Pets.ageText(p.birth, vm.today),
                                ev.firstOrNull { it.weight != null }?.weight?.let { "%.1f кг".format(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Вес на графике
                    val weights = w.petEvents.filter { it.petId == p.id && it.weight != null }.sortedBy { it.date }
                    if (weights.size >= 2) {
                        val c = moduleColor(Tab.PETS)
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                            val ws = weights.map { it.weight!! }
                            val lo = ws.min() - 0.5; val hi = ws.max() + 0.5
                            val step = size.width / (ws.size - 1)
                            for (k in 1 until ws.size) drawLine(c, androidx.compose.ui.geometry.Offset(step * (k - 1), (size.height * (1 - (ws[k - 1] - lo) / (hi - lo))).toFloat()),
                                androidx.compose.ui.geometry.Offset(step * k, (size.height * (1 - (ws[k] - lo) / (hi - lo))).toFloat()), 5f)
                        }
                        Text("Вес: " + weights.takeLast(4).joinToString(" → ") { "%.1f".format(it.weight) } + " кг", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    p.foodDaysLeft(vm.today)?.let { left ->
                        Text("🥫 Корма хватит на ${maxOf(0, left)} дн." + if (left <= 5) " — пора купить" else "", style = MaterialTheme.typography.bodySmall,
                            color = if (left <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    ev.take(4).forEach { e ->
                        Text("${e.type.emoji} ${Fmt.dateNoYear(LocalDate.parse(e.date))} · ${e.type.title}" + (e.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") +
                            (e.next?.let { " → след. ${Fmt.dateNoYear(LocalDate.parse(it))}" } ?: ""),
                            Modifier.clickable { event = e }, style = MaterialTheme.typography.bodySmall)
                    }
                    val pctx = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { event = PetEvent(newId(), p.id, PetEventType.VACCINE, vm.today.toString()) }) { Text("+ Событие") }
                        OutlinedButton(onClick = { foodFor = p }) { Text("🥫") }
                        OutlinedButton(onClick = { runCatching { petPassport(pctx, w, p) }.onFailure { vm.message = "Не удалось собрать PDF" } }) { Text("📄 PDF") }
                    }
                }
            }
        }
    }
    foodFor?.let { p ->
        var bag by remember { mutableStateOf(p.foodBagKg?.toString() ?: "") }
        var per by remember { mutableStateOf(p.foodPerDayG?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { foodFor = null }, title = { Text("Корм: ${p.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Укажите вес упаковки и сколько съедает в день — напомню купить заранее.")
                OutlinedTextField(bag, { bag = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("Упаковка, кг") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(per, { per = it.filter(Char::isDigit) }, label = { Text("В день, г") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            } },
            confirmButton = { TextButton(onClick = {
                vm.updateWork { w0 -> w0.copy(pets = w0.pets.map { if (it.id == p.id) it.copy(foodBagKg = bag.replace(',', '.').toDoubleOrNull(), foodPerDayG = per.toIntOrNull(), foodOpened = vm.today.toString()) else it }) }
                vm.message = "Открыта новая упаковка — посчитаю, когда закончится"; foodFor = null
            }) { Text("Открыл новую упаковку") } },
            dismissButton = { TextButton(onClick = { foodFor = null }) { Text("Отмена") } },
        )
    }
    petDialog?.let { p ->
        val ctx = LocalContext.current
        val exists = w.pets.any { it.id == p.id }
        var name by remember { mutableStateOf(p.name) }
        var kind by remember { mutableStateOf(p.kind) }
        var breed by remember { mutableStateOf(p.breed) }
        var birth by remember { mutableStateOf(p.birth?.let { LocalDate.parse(it) }) }
        var chip by remember { mutableStateOf(p.chip) }
        AlertDialog(
            onDismissRequest = { petDialog = null },
            title = { Text(if (exists) "Питомец" else "Новый питомец") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🐶", "🐱", "🐰", "🦜", "🐹", "🐢", "🐠").forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) }) }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Кличка") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(breed, { breed = it }, label = { Text("Порода") }, singleLine = true)
                    OutlinedTextField(chip, { chip = it }, label = { Text("Номер чипа") }, singleLine = true)
                    OutlinedButton(onClick = { pickDate(ctx, birth ?: vm.today.minusYears(1)) { birth = it } }) { Text(birth?.let { "Родился ${Fmt.date(it)}" } ?: "Дата рождения") }
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(pets = w0.pets.filter { it.id != p.id }, petEvents = w0.petEvents.filter { it.petId != p.id }) }; petDialog = null }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val np = p.copy(name = name.trim(), kind = kind, breed = breed.trim(), birth = birth?.toString(), chip = chip.trim())
                    vm.updateWork { w0 -> w0.copy(pets = if (exists) w0.pets.map { if (it.id == p.id) np else it } else w0.pets + np) }
                    petDialog = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { petDialog = null }) { Text("Отмена") } },
        )
    }
    event?.let { e ->
        val ctx = LocalContext.current
        val exists = w.petEvents.any { it.id == e.id }
        var type by remember { mutableStateOf(e.type) }
        var date by remember { mutableStateOf(LocalDate.parse(e.date)) }
        var next by remember { mutableStateOf(e.next?.let { LocalDate.parse(it) } ?: e.type.repeatMonths?.let { LocalDate.parse(e.date).plusMonths(it.toLong()) }) }
        var weight by remember { mutableStateOf(e.weight?.toString().orEmpty()) }
        var note by remember { mutableStateOf(e.note) }
        AlertDialog(
            onDismissRequest = { event = null },
            title = { Text("Событие") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PetEventType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t; next = t.repeatMonths?.let { date.plusMonths(it.toLong()) } }, label = { Text("${t.emoji} ${t.title}") })
                        }
                    }
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it; next = type.repeatMonths?.let { m -> it.plusMonths(m.toLong()) } ?: next } }) { Text("Дата: ${Fmt.date(date)}") }
                    if (type == PetEventType.WEIGHT) OutlinedTextField(weight, { weight = it }, label = { Text("Вес, кг") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(note, { note = it }, label = { Text("Препарат, клиника, комментарий") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate(ctx, next ?: date.plusMonths(1)) { next = it } }) { Text(next?.let { "Повторить ${Fmt.date(it)}" } ?: "Когда повторить") }
                        if (next != null) TextButton(onClick = { next = null }) { Text("✕") }
                    }
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(petEvents = w0.petEvents.filter { it.id != e.id }) }; event = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ne = e.copy(type = type, date = date.toString(), next = next?.toString(), weight = weight.replace(',', '.').toDoubleOrNull(), note = note.trim())
                    vm.updateWork { w0 -> w0.copy(petEvents = if (exists) w0.petEvents.map { if (it.id == e.id) ne else it } else w0.petEvents + ne) }
                    event = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { event = null }) { Text("Отмена") } },
        )
    }
}


@Composable
private fun JournalPhoto(path: String, modifier: Modifier, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(java.io.File(ctx.filesDir, path).path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).combinedClickable(onClick = {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", java.io.File(ctx.filesDir, path))
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    }, onLongClick = { ask = true })) {
        bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Удалить фото?") },
        confirmButton = { TextButton(onClick = { ask = false; onDelete() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Отмена") } })
}


/** Паспорт питомца в PDF: данные, прививки и обработки. */
private fun petPassport(ctx: android.content.Context, w: app.calstats.work.Workspace, p: Pet) {
    val doc = android.graphics.pdf.PdfDocument()
    val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val t = android.graphics.Paint().apply { textSize = 20f; isFakeBoldText = true; color = 0xFF2B2F35.toInt() }
    val h = android.graphics.Paint().apply { textSize = 13f; isFakeBoldText = true; color = 0xFF2B2F35.toInt() }
    val b = android.graphics.Paint().apply { textSize = 11f; color = 0xFF33373D.toInt() }
    var y = 60f
    fun line(s: String, pt: android.graphics.Paint = b, gap: Float = 16f) { if (y < 800) { c.drawText(s, 40f, y, pt); y += gap } }
    line("${p.kind} ${p.name}", t, 28f)
    line(listOfNotNull(p.breed.takeIf { it.isNotBlank() }, p.birth?.let { "родился ${Fmt.date(LocalDate.parse(it))}" }, p.chip.takeIf { it.isNotBlank() }?.let { "чип $it" }).joinToString(" · "))
    w.petEvents.filter { it.petId == p.id && it.weight != null }.maxByOrNull { it.date }?.let { line("Вес: %.1f кг (${Fmt.date(LocalDate.parse(it.date))})".format(it.weight)) }
    PetEventType.entries.forEach { type ->
        val l = w.petEvents.filter { it.petId == p.id && it.type == type }.sortedByDescending { it.date }
        if (l.isEmpty() || type == PetEventType.WEIGHT) return@forEach
        y += 8f; line(type.title, h, 18f)
        l.take(12).forEach { e -> line("  ${Fmt.date(LocalDate.parse(e.date))}" + (if (e.note.isNotBlank()) " — ${e.note}" else "") + (e.next?.let { "  · следующая ${Fmt.date(LocalDate.parse(it))}" } ?: "")) }
    }
    y += 10f; line("Подготовлено в «Зелёном ритме»", android.graphics.Paint(b).apply { color = 0xFF7A808B.toInt() })
    doc.finishPage(page)
    val dir = java.io.File(ctx.cacheDir, "reports").apply { mkdirs() }
    val f = java.io.File(dir, "pasport-${p.name}.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Паспорт питомца"))
}
