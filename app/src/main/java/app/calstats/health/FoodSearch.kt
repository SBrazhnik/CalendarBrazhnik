package app.calstats.health

import app.calstats.work.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open Food Facts — открытая база продуктов с калорийностью и штрихкодами (много российских товаров). */
object FoodSearch {
    private const val UA = "ZelenyiRitm/1.0 (Android; personal diary)"
    private const val FIELDS = "code,product_name,product_name_ru,brands,nutriments,serving_quantity"

    suspend fun search(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://ru.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=30&lc=ru&fields=$FIELDS"
        val root = Json.parseToJsonElement(get(url)).jsonObject
        (root["products"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }

    suspend fun byBarcode(code: String): FoodItem? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$FIELDS"
        val root = Json.parseToJsonElement(get(url)).jsonObject
        (root["product"] as? JsonObject)?.let(::parse)?.copy(barcode = code)
    }

    fun parse(p: JsonObject): FoodItem? {
        fun str(k: String) = (p[k] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        val n = p["nutriments"] as? JsonObject ?: return null
        fun num(k: String) = (n[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
        val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: return null
        val name = str("product_name_ru") ?: str("product_name") ?: return null
        val brand = str("brands")?.split(',')?.firstOrNull()?.trim()
        val serving = (p["serving_quantity"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.toInt()
        return FoodItem(
            name = if (brand != null && !name.contains(brand, true)) "$name, $brand" else name,
            kcal = kcal,
            protein = num("proteins_100g") ?: 0.0,
            fat = num("fat_100g") ?: 0.0,
            carbs = num("carbohydrates_100g") ?: 0.0,
            barcode = str("code"),
            portion = serving?.takeIf { it in 1..2000 } ?: 100,
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
