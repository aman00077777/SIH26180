package com.farmassist.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farmassist.app.R
import com.farmassist.app.ble.BleConnectionState
import com.farmassist.app.ble.LiveSensorData
import com.farmassist.app.ui.ManualSensorData
import com.farmassist.app.ui.components.ArcGauge
import com.farmassist.app.ui.components.pressScale
import com.farmassist.app.ui.theme.LeafGreen
import com.farmassist.app.ui.theme.WheatGold
import com.farmassist.app.util.IrrigationAdvisor
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    isAutoMode: Boolean,
    onToggleAutoMode: (Boolean) -> Unit,
    sensorData: LiveSensorData,
    connectionState: BleConnectionState,
    manualSensorData: ManualSensorData,
    onUpdateManualSensors: (ManualSensorData) -> Unit,
    pendingPhoto: Bitmap?,
    onPhotoSelected: (Bitmap) -> Unit,
    onTakePhoto: () -> Unit,
    selectedCrop: String?,
    onSelectCrop: (String) -> Unit,
    availableCrops: List<String>,
    onAnalyze: () -> Unit,
    onScanCrop: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current

    // Gallery photo picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = uriToBitmap(context, uri)
            if (bitmap != null) {
                onPhotoSelected(bitmap)
            }
        }
    }

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
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 80.dp) // Space for bottom floating nav
        ) {
            // Greeting header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
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

            Spacer(Modifier.height(18.dp))

            // ═════════════════════════════════════════════════════════════════
            // PROMINENT AUTO / MANUAL TOGGLE SWITCH
            // ═════════════════════════════════════════════════════════════════
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAutoMode) Color(0xEE162C1C) else Color(0xEE2A2518)
                ),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isAutoMode) LeafGreen.copy(alpha = 0.7f) else WheatGold.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isAutoMode) LeafGreen.copy(alpha = 0.25f) else WheatGold.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isAutoMode) Icons.Default.Sensors else Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isAutoMode) LeafGreen else WheatGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isAutoMode) "Auto Mode" else "Manual Mode",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isAutoMode) "(ON)" else "(OFF)",
                                    color = if (isAutoMode) LeafGreen else WheatGold,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isAutoMode) "Live BLE & camera polling active" else "Manual inputs & local capture",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Switch(
                        checked = isAutoMode,
                        onCheckedChange = onToggleAutoMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LeafGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF5A4D3B)
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ═════════════════════════════════════════════════════════════════
            // FIELD STATUS CARD: LIVE READOUT (AUTO) vs EDITABLE INPUTS (MANUAL)
            // ═════════════════════════════════════════════════════════════════
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC1B2E1F)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Field Status", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isAutoMode) "Data source: Live Field Node" else "Data source: Manual overrides",
                                color = if (isAutoMode) Color(0xFFA5D6A7) else WheatGold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (isAutoMode) {
                            AssistChip(
                                onClick = onOpenSensors,
                                label = { Text(connectionState.name, color = Color.White) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.12f))
                            )
                        } else {
                            AssistChip(
                                onClick = {},
                                label = { Text("Manual Mode", color = WheatGold) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = WheatGold.copy(alpha = 0.15f))
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (isAutoMode) {
                        // ── AUTO MODE: Live Sensor Readout ──────────────────────────
                        Text(
                            "Sensor array (Live)",
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

                        // Power system
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

                    } else {
                        // ── MANUAL MODE: Editable Sensor Input Fields ────────────────
                        Text(
                            "Sensor array (Editable Inputs)",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))

                        // 1. Soil Moisture Slider
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Soil Moisture", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${manualSensorData.soilMoisture.roundToInt()}%",
                                color = Color(0xFF64B5F6),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = manualSensorData.soilMoisture,
                            onValueChange = { onUpdateManualSensors(manualSensorData.copy(soilMoisture = it.roundToInt().toFloat())) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF64B5F6),
                                activeTrackColor = Color(0xFF64B5F6),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        // 2. Temperature Slider
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFFF8A65), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Temperature", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${manualSensorData.temperature.roundToInt()}°C",
                                color = Color(0xFFFF8A65),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = manualSensorData.temperature,
                            onValueChange = { onUpdateManualSensors(manualSensorData.copy(temperature = it.roundToInt().toFloat())) },
                            valueRange = 10f..50f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF8A65),
                                activeTrackColor = Color(0xFFFF8A65),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        // 3. Humidity Slider
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = WheatGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Humidity", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${manualSensorData.humidity.roundToInt()}%",
                                color = WheatGold,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Slider(
                            value = manualSensorData.humidity,
                            onValueChange = { onUpdateManualSensors(manualSensorData.copy(humidity = it.roundToInt().toFloat())) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = WheatGold,
                                activeTrackColor = WheatGold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        // 4. Rainfall Switch
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rainfall", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (manualSensorData.rainfall) "🌧 Raining" else "☀ Dry",
                                    color = if (manualSensorData.rainfall) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(10.dp))
                                Switch(
                                    checked = manualSensorData.rainfall,
                                    onCheckedChange = { onUpdateManualSensors(manualSensorData.copy(rainfall = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF64B5F6),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )
                            }
                        }

                        // Live derived irrigation advice preview
                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(Modifier.height(12.dp))
                        val advice = IrrigationAdvisor.adviseFor(
                            manualSensorData.soilMoisture,
                            manualSensorData.temperature,
                            manualSensorData.humidity
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (advice == "Irrigate now") Icons.Default.WaterDamage else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (advice == "Irrigate now") Color(0xFFFF5252) else LeafGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Derived Irrigation Advice", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                                Text(advice, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ═════════════════════════════════════════════════════════════════
            // PHOTO SOURCING & ACTIONS SECTION
            // ═════════════════════════════════════════════════════════════════
            if (isAutoMode) {
                // Quick actions in Auto mode
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActionCard(Icons.Default.CameraAlt, "Manual scan", Modifier.weight(1f), onScanCrop)
                    HomeActionCard(Icons.Default.History, "History", Modifier.weight(1f), onOpenHistory)
                }
            } else {
                // MANUAL MODE: Photo choice (CameraX or Gallery), crop selector, and Analyze button
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC14231A)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = LeafGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Leaf / Crop Photo",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Supply a photo from your phone's camera or choose from gallery:",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(14.dp))

                        // Photo preview or choice buttons
                        if (pendingPhoto != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = pendingPhoto.asImageBitmap(),
                                    contentDescription = "Selected leaf",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, LeafGreen, RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Photo ready ✓", color = LeafGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (selectedCrop != null) "Crop: $selectedCrop" else "All crop models active",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Change", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onTakePhoto,
                                    colors = ButtonDefaults.buttonColors(containerColor = LeafGreen),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Camera", maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gallery", maxLines = 1)
                                }
                            }
                        }

                        // Target crop selection chips
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Select Target Crop (optional):",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(8.dp))

                        val topCrops = remember(availableCrops) {
                            val standard = listOf("Tomato", "Potato", "Corn_(maize)", "Wheat", "Cotton", "Apple", "Grape")
                            val found = standard.filter { s -> availableCrops.any { it.contains(s, ignoreCase = true) } }
                            if (found.isNotEmpty()) found else availableCrops.take(6)
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCrop == null,
                                onClick = { onSelectCrop("") },
                                label = { Text("Auto detect") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LeafGreen,
                                    selectedLabelColor = Color.White,
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                )
                            )
                            topCrops.forEach { crop ->
                                val cleanName = crop.replace("_", " ")
                                FilterChip(
                                    selected = selectedCrop == crop,
                                    onClick = { onSelectCrop(crop) },
                                    label = { Text(cleanName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LeafGreen,
                                        selectedLabelColor = Color.White,
                                        labelColor = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }

                        // ═════════════════════════════════════════════════════════════
                        // ANALYZE / SUBMIT BUTTON
                        // ═════════════════════════════════════════════════════════════
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onAnalyze,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pendingPhoto != null) LeafGreen else Color(0xFF388E3C)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (pendingPhoto != null) "Analyze Field & Crop" else "Take Photo & Analyze",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
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

/** Converts a gallery-picked image Uri to a Bitmap for inference. */
private fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } catch (e: Exception) {
        null
    }
}
