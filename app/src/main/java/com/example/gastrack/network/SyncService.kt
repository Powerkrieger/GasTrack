package com.example.gastrack.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.gastrack.data.FuelEntry
import com.example.gastrack.data.FuelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "SyncService"
private const val PREFS = "sync_prefs"

sealed class SyncResult {
    object NotConfigured : SyncResult()
    data class Success(val pushed: Int, val pulled: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class SyncService(private val context: Context, private val repository: FuelRepository) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val serverUrl: String get() = prefs.getString("server_url", "") ?: ""
    val apiKey: String get() = prefs.getString("api_key", "") ?: ""
    val isConfigured: Boolean get() = serverUrl.isNotEmpty() && apiKey.isNotEmpty()

    fun saveConfig(url: String, key: String) {
        prefs.edit()
            .putString("server_url", url.trimEnd('/'))
            .putString("api_key", key.trim())
            .apply()
    }

    suspend fun sync(): SyncResult {
        if (!isConfigured) return SyncResult.NotConfigured

        return withContext(Dispatchers.IO) {
            try {
                val unsynced = repository.getUnsyncedEntries()
                val allIds = repository.getAllIds()
                val deletedIds = repository.getDeletedIds()

                // Protocol contract:
                // Request:  { known_ids, entries, deleted_ids }
                // Response: { entries, deleted_ids }
                // Server stores tombstones and returns IDs deleted by other devices
                // that are still present in this client's known_ids.
                // Server must never push back entries whose ID is tombstoned.
                val requestBody = JSONObject().apply {
                    put("known_ids", JSONArray(allIds))
                    put("entries", JSONArray(unsynced.map { it.toJson() }))
                    put("deleted_ids", JSONArray(deletedIds))
                }

                val (code, body) = httpPost("$serverUrl/sync", requestBody.toString(), apiKey)
                if (code != 200) return@withContext SyncResult.Error("Server returned $code")

                val receiptsDir = File(context.filesDir, "receipts").also { it.mkdirs() }
                val responseObj = JSONObject(body)
                val responseEntries = responseObj.getJSONArray("entries")
                val pulled = mutableListOf<FuelEntry>()

                for (i in 0 until responseEntries.length()) {
                    val obj = responseEntries.getJSONObject(i)
                    val photoB64 = obj.optString("photo", "")
                    val receiptPath: String? = if (photoB64.isNotEmpty()) {
                        val photoFile = File(receiptsDir, "${obj.getString("id")}.jpg")
                        photoFile.writeBytes(Base64.decode(photoB64, Base64.DEFAULT))
                        photoFile.absolutePath
                    } else null

                    pulled.add(FuelEntry(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        city = obj.getString("city"),
                        stationName = obj.getString("station_name"),
                        liters = obj.getDouble("liters"),
                        euros = obj.getDouble("euros"),
                        pricePerLiter = obj.getDouble("price_per_liter"),
                        kilometers = obj.getDouble("kilometers"),
                        receiptPath = receiptPath,
                        synced = true
                    ))
                }

                for (entry in pulled) repository.upsertEntry(entry)
                repository.markSynced(unsynced.map { it.id })
                repository.purgeTombstones(deletedIds)

                val remoteTombstones = responseObj.optJSONArray("deleted_ids")
                if (remoteTombstones != null) {
                    val ids = (0 until remoteTombstones.length()).map { remoteTombstones.getString(it) }
                    repository.applyTombstones(ids)
                }

                Log.i(TAG, "Sync complete: pushed=${unsynced.size}, pulled=${pulled.size}")
                SyncResult.Success(pushed = unsynced.size, pulled = pulled.size)
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}", e)
                SyncResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun httpPost(url: String, body: String, key: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Api-Key", key)
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val responseBody = (if (code < 400) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return Pair(code, responseBody)
    }

    private fun FuelEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("latitude", latitude)
        put("longitude", longitude)
        put("city", city)
        put("station_name", stationName)
        put("liters", liters)
        put("euros", euros)
        put("price_per_liter", pricePerLiter)
        put("kilometers", kilometers)
        if (receiptPath != null) {
            val file = File(receiptPath)
            if (file.exists()) {
                put("photo", Base64.encodeToString(file.readBytes(), Base64.DEFAULT))
            }
        }
    }
}
