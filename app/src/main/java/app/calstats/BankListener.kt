package app.calstats

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.calstats.work.BankNotify
import app.calstats.work.PendingTx
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/**
 * Читает уведомления банковских приложений и складывает найденные операции в очередь —
 * в «Финансах» их можно подтвердить одной кнопкой. Другие уведомления не читаются и не сохраняются.
 */
class BankListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in BankNotify.BANKS) return
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val date = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val tx = BankNotify.parse(sbn.packageName, title, text, date, "${sbn.packageName}:${sbn.postTime}:${text.hashCode()}") ?: return
        BankQueue.add(this, tx)
    }
}

object BankQueue {
    private val json = Json { ignoreUnknownKeys = true }
    private fun p(ctx: Context) = ctx.getSharedPreferences("bank_queue", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, BankListener::class.java).flattenToString())
    }

    @Synchronized
    fun list(ctx: Context): List<PendingTx> = runCatching { json.decodeFromString<List<PendingTx>>(p(ctx).getString("q", "[]")!!) }.getOrDefault(emptyList())

    @Synchronized
    fun add(ctx: Context, t: PendingTx) {
        val cur = list(ctx)
        // одна и та же операция часто приходит дважды (обновление уведомления)
        if (cur.any { it.id == t.id || (it.amount == t.amount && it.merchant == t.merchant && it.date == t.date && it.income == t.income) }) return
        p(ctx).edit().putString("q", json.encodeToString((cur + t).takeLast(100))).apply()
    }

    @Synchronized
    fun remove(ctx: Context, ids: Set<String>) {
        p(ctx).edit().putString("q", json.encodeToString(list(ctx).filter { it.id !in ids })).apply()
    }
}
