package app.calstats

import app.calstats.work.Recipe
import app.calstats.work.RecipeIng
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Небольшие запросы в интернет: рецепт по ссылке, курс валют ЦБ, книга по ISBN. */
object Net {
    private fun get(url: String, charset: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ZelenyiRitm")
        c.instanceFollowRedirects = true
        return c.inputStream.use { s ->
            val bytes = s.readBytes()
            val cs = charset ?: c.contentType?.substringAfter("charset=", "")?.takeIf { it.isNotBlank() } ?: "UTF-8"
            String(bytes, charset(cs))
        }
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

    /** Найденная бесплатная книга (общественное достояние, Project Gutenberg). */
    data class FreeBook(val id: Int, val title: String, val authors: String, val lang: String, val downloads: Int, val epub: String?, val txt: String?)

    /** Поиск в Project Gutenberg через открытый каталог Gutendex. lang: "ru", "en" или "" (все). */
    fun searchFree(q: String, lang: String): List<FreeBook> {
        val url = "https://gutendex.com/books/?search=" + java.net.URLEncoder.encode(q, "UTF-8") + if (lang.isNotBlank()) "&languages=$lang" else ""
        val a = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val f = o.optJSONObject("formats") ?: JSONObject()
            fun fmt(prefix: String) = f.keys().asSequence().firstOrNull { it.startsWith(prefix) && !f.optString(it).endsWith(".zip") }?.let { f.optString(it) }
            val authors = o.optJSONArray("authors")?.let { au -> (0 until au.length()).joinToString { k -> au.getJSONObject(k).optString("name").split(", ").reversed().joinToString(" ") } }.orEmpty()
            FreeBook(o.optInt("id"), o.optString("title"), authors, o.optJSONArray("languages")?.optString(0).orEmpty(), o.optInt("download_count"),
                fmt("application/epub+zip"), fmt("text/plain"))
        }
    }

    /** Скачать файл в папку приложения. */
    fun download(url: String, to: java.io.File) {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ZelenyiRitm")
            if (c.responseCode in 300..399) { u = java.net.URL(URL(u), c.getHeaderField("Location")).toString(); c.disconnect(); return@repeat }
            if (c.responseCode !in 200..299) error("Сервер ответил ${c.responseCode}")
            to.parentFile?.mkdirs()
            c.inputStream.use { i -> to.outputStream().use { i.copyTo(it) } }
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
