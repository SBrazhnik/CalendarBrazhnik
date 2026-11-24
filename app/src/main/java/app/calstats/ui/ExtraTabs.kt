@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.Tab
import app.calstats.work.Book
import app.calstats.work.BookKind
import app.calstats.work.BookStatus
import app.calstats.work.Chore
import app.calstats.work.Chores
import app.calstats.work.Exercise
import app.calstats.work.MealPlan
import app.calstats.work.MealSlot
import app.calstats.work.Meals
import app.calstats.work.Plant
import app.calstats.work.Plants
import app.calstats.work.ReadLog
import app.calstats.work.Recipe
import app.calstats.work.RecipeIng
import app.calstats.work.ShopItem
import app.calstats.work.ShopList
import app.calstats.work.SplitMath
import app.calstats.work.Training
import app.calstats.work.Trip
import app.calstats.work.TripCost
import app.calstats.work.TripItem
import app.calstats.work.Trips
import app.calstats.work.WorkSet
import app.calstats.work.WorkoutLog
import app.calstats.work.newId
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
private fun Tip(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Head(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

// ======================= МЕНЮ =======================

@Composable
fun MealsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var part by navView(vm, "meals", "WEEK")
    var week by rememberSaveable { mutableStateOf(0) }
    var editRecipe by remember { mutableStateOf<Recipe?>(null) }
    var pick by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }
    var openRecipe by remember { mutableStateOf<Recipe?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    var pantry by remember { mutableStateOf(false) }
    val w = vm.work
    val monday = vm.today.minusDays(vm.today.dayOfWeek.value - 1L).plusWeeks(week.toLong())
    ModuleScaffold("Меню", snackbar, fab = if (part == "RECIPES") "Рецепт" to { editRecipe = Recipe(newId(), "") } else null) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(part == "WEEK", { part = "WEEK" }, label = { Text("Неделя") })
                FilterChip(part == "RECIPES", { part = "RECIPES" }, label = { Text("Рецепты · ${w.recipes.size}") })
            }
        }
        if (part == "WEEK") {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { week-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                    Text("${Fmt.date(monday).substringBeforeLast(' ')} – ${Fmt.date(monday.plusDays(6)).substringBeforeLast(' ')}",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { week++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                }
            }
            item {
                val list = Meals.shopping(w, monday, monday.plusDays(6))
                Button(onClick = {
                    vm.updateWork { w0 ->
                        var x = w0
                        val shop = x.shopLists.firstOrNull { !it.archived } ?: ShopList(newId(), "Продукты", "🛒").also { x = x.copy(shopLists = x.shopLists + it) }
                        val have = x.shopItems.filter { it.listId == shop.id && !it.done }.map { it.name.lowercase() }.toSet()
                        x.copy(shopItems = x.shopItems + list.filter { it.first.lowercase() !in have }.map { (n, q) -> ShopItem(newId(), shop.id, n, q) })
                    }
                    if (!vm.isEnabled(Tab.SHOPPING)) vm.setModuleEnabled(Tab.SHOPPING, true)
                    vm.message = "В покупки добавлено: ${list.size}"
                }, enabled = list.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("🛒 Продукты на неделю → в покупки (${list.size})") }
            }
            if (w.recipes.isEmpty()) item { Tip("Добавьте рецепты во вкладке «Рецепты» — тогда продукты для меню соберутся в список покупок сами. Можно и просто вписать блюдо текстом.") }
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                item(key = "d$d") {
                    Flat {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Fmt.dayTitle(d, vm.today), style = MaterialTheme.typography.titleSmall,
                                color = if (d == vm.today) moduleColor(Tab.MEALS) else MaterialTheme.colorScheme.onSurface)
                            MealSlot.entries.forEach { s ->
                                val plans = w.mealPlans.filter { it.date == d.toString() && it.slot == s }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.emoji, Modifier.width(28.dp))
                                    Column(Modifier.weight(1f)) {
                                        if (plans.isEmpty()) Text(s.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                        plans.forEach { p ->
                                            val r = w.recipes.firstOrNull { it.id == p.recipeId }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(r?.title ?: p.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable(enabled = r != null) { openRecipe = r })
                                                Text("✕", Modifier.clickable { vm.updateWork { x -> x.copy(mealPlans = x.mealPlans.filter { it.id != p.id }) } }.padding(6.dp),
                                                    color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                    TextButton(onClick = { pick = d to s }) { Text("+") }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { linkDialog = true }) { Text("🔗 По ссылке") }
                    OutlinedButton(onClick = { pantry = true }) { Text("🧺 Что приготовить?") }
                }
            }
            if (w.recipes.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tip("Рецепт — это список продуктов и как готовить. Добавьте свои или начните с простых.")
                    OutlinedButton(onClick = { vm.updateWork { it.copy(recipes = it.recipes + Meals.STARTERS.map { r -> r.copy(id = newId()) }) } }) { Text("Добавить 6 простых рецептов") }
                }
            }
            items(w.recipes.sortedBy { it.title.lowercase() }, key = { it.id }) { r ->
                Flat(onClick = { openRecipe = r }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall)
                            Tip(listOfNotNull(r.minutes?.let { "$it мин" }, "${r.servings} порц.", "${r.ingredients.size} продуктов").joinToString(" · "))
                        }
                    }
                }
            }
        }
    }
    pick?.let { (d, s) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("${s.emoji} ${s.title} · ${Fmt.dayTitle(d, vm.today)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Блюдо текстом") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    if (w.recipes.isNotEmpty()) Tip("или выберите рецепт:")
                    w.recipes.sortedByDescending { r -> r.tags.any { it.contains(s.title.lowercase().take(4)) } }.forEach { r ->
                        Text("${r.emoji} ${r.title}", Modifier.fillMaxWidth().clickable {
                            vm.updateWork { it.copy(mealPlans = it.mealPlans + MealPlan(newId(), d.toString(), s, recipeId = r.id)) }
                            pick = null
                        }.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = {
                vm.updateWork { it.copy(mealPlans = it.mealPlans + MealPlan(newId(), d.toString(), s, text = text.trim())) }; pick = null
            }) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Отмена") } },
        )
    }
    openRecipe?.let { r ->
        AlertDialog(
            onDismissRequest = { openRecipe = null },
            title = { Text("${r.emoji} ${r.title}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tip(listOfNotNull(r.minutes?.let { "$it мин" }, "${r.servings} порц.").joinToString(" · "))
                    r.ingredients.forEach { Text("• ${it.name}" + if (it.qty.isNotBlank()) " — ${it.qty}" else "", style = MaterialTheme.typography.bodyMedium) }
                    if (r.steps.isNotBlank()) { Spacer(Modifier.width(4.dp)); Text(r.steps, style = MaterialTheme.typography.bodyMedium) }
                    r.kcal?.let { k -> Tip("$k ккал на порцию") }
                }
            },
            confirmButton = {
                if (r.kcal != null && vm.isEnabled(Tab.HEALTH)) TextButton(onClick = {
                    val h = java.time.LocalTime.now().hour
                    val meal = when { h < 11 -> app.calstats.work.Meal.BREAKFAST; h < 16 -> app.calstats.work.Meal.LUNCH; h < 21 -> app.calstats.work.Meal.DINNER; else -> app.calstats.work.Meal.SNACK }
                    vm.updateWork { app.calstats.work.HealthOps.addFood(it, app.calstats.work.FoodEntry(newId(), vm.today.toString(), meal, r.title, 300, r.kcal.toDouble(), 0.0, 0.0, 0.0), null) }
                    vm.message = "🍽 ${r.title}: ${r.kcal} ккал записано в «Здоровье»"; openRecipe = null
                }) { Text("Съел порцию") } else TextButton(onClick = { openRecipe = null }) { Text("Закрыть") }
            },
            dismissButton = { TextButton(onClick = { editRecipe = r; openRecipe = null }) { Text("Изменить") } },
        )
    }
    editRecipe?.let { r -> RecipeEditDialog(vm, r) { editRecipe = null } }
    if (linkDialog) {
        var url by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { linkDialog = false }, title = { Text("Рецепт по ссылке") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tip("Вставьте ссылку на рецепт с кулинарного сайта (Едим Дома, Поварёнок, Food.ru и др.) — название, продукты и шаги подставятся сами.")
                OutlinedTextField(url, { url = it.trim() }, placeholder = { Text("https://…") }, singleLine = true)
                if (busy) Tip("Загружаю…")
            } },
            confirmButton = { TextButton(enabled = url.startsWith("http") && !busy, onClick = {
                busy = true
                scope.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.calstats.Net.recipe(url) }.getOrNull() }
                    busy = false
                    if (r == null) vm.message = "На странице не нашёл рецепт — добавьте вручную"
                    else { linkDialog = false; editRecipe = r.copy(source = url) }
                }
            }) { Text("Загрузить") } },
            dismissButton = { TextButton(onClick = { linkDialog = false }) { Text("Отмена") } },
        )
    }
    if (pantry) {
        var have by remember { mutableStateOf("") }
        val res = remember(have, w.recipes) { Meals.fromPantry(w.recipes, have.split(',', '\n')) }
        AlertDialog(
            onDismissRequest = { pantry = false }, title = { Text("Что есть дома?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(have, { have = it }, placeholder = { Text("курица, рис, помидоры") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                if (have.isNotBlank() && res.isEmpty()) Tip("Подходящих рецептов нет — добавьте больше рецептов")
                res.take(10).forEach { (r, n) ->
                    Text("${r.emoji} ${r.title} — есть $n из ${r.ingredients.size}", Modifier.fillMaxWidth().clickable { pantry = false; openRecipe = r }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
            } },
            confirmButton = { TextButton(onClick = { pantry = false }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun RecipeEditDialog(vm: MainViewModel, r: Recipe, onDismiss: () -> Unit) {
    val exists = vm.work.recipes.any { it.id == r.id }
    var title by remember { mutableStateOf(r.title) }
    var emoji by remember { mutableStateOf(r.emoji) }
    var minutes by remember { mutableStateOf(r.minutes?.toString() ?: "") }
    var servings by remember { mutableStateOf(r.servings.toString()) }
    var ings by remember { mutableStateOf(r.ingredients.joinToString("\n") { it.name + if (it.qty.isNotBlank()) " — ${it.qty}" else "" }) }
    var steps by remember { mutableStateOf(r.steps) }
    var kcal by remember { mutableStateOf(r.kcal?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Рецепт" else "Новый рецепт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("🍲", "🍳", "🥗", "🍝", "🥘", "🍖", "🐟", "🥣", "🍰", "🥞").forEach { e -> FilterChip(emoji == e, { emoji = e }, label = { Text(e) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Минут") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Порций") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(ings, { ings = it }, label = { Text("Продукты — по одному в строке") }, placeholder = { Text("Яйца — 3 шт\nМолоко — 200 мл") }, minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(steps, { steps = it }, label = { Text("Как готовить") }, minLines = 3, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit) }, label = { Text("Ккал на порцию (необязательно)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(recipes = w.recipes.filter { it.id != r.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val list = ings.lines().map { it.trim() }.filter { it.isNotBlank() }.map { l ->
                    val p = l.split(Regex("\\s+[—–-]\\s+|:\\s*"), limit = 2)
                    RecipeIng(p[0].trim(), p.getOrNull(1)?.trim().orEmpty())
                }
                val nr = r.copy(title = title.trim(), emoji = emoji, minutes = minutes.toIntOrNull(), servings = servings.toIntOrNull() ?: 2, ingredients = list, steps = steps.trim(), kcal = kcal.toIntOrNull())
                vm.updateWork { w -> w.copy(recipes = if (exists) w.recipes.map { if (it.id == r.id) nr else it } else w.recipes + nr) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ТРЕНИРОВКИ =======================

@Composable
fun WorkoutsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var edit by remember { mutableStateOf<WorkoutLog?>(null) }
    val w = vm.work
    ModuleScaffold("Тренировки", snackbar, fab = "Тренировка" to {
        if (w.exercises.isEmpty()) vm.updateWork { it.copy(exercises = Training.STARTERS) }
        edit = WorkoutLog(newId(), vm.today.toString())
    }) {
        item {
            Flat {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${Training.weekCount(w, vm.today)}", style = MaterialTheme.typography.displaySmall)
                        Tip("тренировок на этой неделе")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${w.workouts.count { it.date.startsWith("${vm.today.year}") }}", style = MaterialTheme.typography.displaySmall)
                        Tip("за ${vm.today.year} год")
                    }
                    vm.health?.let { h -> h.days.indexOf(vm.today).takeIf { it >= 0 }?.let { i ->
                        Column(Modifier.weight(1f)) {
                            Text("${h.steps[i]}", style = MaterialTheme.typography.displaySmall)
                            Tip("шагов сегодня")
                        }
                    } }
                }
            }
        }
        if (w.workouts.isEmpty()) item { Tip("Нажмите «Тренировка», выберите упражнения и запишите подходы: вес и повторения. В следующий раз приложение подставит прошлые значения и покажет рекорды.") }
        val records = w.exercises.mapNotNull { e -> Training.best(w, e.id)?.let { e to it } }
        if (records.isNotEmpty()) item {
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🏆 Рекорды", style = MaterialTheme.typography.titleSmall)
                    records.sortedByDescending { it.second.weight }.take(8).forEach { (e, s) -> Text("${e.name}: ${fmtKg(s.weight)} × ${s.reps}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (w.workouts.isNotEmpty()) item { Head("История") }
        items(w.workouts.sortedByDescending { it.date }.take(60), key = { it.id }) { l ->
            Flat(onClick = { edit = l }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row {
                        Text(Fmt.dayTitle(LocalDate.parse(l.date), vm.today) + if (l.title.isNotBlank()) " · ${l.title}" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (l.minutes > 0) Tip("${l.minutes} мин")
                    }
                    Tip(l.sets.groupBy { it.exerciseId }.map { (id, s) -> "${w.exercises.firstOrNull { it.id == id }?.name ?: "?"} ${s.size}×" }.joinToString(", "))
                    val v = Training.volume(l)
                    if (v > 0) Tip("Объём: ${"%,.0f".format(v).replace(',', ' ')} кг")
                }
            }
        }
    }
    edit?.let { l -> WorkoutDialog(vm, l) { edit = null } }
}

private fun fmtKg(v: Double) = (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')) + " кг"

@Composable
private fun WorkoutDialog(vm: MainViewModel, l: WorkoutLog, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.workouts.any { it.id == l.id }
    var date by remember { mutableStateOf(LocalDate.parse(l.date)) }
    var title by remember { mutableStateOf(l.title) }
    var minutes by remember { mutableStateOf(if (l.minutes > 0) l.minutes.toString() else "") }
    // подходы как строки ввода
    var sets by remember { mutableStateOf(l.sets.map { Triple(it.exerciseId, if (it.weight > 0) fmtKg(it.weight).removeSuffix(" кг") else "", if (it.reps > 0) it.reps.toString() else "") }) }
    var newEx by remember { mutableStateOf("") }
    val exercises = vm.work.exercises
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Тренировка" else "Новая тренировка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }) { Text(Fmt.dayTitle(date, vm.today)) }
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Минут") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(title, { title = it }, label = { Text("Название (необязательно)") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                sets.groupBy { it.first }.keys.forEach { exId ->
                    val e = exercises.firstOrNull { it.id == exId }
                    Text(e?.name ?: "?", style = MaterialTheme.typography.titleSmall)
                    sets.withIndex().filter { it.value.first == exId }.forEach { (i, s) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(s.second, { v -> sets = sets.toMutableList().also { it[i] = s.copy(second = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                                Modifier.weight(1f), label = { Text(if (e?.cardio == true) "км" else "кг") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(s.third, { v -> sets = sets.toMutableList().also { it[i] = s.copy(third = v.filter(Char::isDigit)) } },
                                Modifier.weight(1f), label = { Text(if (e?.cardio == true) "мин" else "повт.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Text("✕", Modifier.clickable { sets = sets.toMutableList().also { it.removeAt(i) } }.padding(4.dp))
                        }
                    }
                    TextButton(onClick = { val last = sets.last { it.first == exId }; sets = sets + last }) { Text("+ подход") }
                }
                if (sets.isEmpty()) {
                    Text("Программа", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Training.PROGRAMS.forEach { pr ->
                            FilterChip(false, {
                                var ex = vm.work.exercises
                                val missing = pr.exercises.filter { n -> ex.none { it.name == n } }.map { Exercise(newId(), it) }
                                if (missing.isNotEmpty()) { vm.updateWork { it.copy(exercises = it.exercises + missing) }; ex = ex + missing }
                                title = pr.name
                                sets = pr.exercises.mapNotNull { n -> ex.firstOrNull { it.name == n } }.map { e ->
                                    val prev = Training.last(vm.work, e.id)
                                    Triple(e.id, prev?.weight?.takeIf { it > 0 }?.let { fmtKg(it).removeSuffix(" кг") } ?: "", prev?.reps?.takeIf { it > 0 }?.toString() ?: "")
                                }
                            }, label = { Text(pr.name) })
                        }
                    }
                }
                RestTimer()
                Text("Добавить упражнение", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    exercises.filter { e -> sets.none { it.first == e.id } }.forEach { e ->
                        FilterChip(false, {
                            val prev = Training.last(vm.work, e.id)
                            sets = sets + Triple(e.id, prev?.weight?.takeIf { it > 0 }?.let { fmtKg(it).removeSuffix(" кг") } ?: "", prev?.reps?.takeIf { it > 0 }?.toString() ?: "")
                        }, label = { Text(e.name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newEx, { newEx = it }, Modifier.weight(1f), label = { Text("Своё упражнение") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    TextButton(enabled = newEx.isNotBlank(), onClick = {
                        val e = Exercise(newId(), newEx.trim())
                        vm.updateWork { it.copy(exercises = it.exercises + e) }
                        sets = sets + Triple(e.id, "", ""); newEx = ""
                    }) { Text("Добавить") }
                }
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(workouts = w.workouts.filter { it.id != l.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = sets.isNotEmpty() || minutes.isNotBlank(), onClick = {
                val nl = l.copy(date = date.toString(), title = title.trim(), minutes = minutes.toIntOrNull() ?: 0,
                    sets = sets.map { (id, wt, r) -> WorkSet(id, num(wt) ?: 0.0, r.toIntOrNull() ?: 0) })
                vm.updateWork { w -> w.copy(workouts = if (exists) w.workouts.map { if (it.id == l.id) nl else it } else w.workouts + nl) }
                val records = nl.sets.filter { s -> s.weight > 0 && vm.work.workouts.filter { it.id != nl.id }.flatMap { it.sets }.none { it.exerciseId == s.exerciseId && it.weight >= s.weight } &&
                    vm.work.workouts.any { o -> o.id != nl.id && o.sets.any { it.exerciseId == s.exerciseId } } }
                vm.message = if (records.isNotEmpty()) "🏆 Новый рекорд: " + records.joinToString { s -> "${exercises.firstOrNull { it.id == s.exerciseId }?.name} ${fmtKg(s.weight)}" } else "Тренировка записана 💪"
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ПОЕЗДКИ =======================

@Composable
fun TripsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var openId by navView<String?>(vm, "trip", null)
    var edit by remember { mutableStateOf<Trip?>(null) }
    val w = vm.work
    val open = w.trips.firstOrNull { it.id == openId }
    if (open != null) { TripDetail(vm, open, snackbar, onBack = { openId = null }, onEdit = { edit = open }); edit?.let { t -> TripDialog(vm, t) { edit = null } }; return }
    ModuleScaffold("Поездки", snackbar, fab = "Поездка" to { edit = Trip(newId(), "", start = vm.today.plusDays(14).toString(), end = vm.today.plusDays(21).toString()) }) {
        val upcoming = w.trips.filter { !it.endDate.isBefore(vm.today) }.sortedBy { it.start }
        val past = w.trips.filter { it.endDate.isBefore(vm.today) }.sortedByDescending { it.start }
        if (w.trips.isEmpty()) item { Tip("Добавьте поездку: приложение соберёт чек-лист вещей, проверит сроки паспортов, посчитает бюджет и напомнит собраться.") }
        if (upcoming.isNotEmpty()) item { Head("Впереди") }
        items(upcoming, key = { it.id }) { t -> TripCard(vm, t) { openId = t.id } }
        if (past.isNotEmpty()) item { Head("Были") }
        items(past, key = { it.id }) { t -> TripCard(vm, t) { openId = t.id } }
    }
    edit?.let { t -> TripDialog(vm, t) { edit = null } }
}

@Composable
private fun TripCard(vm: MainViewModel, t: Trip, onClick: () -> Unit) {
    Flat(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("${if (t.abroad) "✈" else "🚆"} ${t.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val d = Trips.daysLeft(t, vm.today)
                if (d > 0) Tip("через $d ${Fmt.plural(d, "день", "дня", "дней")}") else if (!t.endDate.isBefore(vm.today)) Tip("сейчас")
            }
            Tip("${Fmt.date(t.startDate)} – ${Fmt.date(t.endDate)}" + if (t.place.isNotBlank()) " · ${t.place}" else "")
            if (t.packing.isNotEmpty()) Bar(t.packing.count { it.done }.toFloat() / t.packing.size, moduleColor(Tab.TRIPS))
            Trips.warnings(t, vm.work).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TripDetail(vm: MainViewModel, t: Trip, snackbar: SnackbarHostState, onBack: () -> Unit, onEdit: () -> Unit) {
    var newItem by remember { mutableStateOf("") }
    var newBooking by remember { mutableStateOf("") }
    var costTitle by remember { mutableStateOf("") }
    var costAmount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf("Я") }
    var dayEdit by remember { mutableStateOf<LocalDate?>(null) }
    var rate by remember { mutableStateOf<Double?>(null) }
    androidx.compose.runtime.LaunchedEffect(t.currency) {
        if (t.currency.isNotBlank() && t.currency != "RUB") rate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.calstats.Net.rates()[t.currency] }.getOrNull() }
    }
    fun upd(f: (Trip) -> Trip) = vm.updateWork { w -> w.copy(trips = w.trips.map { if (it.id == t.id) f(it) else it }) }
    ModuleScaffold(t.title, snackbar, actions = {
        TextButton(onClick = onEdit) { Text("Изменить") }
        TextButton(onClick = onBack) { Text("Все") }
    }) {
        item {
            Flat {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${Fmt.date(t.startDate)} – ${Fmt.date(t.endDate)}", style = MaterialTheme.typography.titleMedium)
                    if (t.place.isNotBlank()) Tip("📍 ${t.place}")
                    val spent = t.costs.sumOf { it.amount }
                    if (t.budget > 0 || spent > 0) Text("Бюджет: ${SplitMath.money(spent)}" + if (t.budget > 0) " из ${SplitMath.money(t.budget)}" else "", style = MaterialTheme.typography.bodyMedium,
                        color = if (t.budget in 1 until spent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Trips.warnings(t, vm.work).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (t.note.isNotBlank()) Tip(t.note)
                    rate?.let { r ->
                        var conv by remember { mutableStateOf("100") }
                        Text("💱 1 ${t.currency} = ${"%.2f".format(r).replace('.', ',')} ₽ (курс ЦБ)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(conv, { conv = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.width(120.dp), suffix = { Text(t.currency) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Text("= " + SplitMath.money(Math.round((num(conv) ?: 0.0) * r * 100)), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        // План по дням
        item { Head("План по дням") }
        val nDays = java.time.temporal.ChronoUnit.DAYS.between(t.startDate, t.endDate).toInt().coerceIn(0, 30)
        items((0..nDays).map { t.startDate.plusDays(it.toLong()) }, key = { "day$it" }) { d ->
            Row(Modifier.fillMaxWidth().clickable { dayEdit = d }.padding(vertical = 6.dp)) {
                Text("${Fmt.dateNoYear(d)}", Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge, color = moduleColor(Tab.TRIPS))
                Text(t.days[d.toString()]?.takeIf { it.isNotBlank() } ?: "+ план", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = if (t.days[d.toString()].isNullOrBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            }
        }
        item { Head("Взять с собой · ${t.packing.count { it.done }}/${t.packing.size}") }
        if (t.packing.isEmpty()) item {
            OutlinedButton(onClick = { upd { it.copy(packing = (Trips.PACKING + if (t.abroad) Trips.ABROAD else emptyList()).map { s -> TripItem(newId(), s) }) } }) { Text("Заполнить базовым списком") }
        }
        items(t.packing, key = { "p" + it.id }) { i -> TripCheckRow(i, { upd { tr -> tr.copy(packing = tr.packing.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } }) { upd { tr -> tr.copy(packing = tr.packing.filter { it.id != i.id }) } } }
        item { AddRow(newItem, { newItem = it }, "Добавить вещь") { upd { it.copy(packing = it.packing + TripItem(newId(), newItem.trim())) }; newItem = "" } }
        item { Head("Брони и билеты") }
        items(t.bookings, key = { "b" + it.id }) { i -> TripCheckRow(i, { upd { tr -> tr.copy(bookings = tr.bookings.map { if (it.id == i.id) it.copy(done = !it.done) else it }) } }) { upd { tr -> tr.copy(bookings = tr.bookings.filter { it.id != i.id }) } } }
        item { AddRow(newBooking, { newBooking = it }, "Билеты, отель, трансфер…") { upd { it.copy(bookings = it.bookings + TripItem(newId(), newBooking.trim())) }; newBooking = "" } }
        item { Head("Расходы") }
        items(t.costs, key = { "c" + it.id }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title + if (t.people.isNotEmpty()) " · платил ${c.paidBy.ifBlank { "Я" }}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(SplitMath.money(c.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("✕", Modifier.clickable { upd { tr -> tr.copy(costs = tr.costs.filter { it.id != c.id }) } }.padding(8.dp), color = MaterialTheme.colorScheme.outline)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(costTitle, { costTitle = it }, Modifier.weight(1.4f), label = { Text("На что") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(costAmount, { costAmount = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.weight(1f), label = { Text("₽") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                TextButton(enabled = costTitle.isNotBlank() && (num(costAmount) ?: 0.0) > 0, onClick = {
                    upd { it.copy(costs = it.costs + TripCost(newId(), costTitle.trim(), Math.round(num(costAmount)!! * 100), payer)) }; costTitle = ""; costAmount = ""
                }) { Text("+") }
            }
        }
        if (t.people.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tip("Платил:")
                    (listOf("Я") + t.people).forEach { pp -> FilterChip(payer == pp, { payer = pp }, label = { Text(pp) }) }
                }
            }
            val st = Trips.settle(t)
            if (st.isNotEmpty()) item {
                Flat { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Кто кому должен (поровну на ${t.people.size + 1})", style = MaterialTheme.typography.titleSmall)
                    st.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                } }
            }
        }
    }
    dayEdit?.let { d ->
        var txt by remember { mutableStateOf(t.days[d.toString()].orEmpty()) }
        AlertDialog(
            onDismissRequest = { dayEdit = null }, title = { Text(Fmt.date(d)) },
            text = { OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Музей утром, обед у моря, вечером прогулка") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) },
            confirmButton = { TextButton(onClick = { upd { it.copy(days = it.days + (d.toString() to txt.trim())) }; dayEdit = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { dayEdit = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun TripCheckRow(i: TripItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(i.done, { onToggle() })
        Text(i.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textDecoration = if (i.done) TextDecoration.LineThrough else null)
        Text("✕", Modifier.clickable(onClick = onDelete).padding(8.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AddRow(value: String, onChange: (String) -> Unit, hint: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, onChange, Modifier.weight(1f), placeholder = { Text(hint) }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        TextButton(enabled = value.isNotBlank(), onClick = onAdd) { Text("Добавить") }
    }
}

@Composable
private fun TripDialog(vm: MainViewModel, t: Trip, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exists = vm.work.trips.any { it.id == t.id }
    var title by remember { mutableStateOf(t.title) }
    var place by remember { mutableStateOf(t.place) }
    var start by remember { mutableStateOf(t.startDate) }
    var end by remember { mutableStateOf(t.endDate) }
    var abroad by remember { mutableStateOf(t.abroad) }
    var budget by remember { mutableStateOf(if (t.budget > 0) SplitMath.plain(t.budget) else "") }
    var note by remember { mutableStateOf(t.note) }
    var people by remember { mutableStateOf(t.people.joinToString(", ")) }
    var currency by remember { mutableStateOf(t.currency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Поездка" else "Новая поездка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, placeholder = { Text("Отпуск в Сочи") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(place, { place = it }, label = { Text("Куда") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, start) { start = it; if (end.isBefore(it)) end = it } }) { Text("С ${Fmt.date(start)}") }
                    OutlinedButton(onClick = { pickDate(ctx, end) { if (!it.isBefore(start)) end = it } }) { Text("по ${Fmt.date(end)}") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(abroad, { abroad = it }); Text("За границу") }
                if (abroad) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("USD", "EUR", "TRY", "AED", "CNY", "THB", "GEL", "AMD", "KZT", "BYN").forEach { c -> FilterChip(currency == c, { currency = if (currency == c) "" else c }, label = { Text(c) }) }
                }
                OutlinedTextField(people, { people = it }, label = { Text("Попутчики через запятую") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(budget, { budget = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("Бюджет, ₽") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(trips = w.trips.filter { it.id != t.id }) }; vm.subViews.remove("trip"); onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val nt = t.copy(title = title.trim(), place = place.trim(), start = start.toString(), end = end.toString(), abroad = abroad,
                    budget = num(budget)?.let { Math.round(it * 100) } ?: 0, note = note.trim(),
                    people = people.split(',').map { it.trim() }.filter { it.isNotBlank() && it != "Я" }, currency = if (abroad) currency else "")
                vm.updateWork { w -> w.copy(trips = if (exists) w.trips.map { if (it.id == t.id) nt else it } else w.trips + nt) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ЧТЕНИЕ =======================

@Composable
fun ReadingTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var edit by remember { mutableStateOf<Book?>(null) }
    var goalEdit by remember { mutableStateOf(false) }
    var progressFor by remember { mutableStateOf<Book?>(null) }
    var timerMin by remember { mutableStateOf(0) }
    var freeSearch by remember { mutableStateOf(false) }
    val ctxR = androidx.compose.ui.platform.LocalContext.current
    val openBook = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.calstats.ui.importEbook(ctxR, vm, uri)
    }
    // Все загруженные книги заранее готовим к чтению — открываются мгновенно
    androidx.compose.runtime.LaunchedEffect(vm.work.readBooks.count { it.isEbook }) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            vm.work.readBooks.filter { it.isEbook && it.status != BookStatus.DONE }.forEach { b ->
                if (!app.calstats.ui.BookPrep.ready(ctxR, b.id)) runCatching { app.calstats.ui.BookPrep.prepare(ctxR, b.id, java.io.File(ctxR.filesDir, b.file!!), b.format) }
            }
        }
    }
    // Таймер чтения: какая книга и с какого момента
    var timing by rememberSaveable { mutableStateOf<String?>(null) }
    var timingStart by rememberSaveable { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(timing) { while (timing != null) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val w = vm.work
    val year = vm.today.year
    val done = app.calstats.work.Reading.doneThisYear(w, year)
    ModuleScaffold("Чтение", snackbar, fab = "Книга" to { edit = Book(newId(), "") }) {
        item {
            Flat(onClick = { goalEdit = true }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tip("Цель на $year год")
                    Text("$done из ${w.readingGoal}", style = MaterialTheme.typography.displaySmall)
                    Bar(done.toFloat() / maxOf(1, w.readingGoal), moduleColor(Tab.READING))
                    val s = app.calstats.work.Reading.streak(w, vm.today)
                    if (s > 0) Tip("🔥 Читаете $s ${Fmt.plural(s.toLong(), "день", "дня", "дней")} подряд")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { freeSearch = true }, Modifier.weight(1f)) { Text("🔎 Бесплатные книги") }
                OutlinedButton(onClick = { openBook.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/zip", "text/plain", "application/octet-stream")) }, Modifier.weight(1f)) { Text("📂 Открыть файл") }
            }
        }
        if (w.readBooks.isEmpty()) item { Tip("Добавьте книгу или курс. Отмечайте, сколько прочитали, — приложение посчитает прогресс, серию дней и цель на год.") }
        BookStatus.entries.forEach { st ->
            val list = w.readBooks.filter { it.status == st }.let { l -> if (st == BookStatus.DONE) l.sortedByDescending { it.finished } else l.sortedByDescending { it.created } }
            if (list.isNotEmpty()) {
                item { Head("${st.title} · ${list.size}") }
                items(list, key = { it.id }) { b ->
                    Flat(onClick = { edit = b }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${b.kind.emoji} ${b.title}", style = MaterialTheme.typography.titleSmall)
                                    if (b.author.isNotBlank()) Tip(b.author)
                                }
                                if (b.isEbook) Button(onClick = { vm.push(app.calstats.Screen.Reader(b.id)) }) { Text("📖 Читать") }
                                else if (st == BookStatus.ACTIVE) {
                                    if (timing == b.id) {
                                        val sec = (now - timingStart) / 1000
                                        TextButton(onClick = { timerMin = ((sec + 30) / 60).toInt(); timing = null; progressFor = b }) { Text("⏹ ${sec / 60}:${"%02d".format(sec % 60)}") }
                                    } else TextButton(onClick = { timing = b.id; timingStart = System.currentTimeMillis() }, enabled = timing == null) { Text("⏱") }
                                    OutlinedButton(onClick = { timerMin = 0; progressFor = b }) { Text("+ прочитал") }
                                }
                                if (st == BookStatus.DONE && b.rating > 0) Text("★".repeat(b.rating))
                            }
                            if (b.isEbook && b.total > 0) {
                                Bar(b.progress, moduleColor(Tab.READING))
                                Tip("${(b.progress * 100).toInt()}% прочитано" + (if (b.bookmarks.isNotEmpty()) " · 🔖 ${b.bookmarks.size}" else "") + if (b.minutes > 0) " · ${b.minutes / 60} ч ${b.minutes % 60} мин" else "")
                            } else if (st == BookStatus.ACTIVE && b.total > 0) {
                                Bar(b.progress, moduleColor(Tab.READING))
                                Tip("${b.current} из ${b.total} ${b.kind.unit} · ${(b.progress * 100).toInt()}%" + if (b.minutes > 0) " · ${b.minutes / 60} ч ${b.minutes % 60} мин чтения" else "")
                            }
                            b.quotes.lastOrNull()?.let { q -> Tip("❝ $q") }
                        }
                    }
                }
            }
        }
    }
    if (freeSearch) app.calstats.ui.FreeBooksDialog(vm) { freeSearch = false }
    if (goalEdit) {
        var g by remember { mutableStateOf(w.readingGoal.toString()) }
        AlertDialog(onDismissRequest = { goalEdit = false }, title = { Text("Сколько книг за год") },
            text = { OutlinedTextField(g, { g = it.filter(Char::isDigit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = { g.toIntOrNull()?.takeIf { it > 0 }?.let { n -> vm.updateWork { it.copy(readingGoal = n) } }; goalEdit = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { goalEdit = false }) { Text("Отмена") } })
    }
    progressFor?.let { b ->
        var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { progressFor = null }, title = { Text(b.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tip("Сколько ${b.kind.unit} прочитали сегодня, или на какой вы сейчас")
                    if (timerMin > 0) Tip("⏱ Читали $timerMin мин")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10, 20, 30, 50).forEach { k -> FilterChip(false, { n = k.toString() }, label = { Text("+$k") }) } }
                    OutlinedTextField(n, { n = it.filter(Char::isDigit) }, label = { Text("Прочитал сегодня") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = {
                val add = n.toIntOrNull() ?: 0
                if (add > 0 || timerMin > 0) vm.updateWork { w0 ->
                    val cur = (b.current + add).let { if (b.total > 0) it.coerceAtMost(b.total) else it }
                    val fin = b.total in 1..cur
                    w0.copy(
                        readBooks = w0.readBooks.map { if (it.id == b.id) it.copy(current = cur, status = if (fin) BookStatus.DONE else it.status, finished = if (fin) vm.today.toString() else it.finished, minutes = it.minutes + timerMin) else it },
                        readLogs = w0.readLogs + ReadLog(vm.today.toString(), b.id, add, timerMin),
                    )
                }
                if (b.total in 1..(b.current + (n.toIntOrNull() ?: 0))) vm.message = "🎉 «${b.title}» прочитана!"
                progressFor = null
            }) { Text("Записать") } },
            dismissButton = { TextButton(onClick = { progressFor = null }) { Text("Отмена") } })
    }
    edit?.let { b -> ReadBookDialog(vm, b) { edit = null } }
}

@Composable
private fun ReadBookDialog(vm: MainViewModel, b: Book, onDismiss: () -> Unit) {
    val exists = vm.work.readBooks.any { it.id == b.id }
    var title by remember { mutableStateOf(b.title) }
    var author by remember { mutableStateOf(b.author) }
    var kind by remember { mutableStateOf(b.kind) }
    var total by remember { mutableStateOf(if (b.total > 0) b.total.toString() else "") }
    var current by remember { mutableStateOf(if (b.current > 0) b.current.toString() else "") }
    var status by remember { mutableStateOf(if (exists) b.status else BookStatus.ACTIVE) }
    var rating by remember { mutableStateOf(b.rating) }
    var note by remember { mutableStateOf(b.note) }
    var quotes by remember { mutableStateOf(b.quotes) }
    var quote by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun scanIsbn() {
        val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13).build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan().addOnSuccessListener { code ->
            val isbn = code.rawValue.orEmpty()
            scope.launch {
                val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.calstats.Net.isbn(isbn) }.getOrNull() }
                if (info == null) vm.message = "Книгу по штрихкоду не нашёл — впишите вручную"
                else { title = info.title; author = info.author; if (info.pages > 0) total = info.pages.toString(); kind = BookKind.BOOK }
            }
        }.addOnFailureListener { vm.message = "Сканер недоступен" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Книга" else "Новая книга или курс") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { BookKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text("${k.emoji} ${k.title}") }) } }
                if (!exists) OutlinedButton(onClick = { scanIsbn() }) { Text("📷 Штрихкод на обложке (ISBN)") }
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(author, { author = it }, label = { Text(if (kind == BookKind.COURSE) "Школа / автор" else "Автор") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(current, { current = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Сейчас") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(total, { total = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Всего ${kind.unit}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { BookStatus.entries.forEach { s -> FilterChip(status == s, { status = s }, label = { Text(s.title) }) } }
                if (status == BookStatus.DONE) Row { (1..5).forEach { i -> Text(if (i <= rating) "★" else "☆", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { rating = i }.padding(2.dp)) } }
                OutlinedTextField(note, { note = it }, label = { Text("Мысли") }, minLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Text("Цитаты", style = MaterialTheme.typography.labelLarge)
                quotes.forEach { q -> Row { Text("❝ $q", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("✕", Modifier.clickable { quotes = quotes - q }.padding(4.dp)) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(quote, { quote = it }, Modifier.weight(1f), placeholder = { Text("Новая цитата") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    TextButton(enabled = quote.isNotBlank(), onClick = { quotes = quotes + quote.trim(); quote = "" }) { Text("+") }
                }
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(readBooks = w.readBooks.filter { it.id != b.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val today = vm.today.toString()
                val nb = b.copy(title = title.trim(), author = author.trim(), kind = kind, total = total.toIntOrNull() ?: 0, current = current.toIntOrNull() ?: 0,
                    status = status, rating = rating, note = note.trim(), quotes = quotes,
                    started = b.started ?: if (status == BookStatus.ACTIVE) today else null,
                    finished = if (status == BookStatus.DONE) (b.finished ?: today) else null)
                vm.updateWork { w -> w.copy(readBooks = if (exists) w.readBooks.map { if (it.id == b.id) nb else it } else w.readBooks + nb) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ДЕЛА ПО ДОМУ =======================

@Composable
fun ChoresTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var edit by remember { mutableStateOf<Chore?>(null) }
    val w = vm.work
    val today = vm.today
    ModuleScaffold("Дела по дому", snackbar, fab = "Дело" to { edit = Chore(newId(), "") }) {
        if (w.chores.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tip("Регулярные дела: уборка, фильтры, сезонное. Отметили «Сделано» — следующий раз посчитается сам. Можно поставить очередь: тогда дело по кругу переходит к следующему в семье.")
                OutlinedButton(onClick = { vm.updateWork { it.copy(chores = Chores.STARTERS.map { c -> c.copy(id = newId()) }) } }) { Text("Добавить типовые дела") }
            }
        }
        if (w.chores.isNotEmpty() && w.chores.none { it.months.isNotEmpty() }) item {
            TextButton(onClick = { vm.updateWork { it.copy(chores = it.chores + Chores.SEASONAL.map { c -> c.copy(id = newId()) }) } }) { Text("+ Добавить сезонные дела (окна, вещи, кондиционер)") }
        }
        val sorted = w.chores.filter { it.inSeason(today) }.sortedBy { it.daysLeft(today) }
        val off = w.chores.filter { !it.inSeason(today) }
        val groups = listOf(
            "Сегодня и просрочено" to sorted.filter { it.daysLeft(today) <= 0 },
            "На этой неделе" to sorted.filter { it.daysLeft(today) in 1..7 },
            "Позже" to sorted.filter { it.daysLeft(today) > 7 },
            "Не сезон" to off,
        )
        groups.forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item { Head(title) }
                items(list, key = { it.id }) { c ->
                    val left = c.daysLeft(today)
                    Flat(onClick = { edit = c }) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(c.room.takeIf { it.isNotBlank() },
                                        when { c.lastDone == null -> "ещё не делали"; left < 0 -> "просрочено на ${-left} дн."; left == 0L -> "сегодня"; else -> "через $left дн." },
                                        "раз в ${c.everyDays} дн.",
                                        c.whoseTurn()?.let { id -> "очередь: ${w.person(id)?.name ?: "?"}" }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                vm.updateWork { x -> x.copy(chores = x.chores.map { if (it.id == c.id) Chores.done(it, today) else it }) }
                                vm.message = "✓ ${c.title}"
                            }) { Text("Сделано") }
                        }
                    }
                }
            }
        }
    }
    edit?.let { c -> ChoreDialog(vm, c) { edit = null } }
}

@Composable
private fun ChoreDialog(vm: MainViewModel, c: Chore, onDismiss: () -> Unit) {
    val exists = vm.work.chores.any { it.id == c.id }
    var title by remember { mutableStateOf(c.title) }
    var room by remember { mutableStateOf(c.room) }
    var every by remember { mutableStateOf(c.everyDays.toString()) }
    var people by remember { mutableStateOf(c.people) }
    var months by remember { mutableStateOf(c.months) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Дело" else "Новое дело") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Что сделать") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Chores.ROOMS.forEach { r -> FilterChip(room == r, { room = if (room == r) "" else r }, label = { Text(r) }) } }
                OutlinedTextField(every, { every = it.filter(Char::isDigit) }, label = { Text("Раз в сколько дней") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(1, 2, 7, 14, 30, 90, 180).forEach { d -> FilterChip(every == d.toString(), { every = d.toString() }, label = { Text(if (d == 1) "каждый день" else "$d дн.") }) } }
                Text("Только в месяцы (сезонное)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..12).forEach { m -> FilterChip(m in months, { months = if (m in months) months - m else (months + m).sorted() }, label = { Text(Fmt.monthName(m).take(3)) }) }
                }
                Text("Очередь (по кругу)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.work.everyone.forEach { p -> FilterChip(p.id in people, { people = if (p.id in people) people - p.id else people + p.id }, label = { Text(p.name) }) }
                }
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(chores = w.chores.filter { it.id != c.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val nc = c.copy(title = title.trim(), room = room, everyDays = every.toIntOrNull()?.coerceAtLeast(1) ?: 7, people = people, months = months)
                vm.updateWork { w -> w.copy(chores = if (exists) w.chores.map { if (it.id == c.id) nc else it } else w.chores + nc) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= РАСТЕНИЯ =======================

@Composable
fun PlantsTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var edit by remember { mutableStateOf<Plant?>(null) }
    val w = vm.work
    val today = vm.today
    ModuleScaffold("Растения", snackbar, fab = "Растение" to { edit = Plant(newId(), "") }) {
        if (w.plants.isEmpty()) item { Tip("Добавьте растения и как часто их поливать — приложение напомнит утром, кого полить сегодня.") }
        val due = w.plants.filter { it.waterIn(today) <= 0 }
        if (due.size > 1) item {
            Button(onClick = { vm.updateWork { x -> x.copy(plants = x.plants.map { if (it.waterIn(today) <= 0) it.copy(lastWater = today.toString()) else it }) }; vm.message = "💧 Полито: ${due.size}" },
                modifier = Modifier.fillMaxWidth()) { Text("💧 Полил все (${due.size})") }
        }
        items(w.plants.sortedBy { it.waterIn(today) }, key = { it.id }) { p ->
            val wi = p.waterIn(today)
            val fi = p.feedIn(today)
            Flat(onClick = { edit = p }) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.place.isNotBlank()) " · ${p.place}" else "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            (if (wi <= 0) "💧 полить сегодня" else "💧 через $wi дн.") + (fi?.let { if (it <= 0) " · 🌱 подкормить" else "" } ?: "") +
                                (p.repotIn(today)?.let { if (it <= 0) " · 🪴 пора пересадить" else "" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (wi < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (fi != null && fi <= 0) TextButton(onClick = { vm.updateWork { x -> x.copy(plants = x.plants.map { if (it.id == p.id) it.copy(lastFeed = today.toString()) else it }) } }) { Text("🌱") }
                    p.repotIn(today)?.takeIf { it <= 0 }?.let { TextButton(onClick = { vm.updateWork { x -> x.copy(plants = x.plants.map { if (it.id == p.id) it.copy(lastRepot = today.toString()) else it }) }; vm.message = "🪴 Пересажено" }) { Text("Пересадил") } }
                    TextButton(onClick = { vm.updateWork { x -> x.copy(plants = x.plants.map { if (it.id == p.id) it.copy(lastWater = today.toString()) else it }) } }) { Text("Полил") }
                }
            }
        }
    }
    edit?.let { p -> PlantDialog(vm, p) { edit = null } }
}

@Composable
private fun PlantDialog(vm: MainViewModel, p: Plant, onDismiss: () -> Unit) {
    val exists = vm.work.plants.any { it.id == p.id }
    var name by remember { mutableStateOf(p.name) }
    var place by remember { mutableStateOf(p.place) }
    var emoji by remember { mutableStateOf(p.emoji) }
    var water by remember { mutableStateOf(p.waterDays.toString()) }
    var feed by remember { mutableStateOf(p.feedDays?.toString() ?: "") }
    var repot by remember { mutableStateOf(p.repotMonths?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "Растение" else "Новое растение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!exists) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Plants.PRESETS.forEach { (n, d) -> FilterChip(name == n, { name = n; water = d.toString() }, label = { Text(n) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(place, { place = it }, label = { Text("Где стоит") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("🪴", "🌵", "🌿", "🌸", "🌱", "🌳", "🌺", "🍀").forEach { e -> FilterChip(emoji == e, { emoji = e }, label = { Text(e) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(water, { water = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Полив, дн.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(feed, { feed = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Подкормка, дн.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(repot, { repot = it.filter(Char::isDigit) }, label = { Text("Пересадка раз в N месяцев (обычно 12–24)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (exists) TextButton(onClick = { vm.updateWork { w -> w.copy(plants = w.plants.filter { it.id != p.id }) }; onDismiss() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val np = p.copy(name = name.trim(), place = place.trim(), emoji = emoji, waterDays = water.toIntOrNull()?.coerceAtLeast(1) ?: 7, feedDays = feed.toIntOrNull()?.takeIf { it > 0 },
                    lastWater = p.lastWater ?: vm.today.toString(), repotMonths = repot.toIntOrNull()?.takeIf { it > 0 }, lastRepot = p.lastRepot ?: vm.today.toString())
                vm.updateWork { w -> w.copy(plants = if (exists) w.plants.map { if (it.id == p.id) np else it } else w.plants + np) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


/** Таймер отдыха между подходами: вибрация в конце. */
@Composable
private fun RestTimer() {
    val ctx = LocalContext.current
    var left by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(left > 0) {
        while (left > 0) { kotlinx.coroutines.delay(1000); left-- }
    }
    androidx.compose.runtime.LaunchedEffect(left) {
        if (left == 0) return@LaunchedEffect
        if (left == 1) {
            kotlinx.coroutines.delay(1000)
            runCatching {
                val v = if (android.os.Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(android.os.VibratorManager::class.java)).defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(android.os.Vibrator::class.java)
                v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (left > 0) "⏱ Отдых ${left / 60}:${"%02d".format(left % 60)}" else "⏱ Отдых:", style = MaterialTheme.typography.labelLarge)
        if (left > 0) TextButton(onClick = { left = 0 }) { Text("Стоп") }
        else listOf(60, 90, 120, 180).forEach { s -> TextButton(onClick = { left = s }) { Text(if (s < 120) "$s с" else "${s / 60} мин") } }
    }
}
