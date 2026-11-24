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
import app.calstats.work.HealthMore
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
    const val ACTION_WEEKLY = "app.calstats.N_WEEKLY"
    const val ACTION_FOCUS = "app.calstats.N_FOCUS"
    const val ACTION_HABIT = "app.calstats.N_HABIT"
    const val ACTION_HABIT_DONE = "app.calstats.N_HABIT_DONE"
    const val ACTION_MED = "app.calstats.N_MED"
    const val ACTION_MED_DONE = "app.calstats.N_MED_DONE"
    const val ACTION_NOTE = "app.calstats.N_NOTE"
    const val ACTION_NOTE_DONE = "app.calstats.N_NOTE_DONE"
    const val ACTION_NOTE_SNOOZE = "app.calstats.N_NOTE_SNOOZE"
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
        var weekly: Boolean
            get() = p.getBoolean("weekly", true)
            set(v) = p.edit().putBoolean("weekly", v).apply()
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

    /** Напоминания по привычкам: каждая — свой будильник на ближайшее время. */
    fun scheduleHabits(ctx: Context, w: app.calstats.work.Workspace = WorkStore(ctx).load()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        w.habits.forEach { h ->
            val code = 30_000 + (h.id.hashCode() and 0xFFFF)
            val p = pi(ctx, ACTION_HABIT, code) { it.putExtra("habit", h.id) }
            am.cancel(p)
            val t = h.remind?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            if (t != null && !h.archived) at(am, nextAt(t), p)
        }
    }

    /** Напоминания о лекарствах: на каждое время приёма. */
    fun scheduleMeds(ctx: Context, w: app.calstats.work.Workspace = WorkStore(ctx).load()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        w.meds.forEach { m ->
            m.times.forEachIndexed { i, t ->
                val code = 40_000 + ((m.id.hashCode() * 7 + i) and 0x7FFF)
                val p = pi(ctx, ACTION_MED, code) { it.putExtra("med", m.id).putExtra("time", t) }
                am.cancel(p)
                val lt = runCatching { LocalTime.parse(t) }.getOrNull()
                if (m.active && lt != null) at(am, nextAt(lt), p)
            }
        }
    }

    /** Напоминания заметок: точный будильник на каждое активное (ближайшие 200). */
    fun scheduleNotes(ctx: Context, w: app.calstats.work.Workspace = WorkStore(ctx).load()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val p = ctx.getSharedPreferences("note_alarms", Context.MODE_PRIVATE)
        val want = w.notes.filter { it.remindAt != null && !it.remindDone && !it.archived }.sortedBy { it.remindAt }.take(200)
        val old = p.getStringSet("ids", emptySet()).orEmpty()
        (old - want.map { it.id }.toSet()).forEach { id -> am.cancel(pi(ctx, ACTION_NOTE, 60_000 + (id.hashCode() and 0x7FFF)) { it.putExtra("note", id) }) }
        want.forEach { n ->
            val pe = pi(ctx, ACTION_NOTE, 60_000 + (n.id.hashCode() and 0x7FFF)) { it.putExtra("note", n.id) }
            am.cancel(pe)
            val t = n.remindAt!!
            if (t > System.currentTimeMillis() - 60_000) exact(am, maxOf(t, System.currentTimeMillis() + 2000), pe)
        }
        p.edit().putStringSet("ids", want.map { it.id }.toSet()).apply()
    }

    private fun exact(am: AlarmManager, time: Long, p: PendingIntent) {
        val can = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (can) runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p) }.onFailure { at(am, time, p) } else at(am, time, p)
    }

    /** Будильник окончания фокуса (null — отменить). */
    fun focusAlarm(ctx: Context, at: Long?, title: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(ctx, ACTION_FOCUS, 504) { it.putExtra("title", title) }
        am.cancel(p)
        if (at != null) at(am, at, p)
    }

    /** Переставить все будильники (запуск приложения, перезагрузка, смена настроек). */
    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val s = Settings(ctx)
        val m = pi(ctx, ACTION_MORNING, 500)
        val e = pi(ctx, ACTION_EVENING, 501)
        am.cancel(m)
        am.cancel(e)
        runCatching { scheduleNotes(ctx) }
        if (s.morning) at(am, nextAt(s.morningTime), m)
        if (s.evening) at(am, nextAt(s.eveningTime), e)
        val wk = pi(ctx, ACTION_WEEKLY, 503)
        am.cancel(wk)
        if (s.weekly) {
            val now = ZonedDateTime.now()
            var z = now.toLocalDate().plusDays((7L - now.dayOfWeek.value) % 7).atTime(19, 0).atZone(now.zone)
            if (!z.isAfter(now)) z = z.plusWeeks(1)
            at(am, z.toInstant().toEpochMilli(), wk)
        }
        // Раз в 3 часа пересматриваем события — чтобы поймать добавленные в Google Календаре.
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 3 * 3600_000L, 3 * 3600_000L, pi(ctx, ACTION_RESCAN, 502))
        scheduleEvents(ctx)
        scheduleHabits(ctx)
        scheduleMeds(ctx)
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

    private fun post(ctx: Context, channel: String, id: Int, title: String, lines: List<String>, tab: Tab? = null, quick: String? = null) {
        if (!DrinkReminder.canNotify(ctx)) return
        channels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 600 + id % 100,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .also { i -> tab?.let { i.putExtra(MainActivity.EXTRA_TAB, it.name) }; quick?.let { i.putExtra(MainActivity.EXTRA_QUICK, it) } },
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
        Birthdays.load(ctx).filter { it.day == java.time.MonthDay.from(today) }.forEach { out += "🎂 День рождения: " + Birthdays.text(it, today) }
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
        app.calstats.work.Pets.upcoming(w.petEvents, today, 1).forEach { e ->
            out += "${e.type.emoji} ${w.pets.firstOrNull { it.id == e.petId }?.name.orEmpty()}: ${e.type.title.lowercase()}"
        }
        w.cars.forEach { c ->
            app.calstats.work.CarMath.nextService(c, w.carEvents, today)?.let { n ->
                if ((n.daysLeft ?: 99) in 0..7 || (n.kmLeft ?: 9999) in 0..500) out += "🔧 ${c.name}: пора на ТО"
            }
        }
        if (today.dayOfMonth in 20..25 && w.meters.isNotEmpty() &&
            w.meters.any { m -> w.meterReadings.none { it.meterId == m.id && it.date.startsWith(today.toString().take(7)) } }) out += "💧 Передайте показания счётчиков"
        out += DocsReminder.lines(ctx, today)
        w.chores.filter { it.daysLeft(today) <= 0 }.takeIf { it.isNotEmpty() }?.let { l -> out += "🧹 По дому: " + l.take(3).joinToString { it.title.lowercase() } + if (l.size > 3) " и ещё ${l.size - 3}" else "" }
        w.plants.filter { it.waterIn(today) <= 0 }.takeIf { it.isNotEmpty() }?.let { l -> out += "🪴 Полить: " + l.joinToString { it.name } }
        w.trips.filter { app.calstats.work.Trips.daysLeft(it, today) in listOf(1L, 3L, 7L) }.forEach { t ->
            val left = t.packing.count { !it.done }
            out += "✈ Через ${app.calstats.work.Trips.daysLeft(t, today)} дн. «${t.title}»" + if (left > 0) " — собрать ещё $left" else ""
        }
        w.mealPlans.filter { it.date == today.toString() }.sortedBy { it.slot.ordinal }.takeIf { it.isNotEmpty() }?.let { l ->
            out += "🍽 Меню: " + l.joinToString { p -> w.recipes.firstOrNull { it.id == p.recipeId }?.title ?: p.text }
        }
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
                if (lines.isNotEmpty()) post(ctx, CH_DAY, 4501, "☀ План на ${Fmt.dayTitle(today, today).lowercase()}", lines, Tab.HOME, quick = "morning")
                if (today.dayOfMonth == 1 && runCatching { app.calstats.work.WorkStore(ctx).load().cashback.isNotEmpty() }.getOrDefault(false)) {
                    post(ctx, CH_DAY, 4510, "💳 Новый месяц — кешбэк", listOf("Выберите категории кешбэка в банках и отметьте их в приложении"), Tab.CARDS, quick = "cashback")
                }
                scheduleAll(ctx)
            }
            ACTION_EVENING -> {
                // «Итог дня»: приходит всегда — за минуту отметить настроение, привычки, траты и запись в дневник.
                val lines = listOf("Нажмите — итог дня за минуту: настроение, привычки, траты, дневник") + eveningLines(ctx, today)
                post(ctx, CH_DAY, 4502, "🌙 Итог дня", lines, Tab.HOME, quick = "checkin")
                scheduleAll(ctx)
            }
            ACTION_RESCAN -> scheduleEvents(ctx)
            ACTION_HABIT -> {
                val id = intent.getStringExtra("habit") ?: return
                val w = WorkStore(ctx).load()
                val h = w.habits.firstOrNull { it.id == id } ?: return
                scheduleHabits(ctx, w)
                val v = HabitMath.values(w.habitLogs, h.id)
                val due = if (h.perWeek != null) HabitMath.weekCount(h, v, today) < h.perWeek else h.scheduled(today) && !HabitMath.done(h, v, today)
                if (!due || HabitMath.done(h, v, today)) return
                if (!DrinkReminder.canNotify(ctx)) return
                channels(ctx)
                val nid = 31_000 + (h.id.hashCode() and 0xFFFF)
                val done = PendingIntent.getBroadcast(
                    ctx, nid, Intent(ctx, NotifyReceiver::class.java).setAction(ACTION_HABIT_DONE).putExtra("habit", h.id).putExtra("nid", nid),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val open = PendingIntent.getActivity(
                    ctx, nid, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_TAB, Tab.HABITS.name),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(ctx, CH_DAY)
                    .setSmallIcon(R.drawable.ic_notify).setColor(0xFF1F3A2B.toInt())
                    .setContentTitle("${h.emoji} ${h.name}")
                    .setContentText(if (h.perWeek != null) "На этой неделе ${HabitMath.weekCount(h, v, today)} из ${h.perWeek}" else "Ещё не отмечено сегодня")
                    .setContentIntent(open).setAutoCancel(true)
                    .addAction(0, "Готово ✓", done)
                    .build()
                try {
                    @Suppress("MissingPermission")
                    NotificationManagerCompat.from(ctx).notify(nid, n)
                } catch (_: SecurityException) {
                }
            }
            ACTION_NOTE -> {
                val id = intent.getStringExtra("note") ?: return
                val w = WorkStore(ctx).load()
                val n = w.notes.firstOrNull { it.id == id } ?: return
                if (n.remindDone || n.remindAt == null || !DrinkReminder.canNotify(ctx)) return
                channels(ctx)
                val nid = 61_000 + (id.hashCode() and 0x7FFF)
                fun act(a: String) = PendingIntent.getBroadcast(ctx, nid + a.hashCode() % 97,
                    Intent(ctx, NotifyReceiver::class.java).setAction(a).putExtra("note", id).putExtra("nid", nid),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val open = PendingIntent.getActivity(ctx, nid, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK, "note:$id")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val nb = NotificationCompat.Builder(ctx, CH_EVENTS)
                    .setSmallIcon(R.drawable.ic_notify).setColor(0xFF1F3A2B.toInt())
                    .setContentTitle("⏰ " + n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.take(80))
                    .setContentText(n.body.take(200))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(n.body.take(600)))
                    .setContentIntent(open).setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(0, "Выполнено ✓", act(ACTION_NOTE_DONE))
                    .addAction(0, "Через час", act(ACTION_NOTE_SNOOZE))
                    .build()
                try {
                    @Suppress("MissingPermission")
                    NotificationManagerCompat.from(ctx).notify(nid, nb)
                } catch (_: SecurityException) {
                }
            }
            ACTION_NOTE_DONE, ACTION_NOTE_SNOOZE -> {
                val id = intent.getStringExtra("note") ?: return
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
                val store = WorkStore(ctx)
                val w = store.load()
                val w2 = w.copy(notes = w.notes.map { n ->
                    if (n.id != id) n
                    else if (intent.action == ACTION_NOTE_DONE) app.calstats.work.NoteRemind.complete(n).copy(updated = System.currentTimeMillis())
                    else n.copy(remindAt = System.currentTimeMillis() + 3600_000L, updated = System.currentTimeMillis())
                })
                store.save(w2)
                scheduleNotes(ctx, w2)
            }
            ACTION_MED -> {
                val id = intent.getStringExtra("med") ?: return
                val time = intent.getStringExtra("time") ?: return
                val w = WorkStore(ctx).load()
                scheduleMeds(ctx, w)
                val m = HealthMore.medsOn(w, today).firstOrNull { it.first.id == id && it.second == time }?.first ?: return
                if (HealthMore.taken(w, id, today, time) || !DrinkReminder.canNotify(ctx)) return
                channels(ctx)
                val nid = 41_000 + ((id.hashCode() * 7 + time.hashCode()) and 0x7FFF)
                val done = PendingIntent.getBroadcast(
                    ctx, nid, Intent(ctx, NotifyReceiver::class.java).setAction(ACTION_MED_DONE).putExtra("med", id).putExtra("time", time).putExtra("nid", nid),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(ctx, CH_EVENTS)
                    .setSmallIcon(R.drawable.ic_notify).setColor(0xFF1F3A2B.toInt())
                    .setContentTitle("💊 ${m.name}")
                    .setContentText(listOf(time, m.dose).filter { it.isNotBlank() }.joinToString(" · "))
                    .setAutoCancel(true)
                    .addAction(0, "Принял ✓", done)
                    .build()
                try {
                    @Suppress("MissingPermission")
                    NotificationManagerCompat.from(ctx).notify(nid, n)
                } catch (_: SecurityException) {
                }
            }
            ACTION_MED_DONE -> {
                val id = intent.getStringExtra("med") ?: return
                val time = intent.getStringExtra("time") ?: return
                val s = WorkStore(ctx)
                val w = s.load()
                if (!HealthMore.taken(w, id, today, time)) s.save(HealthMore.toggleMed(w, id, today, time))
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
            }
            ACTION_HABIT_DONE -> {
                val id = intent.getStringExtra("habit") ?: return
                val s = WorkStore(ctx)
                val w = s.load()
                val h = w.habits.firstOrNull { it.id == id } ?: return
                s.save(app.calstats.work.HabitOps.set(w, id, today, h.target))
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
                app.calstats.widget.TodayWidget.refresh(ctx)
            }
            ACTION_FOCUS -> post(ctx, CH_EVENTS, 4504, "⏰ Время вышло", listOf(intent.getStringExtra("title").orEmpty().ifBlank { "Фокус" } + " — откройте приложение, чтобы записать время"), Tab.TASKS)
            ACTION_WEEKLY -> {
                val r = WeekReport.build(ctx, WorkStore(ctx).load(), today)
                post(ctx, CH_DAY, 4503, "📊 Итог недели", r.lines(), Tab.HOME)
                scheduleAll(ctx)
            }
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
