package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/*
 * Модель таск-трекера, групп и общих расходов.
 * У всех сущностей строковые id (UUID) и отметка времени изменения — чтобы позже
 * подключить облачную синхронизацию без переделки данных.
 */

fun newId(): String = UUID.randomUUID().toString()

const val ME = "me"

@Serializable
data class Person(
    val id: String,
    val name: String,
    val color: Int,
    val note: String = "",
    val phone: String = "",
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val color: Int,
)

@Serializable
data class Project(
    val id: String,
    val key: String,
    val name: String,
    val color: Int,
    val groupId: String? = null,
    val description: String = "",
    val archived: Boolean = false,
    val nextNumber: Int = 1,
    val created: Long = System.currentTimeMillis(),
)

enum class IssueType(val title: String, val color: Long) {
    TASK("Задача", 0xFF4C9AFF),
    STORY("История", 0xFF36B37E),
    BUG("Ошибка", 0xFFE5493A),
    EPIC("Эпик", 0xFF8777D9),
    SUBTASK("Подзадача", 0xFF2FB5C6),
}

enum class Status(val title: String) {
    TODO("К выполнению"),
    IN_PROGRESS("В работе"),
    REVIEW("На проверке"),
    DONE("Готово"),
}

enum class Priority(val title: String, val color: Long, val arrow: String) {
    HIGHEST("Наивысший", 0xFFCD1317, "⇈"),
    HIGH("Высокий", 0xFFE9494A, "↑"),
    MEDIUM("Средний", 0xFFE97F33, "="),
    LOW("Низкий", 0xFF2D8738, "↓"),
    LOWEST("Самый низкий", 0xFF57A55A, "⇊"),
}

@Serializable
data class WorkLog(
    val id: String,
    val personId: String,
    val date: String,
    val minutes: Int,
    val note: String = "",
)

@Serializable
data class Comment(
    val id: String,
    val authorId: String,
    val text: String,
    val created: Long,
)

@Serializable
data class Issue(
    val id: String,
    val projectId: String,
    val number: Int,
    val title: String,
    val description: String = "",
    val type: IssueType = IssueType.TASK,
    val status: Status = Status.TODO,
    val priority: Priority = Priority.MEDIUM,
    val assigneeId: String? = null,
    val reporterId: String? = ME,
    val labels: List<String> = emptyList(),
    val sprintId: String? = null,
    val parentId: String? = null,
    val epicId: String? = null,
    val start: String? = null,
    val due: String? = null,
    val estimateMin: Int? = null,
    val worklogs: List<WorkLog> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val color: Int? = null,
    val order: Double = 0.0,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    /** Повтор: DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY. При выполнении создаётся следующая задача. */
    val repeat: String? = null,
) {
    val dueDate: LocalDate? get() = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val startDate: LocalDate? get() = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val loggedMin: Int get() = worklogs.sumOf { it.minutes }
    val done: Boolean get() = status == Status.DONE
}

enum class SprintState(val title: String) { PLANNED("Запланирован"), ACTIVE("Активный"), CLOSED("Завершён") }

@Serializable
data class Sprint(
    val id: String,
    val projectId: String,
    val name: String,
    val goal: String = "",
    val start: String? = null,
    val end: String? = null,
    val state: SprintState = SprintState.PLANNED,
) {
    val startDate: LocalDate? get() = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val endDate: LocalDate? get() = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

// ---------------- Расходы ----------------

@Serializable
data class SplitBook(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val groupId: String? = null,
    val color: Int = 0xFF4C9AFF.toInt(),
    val created: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

enum class SplitMode(val title: String) {
    EQUAL("Поровну"),
    SHARES("Доли"),
    EXACT("Суммы"),
    ITEMS("По чеку"),
}

enum class Category(val title: String, val emoji: String) {
    FOOD("Еда", "🍽"),
    HOME("Жильё", "🏠"),
    REPAIR("Ремонт", "🛠"),
    TRANSPORT("Транспорт", "🚕"),
    FUN("Развлечения", "🎉"),
    SHOP("Покупки", "🛍"),
    TRAVEL("Поездки", "✈"),
    OTHER("Другое", "•"),
}

@Serializable
data class ReceiptItem(
    val id: String,
    val name: String,
    val amount: Long,
    val personIds: List<String>,
)

/** Сумма в копейках. Перевод долга (isTransfer) хранится как расход: платит должник, «доля» — у получателя. */
@Serializable
data class Expense(
    val id: String,
    val bookId: String,
    val title: String,
    val amount: Long,
    val payerId: String,
    val date: String,
    val mode: SplitMode = SplitMode.EQUAL,
    val shares: Map<String, Long> = emptyMap(),
    val items: List<ReceiptItem> = emptyList(),
    val servicePercent: Int = 0,
    val category: Category = Category.OTHER,
    val note: String = "",
    val isTransfer: Boolean = false,
    val created: Long = System.currentTimeMillis(),
) {
    val localDate: LocalDate get() = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
}

// ---------------- Желания ----------------

enum class WishStatus(val title: String) {
    WANTED("Хочу"),
    RESERVED("Забронировано"),
    BOUGHT("Куплено"),
    RECEIVED("Подарено"),
}

/** Список желаний: свой (ownerId = ME) или для близкого (подарки ему). */
@Serializable
data class WishList(
    val id: String,
    val title: String,
    val ownerId: String = ME,
    val occasion: String = "",
    val date: String? = null,
    val yearly: Boolean = false,
    val color: Int = 0xFFE4572E.toInt(),
    val emoji: String = "🎁",
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
) {
    val localDate: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@Serializable
data class Wish(
    val id: String,
    val listId: String,
    val title: String,
    val link: String = "",
    val price: Long? = null,
    val note: String = "",
    val priority: Int = 2,
    val status: WishStatus = WishStatus.WANTED,
    val reservedBy: String? = null,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
)

// ---------------- Заметки ----------------

@Serializable
data class CheckItem(val id: String, val text: String, val checked: Boolean = false)

@Serializable
data class NoteFolder(val id: String, val name: String, val color: Int? = null, val order: Int = 0)

@Serializable
data class NoteFile(val id: String, val name: String, val mime: String, val path: String, val size: Long = 0, val durationMs: Long = 0, val created: Long = System.currentTimeMillis()) {
    val isImage get() = mime.startsWith("image/")
    val isAudio get() = mime.startsWith("audio/")
    val emoji get() = when {
        isImage -> "🖼"; isAudio -> "🎙"; mime == "application/pdf" -> "📕"
        mime.contains("word") || name.endsWith(".doc") || name.endsWith(".docx") -> "📘"
        mime.contains("sheet") || mime.contains("excel") || name.endsWith(".xlsx") -> "📗"
        mime.startsWith("video/") -> "🎬"; else -> "📎"
    }
}

enum class NoteRepeat(val title: String) { NONE("Не повторять"), DAY("Каждый день"), WEEK("Каждую неделю"), MONTH("Каждый месяц"), YEAR("Каждый год") }

object NoteRemind {
    /** Следующее время для повторяющегося напоминания (после выполнения). */
    fun next(at: Long, r: NoteRepeat): Long? {
        val z = java.time.ZoneId.systemDefault()
        val t = java.time.Instant.ofEpochMilli(at).atZone(z)
        val n = when (r) {
            NoteRepeat.NONE -> return null
            NoteRepeat.DAY -> t.plusDays(1); NoteRepeat.WEEK -> t.plusWeeks(1)
            NoteRepeat.MONTH -> t.plusMonths(1); NoteRepeat.YEAR -> t.plusYears(1)
        }
        return n.toInstant().toEpochMilli()
    }

    /** Отметить выполненным: повторяющееся переносится на следующий раз. */
    fun complete(n: Note): Note {
        val at = n.remindAt ?: return n.copy(remindDone = true)
        val nx = next(at, n.repeat)
        return if (nx != null) n.copy(remindAt = nx, remindDone = false) else n.copy(remindDone = true)
    }
}

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val checklist: List<CheckItem> = emptyList(),
    val isChecklist: Boolean = false,
    val color: Int? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val labels: List<String> = emptyList(),
    val source: String = "",
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    /** Папка (как в Samsung Notes). */
    val folderId: String? = null,
    /** Оформление текста body (жирный, цвет, размер…) — JSON со списком отрезков. */
    val rich: String = "",
    val files: List<NoteFile> = emptyList(),
    /** Напоминание (как в Samsung Reminder): время, повтор, выполнено, попадает ли в календарь. */
    val remindAt: Long? = null,
    val repeat: NoteRepeat = NoteRepeat.NONE,
    val remindDone: Boolean = false,
    val toCalendar: Boolean = false,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && checklist.none { it.text.isNotBlank() } && files.isEmpty() && remindAt == null

    /** Текст для отправки в другие приложения. */
    fun asText(): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n")
        if (isChecklist) checklist.forEach { append(if (it.checked) "☑ " else "☐ ").append(it.text).append("\n") }
        else append(body)
    }.trim()
}

@Serializable
data class Workspace(
    val version: Int = 1,
    val meName: String = "Я",
    val people: List<Person> = emptyList(),
    val groups: List<Group> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sprints: List<Sprint> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val books: List<SplitBook> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val wishLists: List<WishList> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val notes: List<Note> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val finCategories: List<FinCategory> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurrings: List<Recurring> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val healthProfile: HealthProfile? = null,
    val foodLog: List<FoodEntry> = emptyList(),
    val recentFoods: List<FoodItem> = emptyList(),
    val customFoods: List<FoodItem> = emptyList(),
    val waterLog: List<WaterEntry> = emptyList(),
    val weightLog: List<WeightEntry> = emptyList(),
    val documents: List<Document> = emptyList(),
    val counterparties: List<Counterparty> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val fasts: List<Fast> = emptyList(),
    val meds: List<Med> = emptyList(),
    val medLogs: List<MedLog> = emptyList(),
    val moods: List<Mood> = emptyList(),
    val cycles: List<CycleLog> = emptyList(),
    val shopLists: List<ShopList> = emptyList(),
    val shopItems: List<ShopItem> = emptyList(),
    val lifeGoals: List<LifeGoal> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val cars: List<Car> = emptyList(),
    val carEvents: List<CarEvent> = emptyList(),
    val meters: List<Meter> = emptyList(),
    val meterReadings: List<MeterReading> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val petEvents: List<PetEvent> = emptyList(),
    val cards: List<LoyaltyCard> = emptyList(),
    val cashback: List<Cashback> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val mealPlans: List<MealPlan> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val workouts: List<WorkoutLog> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val readBooks: List<Book> = emptyList(),
    val readLogs: List<ReadLog> = emptyList(),
    val readingGoal: Int = 12,
    val chores: List<Chore> = emptyList(),
    val plants: List<Plant> = emptyList(),
    val noteFolders: List<NoteFolder> = emptyList(),
    val pressure: List<Pressure> = emptyList(),
    val symptoms: List<Symptom> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val shopPrices: List<ShopPrice> = emptyList(),
) {
    fun category(id: String?) = finCategories.firstOrNull { it.id == id }
    fun account(id: String?) = accounts.firstOrNull { it.id == id }

    fun person(id: String?): Person? = when (id) {
        null -> null
        ME -> Person(ME, meName, 0xFF1F3A2B.toInt())
        else -> people.firstOrNull { it.id == id }
    }

    /** «Я» + все люди. */
    val everyone: List<Person> get() = listOf(Person(ME, meName, 0xFF1F3A2B.toInt())) + people

    fun project(id: String?) = projects.firstOrNull { it.id == id }
    fun issue(id: String?) = issues.firstOrNull { it.id == id }
    fun key(i: Issue) = (project(i.projectId)?.key ?: "?") + "-" + i.number
}

/** Палитра для людей, проектов и групп. */
val PALETTE = listOf(
    0xFF4C9AFF, 0xFF36B37E, 0xFFE5493A, 0xFF8777D9, 0xFFFFAB00, 0xFF2FB5C6,
    0xFFE4572E, 0xFF6554C0, 0xFF00875A, 0xFFDE350B, 0xFF0065FF, 0xFFFF7452,
).map { it.toInt() }
