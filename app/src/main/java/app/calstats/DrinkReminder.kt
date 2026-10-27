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
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Ежедневное уведомление «Пил вчера?» с кнопками «Не пил» / «Пил».
 * Будильник Android срабатывает около заданного времени и переставляется на следующий день.
 */
object DrinkReminder {
    private const val CHANNEL = "drink_reminder"
    private const val NOTIF_ID = 4201
    const val ACTION_FIRE = "app.calstats.DRINK_REMINDER_FIRE"
    const val ACTION_ANSWER = "app.calstats.DRINK_ANSWER"
    const val EXTRA_DATE = "date"
    const val EXTRA_DRANK = "drank"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Учёт алкоголя", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Ежедневный вопрос: пили ли вы вчера"
                },
            )
        }
    }

    private fun firePendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 100,
            Intent(ctx, DrinkReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Ставит (или снимает) будильник на ближайшее время напоминания. */
    fun schedule(ctx: Context) {
        val store = SobrietyStore(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePendingIntent(ctx)
        am.cancel(pi)
        if (!store.reminderEnabled) return
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(store.reminderTime).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Показывает вопрос про указанный день (обычно вчера), если он ещё не отмечен. */
    fun showQuestion(ctx: Context, date: LocalDate, force: Boolean = false) {
        val store = SobrietyStore(ctx)
        if (!force && store.load().containsKey(date)) return
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val today = LocalDate.now()
        val summary = Sobriety.summary(store.load(), today)
        val dayWord = if (date == today.minusDays(1)) "вчера" else Fmt.dateNoYear(date)
        val streakLine = summary.streak?.takeIf { it > 0 }?.let { "Сейчас: ${Sobriety.streakText(summary)}." } ?: ""

        val open = PendingIntent.getActivity(
            ctx, 101,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFE4572E.toInt())
            .setContentTitle("Пили $dayWord?")
            .setContentText("${Fmt.dayTitle(date, today)} — ответьте одним нажатием")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${Fmt.dayTitle(date, today)} — ответьте одним нажатием. $streakLine".trim(),
                ),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Не пил", answerIntent(ctx, date, false))
            .addAction(0, "Пил", answerIntent(ctx, date, true))
            .build()
        notify(ctx, NOTIF_ID, n)
    }

    private fun answerIntent(ctx: Context, date: LocalDate, drank: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, if (drank) 111 else 110,
            Intent(ctx, DrinkReminderReceiver::class.java)
                .setAction(ACTION_ANSWER)
                .putExtra(EXTRA_DATE, date.toString())
                .putExtra(EXTRA_DRANK, drank),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun showConfirmation(ctx: Context, drank: Boolean) {
        if (!canNotify(ctx)) return
        val summary = Sobriety.summary(SobrietyStore(ctx).load(), LocalDate.now())
        val text = if (drank) "Записано: пил. Счётчик начнётся заново." else "Записано: не пил. ${Sobriety.streakText(summary)}."
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFE4572E.toInt())
            .setContentTitle(if (drank) "Отмечено" else "Отлично!")
            .setContentText(text)
            .setTimeoutAfter(6000)
            .setAutoCancel(true)
            .build()
        notify(ctx, NOTIF_ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)

    @Suppress("MissingPermission")
    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canNotify(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

class DrinkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            DrinkReminder.ACTION_FIRE -> {
                DrinkReminder.showQuestion(ctx, LocalDate.now().minusDays(1))
                DrinkReminder.schedule(ctx)
            }
            DrinkReminder.ACTION_ANSWER -> {
                val date = intent.getStringExtra(DrinkReminder.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return
                val drank = intent.getBooleanExtra(DrinkReminder.EXTRA_DRANK, false)
                SobrietyStore(ctx).set(date, drank)
                DrinkReminder.showConfirmation(ctx, drank)
            }
        }
    }
}

/** После перезагрузки, обновления или смены времени будильник нужно поставить заново. */
class DrinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        DrinkReminder.schedule(ctx)
    }
}
