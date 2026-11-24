package app.calstats

import android.content.Context
import app.calstats.work.WorkStore
import app.calstats.work.Workspace
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Корзина: всё удалённое из любого модуля хранится 30 дней.
 * Работает по разнице данных до и после изменения — без правок в каждом модуле.
 */
@Serializable
data class TrashEntry(
    val id: String,
    val time: Long,
    val label: String,
    val items: Map<String, List<JsonElement>>,
) {
    val count: Int get() = items.values.sumOf { it.size }
}

object Trash {
    private const val DAYS = 30
    private const val MAX = 80
    private val FIELDS = listOf("people", "groups", "projects", "sprints", "issues", "books", "expenses", "wishLists", "wishes", "notes", "accounts", "finCategories", "transactions", "budgets", "recurrings", "goals", "habits", "foodLog", "customFoods", "waterLog", "weightLog", "documents", "counterparties", "debts", "fasts", "meds", "cycles", "shopLists", "shopItems", "lifeGoals", "cars", "carEvents", "meters", "meterReadings", "pets", "petEvents", "cards")
    val NAMES = mapOf(
        "people" to "человек",
        "groups" to "группа",
        "projects" to "проект",
        "sprints" to "спринт",
        "issues" to "задача",
        "books" to "общий счёт",
        "expenses" to "общий расход",
        "wishLists" to "список желаний",
        "wishes" to "желание",
        "notes" to "заметка",
        "accounts" to "счёт",
        "finCategories" to "категория",
        "transactions" to "операция",
        "budgets" to "бюджет",
        "recurrings" to "регулярный платёж",
        "goals" to "накопление",
        "habits" to "привычка",
        "foodLog" to "еда",
        "customFoods" to "продукт",
        "waterLog" to "вода",
        "weightLog" to "вес",
        "documents" to "документ",
        "counterparties" to "контрагент",
        "debts" to "долг",
        "fasts" to "голодание",
        "meds" to "лекарство",
        "cycles" to "цикл",
        "shopLists" to "список покупок",
        "shopItems" to "покупка",
        "lifeGoals" to "цель",
        "cars" to "авто",
        "carEvents" to "запись авто",
        "meters" to "счётчик",
        "meterReadings" to "показание",
        "pets" to "питомец",
        "petEvents" to "запись питомца",
        "cards" to "карта",
    )

    fun count(w: Workspace): Int = w.people.size + w.groups.size + w.projects.size + w.sprints.size + w.issues.size + w.books.size + w.expenses.size + w.wishLists.size + w.wishes.size + w.notes.size + w.accounts.size + w.finCategories.size + w.transactions.size + w.budgets.size + w.recurrings.size + w.goals.size + w.habits.size + w.foodLog.size + w.customFoods.size + w.waterLog.size + w.weightLog.size + w.documents.size + w.counterparties.size + w.debts.size + w.fasts.size + w.meds.size + w.cycles.size + w.shopLists.size + w.shopItems.size + w.lifeGoals.size + w.cars.size + w.carEvents.size + w.meters.size + w.meterReadings.size + w.pets.size + w.petEvents.size + w.cards.size

    private fun file(ctx: Context) = File(ctx.filesDir, "trash.json")
    private val ser = ListSerializer(TrashEntry.serializer())

    fun load(ctx: Context): List<TrashEntry> = runCatching {
        WorkStore.json.decodeFromString(ser, file(ctx).readText())
    }.getOrDefault(emptyList()).filter { System.currentTimeMillis() - it.time < DAYS * 86_400_000L }

    private fun save(ctx: Context, list: List<TrashEntry>) = runCatching {
        file(ctx).writeText(WorkStore.json.encodeToString(ser, list.sortedByDescending { it.time }.take(MAX)))
    }

    private fun idOf(e: JsonElement) = (e as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull }

    private fun titleOf(e: JsonElement): String {
        val o = e as? JsonObject ?: return ""
        for (k in listOf("title", "name", "store", "note", "body")) {
            val v = (o[k] as? JsonPrimitive)?.contentOrNull
            if (!v.isNullOrBlank()) return v.lineSequence().first().take(60)
        }
        return ""
    }

    /** Что удалено между old и new. null — ничего. */
    fun diff(old: Workspace, new: Workspace): TrashEntry? {
        val a = WorkStore.json.encodeToJsonElement(Workspace.serializer(), old).jsonObject
        val b = WorkStore.json.encodeToJsonElement(Workspace.serializer(), new).jsonObject
        val removed = LinkedHashMap<String, List<JsonElement>>()
        for (f in FIELDS) {
            val after = (b[f] as? JsonArray)?.mapNotNull(::idOf)?.toHashSet() ?: continue
            val gone = (a[f] as? JsonArray).orEmpty().filter { e -> idOf(e)?.let { it !in after } == true }
            if (gone.isNotEmpty()) removed[f] = gone
        }
        if (removed.isEmpty()) return null
        // Подпись: главное удалённое (задача, а не её комментарии; долг, а не платёж)
        val (mainField, mainItems) = removed.entries.first()
        val name = NAMES[mainField] ?: "запись"
        val t = titleOf(mainItems.first())
        val total = removed.values.sumOf { it.size }
        val label = name.replaceFirstChar { it.uppercase() } + (if (t.isNotBlank()) " «$t»" else "") +
            (if (total > 1) " и ещё ${total - 1}" else "")
        return TrashEntry(app.calstats.work.newId(), System.currentTimeMillis(), label, removed)
    }

    fun add(ctx: Context, e: TrashEntry) = save(ctx, listOf(e) + load(ctx))
    fun remove(ctx: Context, id: String) = save(ctx, load(ctx).filter { it.id != id })
    fun clear(ctx: Context) = save(ctx, emptyList())

    /** Возвращает удалённое на место (без дублей). */
    fun restore(cur: Workspace, e: TrashEntry): Workspace {
        val o = WorkStore.json.encodeToJsonElement(Workspace.serializer(), cur).jsonObject.toMutableMap()
        for ((f, list) in e.items) {
            val arr = (o[f] as? JsonArray).orEmpty()
            val have = arr.mapNotNull(::idOf).toHashSet()
            o[f] = JsonArray(arr + list.filter { idOf(it) !in have })
        }
        return WorkStore.json.decodeFromJsonElement(Workspace.serializer(), JsonObject(o))
    }
}
