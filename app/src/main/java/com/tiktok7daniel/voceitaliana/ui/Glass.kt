package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft card: white with a gentle shadow (accent = true lifts it a bit more, for the card that
 * matters most on the screen). A low [alpha] marks a tile nested inside a card: light grey,
 * flat, no shadow.
 */
fun Modifier.glass(shape: Shape, accent: Boolean = false, alpha: Float = 0.55f): Modifier =
    if (alpha < 0.45f) {
        this.clip(shape).background(Mono.Tile, shape)
    } else {
        this
            .shadow(if (accent) 14.dp else 6.dp, shape, ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(shape)
            .background(Mono.Card, shape)
    }

private val ShadowTint = Color(0x33101318)

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

/** Plain soft-grey page behind every screen. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(Mono.Page)) {
        content()
    }
}
