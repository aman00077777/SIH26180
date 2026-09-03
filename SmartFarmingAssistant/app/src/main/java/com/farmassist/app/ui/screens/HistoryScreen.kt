package com.farmassist.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.farmassist.app.data.PredictionRecord
import com.farmassist.app.data.SensorReadingRecord
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    predictions: List<PredictionRecord>,
    sensorReadings: List<SensorReadingRecord>,
    onClearAll: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            if (predictions.isNotEmpty() || sensorReadings.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all")
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text("Soil moisture trend", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (sensorReadings.isNotEmpty()) {
                item {
                    Card {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(8.dp),
                            factory = { context -> LineChart(context) },
                            update = { chart ->
                                val entries = sensorReadings.reversed().mapIndexed { i, r ->
                                    Entry(i.toFloat(), r.soilMoisturePercent)
                                }
                                val dataSet = LineDataSet(entries, "Soil moisture %")
                                chart.data = LineData(dataSet)
                                chart.description.isEnabled = false
                                chart.invalidate()
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                item {
                    Text("No sensor readings logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                }
            }

            item {
                Text("Prediction history", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (predictions.isEmpty()) {
                item { Text("No predictions logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(predictions) { record ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                record.className,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${(record.confidence * 100).toInt()}% confidence  •  ${record.type}" +
                                    (record.cropSelected?.let { "  •  $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                dateFormat.format(Date(record.timestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all history?") },
            text = { Text("This deletes every saved prediction and sensor reading. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onClearAll(); showClearConfirm = false }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
