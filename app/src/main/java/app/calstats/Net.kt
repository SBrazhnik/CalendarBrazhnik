package app.calstats

import app.calstats.work.Recipe
import app.calstats.work.RecipeIng
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.async

/** Небольшие запросы в интернет: рецепт по ссылке, курс валют ЦБ, книга по ISBN. */
object Net {
    const val UA = "ZelenyiRitm/1.0 (Android; app.calstats.brazhnik)"

    /** GET с проверкой ответа и повтором при временных сбоях (таймаут, 5xx). */
    fun get(url: String, charset: String? = null, ua: String = UA, readTimeout: Int = 30_000, tries: Int = 2): String {
        var last: Exception? = null
        repeat(tries) { attempt ->
            try { return getOnce(url, charset, ua, readTimeout) } catch (e: Exception) {
                last = e
                val retry = e is java.net.SocketTimeoutException || (e is HttpError && e.code >= 500) || e is java.io.IOException && e !is java.net.UnknownHostException && e !is HttpError
                if (!retry || attempt == tries - 1) throw e
                Thread.sleep(1500L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException()
    }

    class HttpError(val code: Int) : java.io.IOException("HTTP $code")

    private fun getOnce(url: String, charset: String?, ua: String, readTimeout: Int): String {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = readTimeout
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", ua)
                c.setRequestProperty("Accept", "application/json, text/xml, */*")
                val code = c.responseCode
                if (code in 300..399) { u = URL(URL(u), c.getHeaderField("Location") ?: throw HttpError(code)).toString(); return@repeat }
                if (code !in 200..299) throw HttpError(code)
                val bytes = c.inputStream.use { it.readBytes() }
                val cs = charset ?: c.contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() } ?: "UTF-8"
                return String(bytes, charset(cs))
            } finally { c.disconnect() }
        }
        throw HttpError(310)
    }

    /** Понятная причина сбоя сети для пользователя. */
    fun why(e: Throwable?): String = when (e) {
        null -> "неизвестная ошибка"
        is java.net.UnknownHostException -> "сайт не находится — нет интернета или адрес заблокирован провайдером"
        is java.net.SocketTimeoutException -> "сервер слишком долго не отвечает — попробуйте ещё раз чуть позже"
        is java.net.ConnectException -> "не удалось подключиться — сайт недоступен из вашей сети (может помочь другая сеть или VPN)"
        is javax.net.ssl.SSLException -> "ошибка защищённого соединения — проверьте дату и время в телефоне"
        is HttpError -> when (e.code) {
            429 -> "слишком много запросов — подождите минуту"
            403 -> "сервер отказал в доступе из вашей сети (403)"
            in 500..599 -> "сервис временно перегружен (${e.code}) — попробуйте позже"
            else -> "сервер ответил ошибкой ${e.code}"
        }
        is org.json.JSONException, is kotlinx.serialization.SerializationException -> "сервер прислал непонятный ответ — вероятно, он перегружен"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Рецепт со страницы сайта по разметке schema.org/Recipe (её используют почти все кулинарные сайты). */
    fun recipe(url: String): Recipe? {
        val html = get(url)
        val blocks = Regex("""<script[^>]*application/ld\+json[^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { it.groupValues[1].trim() }
        for (b in blocks) {
            val found = runCatching { findRecipe(if (b.startsWith("[")) JSONArray(b) else JSONObject(b)) }.getOrNull() ?: continue
            return found
        }
        return null
    }

    private fun findRecipe(x: Any?): Recipe? {
        when (x) {
            is JSONArray -> for (i in 0 until x.length()) findRecipe(x.opt(i))?.let { return it }
            is JSONObject -> {
                val t = x.opt("@type")
                val isRecipe = t == "Recipe" || (t is JSONArray && (0 until t.length()).any { t.optString(it) == "Recipe" })
                if (isRecipe) return parse(x)
                x.optJSONArray("@graph")?.let { findRecipe(it)?.let { r -> return r } }
            }
        }
        return null
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&quot;", "\"").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    private fun parse(o: JSONObject): Recipe {
        val ings = o.optJSONArray("recipeIngredient")?.let { a -> (0 until a.length()).map { clean(a.optString(it)) } }.orEmpty().filter { it.isNotBlank() }.map { line ->
            // «Мука — 200 г» / «200 г муки»
            val p = line.split(Regex("\\s+[—–-]\\s+|:\\s+"), limit = 2)
            if (p.size == 2) RecipeIng(p[0].trim(), p[1].trim()) else RecipeIng(line, "")
        }
        val steps = mutableListOf<String>()
        fun addSteps(v: Any?) {
            when (v) {
                is String -> steps += clean(v)
                is JSONArray -> for (i in 0 until v.length()) addSteps(v.opt(i))
                is JSONObject -> { v.optString("text").takeIf { it.isNotBlank() }?.let { steps += clean(it) }; v.optJSONArray("itemListElement")?.let { addSteps(it) } }
            }
        }
        addSteps(o.opt("recipeInstructions"))
        val minutes = (o.optString("totalTime").ifBlank { o.optString("cookTime") }).let { iso ->
            val h = Regex("(\\d+)H").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = Regex("(\\d+)M").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            (h * 60 + m).takeIf { it > 0 }
        }
        val yieldTxt = o.opt("recipeYield").let { if (it is JSONArray) it.optString(0) else it?.toString().orEmpty() }
        val servings = Regex("\\d+").find(yieldTxt)?.value?.toIntOrNull() ?: 2
        val kcal = o.optJSONObject("nutrition")?.optString("calories")?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
        return Recipe(app.calstats.work.newId(), clean(o.optString("name")).ifBlank { "Рецепт" }, "🍲", minutes, servings, ings,
            steps.filter { it.isNotBlank() }.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"), kcal = kcal)
    }

    /** Курсы ЦБ РФ: код валюты → рублей за 1 единицу. */
    fun rates(): Map<String, Double> {
        val xml = get("https://www.cbr.ru/scripts/XML_daily.asp", "windows-1251")
        val out = mutableMapOf("RUB" to 1.0)
        Regex("""<Valute[^>]*>.*?<CharCode>(\w+)</CharCode>.*?<Nominal>(\d+)</Nominal>.*?<Value>([\d,]+)</Value>.*?</Valute>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val nominal = m.groupValues[2].toDouble(); val v = m.groupValues[3].replace(',', '.').toDouble()
            out[m.groupValues[1]] = v / nominal
        }
        return out
    }

    /** Найденная бесплатная книга (общественное достояние). */
    data class FreeBook(val source: String, val id: String, val title: String, val authors: String, val lang: String, val url: String, val fmt: String)

    /** Результат поиска по всем библиотекам: найденное и ошибки по каждой. */
    data class FreeSearch(val books: List<FreeBook>, val errors: List<String>)

    /**
     * Поиск сразу в нескольких открытых библиотеках параллельно:
     * Викитека (русская классика), Project Gutenberg (через Gutendex и напрямую).
     */
    suspend fun searchFree(q: String, lang: String): FreeSearch = kotlinx.coroutines.coroutineScope {
        val wsLang = if (lang == "en") "en" else "ru"
        val ws = async(kotlinx.coroutines.Dispatchers.IO) { runCatching { wikisource(q, wsLang) } }
        val gb = async(kotlinx.coroutines.Dispatchers.IO) {
            val first = runCatching { gutendex(q, lang) }
            if (first.isSuccess) first else runCatching { gutenbergOpds(q, lang) }.let { if (it.isSuccess) it else first }
        }
        val parts = listOf("Викитека" to ws.await(), "Project Gutenberg" to gb.await())
        val books = parts.flatMap { it.second.getOrNull().orEmpty() }
        val errors = parts.filter { it.second.isFailure }.map { "${it.first}: ${why(it.second.exceptionOrNull())}" }
        if (books.isEmpty() && errors.size == parts.size) throw IllegalStateException(errors.joinToString("\n"))
        FreeSearch(if (lang == "en") books.sortedBy { it.source != "Project Gutenberg" } else books, errors)
    }

    private val PAREN = Regex("""^(.*?)\s*\(([^()]+)\)\s*$""")
    private val TAGS = Regex("<[^>]+>")

    /** Викитека: свободные тексты на русском. Книгу собирает официальный сервис ws-export в EPUB. */
    private fun wikisource(q: String, lang: String): List<FreeBook> {
        val url = "https://$lang.wikisource.org/w/api.php?action=query&list=search&format=json&srnamespace=0&srlimit=40&srprop=&srsearch=" +
            java.net.URLEncoder.encode(q, "UTF-8")
        val a = JSONObject(get(url, readTimeout = 20_000)).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).optString("title") }
            .filter { it.isNotBlank() && '/' !in it && !it.contains(':') }
            .distinct().take(20).map { t ->
                // «Война и мир (Толстой)» → название + автор
                val m = PAREN.find(t)
                val title = m?.groupValues?.get(1) ?: t
                val author = m?.groupValues?.get(2).orEmpty()
                val page = java.net.URLEncoder.encode(t.replace(' ', '_'), "UTF-8")
                FreeBook("Викитека", t, title, author, lang, "https://ws-export.wmcloud.org/?format=epub&lang=$lang&page=$page", "epub")
            }
    }

    private fun gutendex(q: String, lang: String): List<FreeBook> {
        val url = "https://gutendex.com/books/?search=" + java.net.URLEncoder.encode(q, "UTF-8") + if (lang.isNotBlank()) "&languages=$lang" else ""
        val a = JSONObject(get(url, readTimeout = 25_000, tries = 1)).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val authors = o.optJSONArray("authors")?.let { au -> (0 until au.length()).joinToString { k -> au.getJSONObject(k).optString("name").split(", ").reversed().joinToString(" ") } }.orEmpty()
            val id = o.optInt("id")
            FreeBook("Project Gutenberg", id.toString(), o.optString("title"), authors, o.optJSONArray("languages")?.optString(0).orEmpty(),
                "https://www.gutenberg.org/ebooks/$id.epub.noimages", "epub")
        }
    }

    /** Запасной путь: OPDS-каталог gutenberg.org (язык задаётся в запросе как «l.ru»). */
    private fun gutenbergOpds(q: String, lang: String): List<FreeBook> {
        val query = q + if (lang.isNotBlank()) " l.$lang" else ""
        val xml = get("https://www.gutenberg.org/ebooks/search.opds/?query=" + java.net.URLEncoder.encode(query, "UTF-8"))
        fun unesc(t: String) = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").trim()
        return Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val e = m.groupValues[1]
            val id = Regex("""/ebooks/(\d+)""").find(e)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1)?.let(::unesc).orEmpty()
            val author = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1)?.let { unesc(it.replace(TAGS, " ")) }.orEmpty()
            FreeBook("Project Gutenberg", id, title, author, lang, "https://www.gutenberg.org/ebooks/$id.epub.noimages", "epub")
        }.distinctBy { it.id }.toList()
    }

    /** Скачать файл в папку приложения. */
    fun download(url: String, to: java.io.File) {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 150_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode in 300..399) { u = java.net.URL(URL(u), c.getHeaderField("Location")).toString(); c.disconnect(); return@repeat }
            if (c.responseCode !in 200..299) throw HttpError(c.responseCode)
            to.parentFile?.mkdirs()
            c.inputStream.use { i -> to.outputStream().use { i.copyTo(it) } }
            c.disconnect()
            // EPUB — это zip: если пришла веб-страница с ошибкой, книги нет
            if (to.extension == "epub") {
                val sig = to.inputStream().use { s -> ByteArray(2).also { s.read(it) } }
                if (!(sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte())) { to.delete(); error("библиотека не смогла собрать книгу, попробуйте другую") }
            }
            return
        }
        error("Слишком много перенаправлений")
    }

    data class BookInfo(val title: String, val author: String, val pages: Int)

    /** Книга по ISBN (Google Книги, без ключа). */
    fun isbn(code: String): BookInfo? {
        val j = JSONObject(get("https://www.googleapis.com/books/v1/volumes?q=isbn:" + code.filter { it.isDigit() || it == 'X' }))
        val v = j.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("volumeInfo") ?: return null
        val authors = v.optJSONArray("authors")?.let { a -> (0 until a.length()).joinToString { a.optString(it) } }.orEmpty()
        return BookInfo(v.optString("title"), authors, v.optInt("pageCount", 0))
    }
}
