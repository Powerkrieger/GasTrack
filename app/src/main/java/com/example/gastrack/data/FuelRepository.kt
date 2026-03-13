package com.example.gastrack.data

import android.content.ContentValues
import android.content.Context
import java.util.UUID

class FuelRepository(context: Context) {

    private val db = GasTrackDatabase(context)

    fun insertEntry(entry: FuelEntry) {
        val stationId = getOrCreateStation(
            name = entry.stationName,
            lat = entry.latitude,
            lon = entry.longitude,
            city = entry.city
        )
        val values = ContentValues().apply {
            put("id", entry.id)
            put("station_id", stationId)
            put("timestamp", entry.timestamp)
            put("liters", entry.liters)
            put("euros", entry.euros)
            put("price_per_liter", entry.pricePerLiter)
            put("kilometers", entry.kilometers)
            put("receipt_path", entry.receiptPath)
        }
        db.writableDatabase.insert("fuel_entries", null, values)
    }

    fun getAllEntries(): List<FuelEntry> {
        val sql = """
            SELECT e.id, e.timestamp, e.liters, e.euros, e.price_per_liter, e.kilometers, e.receipt_path,
                   s.name, s.latitude, s.longitude, s.city
            FROM fuel_entries e
            JOIN stations s ON e.station_id = s.id
            ORDER BY e.timestamp DESC
        """.trimIndent()
        val cursor = db.readableDatabase.rawQuery(sql, null)
        val entries = mutableListOf<FuelEntry>()
        cursor.use {
            while (it.moveToNext()) {
                entries.add(cursorToEntry(it))
            }
        }
        return entries
    }

    fun getLatestEntry(): FuelEntry? {
        val sql = """
            SELECT e.id, e.timestamp, e.liters, e.euros, e.price_per_liter, e.kilometers, e.receipt_path,
                   s.name, s.latitude, s.longitude, s.city
            FROM fuel_entries e
            JOIN stations s ON e.station_id = s.id
            ORDER BY e.timestamp DESC
            LIMIT 1
        """.trimIndent()
        val cursor = db.readableDatabase.rawQuery(sql, null)
        cursor.use {
            if (it.moveToFirst()) return cursorToEntry(it)
        }
        return null
    }

    fun getEntryById(id: String): FuelEntry? {
        val sql = """
            SELECT e.id, e.timestamp, e.liters, e.euros, e.price_per_liter, e.kilometers, e.receipt_path,
                   s.name, s.latitude, s.longitude, s.city
            FROM fuel_entries e
            JOIN stations s ON e.station_id = s.id
            WHERE e.id = ?
        """.trimIndent()
        val cursor = db.readableDatabase.rawQuery(sql, arrayOf(id))
        cursor.use {
            if (it.moveToFirst()) return cursorToEntry(it)
        }
        return null
    }

    private fun cursorToEntry(it: android.database.Cursor) = FuelEntry(
        id = it.getString(0),
        timestamp = it.getLong(1),
        liters = it.getDouble(2),
        euros = it.getDouble(3),
        pricePerLiter = it.getDouble(4),
        kilometers = it.getDouble(5),
        receiptPath = it.getString(6),
        stationName = it.getString(7),
        latitude = it.getDouble(8),
        longitude = it.getDouble(9),
        city = it.getString(10)
    )

    private fun getOrCreateStation(name: String, lat: Double, lon: Double, city: String): String {
        val cursor = db.readableDatabase.query(
            "stations", arrayOf("id"), "name=?", arrayOf(name), null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("latitude", lat)
            put("longitude", lon)
            put("city", city)
        }
        db.writableDatabase.insert("stations", null, values)
        return id
    }
}
