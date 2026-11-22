package app.calstats

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.calstats.work.DebtMath
import app.calstats.work.HabitKind
import app.calstats.work.HabitMath
import app.calstats.work.ME
import app.calstats.work.SplitMath
import app.calstats.work.Status
import app.calstats.work.WorkStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Все уведомления приложения:
 *  • утренняя сводка — события дня, задачи, платежи, долги, документы, привычки;
 *  • напоминания о событиях календаря за N минут;
 *  • вечером — неотмеченные привычки и задачи на завтра.
 */
object Notify {
    const val ACTION_MORNING = "app.calstats.N_MORNING"
    const val ACTION_EVENING = "app.calstats.N_EVENING"
    const val ACTION_EVENT = "app.calstats.N_EVENT"
    const val ACTION_RESCAN = "app.calstats.N_RESCAN"
    private const val CH_DAY = "daily"
    private const val CH_EVENTS = "events"

    class Settings(ctx: Context) {
        private val p = ctx.getSharedPreferences("notify", Context.MODE_PRIVATE)
        var morning: Boolean
            get() = p.getBoolean("morning", true)
            set(v) = p.edit().putBoolean("morning", v).apply()
        var morningTime: LocalTime
            get() = LocalTime.ofSecondOfDay(p.getInt("morning_t", 8 * 3600).toLong())
            set(v) = p.edit().putInt("morning_t", v.toSecondOfDay()).apply()
        var evening: Boolean
            get() = p.getBoolean("evening", true)
            set(v) = p.edit().putBoolean("evening", v).apply()
        var eveningTime: LocalTime
            get() = LocalTime.ofSecondOfDay(p.getInt("evening_t", 21 * 3600).toLong())
            set(v) = p.edit().putInt("evening_t", v.toSecondOfDay()).apply()
        var events: Boolean
            get() = p.getBoolean("events", true)
            set(v) = p.edit().putBoolean("events", v).apply()
        var before: Int
            get() = p.getInt("before", 15)
            set(v) = p.edit().putInt("before", v).apply()
    }

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_DAY) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_DAY, "Сводка дня", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Утром — план на день, вечером — что не отмечено"
            })
        }
        if (nm.getNotificationChannel(CH_EVENTS) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "События календаря", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминание перед началом события"
            })
        }
    }

    private fun pi(ctx: Context, action: String, code: Int, extra: (Intent) -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, NotifyReceiver::class.java).setAction(action).also(extra),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun at(am: AlarmManager, time: Long, p: PendingIntent) = am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)

    private fun nextAt(t: LocalTime): Long {
        val now = ZonedDateTime.now()
        var z = now.toLocalDate().atTime(t).atZone(now.zone)
        if (!z.isAfter(now)) z = z.plusDays(1)
        return z.toInstant().toEpochMilli()
    }

    /** Переставить все будильники (запуск приложения, перезагрузка, смена настроек). */
    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val s = Settings(ctx)
        val m = pi(ctx, ACTION_MORNING, 500)
        val e = pi(ctx, ACTION_EVENING, 501)
        am.cancel(m)
        am.cancel(e)
        if (s.morning) at(am, nextAt(s.morningTime), m)
        if (s.evening) at(am, nextAt(s.eveningTime), e)
        // Раз в 3 часа пересматриваем события — чтобы поймать добавленные в Google Календаре.
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 3 * 3600_000L, 3 * 3600_000L, pi(ctx, ACTION_RESCAN, 502))
        scheduleEvents(ctx)
    }

    private fun hasCalendar(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Будильники на события ближайших 26 часов. */
    fun scheduleEvents(ctx: Context) {
        val s = Settings(ctx)
        if (!s.events || !hasCalendar(ctx)) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val list = runCatching { CalendarRepository(ctx).instances(now, now + 26 * 3600_000L) }.getOrDefault(emptyList())
        list.filter { !it.allDay }.forEach { ev ->
            val fire = ev.begin - s.before * 60_000L
            if (fire <= now) return@forEach
            val code = 10_000 + ((ev.eventId * 31 + ev.begin / 60_000) % 1_000_000).toInt()
            at(am, fire, pi(ctx, ACTION_EVENT, code) {
                it.putExtra("id", ev.eventId).putExtra("begin", ev.begin).putExtra("title", ev.title).putExtra("end", ev.end)
            })
        }
    }

    private fun post(ctx: Context, channel: String, id: Int, title: String, lines: List<String>, tab: Tab? = null) {
        if (!DrinkReminder.canNotify(ctx)) return
        channels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 600 + id % 100,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .also { i -> tab?.let { i.putExtra(MainActivity.EXTRA_TAB, it.name) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF1F3A2B.toInt())
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun time(ms: Long) = Fmt.time(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime())

    /** Текст утренней сводки (используется и в Telegram). */
    fun morningLines(ctx: Context, today: LocalDate): List<String> {
        val out = ArrayList<String>()
        val zone = ZoneId.systemDefault()
        if (hasCalendar(ctx)) {
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val evs = runCatching { CalendarRepository(ctx).instances(from, to) }.getOrDefault(emptyList())
                .sortedWith(compareBy({ !it.allDay }, { it.begin }))
            if (evs.isEmpty()) out += "📅 Событий нет"
            evs.take(8).forEach { e -> out += "📅 " + (if (e.allDay) "" else time(e.begin) + " ") + e.title }
            if (evs.size > 8) out += "   и ещё ${evs.size - 8}"
        }
        val w = WorkStore(ctx).load()
        val open = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
        open.filter { it.dueDate == today }.take(6).forEach { out += "📌 ${it.title}" }
        open.count { it.dueDate?.isBefore(today) == true }.takeIf { it > 0 }?.let { out += "⚠ Просроченных задач: $it" }
        w.recurrings.filter { it.active && it.next == today }.forEach { out += "🔁 Платёж: ${it.name} — ${SplitMath.money(it.amount)}" }
        out += DebtMath.reminders(w, today)
        out += DocsReminder.lines(ctx, today)
        val habits = w.habits.filter { !it.archived && it.kind == HabitKind.DO && it.scheduled(today) }
        if (habits.isNotEmpty()) out += "✅ Привычки на сегодня: ${habits.size}"
        return out
    }

    fun eveningLines(ctx: Context, today: LocalDate): List<String> {
        val w = WorkStore(ctx).load()
        val out = ArrayList<String>()
        val habits = w.habits.filter { !it.archived && it.kind == HabitKind.DO && it.scheduled(today) }
        val left = habits.filter { !HabitMath.done(it, HabitMath.values(w.habitLogs, it.id), today) }
        if (left.isNotEmpty()) out += "✅ Не отмечены привычки: " + left.joinToString(", ") { it.name }
        val tomorrow = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE && it.dueDate == today.plusDays(1) }
        if (tomorrow.isNotEmpty()) out += "📌 Завтра срок: " + tomorrow.take(4).joinToString(", ") { it.title }
        w.recurrings.filter { it.active && it.next == today.plusDays(1) }.forEach { out += "🔁 Завтра платёж: ${it.name} — ${SplitMath.money(it.amount)}" }
        val food = w.foodLog.count { it.date == today.toString() }
        if (w.healthProfile != null && food == 0) out += "🍽 Дневник питания за сегодня пуст"
        return out
    }

    fun handle(ctx: Context, intent: Intent) {
        val today = LocalDate.now()
        when (intent.action) {
            ACTION_MORNING -> {
                val lines = morningLines(ctx, today)
                if (lines.isNotEmpty()) post(ctx, CH_DAY, 4501, "☀ План на ${Fmt.dayTitle(today, today).lowercase()}", lines, Tab.HOME)
                scheduleAll(ctx)
            }
            ACTION_EVENING -> {
                val lines = eveningLines(ctx, today)
                if (lines.isNotEmpty()) post(ctx, CH_DAY, 4502, "🌙 Перед сном", lines, Tab.HOME)
                scheduleAll(ctx)
            }
            ACTION_RESCAN -> scheduleEvents(ctx)
            ACTION_EVENT -> {
                val id = intent.getLongExtra("id", 0)
                val begin = intent.getLongExtra("begin", 0)
                // Событие могли удалить или перенести — проверяем.
                val still = hasCalendar(ctx) && runCatching {
                    CalendarRepository(ctx).instances(begin - 60_000, begin + 60_000).any { it.eventId == id && it.begin == begin }
                }.getOrDefault(false)
                if (!still) return
                val mins = ((begin - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                post(
                    ctx, CH_EVENTS, (id % 100_000).toInt() + 20_000,
                    intent.getStringExtra("title").orEmpty().ifBlank { "Событие" },
                    listOf("${time(begin)}–${time(intent.getLongExtra("end", begin))}" + if (mins > 0) " · через $mins мин" else " · начинается"),
                    Tab.CALENDAR,
                )
            }
        }
    }
}

class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Notify.handle(ctx, intent)
}
