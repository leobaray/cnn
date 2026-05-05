package com.lbwma.cnn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.GlassBorder

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.04f),
            Color.Transparent,
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 600f, 200f)
    )
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, cornerDp: Int = 14) {
    val shimmer = shimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(
                Brush.verticalGradient(listOf(Dark15, Dark10))
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(cornerDp.dp))
            .drawWithContent {
                drawContent()
                drawRect(shimmer)
            }
    )
}
