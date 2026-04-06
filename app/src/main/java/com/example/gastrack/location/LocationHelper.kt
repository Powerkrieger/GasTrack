package com.example.gastrack.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.example.gastrack.network.NearbyStation
import com.example.gastrack.network.OverpassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationHelper"

data class LocationData(
    val lat: Double,
    val lon: Double,
    val city: String,
    val nearbyStation: NearbyStation?
)

sealed class LocationResult {
    data class Success(val location: Location) : LocationResult()
    /** Both GPS and network providers are disabled on the device. */
    object ProvidersDisabled : LocationResult()
}

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationResult = suspendCancellableCoroutine { cont ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Prefer network (fast) then GPS — try both simultaneously
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }

        if (providers.isEmpty()) {
            Log.w(TAG, "No location providers enabled")
            cont.resume(LocationResult.ProvidersDisabled)
            return@suspendCancellableCoroutine
        }

        // Return a fresh cached fix immediately if available (≤60 s old)
        for (p in providers) {
            val last = manager.getLastKnownLocation(p)
            if (last != null && System.currentTimeMillis() - last.time < 60_000L) {
                Log.d(TAG, "Using cached location from provider $p (age ${System.currentTimeMillis() - last.time} ms)")
                cont.resume(LocationResult.Success(last))
                return@suspendCancellableCoroutine
            }
        }

        Log.d(TAG, "Requesting live location from providers: $providers")

        // Register on all enabled providers; first callback wins
        val listeners = mutableListOf<LocationListener>()

        fun deliver(location: Location) {
            if (!cont.isCompleted) {
                Log.d(TAG, "Got location from ${location.provider}: ${location.latitude}, ${location.longitude} acc=${location.accuracy}m")
                listeners.forEach { manager.removeUpdates(it) }
                cont.resume(LocationResult.Success(location))
            }
        }

        for (p in providers) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = deliver(location)

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

                override fun onProviderDisabled(provider: String) {
                    Log.w(TAG, "Provider disabled during request: $provider")
                    // If all providers become disabled, give up
                    if (providers.none { manager.isProviderEnabled(it) }) {
                        Log.w(TAG, "All providers disabled — giving up")
                        listeners.forEach { manager.removeUpdates(it) }
                        if (!cont.isCompleted) cont.resume(LocationResult.ProvidersDisabled)
                    }
                }
            }
            listeners.add(listener)
            manager.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
        }

        cont.invokeOnCancellation { listeners.forEach { manager.removeUpdates(it) } }
    }

    @Suppress("DEPRECATION")
    suspend fun getCity(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val city = addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.adminArea
                ?: ""
            if (city.isEmpty()) Log.w(TAG, "Geocoder returned no city for $lat,$lon (${addresses?.size} addresses)")
            city
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed for $lat,$lon: ${e.message}")
            ""
        }
    }

    suspend fun getLocationData(): LocationData? {
        val location = (getCurrentLocation() as? LocationResult.Success)?.location ?: return null
        val city = getCity(location.latitude, location.longitude)
        val nearby = withContext(Dispatchers.IO) {
            OverpassService.findNearestStation(location.latitude, location.longitude)
        }
        return LocationData(location.latitude, location.longitude, city, nearby)
    }
}
