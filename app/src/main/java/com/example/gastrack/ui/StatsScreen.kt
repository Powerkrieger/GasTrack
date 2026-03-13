package com.example.gastrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gastrack.data.FuelEntry
import com.example.gastrack.data.FuelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StatsScreen(repository: FuelRepository, modifier: Modifier = Modifier) {
    var entries by remember { mutableStateOf(listOf<FuelEntry>()) }

    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { repository.getAllEntries() }
        entries = all.reversed() // chronological order for charts
    }

    if (entries.size < 2) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (entries.isEmpty()) "No data yet." else "Need at least 2 entries for charts.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        val pricePoints = entries.map { it.pricePerLiter.toFloat() }
        val litersPoints = entries.map { it.liters.toFloat() }
        val efficiencyEntries = entries.filter { it.kilometers > 0 }
        val efficiencyPoints = efficiencyEntries.map { (it.liters / it.kilometers * 100).toFloat() }
        val costPerKmPoints = efficiencyEntries.map { (it.euros / it.kilometers).toFloat() }
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val surfaceColor = MaterialTheme.colorScheme.onSurfaceVariant

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Price per Liter (€/L)", style = MaterialTheme.typography.titleMedium)
            Text(
                "avg: €${"%.3f".format(pricePoints.average())}  " +
                    "min: €${"%.3f".format(pricePoints.min())}  " +
                    "max: €${"%.3f".format(pricePoints.max())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LineChart(
                dataPoints = pricePoints,
                lineColor = primaryColor,
                axisColor = surfaceColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Liters per Fill-Up", style = MaterialTheme.typography.titleMedium)
            Text(
                "avg: ${"%.1f".format(litersPoints.average())} L  " +
                    "min: ${"%.1f".format(litersPoints.min())} L  " +
                    "max: ${"%.1f".format(litersPoints.max())} L",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LineChart(
                dataPoints = litersPoints,
                lineColor = secondaryColor,
                axisColor = surfaceColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        if (efficiencyPoints.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fuel Efficiency (L/100km)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "avg: ${"%.1f".format(efficiencyPoints.average())}  " +
                        "min: ${"%.1f".format(efficiencyPoints.min())}  " +
                        "max: ${"%.1f".format(efficiencyPoints.max())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LineChart(
                    dataPoints = efficiencyPoints,
                    lineColor = tertiaryColor,
                    axisColor = surfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cost per km (€/km)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "avg: €${"%.3f".format(costPerKmPoints.average())}  " +
                        "min: €${"%.3f".format(costPerKmPoints.min())}  " +
                        "max: €${"%.3f".format(costPerKmPoints.max())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LineChart(
                    dataPoints = costPerKmPoints,
                    lineColor = primaryColor,
                    axisColor = surfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@Composable
private fun LineChart(
    dataPoints: List<Float>,
    lineColor: Color,
    axisColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas

        val padding = 48f
        val chartWidth = size.width - 2 * padding
        val chartHeight = size.height - 2 * padding
        val minVal = dataPoints.min()
        val maxVal = dataPoints.max()
        val range = if (maxVal > minVal) maxVal - minVal else 1f

        // Axes
        drawLine(
            color = axisColor,
            start = Offset(padding, padding),
            end = Offset(padding, size.height - padding),
            strokeWidth = 2f
        )
        drawLine(
            color = axisColor,
            start = Offset(padding, size.height - padding),
            end = Offset(size.width - padding, size.height - padding),
            strokeWidth = 2f
        )

        // Line path
        val stepX = chartWidth / (dataPoints.size - 1)
        val path = Path()
        dataPoints.forEachIndexed { i, value ->
            val x = padding + i * stepX
            val y = size.height - padding - ((value - minVal) / range) * chartHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))

        // Points
        dataPoints.forEachIndexed { i, value ->
            val x = padding + i * stepX
            val y = size.height - padding - ((value - minVal) / range) * chartHeight
            drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
        }
    }
}
