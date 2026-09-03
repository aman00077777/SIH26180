package com.farmassist.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmassist.app.ble.BleConnectionState
import com.farmassist.app.ble.LiveSensorData
import com.farmassist.app.util.IrrigationAdvisor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SensorScreen(
    connectionState: BleConnectionState,
    sensorData: LiveSensorData,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val blePermissions = rememberMultiplePermissionsState(
        listOfNotNull(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
    )

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Field Node Status", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Status: ${connectionState.name}")

        Spacer(Modifier.height(24.dp))

        if (!blePermissions.allPermissionsGranted) {
            Button(onClick = { blePermissions.launchMultiplePermissionRequest() }) {
                Text("Grant Bluetooth permissions")
            }
        } else {
            Row {
                Button(onClick = onStartScan) { Text("Scan for field node") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onStopScan) { Text("Stop") }
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()

        // ── Sensor array section ─────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row {
            Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF4CAF50))
            Spacer(Modifier.width(8.dp))
            Text(
                "Sensor array",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))

        ReadingRow("Soil moisture", sensorData.soilMoisturePercent, "%")
        ReadingRow("Temperature", sensorData.temperatureCelsius, "°C")
        ReadingRow("Humidity", sensorData.humidityPercent, "%")
        ReadingRow(
            "Rainfall",
            if (sensorData.connected) (if (sensorData.rainfallFlag) 1f else 0f) else null,
            "",
            displayOverride = if (sensorData.connected) (if (sensorData.rainfallFlag) "🌧 Raining" else "☀ Dry") else null
        )

        Spacer(Modifier.height(20.dp))
        Divider()

        // ── Power system section ─────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFFF2A93B))
            Spacer(Modifier.width(8.dp))
            Text(
                "Power system",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))

        ReadingRow("Battery level", sensorData.batteryLevelPercent, "%")

        Spacer(Modifier.height(24.dp))

        // ── Irrigation advice (unchanged) ────────────────────────────────
        val advice = if (
            sensorData.soilMoisturePercent != null &&
            sensorData.temperatureCelsius != null &&
            sensorData.humidityPercent != null
        ) {
            IrrigationAdvisor.adviseFor(
                sensorData.soilMoisturePercent,
                sensorData.temperatureCelsius,
                sensorData.humidityPercent
            )
        } else "Waiting for sensor data..."

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Irrigation advice", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(advice, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ReadingRow(
    label: String,
    value: Float?,
    unit: String,
    displayOverride: String? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(displayOverride ?: if (value != null) "${"%.1f".format(value)}$unit" else "—")
    }
}
