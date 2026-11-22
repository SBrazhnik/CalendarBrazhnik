package app.calstats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления приложения: новые сборки публикуются в GitHub Releases (тег build-N, N = versionCode).
 * Приложение само находит свежую версию, скачивает APK и открывает системную установку.
 */
object Updater {
    const val REPO = "SBrazhnik/CalendarBrazhnik"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    data class Release(val version: Long, val apkUrl: String, val size: Long, val notes: String)

    fun currentVersion(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""

    /** Разбор ответа GitHub API /releases/latest. */
    fun parse(json: String): Release? {
        val o = Json.parseToJsonElement(json).jsonObject
        val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return null
        val v = tag.substringAfterLast('-').toLongOrNull() ?: return null
        val asset = (o["assets"] as? JsonArray).orEmpty().map { it.jsonObject }
            .firstOrNull { (it["name"] as? JsonPrimitive)?.content?.endsWith(".apk") == true } ?: return null
        val url = (asset["browser_download_url"] as? JsonPrimitive)?.content ?: return null
        val size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0
        val body = (o["body"] as? JsonPrimitive)?.content.orEmpty()
        return Release(v, url, size, body)
    }

    /** Свежая версия, если она новее установленной. */
    suspend fun check(ctx: Context): Release? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "ZelenyiRitm")
        try {
            if (c.responseCode != 200) return@withContext null
            val r = parse(c.inputStream.bufferedReader().use { it.readText() })
            r?.takeIf { it.version > currentVersion(ctx) }
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(ctx: Context, r: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "zelenyi-ritm-${r.version}.apk")
        var url = URL(r.apkUrl)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "ZelenyiRitm")
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) {
                url = URL(url, c.getHeaderField("Location"))
                c.disconnect()
                continue
            }
            if (code != 200) throw IllegalStateException("HTTP $code")
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
        c.inputStream.use { inp ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    val p = if (total > 0) (done * 100 / total).toInt() else 0
                    if (p != last) {
                        last = p
                        withContext(Dispatchers.Main) { progress(p) }
                    }
                }
            }
        }
        c.disconnect()
        out
    }

    /** Открыть системную установку. Если Android ещё не разрешил установку из приложения — открыть это разрешение. */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    /** Поделиться ссылкой на свежий APK (для второго телефона, близких, коллег). */
    fun shareDownloadLink(ctx: Context) {
        val text = "Зелёный ритм — приложение для планирования жизни: календарь, задачи, финансы, здоровье.\n" +
            "Скачать для Android: $RELEASES_PAGE"
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "Поделиться приложением",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
