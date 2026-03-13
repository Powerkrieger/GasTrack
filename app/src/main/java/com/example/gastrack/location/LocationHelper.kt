package com.example.gastrack.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.example.gastrack.network.NearbyStation
import com.example.gastrack.network.OverpassService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationData(
    val lat: Double,
    val lon: Double,
    val city: String,
    val nearbyStation: NearbyStation?
)

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Prefer network (fast) then GPS — try both simultaneously
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }

        if (providers.isEmpty()) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        // Return a fresh cached fix immediately if available (≤60 s old)
        for (p in providers) {
            val last = manager.getLastKnownLocation(p)
            if (last != null && System.currentTimeMillis() - last.time < 60_000L) {
                cont.resume(last)
                return@suspendCancellableCoroutine
            }
        }

        // Register on all enabled providers; first callback wins
        val listeners = mutableListOf<LocationListener>()

        fun deliver(location: Location) {
            if (!cont.isCompleted) {
                listeners.forEach { manager.removeUpdates(it) }
                cont.resume(location)
            }
        }

        for (p in providers) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = deliver(location)

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

                override fun onProviderDisabled(provider: String) {
                    // If all providers become disabled, give up
                    if (providers.none { manager.isProviderEnabled(it) }) {
                        listeners.forEach { manager.removeUpdates(it) }
                        if (!cont.isCompleted) cont.resume(null)
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
            addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.adminArea
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun getLocationData(): LocationData? {
        val location = getCurrentLocation() ?: return null
        val city = getCity(location.latitude, location.longitude)
        val nearby = withContext(Dispatchers.IO) {
            OverpassService.findNearestStation(location.latitude, location.longitude)
        }
        return LocationData(location.latitude, location.longitude, city, nearby)
    }
}
