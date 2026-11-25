package app.calstats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Модуль «Доработка»: запросы разработчику уходят задачами (issues) в закрытый репозиторий GitHub,
 * ответы приходят комментариями. Токен хранится только на телефоне и не попадает в копии.
 */
object DevRequests {
    const val DEFAULT_REPO = "SBrazhnik/zelenyi-ritm-requests"
    const val LABEL = "из приложения"

    enum class Kind(val title: String, val emoji: String, val label: String) {
        FEATURE("Доработка", "💡", "доработка"), BUG("Ошибка", "🐞", "ошибка"), QUESTION("Вопрос", "❓", "вопрос")
    }

    data class Issue(
        val number: Int, val title: String, val body: String, val open: Boolean, val labels: List<String>,
        val comments: Int, val created: String, val updated: String, val url: String,
    ) {
        val kind get() = Kind.entries.firstOrNull { it.label in labels }
        val inWork get() = open && labels.any { it == "в работе" }
        val status get() = when { !open -> "✅ Готово"; inWork -> "🔧 В работе"; labels.contains("нужен ответ") -> "💬 Нужен ваш ответ"; else -> "🕓 Ждёт" }
    }

    data class Comment(val id: Long, val author: String, val body: String, val created: String, val mine: Boolean)

    // ---------- настройки (не попадают в резервные копии) ----------
    private fun sp(ctx: Context) = ctx.getSharedPreferences("devreq", Context.MODE_PRIVATE)
    fun repo(ctx: Context) = sp(ctx).getString("repo", DEFAULT_REPO)!!
    fun token(ctx: Context) = sp(ctx).getString("token", null)
    fun login(ctx: Context) = sp(ctx).getString("login", null)
    fun configured(ctx: Context) = !token(ctx).isNullOrBlank()
    fun save(ctx: Context, repo: String, token: String?, login: String?) =
        sp(ctx).edit().putString("repo", repo.trim()).putString("token", token?.trim()).putString("login", login).apply()

    /** Сколько комментариев по задаче уже видели — для значка «новый ответ». */
    fun seen(ctx: Context, n: Int) = sp(ctx).getInt("seen_$n", 0)
    fun markSeen(ctx: Context, n: Int, count: Int) = sp(ctx).edit().putInt("seen_$n", count).apply()

    /** Последний список — показываем сразу, пока обновляется. */
    fun cached(ctx: Context): List<Issue> = runCatching { parseIssues(JSONArray(sp(ctx).getString("cache", "[]"))) }.getOrDefault(emptyList())
    fun unread(ctx: Context) = cached(ctx).count { it.comments > seen(ctx, it.number) }

    // ---------- свой пароль модуля ----------
    object Lock {
        private fun hash(pin: String, salt: ByteArray): String {
            val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        }
        fun has(ctx: Context) = sp(ctx).getString("pin_hash", null) != null
        fun length(ctx: Context) = sp(ctx).getInt("pin_len", 4)
        fun bio(ctx: Context) = sp(ctx).getBoolean("pin_bio", true)
        fun setBio(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("pin_bio", on).apply()
        fun set(ctx: Context, pin: String) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            sp(ctx).edit().putString("pin_salt", salt.joinToString("") { "%02x".format(it) }).putString("pin_hash", hash(pin, salt))
                .putInt("pin_len", pin.length).putInt("pin_fails", 0).putLong("pin_until", 0).apply()
        }
        fun waitSeconds(ctx: Context) = ((sp(ctx).getLong("pin_until", 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        fun check(ctx: Context, pin: String): Boolean {
            if (waitSeconds(ctx) > 0) return false
            val p = sp(ctx)
            val salt = p.getString("pin_salt", null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: return false
            val a = hash(pin, salt); val b = p.getString("pin_hash", "")!!
            var r = a.length xor b.length
            for (i in 0 until minOf(a.length, b.length)) r = r or (a[i].code xor b[i].code)
            if (r == 0) { p.edit().putInt("pin_fails", 0).putLong("pin_until", 0).apply(); return true }
            val fails = p.getInt("pin_fails", 0) + 1
            val e = p.edit().putInt("pin_fails", fails)
            if (fails >= 5) e.putLong("pin_until", System.currentTimeMillis() + 30_000L * (1L shl (fails - 5).coerceAtMost(6)))
            e.apply()
            return false
        }
        /** Сброс пароля модуля (после подтверждения паролем телефона) — вместе с токеном. */
        fun reset(ctx: Context) = sp(ctx).edit().remove("pin_hash").remove("pin_salt").remove("token").apply()
    }

    // ---------- GitHub API ----------
    class ApiError(val code: Int, msg: String) : Exception(msg)

    private fun call(ctx: Context, method: String, path: String, body: JSONObject? = null): String {
        val t = token(ctx) ?: throw ApiError(401, "не задан токен")
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method // Android поддерживает PATCH
            c.connectTimeout = 15_000; c.readTimeout = 40_000
            c.setRequestProperty("Authorization", "Bearer $t")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", Net.UA)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw ApiError(code, when (code) {
                401 -> "токен неверный или истёк — создайте новый"
                403 -> "у токена нет прав на этот репозиторий (нужны Issues и Contents: чтение и запись)"
                404 -> "репозиторий не найден или токен к нему не допущен"
                422 -> "GitHub не принял данные: " + runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                else -> "GitHub ответил $code"
            })
            return text
        } finally { c.disconnect() }
    }

    private fun path(ctx: Context) = "/repos/" + repo(ctx)

    /** Проверка настроек: возвращает имя владельца токена. */
    fun verify(ctx: Context): String {
        val r = JSONObject(call(ctx, "GET", path(ctx)))
        if (!r.optBoolean("private")) throw ApiError(0, "репозиторий открытый — запросы увидят все. Сделайте его закрытым (Private)")
        if (r.optJSONObject("permissions")?.optBoolean("push") == false) throw ApiError(403, "токену нужен доступ на запись")
        val me = runCatching { JSONObject(call(ctx, "GET", "/user")).optString("login") }.getOrNull()
        return me ?: r.optJSONObject("owner")?.optString("login").orEmpty()
    }

    private fun parseIssues(a: JSONArray): List<Issue> = (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i)
        if (o.has("pull_request")) return@mapNotNull null
        val labels = o.optJSONArray("labels")?.let { l -> (0 until l.length()).map { l.getJSONObject(it).optString("name") } }.orEmpty()
        Issue(o.optInt("number"), o.optString("title"), o.optString("body"), o.optString("state") == "open", labels, o.optInt("comments"),
            o.optString("created_at"), o.optString("updated_at"), o.optString("html_url"))
    }

    fun list(ctx: Context): List<Issue> {
        val raw = call(ctx, "GET", path(ctx) + "/issues?state=all&per_page=100&sort=updated&direction=desc")
        sp(ctx).edit().putString("cache", raw).apply()
        return parseIssues(JSONArray(raw))
    }

    fun comments(ctx: Context, n: Int): List<Comment> {
        val a = JSONArray(call(ctx, "GET", path(ctx) + "/issues/$n/comments?per_page=100"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val who = o.optJSONObject("user")?.optString("login").orEmpty()
            val body = o.optString("body")
            // Свои реплики из приложения помечены скрытой меткой, всё остальное — ответы разработчика
            Comment(o.optLong("id"), who, body.replace("<!-- app -->", "").trim(), o.optString("created_at"), body.contains("<!-- app -->"))
        }
    }

    fun device(ctx: Context) = "Сборка ${Updater.versionName(ctx)}, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"

    fun create(ctx: Context, kind: Kind, module: String?, title: String, text: String, photos: List<Uri>): Issue {
        val links = photos.mapIndexedNotNull { i, u -> runCatching { upload(ctx, u, "${System.currentTimeMillis()}_$i.jpg") }.getOrNull() }
        val body = buildString {
            append("**Тип:** ${kind.title}\n")
            if (!module.isNullOrBlank()) append("**Модуль:** $module\n")
            append("**Устройство:** ${device(ctx)}\n\n")
            append(text.trim()).append('\n')
            if (links.isNotEmpty()) { append("\n**Скриншоты:**\n"); links.forEach { append("![скриншот]($it)\n") } }
            append("\n<!-- app -->")
        }
        val o = JSONObject().put("title", "${kind.emoji} $title").put("body", body).put("labels", JSONArray(listOfNotNull(LABEL, kind.label, module?.lowercase())))
        val r = JSONObject(call(ctx, "POST", path(ctx) + "/issues", o))
        return parseIssues(JSONArray().put(r)).first()
    }

    fun reply(ctx: Context, n: Int, text: String, photos: List<Uri>) {
        val links = photos.mapIndexedNotNull { i, u -> runCatching { upload(ctx, u, "${System.currentTimeMillis()}_$i.jpg") }.getOrNull() }
        val body = text.trim() + links.joinToString("") { "\n![скриншот]($it)" } + "\n<!-- app -->"
        call(ctx, "POST", path(ctx) + "/issues/$n/comments", JSONObject().put("body", body))
        // если разработчик ждал ответа — снимаем метку
        runCatching { call(ctx, "DELETE", path(ctx) + "/issues/$n/labels/" + java.net.URLEncoder.encode("нужен ответ", "UTF-8").replace("+", "%20")) }
    }

    fun setOpen(ctx: Context, n: Int, open: Boolean) {
        call(ctx, "PATCH", path(ctx) + "/issues/$n", JSONObject().put("state", if (open) "open" else "closed"))
    }

    /** Скриншот → сжатый JPEG в папку attachments закрытого репозитория. Возвращает ссылку для текста задачи. */
    private fun upload(ctx: Context, uri: Uri, name: String): String {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("не картинка")
        val k = 1600f / maxOf(bmp.width, bmp.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val out = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val o = JSONObject().put("message", "Скриншот из приложения").put("content", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        val r = JSONObject(call(ctx, "PUT", path(ctx) + "/contents/attachments/$name", o))
        return r.optJSONObject("content")?.optString("html_url")?.plus("?raw=true") ?: error("нет ссылки")
    }

    /** Картинки из текста ответа (вложения закрытого репозитория грузятся с токеном). */
    fun image(ctx: Context, url: String): Bitmap? = runCatching {
        // https://github.com/o/r/blob/main/attachments/x.jpg?raw=true → contents API
        val p = Regex("github\\.com/([^/]+/[^/]+)/blob/[^/]+/(.+?)(\\?.*)?$").find(url)
        val c = if (p != null) URL("https://api.github.com/repos/${p.groupValues[1]}/contents/${p.groupValues[2]}").openConnection() as HttpURLConnection
        else URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", Net.UA)
            token(ctx)?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (p != null) c.setRequestProperty("Accept", "application/vnd.github.raw")
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally { c.disconnect() }
    }.getOrNull()
}
