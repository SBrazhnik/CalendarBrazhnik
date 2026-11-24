package app.calstats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Бот «на связи»: постоянно ждёт сообщения (долгий опрос Telegram) и отвечает за 1–2 секунды,
 * даже когда приложение закрыто. Работает как служба с тихим уведомлением.
 */
class TelegramService : Service() {
    companion object {
        /** Пока приложение открыто — данные меняются через него (без конфликтов). */
        @Volatile var access: BotBrain.Access? = null
        @Volatile var running = false
            private set

        private fun sp(ctx: Context) = ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE)
        fun enabled(ctx: Context) = sp(ctx).getBoolean("live", false)
        fun setEnabled(ctx: Context, on: Boolean) {
            sp(ctx).edit().putBoolean("live", on).apply()
            if (on) start(ctx) else stop(ctx)
        }

        fun start(ctx: Context) {
            if (!enabled(ctx) || !TelegramBot(ctx).state().connected) return
            val i = Intent(ctx, TelegramService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, TelegramService::class.java)) } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("bot", "Telegram-бот на связи", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.ACCOUNTS.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "bot").setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Telegram-бот на связи").setContentText("Отвечает сразу. Отключить — в «Подключениях»")
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(78, n)
        if (loop?.isActive != true) loop = scope.launch { run() }
        running = true
        return START_STICKY
    }

    private suspend fun run() {
        val bot = TelegramBot(this)
        runCatching { bot.setupMenu() }
        var fails = 0
        while (scope.isActive) {
            if (!bot.state().connected) { stopSelf(); return }
            val msgs = runCatching { bot.poll(50) }.onFailure { fails++ }.onSuccess { fails = 0 }.getOrDefault(emptyList())
            for (m in msgs) {
                runCatching { bot.typing() }
                val reply = runCatching { BotBrain.answer(this, m, access ?: BotBrain.StoreAccess(this)) }.getOrElse { "Ошибка: ${it.message}" }
                bot.send(reply, keyboard = true)
            }
            if (fails > 0) delay(minOf(60_000L, 3_000L * fails)) // нет сети — пробуем реже
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }
}

/** Отправить в бот из уведомлений/будильников (не блокируя). */
object TelegramPush {
    fun send(ctx: Context, text: String, kind: String = "reminders") {
        val bot = TelegramBot(ctx.applicationContext)
        val st = bot.state()
        if (!st.connected) return
        val on = when (kind) { "daily" -> st.daily; "updates" -> st.updates; else -> st.reminders }
        if (!on) return
        CoroutineScope(Dispatchers.IO).launch { runCatching { bot.send(text) } }
    }
}
