package com.farmassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmassist.app.R
import com.farmassist.app.ble.BleConnectionState
import com.farmassist.app.ble.LiveSensorData
import com.farmassist.app.ui.components.ArcGauge
import com.farmassist.app.ui.components.pressScale
import com.farmassist.app.ui.theme.WheatGold

@Composable
fun HomeScreen(
    sensorData: LiveSensorData,
    connectionState: BleConnectionState,
    onScanCrop: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Farm background photo
        Image(
            painter = painterResource(R.drawable.bg_farm_aerial),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark gradient scrim so text stays readable over the photo
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Greeting header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(WheatGold)
                        .clickable { onOpenProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Agriculture, contentDescription = "Open profile", tint = Color(0xFF3A2A00))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Good day,", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    Text("Farmer", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Field status card — grouped by hardware component
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC1B2E1F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Field Status", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = onOpenSensors,
                            label = { Text(connectionState.name, color = Color.White) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.12f))
                        )
                    }

                    // ── Sensor array ─────────────────────────────────────
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sensor array",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ArcGauge(
                            percent = (sensorData.soilMoisturePercent ?: 0f) / 100f,
                            color = Color(0xFF64B5F6),
                            label = "Soil moisture",
                            size = 100.dp,
                            strokeWidth = 9.dp
                        )
                        ArcGauge(
                            percent = ((sensorData.humidityPercent ?: 0f) / 100f),
                            color = WheatGold,
                            label = "Humidity",
                            size = 100.dp,
                            strokeWidth = 9.dp
                        )
                    }

                    if (sensorData.temperatureCelsius != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${"%.1f".format(sensorData.temperatureCelsius)}°C",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Rainfall status
                    if (sensorData.connected) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (sensorData.rainfallFlag) "🌧 Raining" else "☀ Dry",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // ── Power system ─────────────────────────────────────
                    if (sensorData.batteryLevelPercent != null) {
                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Power system",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFFF2A93B))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${"%.0f".format(sensorData.batteryLevelPercent)}%",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    if (!sensorData.connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No field node connected yet — tap status to scan.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Quick actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionCard(Icons.Default.CameraAlt, "Scan a leaf", Modifier.weight(1f), onScanCrop)
                HomeActionCard(Icons.Default.History, "History", Modifier.weight(1f), onOpenHistory)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.pressScale(onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
