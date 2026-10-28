package app.calstats

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Работа с календарём телефона. Android сам синхронизирует его с Google:
 * всё, что мы здесь читаем и записываем, попадает в Google Календарь и обратно.
 */
class CalendarRepository(context: Context) {
    private val cr: ContentResolver = context.contentResolver

    fun calendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val result = mutableListOf<CalendarInfo>()
        cr.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(5)
                result += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Календарь",
                    accountName = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    color = c.getInt(4),
                    writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    visible = c.getInt(6) == 1,
                    primary = !c.isNull(7) && c.getInt(7) == 1,
                )
            }
        }
        return result.sortedWith(compareBy({ it.accountName }, { !it.primary }, { it.name }))
    }

    fun instances(from: Long, to: Long): List<EventInstance> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
        )
        // Удалённые (DELETED=1 ждут отправки на сервер) и отменённые события не показываем.
        val selection = "(${CalendarContract.Instances.STATUS} IS NULL OR " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) AND " +
            "(${CalendarContract.Events.DELETED} IS NULL OR ${CalendarContract.Events.DELETED} = 0)"
        val result = ArrayList<EventInstance>()
        cr.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                result += EventInstance(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    color = c.getInt(5),
                    calendarId = c.getLong(6),
                )
            }
        }
        return result
    }

    fun eventColors(accountName: String, accountType: String): List<EventColorOption> {
        val projection = arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR)
        val selection = "${CalendarContract.Colors.ACCOUNT_NAME}=? AND " +
            "${CalendarContract.Colors.ACCOUNT_TYPE}=? AND ${CalendarContract.Colors.COLOR_TYPE}=?"
        val args = arrayOf(accountName, accountType, CalendarContract.Colors.TYPE_EVENT.toString())
        val result = mutableListOf<EventColorOption>()
        try {
            cr.query(CalendarContract.Colors.CONTENT_URI, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) ?: continue
                    result += EventColorOption(key, c.getInt(1))
                }
            }
        } catch (_: Exception) {
            // У некоторых аккаунтов нет таблицы цветов — тогда только цвет календаря.
        }
        return result.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
    }

    fun event(id: Long): EventDetails? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_COLOR_KEY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.DURATION,
        )
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        cr.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                return EventDetails(
                    id = c.getLong(0),
                    calendarId = c.getLong(1),
                    title = c.getString(2) ?: "",
                    description = c.getString(3) ?: "",
                    dtStart = c.getLong(4),
                    dtEnd = if (c.isNull(5)) null else c.getLong(5),
                    allDay = c.getInt(6) == 1,
                    colorKey = c.getString(7)?.takeIf { it.isNotEmpty() },
                    recurring = !c.getString(8).isNullOrEmpty() || !c.getString(9).isNullOrEmpty(),
                    rrule = c.getString(8)?.takeIf { it.isNotEmpty() },
                    durationMs = parseDuration(c.getString(10)),
                    reminders = reminders(id),
                )
            }
        }
        return null
    }

    fun insert(
        calendarId: Long, title: String, description: String,
        begin: Long, end: Long, allDay: Boolean, colorKey: String?,
        rrule: String?, reminders: List<Int>,
    ): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            putTime(this, begin, end, allDay, rrule)
            if (colorKey != null) put(CalendarContract.Events.EVENT_COLOR_KEY, colorKey)
        }
        val id = cr.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: return null
        setReminders(id, reminders)
        return id
    }

    fun update(
        id: Long, title: String, description: String,
        begin: Long, end: Long, allDay: Boolean, colorKey: String?,
        rrule: String?, reminders: List<Int>,
    ): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            putTime(this, begin, end, allDay, rrule)
            if (colorKey != null) {
                put(CalendarContract.Events.EVENT_COLOR_KEY, colorKey)
            } else {
                putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                putNull(CalendarContract.Events.EVENT_COLOR)
            }
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val ok = cr.update(uri, values, null, null) > 0
        if (ok) setReminders(id, reminders)
        return ok
    }

    /** Повторяющиеся события хранятся с DURATION вместо DTEND — так требует Android. */
    private fun putTime(v: ContentValues, begin: Long, end: Long, allDay: Boolean, rrule: String?) {
        v.put(CalendarContract.Events.DTSTART, begin)
        v.put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        v.put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
        if (rrule != null) {
            v.put(CalendarContract.Events.RRULE, rrule)
            val dur = if (allDay) "P${((end - begin) / 86_400_000L).coerceAtLeast(1)}D"
            else "P${((end - begin) / 1000L).coerceAtLeast(0)}S"
            v.put(CalendarContract.Events.DURATION, dur)
            v.putNull(CalendarContract.Events.DTEND)
        } else {
            v.put(CalendarContract.Events.DTEND, end)
            v.putNull(CalendarContract.Events.RRULE)
            v.putNull(CalendarContract.Events.DURATION)
        }
    }

    fun reminders(eventId: Long): List<Int> {
        val out = mutableListOf<Int>()
        try {
            cr.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID}=?",
                arrayOf(eventId.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) out += c.getInt(0) }
        } catch (_: Exception) {
        }
        return out.filter { it >= 0 }.distinct().sorted()
    }

    private fun setReminders(eventId: Long, minutes: List<Int>) {
        try {
            cr.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID}=?",
                arrayOf(eventId.toString()),
            )
            minutes.distinct().forEach { m ->
                cr.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, m)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }
        } catch (_: Exception) {
        }
    }

    /** Удалить только одно повторение серии (как «Только это событие» в Google). */
    fun deleteInstance(eventId: Long, instanceBegin: Long): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId)
        return cr.insert(uri, values) != null
    }

    private fun parseDuration(d: String?): Long? {
        if (d.isNullOrBlank()) return null
        // RFC 2445: P1D, PT1H30M, P3600S, P1W
        val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""").find(d.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return ((g(2) * 7 + g(3)) * 86_400L + g(4) * 3600L + g(5) * 60L + g(6)) * 1000L
    }

    fun delete(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return cr.delete(uri, null, null) > 0
    }

    /** Просим Android немедленно синхронизировать календари с Google. */
    fun requestSync(calendars: List<CalendarInfo>) {
        calendars
            .filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL && it.accountName.isNotEmpty() }
            .map { it.accountName to it.accountType }
            .distinct()
            .forEach { (name, type) ->
                try {
                    val extras = Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    }
                    ContentResolver.requestSync(Account(name, type), CalendarContract.AUTHORITY, extras)
                } catch (_: Exception) {
                    // Синхронизацию всё равно выполнит Android по своему расписанию.
                }
            }
    }
}
