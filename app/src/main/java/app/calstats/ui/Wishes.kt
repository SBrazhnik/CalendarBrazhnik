@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.work.ME
import app.calstats.work.PALETTE
import app.calstats.work.SplitMath
import app.calstats.work.Wish
import app.calstats.work.WishList
import app.calstats.work.WishStatus
import app.calstats.work.WorkOps
import app.calstats.work.newId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val OCCASIONS = listOf("День рождения", "Новый год", "8 Марта", "23 Февраля", "Годовщина", "Свадьба", "Новоселье", "Просто так")
private val EMOJIS = listOf("🎁", "🎂", "🎄", "💐", "💍", "🏠", "✈", "🎮", "📚", "👗", "⭐", "❤")

fun wishStatusColor(s: WishStatus): Color = when (s) {
    WishStatus.WANTED -> Color(0xFF6B778C)
    WishStatus.RESERVED -> Color(0xFFE5A100)
    WishStatus.BOUGHT -> Color(0xFF0C66E4)
    WishStatus.RECEIVED -> Color(0xFF22A06B)
}

/** Модуль «Желания»: свои списки и подарки близким (в духе «Подаркуса»). */
@Composable
fun WishesTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val w = vm.work
    var editing by remember { mutableStateOf<WishList?>(null) }
    val lists = w.wishLists.filter { !it.archived }
    val mine = lists.filter { it.ownerId == ME }
    val others = lists.filter { it.ownerId != ME }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Желания", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = WishList(newId(), "", color = PALETTE[lists.size % PALETTE.size]) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Список") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lists.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Соберите желания в одном месте", style = MaterialTheme.typography.titleLarge)
                        Muted2(
                            "Свой список — чтобы отправить близким перед днём рождения. Список для другого человека — " +
                                "чтобы копить идеи подарков и отмечать, что уже куплено. Ссылку на товар можно прислать " +
                                "сюда из Ozon, WB или браузера через «Поделиться».",
                        )
                        Button(onClick = { editing = WishList(newId(), "Мои желания", occasion = "День рождения", yearly = true) }) {
                            Text("Создать мой список")
                        }
                    }
                }
            }
            if (mine.isNotEmpty()) item { SectionLabel("Мои желания") }
            items(mine, key = { it.id }) { l -> WishListCard(vm, l) }
            if (others.isNotEmpty()) item { SectionLabel("Подарки близким") }
            items(others, key = { it.id }) { l -> WishListCard(vm, l) }
            val archived = w.wishLists.filter { it.archived }
            if (archived.isNotEmpty()) {
                item { SectionLabel("Архив") }
                items(archived, key = { it.id }) { l -> WishListCard(vm, l) }
            }
        }
    }

    editing?.let { l -> WishListDialog(vm, l) { editing = null } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Muted2(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WishListCard(vm: MainViewModel, l: WishList) {
    val w = vm.work
    val items = w.wishes.filter { it.listId == l.id }
    val done = items.count { it.status == WishStatus.BOUGHT || it.status == WishStatus.RECEIVED }
    val sum = items.filter { it.status != WishStatus.RECEIVED }.sumOf { it.price ?: 0L }
    val next = nextOccurrence(l.localDate, vm.today, l.yearly)
    Card(onClick = { vm.push(Screen.WishListDetail(l.id)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(l.color).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(l.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    if (l.ownerId != ME) w.person(l.ownerId)?.let { add("для ${it.name}") }
                    if (l.occasion.isNotBlank()) add(l.occasion)
                    next?.let {
                        val d = ChronoUnit.DAYS.between(vm.today, it)
                        add(if (d == 0L) "сегодня" else "через ${Fmt.days(d)}")
                    }
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${items.size} ${Fmt.plural(items.size.toLong(), "желание", "желания", "желаний")}" +
                        (if (sum > 0) " · ${SplitMath.money(sum)}" else "") +
                        (if (done > 0) " · куплено $done" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (l.ownerId != ME) Avatar(w.person(l.ownerId), 28.dp)
        }
    }
}

@Composable
private fun WishListDialog(vm: MainViewModel, l: WishList, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val w = vm.work
    val exists = w.wishLists.any { it.id == l.id }
    var title by remember { mutableStateOf(l.title) }
    var owner by remember { mutableStateOf(l.ownerId) }
    var occasion by remember { mutableStateOf(l.occasion) }
    var date by remember { mutableStateOf(l.localDate) }
    var yearly by remember { mutableStateOf(l.yearly) }
    var emoji by remember { mutableStateOf(l.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Список" else "Новый список") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Название") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text("Чей список", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    w.everyone.forEach { p ->
                        FilterChip(
                            selected = owner == p.id,
                            onClick = {
                                owner = p.id
                                if (title.isBlank() && p.id != ME) title = "Подарки для ${p.name}"
                            },
                            label = { Text(if (p.id == ME) "Мой" else p.name) },
                            leadingIcon = { Avatar(p, 18.dp) },
                        )
                    }
                }
                TextButton(onClick = { vm.push(Screen.People) }) { Text("+ Добавить человека") }
                Text("Повод", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OCCASIONS.forEach { o ->
                        FilterChip(selected = occasion == o, onClick = {
                            occasion = if (occasion == o) "" else o
                            if (o in listOf("День рождения", "Новый год", "8 Марта", "23 Февраля", "Годовщина")) yearly = true
                            if (o == "Новый год" && date == null) date = LocalDate.of(vm.today.year, 12, 31)
                            if (o == "8 Марта" && date == null) date = nextOccurrence(LocalDate.of(vm.today.year, 3, 8), vm.today, true)
                            if (o == "23 Февраля" && date == null) date = nextOccurrence(LocalDate.of(vm.today.year, 2, 23), vm.today, true)
                        }, label = { Text(o) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, date ?: vm.today) { date = it } }) {
                        Text(date?.let { "Дата: " + Fmt.date(it) } ?: "Дата повода")
                    }
                    if (date != null) TextButton(onClick = { date = null }) { Text("Убрать") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = yearly, onCheckedChange = { yearly = it })
                    Text("Повторяется каждый год")
                }
                Text("Значок", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val list = l.copy(title = title.trim(), ownerId = owner, occasion = occasion, date = date?.toString(), yearly = yearly, emoji = emoji)
                vm.updateWork { WorkOps.upsertWishList(it, list) }
                onDismiss()
                if (!exists) vm.push(Screen.WishListDetail(list.id))
            }) { Text(if (exists) "Сохранить" else "Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Экран списка желаний. */
@Composable
fun WishListScreen(vm: MainViewModel, id: String, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val w = vm.work
    val list = w.wishLists.firstOrNull { it.id == id }
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = { vm.pop() }) { Text("Список удалён — назад") } }
        return
    }
    var editList by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editWish by remember { mutableStateOf<Wish?>(null) }
    var showReceived by remember { mutableStateOf(false) }
    val items = w.wishes.filter { it.listId == id }
    val shown = items.filter { showReceived || it.status != WishStatus.RECEIVED }
        .sortedWith(compareBy<Wish>({ it.status.ordinal }, { -it.priority }, { -it.created }))
    val next = nextOccurrence(list.localDate, vm.today, list.yearly)
    val isMine = list.ownerId == ME

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { Text("${list.emoji} ${list.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { shareText(ctx, wishListText(vm, list, items)) }) { Icon(Icons.Filled.Share, "Поделиться") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Edit, "Изменить") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Название, повод, дата") }, onClick = {
                                menu = false
                                editList = true
                            })
                            DropdownMenuItem(text = { Text(if (list.archived) "Вернуть из архива" else "В архив") }, onClick = {
                                menu = false
                                vm.updateWork { WorkOps.upsertWishList(it, list.copy(archived = !list.archived)) }
                            })
                            DropdownMenuItem(text = { Text("Удалить список") }, onClick = {
                                menu = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editWish = Wish(newId(), id, "") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Желание") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(list.color).copy(alpha = 0.14f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        val head = buildList {
                            if (!isMine) w.person(list.ownerId)?.let { add("Для: ${it.name}") }
                            if (list.occasion.isNotBlank()) add(list.occasion)
                            next?.let { add(Fmt.date(it)) }
                        }.joinToString(" · ")
                        if (head.isNotEmpty()) Text(head, style = MaterialTheme.typography.bodyMedium)
                        next?.let {
                            val d = ChronoUnit.DAYS.between(vm.today, it)
                            Text(
                                if (d == 0L) "Сегодня!" else "Осталось ${Fmt.days(d)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        val total = items.filter { it.status != WishStatus.RECEIVED }.sumOf { it.price ?: 0L }
                        val done = items.count { it.status == WishStatus.BOUGHT || it.status == WishStatus.RECEIVED }
                        Text(
                            "${items.size} ${Fmt.plural(items.size.toLong(), "желание", "желания", "желаний")}" +
                                if (total > 0) " на ${SplitMath.money(total)}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (items.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { done.toFloat() / items.size },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = wishStatusColor(WishStatus.RECEIVED),
                            )
                            Text(
                                "Куплено или подарено: $done из ${items.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Muted2(
                        if (isMine) "Добавьте, что хотели бы получить: название, ссылку на товар и примерную цену. Потом отправьте список близким кнопкой «Поделиться»."
                        else "Записывайте идеи подарков. Отмечайте «Забронировано», «Куплено» и «Подарено», чтобы ничего не повторить.",
                    )
                }
            }
            items(shown, key = { it.id }) { x -> WishCard(vm, x) { editWish = x } }
            if (items.any { it.status == WishStatus.RECEIVED }) {
                item {
                    TextButton(onClick = { showReceived = !showReceived }) {
                        Text(if (showReceived) "Скрыть подаренные" else "Показать подаренные (${items.count { it.status == WishStatus.RECEIVED }})")
                    }
                }
            }
        }
    }

    if (editList) WishListDialog(vm, list) { editList = false }
    editWish?.let { x -> WishSheet(vm, x) { editWish = null } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${list.title}»?") },
            text = { Text("Вместе со всеми желаниями списка.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.pop()
                    vm.updateWork { WorkOps.deleteWishList(it, id) }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun WishCard(vm: MainViewModel, x: Wish, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var statusMenu by remember { mutableStateOf(false) }
    val received = x.status == WishStatus.RECEIVED
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        x.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (received) TextDecoration.LineThrough else null,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { k ->
                            Icon(
                                Icons.Filled.Star, null,
                                Modifier.size(14.dp),
                                tint = if (k < x.priority) Color(0xFFFFB400) else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        x.price?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(SplitMath.money(it), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box {
                    Text(
                        x.status.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(wishStatusColor(x.status))
                            .clickable { statusMenu = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        WishStatus.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.title) }, onClick = {
                                statusMenu = false
                                vm.updateWork { WorkOps.upsertWish(it, x.copy(status = s, reservedBy = if (s == WishStatus.WANTED) null else x.reservedBy)) }
                            })
                        }
                    }
                }
            }
            if (x.note.isNotBlank()) {
                Text(x.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            val who = x.reservedBy?.let { vm.work.person(it)?.name }
            if (who != null && x.status != WishStatus.WANTED) {
                Text("${x.status.title.lowercase()}: $who", style = MaterialTheme.typography.bodySmall, color = wishStatusColor(x.status))
            }
            if (x.link.isNotBlank()) {
                Text(
                    "🔗 " + x.link.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(48),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { openLink(ctx, x.link) }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** Добавление и изменение желания; закрыли окно — введённое сохраняется. */
@Composable
private fun WishSheet(vm: MainViewModel, x: Wish, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exists = vm.work.wishes.any { it.id == x.id }
    var title by remember { mutableStateOf(x.title) }
    var link by remember { mutableStateOf(x.link) }
    var price by remember { mutableStateOf(x.price?.let { SplitMath.plain(it) } ?: "") }
    var note by remember { mutableStateOf(x.note) }
    var priority by remember { mutableStateOf(x.priority) }
    var status by remember { mutableStateOf(x.status) }
    var reservedBy by remember { mutableStateOf(x.reservedBy) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save(): Boolean {
        val t = title.trim().ifEmpty { link.trim().takeIf { it.isNotEmpty() }?.let { "Желание по ссылке" } ?: "" }
        if (t.isEmpty()) return false
        vm.updateWork {
            WorkOps.upsertWish(
                it,
                x.copy(
                    title = t, link = link.trim(), price = SplitMath.parse(price), note = note.trim(),
                    priority = priority, status = status, reservedBy = if (status == WishStatus.WANTED) null else reservedBy,
                ),
            )
        }
        return true
    }

    ModalBottomSheet(onDismissRequest = {
        if (!confirmDelete && save() && !exists) vm.message = "Желание сохранено"
        onDismiss()
    }, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (exists) "Желание" else "Новое желание", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                title, { title = it }, label = { Text("Что хочется") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                link, { link = it }, label = { Text("Ссылка на товар") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { clip ->
                            Regex("""https?://\S+""").find(clip)?.value?.let { link = it }
                        }
                    }) { Text("Вставить") }
                },
            )
            OutlinedTextField(
                price, { v -> price = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Цена, ₽") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text("Насколько хочется", style = MaterialTheme.typography.titleSmall)
            Row {
                (1..3).forEach { k ->
                    IconButton(onClick = { priority = k }) {
                        Icon(Icons.Filled.Star, "$k", tint = if (k <= priority) Color(0xFFFFB400) else MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text("Статус", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WishStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.title) }) }
            }
            if (status != WishStatus.WANTED) {
                Text("Кто ${status.title.lowercase()}", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.work.everyone.forEach { p ->
                        FilterChip(selected = reservedBy == p.id, onClick = { reservedBy = if (reservedBy == p.id) null else p.id }, label = { Text(p.name) }, leadingIcon = { Avatar(p, 18.dp) })
                    }
                }
            }
            OutlinedTextField(
                note, { note = it }, label = { Text("Заметка: размер, цвет, где купить") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (exists) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Filled.Delete, null)
                    }
                }
                Button(
                    onClick = { if (save()) onDismiss() },
                    enabled = title.isNotBlank() || link.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (exists) "Сохранить" else "Добавить") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить желание?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateWork { WorkOps.deleteWish(it, x.id) }
                    onDismiss()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private fun wishListText(vm: MainViewModel, l: WishList, items: List<Wish>): String = buildString {
    val owner = if (l.ownerId == ME) "Мой список желаний" else "Идеи подарков для ${vm.work.person(l.ownerId)?.name}"
    append("${l.emoji} $owner «${l.title}»")
    val d = nextOccurrence(l.localDate, vm.today, l.yearly)
    if (l.occasion.isNotBlank() || d != null) append(" — ").append(listOfNotNull(l.occasion.ifBlank { null }, d?.let { Fmt.date(it) }).joinToString(", "))
    append("\n\n")
    items.filter { it.status != WishStatus.RECEIVED }.sortedByDescending { it.priority }.forEachIndexed { i, x ->
        append("${i + 1}. ${x.title}")
        x.price?.let { append(" — ${SplitMath.money(it)}") }
        if (l.ownerId != ME && x.status != WishStatus.WANTED) append(" [${x.status.title}]")
        append("\n")
        if (x.note.isNotBlank()) append("   ${x.note}\n")
        if (x.link.isNotBlank()) append("   ${x.link}\n")
    }
}.trim()

fun shareText(ctx: Context, text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    ctx.startActivity(Intent.createChooser(send, "Отправить"))
}

fun openLink(ctx: Context, link: String) {
    val url = if (link.startsWith("http")) link else "https://$link"
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
