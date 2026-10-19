package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Text and icons on light surfaces. Lower alphas for secondary text. */
val Ink = Color(0xFF111113)

/** Monochrome palette: soft grey page, white cards, black actions. */
object Mono {
    val Page = Color(0xFFECEDEF)
    val Card = Color(0xFFFFFFFF)
    val Tile = Color(0xFFF4F5F7)
    val Line = Color(0xFFE2E3E7)
    val Black = Color(0xFF111113)
    val Grey = Color(0xFF8A8D93)
}

private val MonoScheme = lightColorScheme(
    primary = Mono.Black,
    onPrimary = Color.White,
    primaryContainer = Mono.Tile,
    onPrimaryContainer = Ink,
    secondary = Mono.Grey,
    background = Mono.Page,
    onBackground = Ink,
    surface = Mono.Card,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF5C5F66),
    surfaceContainer = Mono.Tile,
    surfaceContainerHighest = Mono.Line,
    outline = Color(0xFFC9CBD1),
    error = Color(0xFFD7263D),
)

// Status accents, kept in black and white: strong black = active/done, grey = waiting.
// Red is the one exception, so errors and deletions still stand out.
object Accent {
    val Ember = Mono.Black // working now
    val Mint = Mono.Black // success
    val Amber = Mono.Grey // attention / waiting
    val Crimson = Color(0xFFD7263D) // error / delete
    val Ink = Mono.Black
}

@Composable
fun VoceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoScheme, content = content)
}
