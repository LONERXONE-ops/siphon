package com.lonerxone.siphon.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import kotlin.math.ceil

/**
 * Wi-Fi: three arcs light up one after another.
 * Mobile data: four signal bars light up one after another.
 * The animation plays once when it appears and again when the connection type changes,
 * then the icon stays still.
 */
@Composable
fun NetworkIndicator(
    isWifi: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isWifi) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = LinearEasing))
    }

    Canvas(modifier = modifier.size(iconSize)) {
        val s = this.size.minDimension
        val on = TextPrimary
        val off = TextMuted.copy(alpha = 0.45f)
        val p = progress.value

        if (isWifi) {
            val lit = ceil(p * 3f).toInt().coerceIn(0, 3)
            val cx = s * 0.5f
            val cy = s * 0.84f
            drawCircle(color = on, radius = s * 0.075f, center = Offset(cx, cy))
            val radii = listOf(s * 0.22f, s * 0.40f, s * 0.58f)
            radii.forEachIndexed { index, r ->
                drawArc(
                    color = if (index < lit) on else off,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = s * 0.095f, cap = StrokeCap.Round)
                )
            }
        } else {
            val lit = ceil(p * 4f).toInt().coerceIn(0, 4)
            val barWidth = s * 0.15f
            val gap = s * 0.09f
            val totalWidth = barWidth * 4f + gap * 3f
            val startX = (s - totalWidth) / 2f
            for (i in 0 until 4) {
                val h = s * (0.30f + 0.20f * i)
                drawRoundRect(
                    color = if (i < lit) on else off,
                    topLeft = Offset(startX + i * (barWidth + gap), s - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}