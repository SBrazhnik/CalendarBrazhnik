package app.calstats

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.calstats.work.DocsMath
import app.calstats.work.SplitMath
import app.calstats.work.WorkStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Раз в день (около 10:00) проверяет сроки документов, завтрашние списания подписок
 * и окончание пробных периодов — и присылает уведомление.
 */
object DocsReminder {
    private const val CHANNEL = "docs_reminder"
    const val ACTION = "app.calstats.DOCS_REMINDER"
    private val AT: LocalTime = LocalTime.of(10, 0)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 300, Intent(ctx, DocsReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(AT).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
    }

    /** Список строк для сегодняшнего уведомления (пустой — ничего не присылаем). */
    fun lines(ctx: Context, today: LocalDate): List<String> {
        val w = WorkStore(ctx).load()
        val out = ArrayList<String>()
        w.documents.filter { DocsMath.remindToday(it, today) }.forEach { d ->
            val left = DocsMath.daysLeft(d, today) ?: return@forEach
            val owner = d.owner.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            out += when (left) {
                0L -> "${d.kind.emoji} ${d.name}$owner — истекает сегодня"
                1L -> "${d.kind.emoji} ${d.name}$owner — истекает завтра"
                else -> "${d.kind.emoji} ${d.name}$owner — истекает через ${Fmt.days(left)}, ${Fmt.date(d.expiry!!)}"
            }
        }
        w.recurrings.filter { it.active && it.subscription }.forEach { r ->
            val trial = DocsMath.trialLeft(r, today)
            if (trial == 2L || trial == 0L) {
                out += "⏳ ${r.name}: пробный период заканчивается ${if (trial == 0L) "сегодня" else "послезавтра"} — потом спишут ${SplitMath.money(r.amount)}"
            } else if (ChronoUnit.DAYS.between(today, r.next) == 1L) {
                out += "🔁 ${r.name}: завтра списание ${SplitMath.money(r.amount)}"
            }
        }
        return out
    }

    fun fire(ctx: Context) {
        val lines = lines(ctx, LocalDate.now())
        if (lines.isEmpty() || !DrinkReminder.canNotify(ctx)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Документы и подписки", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Сроки документов, списания и пробные периоды подписок"
                    },
                )
            }
        }
        val open = PendingIntent.getActivity(
            ctx, 301,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, Tab.DOCS.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF1F3A2B.toInt())
            .setContentTitle(if (lines.size == 1) "Напоминание" else "Напоминания: ${lines.size}")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(4301, n)
        } catch (_: SecurityException) {
        }
    }
}

class DocsReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != DocsReminder.ACTION) return
        DocsReminder.fire(ctx)
        DocsReminder.schedule(ctx)
    }
}
