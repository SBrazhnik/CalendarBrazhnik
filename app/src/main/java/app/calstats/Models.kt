package app.calstats

/** Календарь на телефоне (Google или локальный). */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val writable: Boolean,
    val visible: Boolean,
    val primary: Boolean,
) {
    /** Ключ, одинаковый на любом телефоне с тем же аккаунтом (в отличие от id). */
    val key: String get() = "$accountName|$accountType|$name"
}

/** Одно конкретное проведение события (повторяющиеся события дают несколько). */
data class EventInstance(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarId: Long,
)

/** Цвет события, доступный для аккаунта (у Google их 11). */
data class EventColorOption(val key: String, val color: Int)

/** Полные данные события для редактирования. */
data class EventDetails(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val dtStart: Long,
    val dtEnd: Long?,
    val allDay: Boolean,
    val colorKey: String?,
    val recurring: Boolean,
)

/** Название направления, назначенное цвету. */
data class DirectionSetting(
    val color: Int,
    val name: String,
    val hidden: Boolean = false,
)

/** Всё, что приложение хранит само (события хранятся в Google Календаре). */
data class AppData(
    val directions: Map<Int, DirectionSetting> = emptyMap(),
    val excludedCalendars: Set<String> = emptySet(),
    val includeFuture: Boolean = false,
    val lastCalendarKey: String? = null,
)

/** Задача. Хранится в приложении и входит в резервную копию. */
data class TaskItem(
    val id: Long,
    val title: String,
    val notes: String = "",
    val due: java.time.LocalDate? = null,
    val color: Int? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val created: Long = System.currentTimeMillis(),
)
