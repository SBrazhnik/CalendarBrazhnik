package app.calstats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.calstats.work.ME
import app.calstats.work.Status
import app.calstats.work.WorkStore
import java.time.LocalDate

/** Ежедневно в 10:00: сводка в Telegram и проверка новой версии приложения. */
object DailyDigest {
    private const val UPD_CHANNEL = "app_update"

    fun text(ctx: Context, today: LocalDate): String {
        val w = WorkStore(ctx).load()
        val open = w.issues.filter { it.assigneeId == ME && it.status != Status.DONE }
        val due = open.filter { it.dueDate == today }
        val overdue = open.filter { it.dueDate?.isBefore(today) == true }
        val out = StringBuilder("☀ Доброе утро! ${Fmt.dayTitle(today, today)}\n")
        if (due.isNotEmpty()) {
            out.append("\n📌 Задачи на сегодня:\n")
            due.take(8).forEach { out.append("• ${it.title}\n") }
        }
        if (overdue.isNotEmpty()) out.append("\n⚠ Просрочено задач: ${overdue.size}\n")
        val lines = DocsReminder.lines(ctx, today)
        if (lines.isNotEmpty()) {
            out.append("\n🔔 Напоминания:\n")
            lines.forEach { out.append("$it\n") }
        }
        if (due.isEmpty() && overdue.isEmpty() && lines.isEmpty()) out.append("\nСрочных дел нет — хорошего дня 🌿")
        return out.toString().trim()
    }

    suspend fun run(ctx: Context) {
        val bot = TelegramBot(ctx)
        val st = bot.state()
        val today = LocalDate.now()
        if (st.connected) {
            if (st.daily) bot.send(text(ctx, today))
            else if (st.reminders) DocsReminder.lines(ctx, today).takeIf { it.isNotEmpty() }?.let { bot.send("🔔 " + it.joinToString("\n")) }
        }
        val prefs = ctx.getSharedPreferences("calstats_ui", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_update", true)) return
        val r = runCatching { Updater.check(ctx) }.getOrNull() ?: return
        if (prefs.getLong("upd_notified", 0) == r.version) return
        prefs.edit().putLong("upd_notified", r.version).apply()
        if (st.connected && st.updates) bot.send("📲 Вышла новая версия «Зелёного ритма» (${r.version}). Откройте приложение — оно предложит обновиться.")
        notify(ctx, r)
    }

    private fun notify(ctx: Context, r: Updater.Release) {
        if (!DrinkReminder.canNotify(ctx)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(UPD_CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(UPD_CHANNEL, "Обновления приложения", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val open = PendingIntent.getActivity(
            ctx, 401, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, UPD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF1F3A2B.toInt())
            .setContentTitle("Новая версия приложения")
            .setContentText("Откройте «Зелёный ритм», чтобы установить версию ${r.version}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(4401, n)
        } catch (_: SecurityException) {
        }
    }
}
