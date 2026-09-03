package com.farmassist.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm, earthy, "growing things" palette rather than a generic Material default.
val LeafGreen = Color(0xFF2E7D32)
val SproutGreen = Color(0xFF66BB6A)
val SoilBrown = Color(0xFF6D4C34)
val WheatGold = Color(0xFFF2A93B)
val AlertRed = Color(0xFFD84315)
val SkyBlue = Color(0xFF4FA3D1)
val CreamBackground = Color(0xFFFBF8F1)
val CardSurface = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color.White,
    secondary = WheatGold,
    onSecondary = Color(0xFF3A2A00),
    tertiary = SoilBrown,
    background = CreamBackground,
    surface = CardSurface,
    error = AlertRed
)

private val DarkColors = darkColorScheme(
    primary = SproutGreen,
    secondary = WheatGold,
    tertiary = SoilBrown,
    error = Color(0xFFFF7043)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun FarmAssistTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Confidence-based accent color — used on Result screen so the number itself communicates trust level. */
fun confidenceColor(confidence: Float): Color = when {
    confidence >= 0.75f -> LeafGreen
    confidence >= 0.5f -> WheatGold
    else -> AlertRed
}
