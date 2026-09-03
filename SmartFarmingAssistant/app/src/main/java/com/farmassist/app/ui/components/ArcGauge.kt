package com.farmassist.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A rounded circular progress arc with a percentage label in the center —
 * used for prediction confidence and for sensor readings (soil moisture etc).
 * 270° sweep starting at 135°, matching the reference "Today Progress" gauge style.
 */
@Composable
fun ArcGauge(
    percent: Float,               // 0f..1f
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = color.copy(alpha = 0.18f),
    valueTextColor: Color = Color.White,
    labelTextColor: Color = Color.White.copy(alpha = 0.75f)
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val sweepMax = 270f
            val startAngle = 135f
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.toPx() - strokeWidth.toPx(), size.toPx() - strokeWidth.toPx())

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepMax,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepMax * percent.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(percent * 100).roundToInt()}%",
                color = valueTextColor,
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(label, color = labelTextColor, fontSize = 12.sp)
        }
    }
}
