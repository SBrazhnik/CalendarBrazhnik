@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Intent
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
            val items = w.shopItems.filter { it.listId == cur.id }.sortedWith(compareBy({ it.done }, { -it.created }))
            if (items.isEmpty()) item { Hint("Пишите через запятую — каждая позиция добавится отдельно. Чем поделились из другого приложения, можно добавить сюда.") }
            items(items, key = { it.id }) { i ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(i.done, { vm.updateWork { w0 -> w0.copy(shopItems = w0.shopItems.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } })
                    Text(
                        i.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (i.done) TextDecoration.LineThrough else null,
                        color = if (i.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (i.qty.isNotBlank()) Text(i.qty, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
    fun save() = vm.updateWork { w ->
        val ne = JournalEntry(day, text.trim(), g.map { it.trim() }.filter { it.isNotEmpty() }, best.trim())
        w.copy(journal = w.journal.filter { it.date != day } + ne)
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
                    Text(Journal.prompt(d), style = MaterialTheme.typography.titleSmall, color = moduleColor(Tab.JOURNAL))
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
        if (changed) item { Button(onClick = { save(); vm.message = "Сохранено" }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") } }
        val past = vm.work.journal.filter { it.date != day && (it.text.isNotBlank() || it.gratitude.isNotEmpty()) }.sortedByDescending { it.date }.take(20)
        if (past.isNotEmpty()) item { Text("Прошлые записи", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(past, key = { it.date }) { p ->
            Flat(onClick = { if (changed) save(); day = p.date }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(Fmt.dayTitle(LocalDate.parse(p.date), vm.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(cars = w0.cars.filter { it.id != c.id }, carEvents = w0.carEvents.filter { it.carId != c.id }) }; carDialog = null }) {
                        Text("Удалить машину и записи", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val nc = c.copy(name = name.trim(), plate = plate.trim(), serviceKm = km.toIntOrNull() ?: 10_000, serviceMonths = months.toIntOrNull() ?: 12)
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
                    OutlinedTextField(note, { note = it }, label = { Text("Комментарий") }, singleLine = true)
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.date(date)) }
                    if (!exists && vm.isEnabled(Tab.FINANCE)) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(toFinance, { toFinance = it }); Text("Записать расход в «Финансы»") }
                    if (exists) TextButton(onClick = { vm.updateWork { w0 -> w0.copy(carEvents = w0.carEvents.filter { it.id != e.id }) }; event = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ne = e.copy(type = type, date = date.toString(), km = km.toIntOrNull(), cost = BankImport.parseAmount(cost) ?: 0,
                        liters = liters.replace(',', '.').toDoubleOrNull(), note = note.trim())
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
                }
            }
        }
    }
    reading?.let { m ->
        var v by remember { mutableStateOf("") }
        var tariff by remember { mutableStateOf(if (m.tariff > 0) (m.tariff / 100.0).toString() else "") }
        AlertDialog(
            onDismissRequest = { reading = null },
            title = { Text("${m.emoji} ${m.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(v, { v = it }, label = { Text("Показание, ${m.unit}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(tariff, { tariff = it }, label = { Text("Тариф, ₽ за ${m.unit}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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
                            meters = w0.meters.map { if (it.id == m.id) it.copy(tariff = t) else it },
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
                    ev.take(4).forEach { e ->
                        Text("${e.type.emoji} ${Fmt.dateNoYear(LocalDate.parse(e.date))} · ${e.type.title}" + (e.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") +
                            (e.next?.let { " → след. ${Fmt.dateNoYear(LocalDate.parse(it))}" } ?: ""),
                            Modifier.clickable { event = e }, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { event = PetEvent(newId(), p.id, PetEventType.VACCINE, vm.today.toString()) }) { Text("+ Событие") }
                }
            }
        }
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
