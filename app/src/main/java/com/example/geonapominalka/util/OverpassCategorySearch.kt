package com.example.geonapominalka.util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Поиск объектов по категории через Overpass API — в отличие от Nominatim, ищет по
 * OSM-тегу в радиусе от точки и сортирует по реальному расстоянию, а не по внутренней
 * "важности" объекта (на запрос "почта" Nominatim мог вернуть отделение за 40км, т.к.
 * оно "важнее" для его ранжирования, чем реально ближайшее).
 */
object OverpassCategorySearch {
    private const val BASE_URL = "https://overpass-api.de/api/interpreter"
    private const val SEARCH_RADIUS_METERS = 20000
    private val CATEGORIES = mapOf(
        "почта" to "amenity=post_office",
        "аптека" to "amenity=pharmacy",
        "магазин" to "shop=supermarket",
        "продукты" to "shop=convenience",
        "банкомат" to "amenity=atm",
        "банк" to "amenity=bank",
        "заправка" to "amenity=fuel",
        "кафе" to "amenity=cafe",
        "ресторан" to "amenity=restaurant",
        "больница" to "amenity=hospital",
        "поликлиника" to "amenity=clinic",
        "стоматология" to "amenity=dentist",
        "ветеринар" to "amenity=veterinary",
        "школа" to "amenity=school",
        "детский сад" to "amenity=kindergarten",
        "библиотека" to "amenity=library",
        "парковка" to "amenity=parking",
        "туалет" to "amenity=toilets",
        "остановка" to "highway=bus_stop",
        "метро" to "railway=station",
        "парикмахерская" to "shop=hairdresser",
        "химчистка" to "shop=dry_cleaning",
        "прачечная" to "shop=laundry",
        "автосервис" to "shop=car_repair",
        "спортзал" to "leisure=fitness_centre",
        "почтомат" to "amenity=parcel_locker"
    )

    /** OSM-тег, если запрос целиком совпадает с известной категорией. */
    fun matchCategory(query: String): String? = CATEGORIES[query.trim().lowercase()]

    data class Result(val latitude: Double, val longitude: Double, val displayName: String)

    /** Ищет ближайшие объекты тега в радиусе SEARCH_RADIUS_METERS от точки. */
    suspend fun searchNearby(tag: String, userLat: Double, userLon: Double): Result? =
        withContext(Dispatchers.IO) {
            val (key, value) = tag.split("=", limit = 2)
            val query = """
                [out:json][timeout:15];
                node["$key"="$value"](around:$SEARCH_RADIUS_METERS,$userLat,$userLon);
                out body 20;
            """.trimIndent()
            var connection: HttpURLConnection? = null
            try {
                val url = "$BASE_URL?data=${URLEncoder.encode(query, "UTF-8")}"
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val elements = org.json.JSONObject(text).optJSONArray("elements") ?: return@withContext null
                nearestElement(elements, userLat, userLon)
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    private fun nearestElement(elements: JSONArray, userLat: Double, userLon: Double): Result? {
        var best: Result? = null
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until elements.length()) {
            val obj = elements.getJSONObject(i)
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val distance = LocationUtils.distanceMeters(userLat, userLon, lat, lon)
            if (distance < bestDistance) {
                bestDistance = distance.toDouble()
                val name = obj.optJSONObject("tags")?.optString("name")?.takeIf { it.isNotBlank() }
                best = Result(lat, lon, name ?: "Ближайший объект")
            }
        }
        return best
    }
}
