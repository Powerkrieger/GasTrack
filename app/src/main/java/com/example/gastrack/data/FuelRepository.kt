package com.example.gastrack.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FuelRepository(private val context: Context) {

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

    fun exportToZip(uri: Uri) {
        val entries = getAllEntries()
        val jsonArray = JSONArray()
        for (entry in entries) {
            jsonArray.put(JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("latitude", entry.latitude)
                put("longitude", entry.longitude)
                put("city", entry.city)
                put("stationName", entry.stationName)
                put("liters", entry.liters)
                put("euros", entry.euros)
                put("pricePerLiter", entry.pricePerLiter)
                put("kilometers", entry.kilometers)
                if (entry.receiptPath != null) put("receiptFile", File(entry.receiptPath).name)
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("entries.json"))
                zip.write(jsonArray.toString(2).toByteArray())
                zip.closeEntry()
                for (entry in entries) {
                    if (entry.receiptPath != null) {
                        val file = File(entry.receiptPath)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("receipts/${file.name}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    fun importFromZip(uri: Uri): Int {
        val receiptsDir = File(context.filesDir, "receipts").also { it.mkdirs() }
        var entriesJson: String? = null
        val receiptData = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "entries.json" ->
                            entriesJson = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("receipts/") -> {
                            val name = entry.name.removePrefix("receipts/")
                            if (name.isNotEmpty()) receiptData[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        for ((name, bytes) in receiptData) {
            File(receiptsDir, name).writeBytes(bytes)
        }

        var imported = 0
        entriesJson?.let { json ->
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                if (!entryExists(id)) {
                    val receiptPath = if (obj.has("receiptFile"))
                        File(receiptsDir, obj.getString("receiptFile")).absolutePath
                    else null
                    insertEntry(FuelEntry(
                        id = id,
                        timestamp = obj.getLong("timestamp"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        city = obj.getString("city"),
                        stationName = obj.getString("stationName"),
                        liters = obj.getDouble("liters"),
                        euros = obj.getDouble("euros"),
                        pricePerLiter = obj.getDouble("pricePerLiter"),
                        kilometers = obj.getDouble("kilometers"),
                        receiptPath = receiptPath
                    ))
                    imported++
                }
            }
        }
        return imported
    }

    private fun entryExists(id: String): Boolean {
        val cursor = db.readableDatabase.query(
            "fuel_entries", arrayOf("id"), "id=?", arrayOf(id), null, null, null
        )
        cursor.use { return it.moveToFirst() }
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
