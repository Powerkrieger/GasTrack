package com.example.gastrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gastrack.data.FuelRepository
import com.example.gastrack.network.SyncResult
import com.example.gastrack.network.SyncService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    syncService: SyncService,
    repository: FuelRepository,
    onBack: () -> Unit,
    onImportDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(syncService.serverUrl) }
    var apiKey by remember { mutableStateOf(syncService.apiKey) }
    var statusMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var entryCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        entryCount = withContext(Dispatchers.IO) { repository.getAllEntries().size }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val count = entryCount
                    withContext(Dispatchers.IO) { repository.exportToZip(uri) }
                    statusMessage = "Exported $count entries."
                } catch (e: Exception) {
                    statusMessage = "Export failed."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) { repository.importFromZip(uri) }
                    onImportDone()
                    statusMessage = "Imported $count new entries."
                } catch (e: Exception) {
                    statusMessage = "Import failed."
                }
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { json ->
            try {
                val obj = JSONObject(json)
                serverUrl = obj.getString("url")
                apiKey = obj.getString("key")
                syncService.saveConfig(serverUrl, apiKey)
                statusMessage = "Paired. Tap Sync Now to pull existing entries."
            } catch (e: JSONException) {
                statusMessage = "Invalid QR code."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Text("Sync Server", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://gastrack.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    scanLauncher.launch(ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan GasTrack pairing code")
                        setBeepEnabled(false)
                    })
                },
                modifier = Modifier.weight(1f)
            ) { Text("Scan QR") }

            Button(
                onClick = {
                    syncService.saveConfig(serverUrl, apiKey)
                    statusMessage = "Saved."
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }

        Button(
            onClick = {
                isSyncing = true
                statusMessage = "Syncing..."
                scope.launch {
                    val result = syncService.sync()
                    statusMessage = when (result) {
                        is SyncResult.NotConfigured -> "Configure server URL and key first."
                        is SyncResult.Success -> "Done — ↑${result.pushed} pushed, ↓${result.pulled} pulled."
                        is SyncResult.Error -> "Sync failed: ${result.message}"
                    }
                    isSyncing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSyncing
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Sync Now")
            }
        }

        HorizontalDivider()

        Text("Data", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("gastrack-$date.zip")
                },
                modifier = Modifier.weight(1f),
                enabled = entryCount > 0
            ) { Text("Export") }
            FilledTonalButton(
                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                modifier = Modifier.weight(1f)
            ) { Text("Import") }
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
