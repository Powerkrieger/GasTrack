package com.example.gastrack.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.gastrack.data.FuelEntry
import com.example.gastrack.data.FuelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(
    entryId: String,
    repository: FuelRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<FuelEntry?>(null) }

    LaunchedEffect(entryId) {
        entry = withContext(Dispatchers.IO) { repository.getEntryById(entryId) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back")
        }

        val e = entry ?: run {
            Text("Loading...")
            return@Column
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(e.timestamp))

        Spacer(modifier = Modifier.height(8.dp))
        Text(e.stationName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (e.city.isNotEmpty()) {
            Text(e.city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Liters", "${"%.2f".format(e.liters)} L")
        DetailRow("Total", "€${"%.2f".format(e.euros)}")
        DetailRow("Price per Liter", "€${"%.3f".format(e.pricePerLiter)}")
        if (e.kilometers > 0) {
            DetailRow("Kilometers", "${"%.0f".format(e.kilometers)} km")
            DetailRow("Efficiency", "${"%.1f".format(e.liters / e.kilometers * 100)} L/100km")
            DetailRow("Cost per km", "€${"%.3f".format(e.euros / e.kilometers)}")
        }

        if (e.latitude != 0.0 || e.longitude != 0.0) {
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("Coordinates", "${"%.5f".format(e.latitude)}, ${"%.5f".format(e.longitude)}")
        }

        e.receiptPath?.let { path ->
            Spacer(modifier = Modifier.height(16.dp))
            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receipt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (bitmap != null) {
                    FilledTonalButton(onClick = {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", File(path)
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }) { Text("Share") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Receipt photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "Receipt image not found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
