package com.example.gastrack.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.gastrack.data.FuelEntry
import com.example.gastrack.data.FuelRepository
import com.example.gastrack.location.LocationHelper
import com.example.gastrack.network.OverpassService
import com.example.gastrack.storage.ImageStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun AddEntryScreen(
    repository: FuelRepository,
    locationHelper: LocationHelper,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stationName by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    var euros by remember { mutableStateOf("") }
    var kilometers by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var city by remember { mutableStateOf("") }
    var receiptPath by remember { mutableStateOf<String?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var lastEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingReceiptFile by remember { mutableStateOf<File?>(null) }
    var stationNameAlert by remember { mutableStateOf(false) }
    var litersAlert by remember { mutableStateOf(false) }
    var eurosAlert by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val litersShakeOffset = remember { Animatable(0f) }
    val eurosShakeOffset = remember { Animatable(0f) }

    suspend fun runShake(animatable: Animatable<Float, *>) {
        for (i in 0..3) animatable.animateTo(if (i % 2 == 0) 14f else -14f, tween(70))
        animatable.animateTo(0f, tween(70))
    }

    LaunchedEffect(stationNameAlert) { if (stationNameAlert) runShake(shakeOffset) }
    LaunchedEffect(litersAlert) { if (litersAlert) runShake(litersShakeOffset) }
    LaunchedEffect(eurosAlert) { if (eurosAlert) runShake(eurosShakeOffset) }

    LaunchedEffect(refreshKey) {
        lastEntry = withContext(Dispatchers.IO) { repository.getLatestEntry() }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            receiptPath = pendingReceiptFile?.absolutePath
            statusMessage = "Receipt photo saved."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = ImageStorage.createReceiptFile(context)
            pendingReceiptFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        } else {
            statusMessage = "Camera permission denied."
        }
    }

    // Phase 1: get coordinates (30 s timeout) → update UI immediately.
    // Phase 2: look up the nearby station without the spinner so the Save button isn't blocked.
    suspend fun fetchLocation() {
        val location = withTimeoutOrNull(30_000) { locationHelper.getCurrentLocation() }
        if (location == null) {
            statusMessage = "Could not get location."
            isFetchingLocation = false
            return
        }
        latitude = location.latitude
        longitude = location.longitude
        city = locationHelper.getCity(location.latitude, location.longitude)
        statusMessage = if (city.isNotEmpty()) "Location: $city" else "Location obtained."
        isFetchingLocation = false  // stop spinner; save button is now available

        val nearby = withContext(Dispatchers.IO) {
            OverpassService.findNearestStation(location.latitude, location.longitude)
        }
        if (stationName.isBlank()) {
            if (nearby != null) {
                stationName = nearby.name
            } else {
                stationNameAlert = true
                delay(1_500)
                stationNameAlert = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isFetchingLocation = true
            statusMessage = "Getting location..."
            scope.launch { fetchLocation() }
        } else {
            statusMessage = "Location permission denied."
        }
    }

    fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            isFetchingLocation = true
            statusMessage = "Getting location..."
            scope.launch { fetchLocation() }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun requestCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val file = ImageStorage.createReceiptFile(context)
            pendingReceiptFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun saveEntry() {
        val litersVal = liters.toDoubleOrNull()
        val eurosVal = euros.toDoubleOrNull()
        var hasError = false
        if (litersVal == null || litersVal <= 0) {
            litersAlert = true
            scope.launch { delay(1_500); litersAlert = false }
            hasError = true
        }
        if (eurosVal == null || eurosVal <= 0) {
            eurosAlert = true
            scope.launch { delay(1_500); eurosAlert = false }
            hasError = true
        }
        if (stationName.isBlank() || hasError) {
            statusMessage = if (stationName.isBlank()) "Please fill in a station name." else ""
            return
        }
        isSaving = true
        statusMessage = "Saving..."
        scope.launch {
            var lat = latitude ?: 0.0
            var lon = longitude ?: 0.0
            var entryCity = city

            if (latitude == null) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val data = withTimeoutOrNull(8_000) { locationHelper.getLocationData() }
                    if (data != null) {
                        lat = data.lat
                        lon = data.lon
                        entryCity = data.city
                        if (stationName.isBlank() && data.nearbyStation != null) {
                            stationName = data.nearbyStation.name
                        }
                        latitude = lat
                        longitude = lon
                        city = entryCity
                    }
                }
            }

            val pricePerLiter = eurosVal!! / litersVal!!
            val entry = FuelEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                latitude = lat,
                longitude = lon,
                city = entryCity,
                stationName = stationName.trim(),
                liters = litersVal,
                euros = eurosVal,
                pricePerLiter = pricePerLiter,
                kilometers = kilometers.toDoubleOrNull() ?: 0.0,
                receiptPath = receiptPath
            )
            withContext(Dispatchers.IO) { repository.insertEntry(entry) }

            stationName = ""
            liters = ""
            euros = ""
            kilometers = ""
            latitude = null
            longitude = null
            city = ""
            receiptPath = null
            pendingReceiptFile = null
            statusMessage = "Entry saved!"
            refreshKey++
            isSaving = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Last entry card
        lastEntry?.let { entry ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Last Entry", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${entry.stationName} – ${entry.city}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append("${"%.1f".format(entry.liters)} L – €${"%.2f".format(entry.euros)}")
                            if (entry.kilometers > 0) append(" – ${"%.0f".format(entry.kilometers)} km")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        buildString {
                            append("€${"%.2f".format(entry.pricePerLiter)} / L")
                            if (entry.kilometers > 0)
                                append("  ·  ${"%.1f".format(entry.liters / entry.kilometers * 100)} L/100km")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text("New Entry", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = stationName,
            onValueChange = { stationName = it },
            label = { Text("Station name") },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            singleLine = true,
            colors = if (stationNameAlert) OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.error,
                focusedBorderColor = MaterialTheme.colorScheme.error,
                unfocusedLabelColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = MaterialTheme.colorScheme.error,
            ) else OutlinedTextFieldDefaults.colors()
        )
        if (stationNameAlert) {
            Text(
                "No station found nearby",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = liters,
            onValueChange = { liters = it },
            label = { Text("Liters") },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(litersShakeOffset.value.roundToInt(), 0) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = if (litersAlert) OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.error,
                focusedBorderColor = MaterialTheme.colorScheme.error,
                unfocusedLabelColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = MaterialTheme.colorScheme.error,
            ) else OutlinedTextFieldDefaults.colors(),
            isError = litersAlert,
            supportingText = if (litersAlert) ({ Text("Enter a valid number") }) else null
        )

        OutlinedTextField(
            value = euros,
            onValueChange = { euros = it },
            label = { Text("Euros (€)") },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(eurosShakeOffset.value.roundToInt(), 0) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = if (eurosAlert) OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.error,
                focusedBorderColor = MaterialTheme.colorScheme.error,
                unfocusedLabelColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = MaterialTheme.colorScheme.error,
            ) else OutlinedTextFieldDefaults.colors(),
            isError = eurosAlert,
            supportingText = if (eurosAlert) ({ Text("Enter a valid number") }) else null
        )

        OutlinedTextField(
            value = kilometers,
            onValueChange = { kilometers = it },
            label = { Text("Kilometers since last fill-up") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        val litersVal = liters.toDoubleOrNull()
        val eurosVal = euros.toDoubleOrNull()
        val kmVal = kilometers.toDoubleOrNull()
        if (litersVal != null && eurosVal != null && litersVal > 0) {
            val parts = buildString {
                append("€${"%.3f".format(eurosVal / litersVal)} / L")
                if (kmVal != null && kmVal > 0) {
                    append("  ·  ${"%.1f".format(litersVal / kmVal * 100)} L/100km")
                    append("  ·  €${"%.3f".format(eurosVal / kmVal)} / km")
                }
            }
            Text(parts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }

        if (city.isNotEmpty()) {
            Text(
                "Location: $city (${latitude?.let { "%.4f".format(it) }}, ${longitude?.let { "%.4f".format(it) }})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (receiptPath != null) {
            Text(
                "Receipt: photo saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { requestLocation() },
                modifier = Modifier.weight(1f),
                enabled = !isFetchingLocation && !isSaving
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("GPS")
                }
            }
            FilledTonalButton(
                onClick = { requestCamera() },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Text(if (receiptPath != null) "Photo ✓" else "Photo")
            }
        }

        Button(
            onClick = { saveEntry() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && !isFetchingLocation
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Save Entry")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
