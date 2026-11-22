package com.lonerxone.siphon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.PrimaryGradientEnd
import com.lonerxone.siphon.ui.theme.TextPrimary

/** The SIPHON logo mark: a download arrow dropping into a tray. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    boxSize: Dp = 44.dp
) {
    val shape = RoundedCornerShape(boxSize * 0.3f)
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DarkElevated, DarkSurface)))
            .border(1.dp, DarkBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(boxSize * 0.56f)) {
            val s = this.size.minDimension
            val stroke = s * 0.13f
            drawLine(
                color = TextPrimary,
                start = Offset(s * 0.5f, s * 0.10f),
                end = Offset(s * 0.5f, s * 0.64f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = TextPrimary,
                start = Offset(s * 0.24f, s * 0.40f),
                end = Offset(s * 0.5f, s * 0.66f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = TextPrimary,
                start = Offset(s * 0.76f, s * 0.40f),
                end = Offset(s * 0.5f, s * 0.66f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = PrimaryGradientEnd,
                start = Offset(s * 0.16f, s * 0.90f),
                end = Offset(s * 0.84f, s * 0.90f),
                strokeWidth = stroke * 0.9f,
                cap = StrokeCap.Round
            )
        }
    }
}