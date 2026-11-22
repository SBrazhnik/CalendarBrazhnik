package app.calstats

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.MonthDay

/** Дни рождения из контактов телефона. */
data class Birthday(val name: String, val day: MonthDay, val year: Int?) {
    fun ageOn(d: LocalDate): Int? = year?.let { d.year - it }
    fun next(from: LocalDate): LocalDate {
        var d = day.atYear(from.year)
        if (d.isBefore(from)) d = day.atYear(from.year + 1)
        return d
    }
}

object Birthdays {
    fun allowed(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Даты в контактах бывают «1990-05-12» и «--05-12» (без года). */
    fun parse(raw: String): Pair<MonthDay, Int?>? {
        val t = raw.trim()
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.let { m ->
            return runCatching { MonthDay.of(m.groupValues[2].toInt(), m.groupValues[3].toInt()) to m.groupValues[1].toInt() }.getOrNull()
        }
        Regex("^--(\\d{2})-?(\\d{2})").find(t)?.let { m ->
            return runCatching { MonthDay.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) to null }.getOrNull()
        }
        Regex("^(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}))?").find(t)?.let { m ->
            return runCatching { MonthDay.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) to m.groupValues[3].toIntOrNull() }.getOrNull()
        }
        return null
    }

    fun load(ctx: Context): List<Birthday> {
        if (!allowed(ctx)) return emptyList()
        val out = ArrayList<Birthday>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val (md, y) = parse(c.getString(1) ?: continue) ?: continue
                    out += Birthday(name, md, y?.takeIf { it in 1900..2100 })
                }
            }
        }
        return out.distinctBy { it.name to it.day }
    }

    fun text(b: Birthday, on: LocalDate): String = b.name + (b.ageOn(on)?.let { " — исполняется $it" } ?: "")
}
