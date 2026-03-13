package com.example.gastrack.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class NearbyStation(val name: String, val lat: Double, val lon: Double)

object OverpassService {

    fun findNearestStation(lat: Double, lon: Double, radiusMeters: Int = 500): NearbyStation? {
        // nwr = node/way/relation so polygon-mapped stations are included
        // out center returns a lat/lon centroid for ways and relations
        val query = """[out:json];nwr["amenity"="fuel"](around:$radiusMeters,$lat,$lon);out center 5;"""
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlString = "https://overpass-api.de/api/interpreter?data=$encoded"
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseResponse(response)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResponse(json: String): NearbyStation? {
        val elements = JSONObject(json).getJSONArray("elements")
        if (elements.length() == 0) return null
        val element = elements.getJSONObject(0)
        // Nodes have top-level lat/lon; ways/relations have a "center" object
        val elLat: Double
        val elLon: Double
        if (element.has("lat")) {
            elLat = element.getDouble("lat")
            elLon = element.getDouble("lon")
        } else {
            val center = element.optJSONObject("center") ?: return null
            elLat = center.getDouble("lat")
            elLon = center.getDouble("lon")
        }
        val tags = element.optJSONObject("tags")
        val name = tags?.optString("name")?.takeIf { it.isNotEmpty() }
            ?: tags?.optString("brand")?.takeIf { it.isNotEmpty() }
            ?: "Fuel Station"
        return NearbyStation(name, elLat, elLon)
    }
}
