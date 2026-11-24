package app.calstats

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Личный Telegram-бот. Пользователь создаёт бота у @BotFather, вставляет токен и нажимает «Старт» в боте.
 * Дальше приложение само шлёт в бот напоминания и сводку, а сообщения боту превращает в заметки, задачи и траты.
 * Токен хранится только на телефоне; сервер не нужен.
 */
class TelegramBot(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE)

    data class State(
        val token: String,
        val botName: String,
        val chatId: Long,
        val daily: Boolean,
        val reminders: Boolean,
        val updates: Boolean,
        val inbox: Boolean,
    ) {
        val hasToken get() = token.isNotBlank()
        val connected get() = hasToken && chatId != 0L
    }

    fun state() = State(
        prefs.getString("token", "").orEmpty(),
        prefs.getString("bot", "").orEmpty(),
        prefs.getLong("chat", 0),
        prefs.getBoolean("daily", true),
        prefs.getBoolean("reminders", true),
        prefs.getBoolean("updates", true),
        prefs.getBoolean("inbox", true),
    )

    fun setFlag(key: String, on: Boolean) = prefs.edit().putBoolean(key, on).apply()

    fun disconnect() = prefs.edit().remove("token").remove("bot").remove("chat").remove("offset").apply()

    /** Проверить токен; вернёт имя бота (@username). */
    suspend fun connect(token: String): String = withContext(Dispatchers.IO) {
        val t = token.trim()
        val me = call(t, "getMe")
        val r = me["result"] as? JsonObject ?: error("Неверный токен")
        val name = (r["username"] as? JsonPrimitive)?.content ?: error("Неверный токен")
        prefs.edit().putString("token", t).putString("bot", name).remove("chat").remove("offset").apply()
        name
    }

    /** Найти чат: пользователь должен нажать «Старт» (или написать боту что угодно). */
    suspend fun findChat(): Boolean = withContext(Dispatchers.IO) {
        val st = state()
        val ups = updates(st.token, prefs.getLong("offset", 0))
        val chat = ups.lastOrNull { it.chatId != 0L }?.chatId ?: return@withContext false
        prefs.edit().putLong("chat", chat).putLong("offset", ups.last().id + 1).apply()
        setupMenu()
        send("✅ «Зелёный ритм» подключён. Сюда будут приходить напоминания и сводка.\n\n" + BotBrain.HELP, keyboard = true)
        true
    }

    /** Меню команд у кнопки «Меню» в Telegram. */
    suspend fun setupMenu() = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.hasToken) return@withContext
        val cmds = BotBrain.COMMANDS.joinToString(",", "[", "]") { (c, d) -> """{"command":"$c","description":"$d"}""" }
        runCatching { post(st.token, "setMyCommands", "commands=" + URLEncoder.encode(cmds, "UTF-8")) }
    }

    /** Долгое ожидание сообщений (до timeout секунд): ответ приходит сразу, как пользователь написал. */
    suspend fun poll(timeout: Int): List<String> = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.connected) return@withContext emptyList()
        val ups = updates(st.token, prefs.getLong("offset", 0), timeout)
        if (ups.isEmpty()) return@withContext emptyList()
        prefs.edit().putLong("offset", ups.last().id + 1).apply()
        ups.filter { it.chatId == st.chatId && it.text.isNotBlank() }.map { it.text }
    }

    /** Новые сообщения из чата пользователя. */
    suspend fun pull(): List<String> = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.connected) return@withContext emptyList()
        val ups = updates(st.token, prefs.getLong("offset", 0))
        if (ups.isEmpty()) return@withContext emptyList()
        prefs.edit().putLong("offset", ups.last().id + 1).apply()
        ups.filter { it.chatId == st.chatId && it.text.isNotBlank() }.map { it.text }
    }

    suspend fun send(text: String, keyboard: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val st = state()
        if (!st.connected) return@withContext false
        runCatching {
            var body = "chat_id=${st.chatId}&text=" + URLEncoder.encode(text.take(4000), "UTF-8") + "&disable_web_page_preview=true"
            if (keyboard) body += "&reply_markup=" + URLEncoder.encode(BotBrain.KEYBOARD, "UTF-8")
            post(st.token, "sendMessage", body)
            true
        }.getOrDefault(false)
    }

    /** «Печатает…» — пока бот готовит ответ. */
    suspend fun typing() = withContext(Dispatchers.IO) {
        val st = state()
        if (st.connected) runCatching { post(st.token, "sendChatAction", "chat_id=${st.chatId}&action=typing") }
    }

    private data class Update(val id: Long, val chatId: Long, val text: String)

    private fun updates(token: String, offset: Long, timeout: Int = 0): List<Update> {
        val o = call(token, "getUpdates", "offset=$offset&timeout=$timeout&allowed_updates=%5B%22message%22%5D", readTimeout = (timeout + 15) * 1000)
        return (o["result"] as? JsonArray).orEmpty().mapNotNull { e ->
            val u = e.jsonObject
            val id = (u["update_id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val m = u["message"] as? JsonObject ?: return@mapNotNull Update(id, 0, "")
            val chat = ((m["chat"] as? JsonObject)?.get("id") as? JsonPrimitive)?.longOrNull ?: 0
            Update(id, chat, (m["text"] as? JsonPrimitive)?.content.orEmpty())
        }
    }

    private fun post(token: String, method: String, body: String): JsonObject {
        val c = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            val txt = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) error("Telegram: ${c.responseCode}")
            return Json.parseToJsonElement(txt).jsonObject
        } finally { c.disconnect() }
    }

    private fun call(token: String, method: String, query: String = "", readTimeout: Int = 15_000): JsonObject {
        val c = URL("https://api.telegram.org/bot$token/$method" + if (query.isEmpty()) "" else "?$query").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = readTimeout
        try {
            val ok = c.responseCode in 200..299
            val body = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) error(if (c.responseCode == 401 || c.responseCode == 404) "Неверный токен" else "Telegram недоступен (${c.responseCode})")
            return Json.parseToJsonElement(body).jsonObject
        } finally {
            c.disconnect()
        }
    }

    enum class Kind { NOTE, TASK, EXPENSE }
    data class Command(val kind: Kind, val text: String, val amount: Long = 0)

    companion object {
        val HELP get() = BotBrain.HELP
        const val HELP_OLD = "Что можно прислать:\n" +
            "• задача Позвонить в банк — новая задача\n" +
            "• трата 350 кофе — расход в Финансы\n" +
            "• выпил 2 стакана воды, вес 82,5, настроение 4\n" +
            "• купить молоко, хлеб — в список покупок\n" +
            "• сделал зарядку — отметить привычку\n" +
            "• любой другой текст или ссылка — заметка"

        /** Разбор сообщения: «задача …», «трата 350 кофе», «/task …», иначе — заметка. */
        fun parseCommand(msg: String): Command {
            val t = msg.trim()
            val lower = t.lowercase()
            fun rest(prefix: String) = t.substring(prefix.length).trim().trimStart(':', '-', ' ').trim()
            listOf("/task", "задача", "задачу").firstOrNull { lower.startsWith(it) }?.let { p ->
                return Command(Kind.TASK, rest(p).ifBlank { t })
            }
            listOf("/note", "заметка").firstOrNull { lower.startsWith(it) }?.let { p ->
                return Command(Kind.NOTE, rest(p).ifBlank { t })
            }
            listOf("/spent", "трата", "расход", "потратил", "потратила").firstOrNull { lower.startsWith(it) }?.let { p ->
                val r = rest(p)
                val m = Regex("""^(\d+(?:[.,]\d{1,2})?)\s*(?:₽|р\.?|руб\.?)?\s*(.*)$""").find(r)
                if (m != null) {
                    val kop = (m.groupValues[1].replace(',', '.').toDouble() * 100).toLong()
                    return Command(Kind.EXPENSE, m.groupValues[2].trim(), kop)
                }
            }
            return Command(Kind.NOTE, t)
        }
    }
}
