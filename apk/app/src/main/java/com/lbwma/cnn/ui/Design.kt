package com.lbwma.cnn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Cyan60
import com.lbwma.cnn.ui.theme.CyanGlow
import com.lbwma.cnn.ui.theme.Dark05
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.Dark30
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.GlassBorderStrong
import com.lbwma.cnn.ui.theme.GlassFog
import com.lbwma.cnn.ui.theme.GlassHighlight
import com.lbwma.cnn.ui.theme.Indigo40
import com.lbwma.cnn.ui.theme.Magenta40
import com.lbwma.cnn.ui.theme.Violet40

// ===================== GRADIENTES PREMIUM =====================

/** Gradiente cyan vibrante para primary actions. */
fun primaryGradient(): Brush = Brush.linearGradient(
    colors = listOf(Cyan40, CyanGlow),
)

/** Gradiente cyan -> violeta -> magenta (logo / hero). */
fun heroGradient(): Brush = Brush.linearGradient(
    colors = listOf(Cyan40, Indigo40, Violet40, Magenta40)
)

/** Glass card gradient — subtil iluminacao no topo. */
fun glassCardGradient(): Brush = Brush.verticalGradient(
    colors = listOf(GlassHighlight, GlassFog, Dark10.copy(alpha = 0.6f))
)

/** Gradiente para card elevado (mais escuro embaixo). */
fun elevatedCardGradient(): Brush = Brush.verticalGradient(
    colors = listOf(Dark15, Dark10, Dark05)
)

/** Glow ambiente (radial). */
fun ambientGlow(color: Color = Cyan40, alpha: Float = 0.12f): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = alpha), Color.Transparent)
)

// ===================== MODIFICADORES REUTILIZAVEIS =====================

/**
 * Glass card moderno — superficie com gradient, borda translucida,
 * ideal para cards e elementos elevados.
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    borderColor: Color = GlassBorder,
    backgroundBrush: Brush? = null
): Modifier = composed {
    val brush = backgroundBrush ?: glassCardGradient()
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(brush)
        .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
}

/**
 * Press scale — pequena escala ao pressionar (feedback fisico premium).
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        scale.animateTo(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        )
    }
    scale(scale.value)
}

/**
 * Glow sombra atras do elemento (halo).
 */
fun Modifier.haloShadow(
    color: Color = Cyan40,
    radius: Float = 1.5f,
    alpha: Float = 0.3f
): Modifier = drawBehind {
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = size.minDimension * radius
    )
}

/**
 * Pulse animado (alpha + scale).
 */
@Composable
fun rememberPulseAlpha(
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.6f,
    durationMs: Int = 1800
): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return alpha
}

@Composable
fun rememberPulseScale(
    minScale: Float = 1f,
    maxScale: Float = 1.15f,
    durationMs: Int = 1800
): Float {
    val transition = rememberInfiniteTransition(label = "pulseS")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return scale
}

/**
 * Mesh gradient ambiente — pano de fundo rico para landing pages.
 */
@Composable
fun MeshAmbientBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val drift by transition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val drift2 by transition.animateFloat(
        initialValue = 30f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )
    Box(
        modifier = modifier
            .drawBehind {
                // Camada base
                drawRect(Brush.verticalGradient(
                    colors = listOf(Color(0xFF05080C), Color(0xFF0A0F16), Color(0xFF05080C))
                ))
                // Glow cyan superior
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Cyan40.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.3f + drift, size.height * 0.15f),
                        radius = size.minDimension * 0.7f
                    ),
                    radius = size.minDimension,
                    center = Offset(size.width * 0.3f + drift, size.height * 0.15f)
                )
                // Glow violeta inferior direito
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Violet40.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.85f + drift2, size.height * 0.85f),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension,
                    center = Offset(size.width * 0.85f + drift2, size.height * 0.85f)
                )
                // Glow magenta central
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Magenta40.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.6f - drift, size.height * 0.55f),
                        radius = size.minDimension * 0.5f
                    ),
                    radius = size.minDimension,
                    center = Offset(size.width * 0.6f - drift, size.height * 0.55f)
                )
            }
    )
}

