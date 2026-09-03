package com.farmassist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmassist.app.ml.DiseaseInfo
import com.farmassist.app.ml.DiseaseInfoRepository
import com.farmassist.app.ui.PredictionUiState
import com.farmassist.app.ui.theme.confidenceColor

@Composable
fun ResultScreen(state: PredictionUiState, acres: Double?, diseaseInfo: DiseaseInfo?, onScanAnother: () -> Unit) {
    if (state.error != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Something went wrong: ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onScanAnother) { Text("Try again") }
        }
        return
    }

    val best = listOfNotNull(state.diseaseResult, state.pestResult).maxByOrNull { it.confidence }
    if (best == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No result — model files may not be loaded yet. Check assets/.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onScanAnother) { Text("Try again") }
        }
        return
    }

    val isHealthy = best.label.endsWith("healthy", ignoreCase = true) || diseaseInfo?.disease?.equals("Healthy", true) == true

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Headline card with confidence arc gauge
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1B2E1F))
                ) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (isHealthy) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = confidenceColor(best.confidence)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            diseaseInfo?.disease?.takeIf { it != "-" } ?: best.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        com.farmassist.app.ui.components.ArcGauge(
                            percent = best.confidence,
                            color = confidenceColor(best.confidence),
                            label = "Confidence"
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.usingNpu) "Running on NPU (NNAPI delegate)" else "Running on CPU",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Crop-compatibility / cross-crop warning (step 3 of recommendation_rules.json) —
            // shown right after the headline since it affects trust in the whole result.
            if (diseaseInfo?.cropCompatibilityWarning != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF3D2B12))
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF2A93B))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                diseaseInfo.cropCompatibilityWarning,
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (diseaseInfo != null) {
                if (diseaseInfo.description != "-" && diseaseInfo.description.isNotBlank()) {
                    item { InfoSection("About", Icons.Default.Info, listOf(diseaseInfo.description)) }
                }
                if (diseaseInfo.symptoms.isNotEmpty()) {
                    item { InfoSection("Symptoms", Icons.Default.ZoomIn, diseaseInfo.symptoms) }
                }
                if (diseaseInfo.immediateActions.isNotEmpty()) {
                    item { InfoSection("What to do now", Icons.Default.PlaylistAddCheck, diseaseInfo.immediateActions) }
                }
                if (diseaseInfo.prevention.isNotEmpty()) {
                    item { InfoSection("Prevention", Icons.Default.Shield, diseaseInfo.prevention) }
                }
                if (acres != null && !isHealthy) {
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Treatment dose", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(diseaseInfo.doseFor(acres), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (diseaseInfo.expertHelp.isNotBlank()) {
                    item { InfoSection("Need more help?", Icons.Default.SupportAgent, listOf(diseaseInfo.expertHelp)) }
                }
                // Sources — resolved via source_registry.json, shown last as supporting info.
                if (diseaseInfo.sourceCitations.isNotEmpty()) {
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(8.dp))
                                diseaseInfo.sourceCitations.forEach { citation ->
                                    Text(
                                        "•  $citation",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "No detailed advisory data found for this class yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.diseaseResult != null && state.pestResult != null) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Both models ran on this photo", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text("Disease model: ${state.diseaseResult.label} (${(state.diseaseResult.confidence * 100).toInt()}%)")
                            Text("Pest model: ${state.pestResult.label} (${(state.pestResult.confidence * 100).toInt()}%)")
                        }
                    }
                }
            }

            // Always-shown AI decision-support disclaimer, per recommendation_rules.json.
            item {
                Text(
                    DiseaseInfoRepository.AI_DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        Divider()
        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan another leaf")
        }
    }
}

@Composable
private fun InfoSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, lines: List<String>) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            lines.forEach { line ->
                Text("•  $line", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}