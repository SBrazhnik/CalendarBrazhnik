package app.calstats

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Пароль на вход (4–6 цифр) + отпечаток/лицо.
 * Пароль не хранится: только соль и хеш PBKDF2. После 5 ошибок — пауза, растущая вдвое.
 */
object AppLock {
    enum class Scope(val title: String) { APP("Всё приложение"), SENSITIVE("Финансы, документы, карты, здоровье") }
    val SENSITIVE_TABS = setOf(Tab.FINANCE, Tab.DOCS, Tab.CARDS, Tab.HEALTH, Tab.JOURNAL)
    val TIMEOUTS = listOf(0 to "Сразу", 1 to "1 мин", 5 to "5 мин", 15 to "15 мин")

    private fun sp(ctx: Context) = ctx.getSharedPreferences("applock", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getString("hash", null) != null
    fun bio(ctx: Context) = sp(ctx).getBoolean("bio", false)
    fun setBio(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("bio", on).apply()
    fun scope(ctx: Context) = runCatching { Scope.valueOf(sp(ctx).getString("scope", "APP")!!) }.getOrDefault(Scope.APP)
    fun setScope(ctx: Context, s: Scope) = sp(ctx).edit().putString("scope", s.name).apply()
    fun timeout(ctx: Context) = sp(ctx).getInt("timeout", 1)
    fun setTimeout(ctx: Context, m: Int) = sp(ctx).edit().putInt("timeout", m).apply()

    fun covers(ctx: Context, tab: Tab) = scope(ctx) == Scope.APP || tab in SENSITIVE_TABS

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return k.joinToString("") { "%02x".format(it) }
    }

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(ctx).edit()
            .putString("salt", salt.joinToString("") { "%02x".format(it) })
            .putString("hash", hash(pin, salt))
            .putInt("len", pin.length)
            .putInt("fails", 0).putLong("until", 0)
            .apply()
    }
    fun pinLength(ctx: Context) = sp(ctx).getInt("len", 4)

    fun disable(ctx: Context) = sp(ctx).edit().clear().apply()

    /** Сколько секунд ещё ждать после неверных попыток. */
    fun waitSeconds(ctx: Context): Long = ((sp(ctx).getLong("until", 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** true — верно. Неверно — считает попытки и включает паузу. */
    fun check(ctx: Context, pin: String): Boolean {
        if (waitSeconds(ctx) > 0) return false
        val p = sp(ctx)
        val salt = p.getString("salt", null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: return false
        val ok = constEq(hash(pin, salt), p.getString("hash", "")!!)
        if (ok) { p.edit().putInt("fails", 0).putLong("until", 0).apply(); return true }
        val fails = p.getInt("fails", 0) + 1
        val e = p.edit().putInt("fails", fails)
        if (fails >= 5) e.putLong("until", System.currentTimeMillis() + 30_000L * (1L shl (fails - 5).coerceAtMost(6)))
        e.apply()
        return false
    }

    private fun constEq(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }

    // ---- когда снова спрашивать ----
    fun markBackground(ctx: Context) = sp(ctx).edit().putLong("bg", System.currentTimeMillis()).apply()
    fun shouldLock(ctx: Context): Boolean {
        if (!enabled(ctx)) return false
        val bg = sp(ctx).getLong("bg", 0)
        if (bg == 0L) return true
        val grace = maxOf(timeout(ctx) * 60_000L, 10_000L) // «Сразу» — с запасом 10 с на выбор фото
        return System.currentTimeMillis() - bg >= grace
    }

    fun bioAvailable(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30) bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        else @Suppress("DEPRECATION") (bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
    }

    /** Есть ли на телефоне блокировка экрана (для «Забыли пароль?»). */
    fun deviceSecure(ctx: Context) = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
}
