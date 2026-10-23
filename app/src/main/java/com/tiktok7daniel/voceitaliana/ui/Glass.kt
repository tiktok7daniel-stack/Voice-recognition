package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card surface. Light: white with a soft shadow. Dark: charcoal glass with a thin edge, and a
 * faint orange halo when [accent] is set (the card that matters most on the screen).
 * A low [alpha] marks a tile nested inside a card: flat, one step lighter/darker.
 */
fun Modifier.glass(shape: Shape, accent: Boolean = false, alpha: Float = 0.55f): Modifier = composed {
    val c = LocalVoce.current
    if (alpha < 0.45f) {
        this.clip(shape).background(c.tile, shape).border(1.dp, c.tileBorder, shape)
    } else if (c.dark) {
        this
            .neonGlow(shape, c.accent, if (accent) 0.07f else 0f, 14.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.cardTop, c.card)), shape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        if (accent) c.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                        c.border.copy(alpha = 0.6f),
                    ),
                ),
                shape,
            )
    } else {
        this
            .shadow(if (accent) 14.dp else 6.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.card, shape)
    }
}

/** Soft halo around a shape: 4 fading strokes, cheaper than a blur. */
fun Modifier.neonGlow(shape: Shape, color: Color, strength: Float = 0.18f, spread: Dp = 10.dp): Modifier =
    if (strength <= 0f) this else neonGlow(shape, color, { strength }, spread)

/** Animated variant: the value is read at draw time only, so pulsing never recomposes. */
fun Modifier.neonGlow(shape: Shape, color: Color, strength: () -> Float, spread: Dp = 10.dp): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val step = spread.toPx() / 4f
        val strokes = (4 downTo 1).map { i -> i to Stroke(width = step * i * 2f) }
        onDrawBehind {
            val value = strength()
            if (value <= 0f) return@onDrawBehind
            strokes.forEach { (i, stroke) -> drawOutline(outline, color.copy(alpha = value / i), style = stroke) }
        }
    }

/** Page behind every screen: flat grey in light mode, near-black with soft light pools in dark. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalVoce.current
    Box(modifier = modifier.fillMaxSize().background(c.page)) {
        if (c.dark) {
            // Own layer, drawn once per size, so scrolling never repaints it.
            Spacer(
                Modifier.matchParentSize().graphicsLayer().drawWithCache {
                    val w = size.width
                    val h = size.height
                    val warm = Brush.radialGradient(
                        listOf(c.accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(w * 0.9f, h * 0.02f), radius = w * 0.95f,
                    )
                    val cool = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                        center = Offset(w * 0.1f, h * 0.55f), radius = w * 0.9f,
                    )
                    val rings = (1..4).map { it * w * 0.28f }
                    onDrawBehind {
                        drawRect(warm)
                        drawRect(cool)
                        // Faint concentric rings, like the glow chart reference.
                        rings.forEach { r ->
                            drawCircle(Color.White.copy(alpha = 0.025f), radius = r, center = Offset(w * 0.5f, h * 0.18f), style = Stroke(1f))
                        }
                    }
                },
            )
        }
        content()
    }
}
