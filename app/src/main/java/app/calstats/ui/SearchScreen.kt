package app.calstats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.work.SplitMath
import app.calstats.work.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Найденное: раздел, заголовок, пояснение и куда перейти. */
data class Hit(val group: String, val emoji: String, val title: String, val sub: String, val sort: Long, val open: () -> Unit)

object SearchIndex {
    private fun d(s: String?) = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    private fun ep(d: LocalDate?) = d?.toEpochDay() ?: 0L

    fun find(vm: MainViewModel, raw: String): List<Hit> {
        val q = raw.trim().lowercase()
        if (q.length < 2) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        fun m(vararg s: String?): Boolean { val t = s.filterNotNull().joinToString(" ").lowercase(); return words.all { t.contains(it) } }
        val w = vm.work
        val out = ArrayList<Hit>()
        fun go(tab: Tab) { vm.tab = tab }

        // Календарь (загруженный период)
        if (vm.isEnabled(Tab.CALENDAR)) vm.instances.filter { m(it.title) }.take(30).forEach { e ->
            val day = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
            out += Hit("Календарь", "📅", e.title, Fmt.date(day.toLocalDate()) + if (e.allDay) "" else ", " + Fmt.time(day.toLocalTime()),
                day.toLocalDate().toEpochDay()) { vm.push(Screen.Edit(eventId = e.eventId, instanceBegin = e.begin, instanceEnd = e.end)) }
        }
        if (vm.isEnabled(Tab.TASKS)) w.issues.filter { m(it.title, it.description, it.labels.joinToString(" "), w.projects.firstOrNull { p -> p.id == it.projectId }?.key + "-" + it.number) }.forEach { i ->
            val key = w.projects.firstOrNull { it.id == i.projectId }?.key ?: ""
            out += Hit("Задачи", if (i.done) "✔" else "✅", i.title, "$key-${i.number} · ${i.status.title}", i.created / 86_400_000) { vm.push(Screen.IssueDetail(i.id)) }
        }
        if (vm.isEnabled(Tab.NOTES)) w.notes.filter { m(it.title, it.body, it.checklist.joinToString(" ") { c -> c.text }, it.labels.joinToString(" ")) }.forEach { n ->
            out += Hit("Заметки", "📝", n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.take(80), n.body.take(80).replace('\n', ' '), n.updated / 86_400_000) { vm.push(Screen.NoteEdit(n.id)) }
        }
        if (vm.isEnabled(Tab.FINANCE)) {
            w.transactions.filter { m(it.note, w.category(it.categoryId)?.name, w.accounts.firstOrNull { a -> a.id == it.accountId }?.name, SplitMath.money(it.amount)) }
                .sortedByDescending { it.date }.take(40).forEach { t ->
                    val sign = when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; else -> "" }
                    out += Hit("Финансы", w.category(t.categoryId)?.emoji ?: "💸", "$sign${SplitMath.money(t.amount)} ${t.note}".trim(),
                        (w.category(t.categoryId)?.name ?: "") + " · " + Fmt.date(t.localDate), ep(t.localDate)) { go(Tab.FINANCE) }
                }
            w.debts.filter { d -> m(d.title, w.counterparties.firstOrNull { it.id == d.partyId }?.name, w.counterparties.firstOrNull { it.id == d.partyId }?.phone) }.forEach { d ->
                val p = w.counterparties.firstOrNull { it.id == d.partyId }?.name ?: ""
                out += Hit("Долги", "🤝", "$p · ${SplitMath.money(d.principal)}", d.title + if (d.closed) " · закрыт" else "", ep(d(d.date))) {
                    vm.subViews["fin"] = "DEBTS"; go(Tab.FINANCE)
                }
            }
            w.goals.filter { m(it.name) }.forEach { g -> out += Hit("Накопления", g.emoji, g.name, "${SplitMath.money(g.saved)} из ${SplitMath.money(g.target)}", 0) { go(Tab.FINANCE) } }
        }
        if (vm.isEnabled(Tab.DOCS)) w.documents.filter { m(it.title, it.owner, it.number, it.issuer, it.note, it.kind.title) }.forEach { doc ->
            val hide = vm.docsLock && !vm.docsUnlocked
            out += Hit("Документы", doc.kind.emoji, doc.title, if (hide) "защищено" else listOf(doc.owner, doc.expires?.let { "до " + Fmt.date(LocalDate.parse(it)) }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                ep(d(doc.expires))) { vm.push(Screen.DocEdit(doc.id)) }
        }
        if (vm.isEnabled(Tab.CARDS)) w.cards.filter { m(it.store, it.note, it.number) }.forEach { c ->
            out += Hit("Карты", "💳", c.store, c.number.takeLast(4).let { if (it.isNotBlank()) "•••• $it" else "" }, c.lastUsed / 86_400_000) {
                vm.showCardId = c.id; go(Tab.CARDS)
            }
        }
        if (vm.isEnabled(Tab.SHOPPING)) w.shopItems.filter { m(it.name) }.forEach { s ->
            out += Hit("Покупки", if (s.done) "✔" else "🛒", s.name + if (s.qty.isNotBlank()) " · ${s.qty}" else "", w.shopLists.firstOrNull { it.id == s.listId }?.name ?: "", s.created / 86_400_000) { go(Tab.SHOPPING) }
        }
        if (vm.isEnabled(Tab.HABITS)) w.habits.filter { m(it.name) }.forEach { h -> out += Hit("Привычки", h.emoji, h.name, if (h.archived) "в архиве" else "", 0) { go(Tab.HABITS) } }
        if (vm.isEnabled(Tab.HEALTH)) w.meds.filter { m(it.name, it.dose) }.forEach { md -> out += Hit("Лекарства", "💊", md.name, md.dose, 0) { vm.subViews["health"] = HView.MEDS; go(Tab.HEALTH) } }
        if (vm.isEnabled(Tab.JOURNAL)) w.journal.filter { m(it.text, it.best, it.gratitude.joinToString(" ")) }.sortedByDescending { it.date }.forEach { j ->
            out += Hit("Дневник", "📔", Fmt.date(LocalDate.parse(j.date)), listOf(j.best, j.text).firstOrNull { it.isNotBlank() }.orEmpty().take(90).replace('\n', ' '), ep(d(j.date))) { go(Tab.JOURNAL) }
        }
        if (vm.isEnabled(Tab.GOALS)) w.lifeGoals.filter { m(it.title, it.why, it.steps.joinToString(" ") { s -> s.title }) }.forEach { g ->
            out += Hit("Цели", g.emoji, g.title, "${g.steps.count { it.done }} из ${g.steps.size} шагов", 0) { go(Tab.GOALS) }
        }
        if (vm.isEnabled(Tab.CAR)) w.carEvents.filter { m(it.note, it.type.title) }.forEach { e ->
            out += Hit("Авто", e.type.emoji, e.type.title + if (e.note.isNotBlank()) " · ${e.note}" else "", Fmt.date(LocalDate.parse(e.date)) + " · " + SplitMath.money(e.cost), ep(d(e.date))) { go(Tab.CAR) }
        }
        if (vm.isEnabled(Tab.PETS)) {
            w.pets.filter { m(it.name, it.breed, it.chip) }.forEach { p -> out += Hit("Питомцы", "🐾", p.name, p.breed, 0) { go(Tab.PETS) } }
            w.petEvents.filter { m(it.note, it.type.title) }.forEach { e -> out += Hit("Питомцы", e.type.emoji, e.type.title + " · " + e.note, Fmt.date(LocalDate.parse(e.date)), ep(d(e.date))) { go(Tab.PETS) } }
        }
        if (vm.isEnabled(Tab.WISHES)) w.wishes.filter { m(it.title, it.note, it.link) }.forEach { x ->
            out += Hit("Желания", "🎁", x.title, w.wishLists.firstOrNull { it.id == x.listId }?.title ?: "", x.updated / 86_400_000) { vm.push(Screen.WishListDetail(x.listId)) }
        }
        if (vm.isEnabled(Tab.MEALS)) w.recipes.filter { m(it.title, it.ingredients.joinToString(" ") { i -> i.name }, it.steps) }.forEach { r ->
            out += Hit("Рецепты", r.emoji, r.title, r.ingredients.take(4).joinToString { it.name }, 0) { vm.subViews["meals"] = "RECIPES"; go(Tab.MEALS) }
        }
        if (vm.isEnabled(Tab.READING)) w.readBooks.filter { m(it.title, it.author, it.note) }.forEach { b ->
            out += Hit("Чтение", b.kind.emoji, b.title, listOf(b.author, b.status.title).filter { it.isNotBlank() }.joinToString(" · "), b.created / 86_400_000) { go(Tab.READING) }
        }
        if (vm.isEnabled(Tab.TRIPS)) w.trips.filter { m(it.title, it.place, it.note, it.packing.joinToString(" ") { i -> i.text }) }.forEach { t ->
            out += Hit("Поездки", "✈", t.title, Fmt.date(t.startDate) + if (t.place.isNotBlank()) " · ${t.place}" else "", t.startDate.toEpochDay()) { vm.subViews["trip"] = t.id; go(Tab.TRIPS) }
        }
        if (vm.isEnabled(Tab.CHORES)) w.chores.filter { m(it.title, it.room) }.forEach { c -> out += Hit("Дела по дому", "🧹", c.title, c.room, 0) { go(Tab.CHORES) } }
        if (vm.isEnabled(Tab.PLANTS)) w.plants.filter { m(it.name, it.place) }.forEach { p -> out += Hit("Растения", p.emoji, p.name, p.place, 0) { go(Tab.PLANTS) } }
        if (vm.isEnabled(Tab.WORKOUTS)) w.exercises.filter { m(it.name, it.group) }.forEach { e ->
            out += Hit("Тренировки", "🏋", e.name, app.calstats.work.Training.best(w, e.id)?.let { "рекорд ${it.weight} кг × ${it.reps}" } ?: e.group, 0) { go(Tab.WORKOUTS) }
        }
        w.people.filter { m(it.name, it.note, it.phone) }.forEach { p -> out += Hit("Люди", "👤", p.name, listOf(p.note, p.phone).filter { it.isNotBlank() }.joinToString(" · "), 0) { vm.push(Screen.People) } }
        return out
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    var q by rememberSaveable { mutableStateOf("") }
    val hits = remember(q, vm.work, vm.instances) { SearchIndex.find(vm, q) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    TextField(
                        q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text("Поиск по всем модулям") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { pad ->
        val groups = hits.groupBy { it.group }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (q.trim().length >= 2 && hits.isEmpty()) item {
                Text("Ничего не нашлось", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (q.trim().length < 2) item {
                Text("Ищет в событиях, задачах, заметках, тратах, долгах, документах, картах, покупках, привычках, дневнике, целях, авто, питомцах, желаниях и людях.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            groups.forEach { (g, list) ->
                item(key = "g$g") {
                    Text("$g · ${list.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(list.sortedByDescending { it.sort }.take(30)) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.pop(); h.open() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            if (h.sub.isNotBlank()) Text(h.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
