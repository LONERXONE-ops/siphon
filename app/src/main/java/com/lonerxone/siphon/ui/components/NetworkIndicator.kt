package com.lonerxone.siphon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * Wi-Fi: three arcs light up one after another.
 * Mobile data: four signal bars light up one after another, left to right.
 */
@Composable
fun NetworkIndicator(
    isWifi: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    val transition = rememberInfiniteTransition(label = "network")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "networkPhase"
    )
    val step = (phase * 6f).toInt()
    val lit = if (isWifi) minOf(3, step) else minOf(4, step)

    Canvas(modifier = modifier.size(iconSize)) {
        val s = this.size.minDimension
        val on = TextPrimary
        val off = TextMuted.copy(alpha = 0.45f)

        if (isWifi) {
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