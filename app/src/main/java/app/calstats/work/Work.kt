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
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && checklist.none { it.text.isNotBlank() }

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
) {
    fun person(id: String?): Person? = when (id) {
        null -> null
        ME -> Person(ME, meName, 0xFFE4572E.toInt())
        else -> people.firstOrNull { it.id == id }
    }

    /** «Я» + все люди. */
    val everyone: List<Person> get() = listOf(Person(ME, meName, 0xFFE4572E.toInt())) + people

    fun project(id: String?) = projects.firstOrNull { it.id == id }
    fun issue(id: String?) = issues.firstOrNull { it.id == id }
    fun key(i: Issue) = (project(i.projectId)?.key ?: "?") + "-" + i.number
}

/** Палитра для людей, проектов и групп. */
val PALETTE = listOf(
    0xFF4C9AFF, 0xFF36B37E, 0xFFE5493A, 0xFF8777D9, 0xFFFFAB00, 0xFF2FB5C6,
    0xFFE4572E, 0xFF6554C0, 0xFF00875A, 0xFFDE350B, 0xFF0065FF, 0xFFFF7452,
).map { it.toInt() }
