package com.farmassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmassist.app.R
import com.farmassist.app.ui.components.pressScale

/** Distinct emoji + accent color per crop — renders natively via the system emoji
 *  font (no assets needed) and gives each tile a recognizable identity instead of
 *  a repeated generic icon. Two rare-berry crops (Raspberry) share a close visual
 *  since Unicode has no dedicated emoji for them. */
private data class CropVisual(val emoji: String, val color: Color)

private val cropVisuals: Map<String, CropVisual> = mapOf(
    "Apple" to CropVisual("🍎", Color(0xFFE53935)),
    "Blueberry" to CropVisual("🫐", Color(0xFF3F51B5)),
    "Cherry_(including_sour)" to CropVisual("🍒", Color(0xFFC2185B)),
    "Corn_(maize)" to CropVisual("🌽", Color(0xFFFBC02D)),
    "Grape" to CropVisual("🍇", Color(0xFF6A1B9A)),
    "Orange" to CropVisual("🍊", Color(0xFFFB8C00)),
    "Peach" to CropVisual("🍑", Color(0xFFFF8A65)),
    "Pepper,_bell" to CropVisual("🫑", Color(0xFF43A047)),
    "Potato" to CropVisual("🥔", Color(0xFF8D6E63)),
    "Raspberry" to CropVisual("🍇", Color(0xFFAD1457)),
    "Soybean" to CropVisual("🌱", Color(0xFF66BB6A)),
    "Squash" to CropVisual("🎃", Color(0xFFEF6C00)),
    "Strawberry" to CropVisual("🍓", Color(0xFFE53935)),
    "Tomato" to CropVisual("🍅", Color(0xFFD84315))
)

@Composable
fun CropSelectionScreen(cropNames: List<String>, displayName: (String) -> String, onBack: () -> Unit, onCropSelected: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_farm_field),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .pressScale(onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("What are you scanning?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick the crop first — it helps the model focus on the right diseases.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(20.dp))

            if (cropNames.isEmpty()) {
                Text("No crop data loaded — check crop_to_classes_mapping.json is in assets/.", color = Color.White)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cropNames) { crop ->
                        CropTile(displayName(crop), cropVisuals[crop]) { onCropSelected(crop) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropTile(label: String, visual: CropVisual?, onClick: () -> Unit) {
    val accent = visual?.color ?: Color(0xFF66BB6A)
    Column(
        Modifier
            .background(Color(0xCC1B2E1F), RoundedCornerShape(20.dp))
            .pressScale(onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).background(accent.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(visual?.emoji ?: "🌿", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
