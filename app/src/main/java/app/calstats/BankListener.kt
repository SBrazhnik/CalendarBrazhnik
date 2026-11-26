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
        val pkg = sbn.packageName
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // Источник: банковское приложение, SMS от банка или приложение со словом «банк» в названии
        val bank = BankNotify.BANKS[pkg]
            ?: (if (pkg in BankNotify.MESSAGING) BankNotify.smsBank(title) else null)
            ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                ?.takeIf { pkg !in BankNotify.MESSAGING && Regex("(?iu)банк|bank").containsMatchIn(it) }
            ?: return
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val date = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val tx = BankNotify.parse(pkg, if (pkg in BankNotify.MESSAGING) null else title, text, date, "$pkg:${sbn.postTime}:${text.hashCode()}", bank)
        BankQueue.log(this, bank, listOfNotNull(title, text).joinToString(" · "), tx)
        if (tx != null) BankQueue.add(this, tx)
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

    /** Банки и карты, от которых уже приходили операции: «Сбер|1234». */
    fun seen(ctx: Context): Set<String> = ctx.getSharedPreferences("bank_map", Context.MODE_PRIVATE).getStringSet("seen", emptySet()) ?: emptySet()

    @Synchronized
    fun add(ctx: Context, t: PendingTx) {
        val m = ctx.getSharedPreferences("bank_map", Context.MODE_PRIVATE)
        m.edit().putStringSet("seen", seen(ctx) + "${t.bank}|${t.card}").apply()
        val cur = list(ctx)
        // одна и та же операция часто приходит дважды (обновление уведомления)
        if (cur.any { it.id == t.id || (it.amount == t.amount && it.merchant == t.merchant && it.date == t.date && it.income == t.income) }) return
        p(ctx).edit().putString("q", json.encodeToString((cur + t).takeLast(100))).apply()
    }

    /** Журнал последних уведомлений банков (с результатом разбора) — чтобы понять, почему операция не попала. */
    @Synchronized
    fun log(ctx: Context, bank: String, text: String, tx: PendingTx?) {
        val p = ctx.getSharedPreferences("bank_log", Context.MODE_PRIVATE)
        val arr = runCatching { org.json.JSONArray(p.getString("l", "[]")) }.getOrDefault(org.json.JSONArray())
        val o = org.json.JSONObject().put("t", System.currentTimeMillis()).put("bank", bank).put("text", text.take(300))
            .put("res", tx?.let { (if (it.isBill) "счёт к оплате " else if (it.income) "доход +" else "расход −") + it.amount / 100 + " ₽" } ?: "не распознано")
        val out = org.json.JSONArray().put(o)
        for (i in 0 until minOf(arr.length(), 29)) out.put(arr.get(i))
        p.edit().putString("l", out.toString()).apply()
    }
    fun logList(ctx: Context): List<Triple<Long, String, Pair<String, String>>> = runCatching {
        val a = org.json.JSONArray(ctx.getSharedPreferences("bank_log", Context.MODE_PRIVATE).getString("l", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Triple(o.getLong("t"), o.getString("bank"), o.getString("text") to o.getString("res")) } }
    }.getOrDefault(emptyList())

    @Synchronized
    fun remove(ctx: Context, ids: Set<String>) {
        p(ctx).edit().putString("q", json.encodeToString(list(ctx).filter { it.id !in ids })).apply()
    }
}
