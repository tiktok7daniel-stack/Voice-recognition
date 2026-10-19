package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color

/** Text and icons on cards and pages. Lower alphas for secondary text. */
val Ink: Color get() = Mono.Ink

/**
 * Monochrome palette: soft grey page, white cards, black actions — or, when the phone is in dark
 * mode, the mirror image: near-black page, charcoal cards, white actions.
 */
object Mono {
    /** Set by [VoceTheme] from the phone's dark-mode setting. */
    internal var dark = false

    val Page get() = if (dark) Color(0xFF0B0B0D) else Color(0xFFECEDEF)
    val Card get() = if (dark) Color(0xFF18181B) else Color(0xFFFFFFFF)
    val Tile get() = if (dark) Color(0xFF232327) else Color(0xFFF4F5F7)
    val Line get() = if (dark) Color(0xFF303035) else Color(0xFFE2E3E7)

    /** The action color: black pills on the light theme, white pills on the dark one. */
    val Black get() = if (dark) Color(0xFFF2F2F4) else Color(0xFF111113)

    /** Text and icons drawn on top of [Black]. */
    val OnBlack get() = if (dark) Color(0xFF111113) else Color.White
    val Grey get() = Color(0xFF8A8D93)
    val Ink get() = if (dark) Color(0xFFF2F2F4) else Color(0xFF111113)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111113),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4F5F7),
    onPrimaryContainer = Color(0xFF111113),
    secondary = Color(0xFF8A8D93),
    background = Color(0xFFECEDEF),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111113),
    onSurfaceVariant = Color(0xFF5C5F66),
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHighest = Color(0xFFE2E3E7),
    outline = Color(0xFFC9CBD1),
    error = Color(0xFFD7263D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2F2F4),
    onPrimary = Color(0xFF111113),
    primaryContainer = Color(0xFF232327),
    onPrimaryContainer = Color(0xFFF2F2F4),
    secondary = Color(0xFF8A8D93),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFF2F2F4),
    onSurfaceVariant = Color(0xFFA9ACB2),
    surfaceContainer = Color(0xFF232327),
    surfaceContainerHighest = Color(0xFF303035),
    outline = Color(0xFF4A4C52),
    error = Color(0xFFFF4D5E),
)

// Status accents, kept in black and white: strong = active/done, grey = waiting.
// Red is the one exception, so errors and deletions still stand out.
object Accent {
    val Ember get() = Mono.Black // working now
    val Mint get() = Mono.Black // success
    val Amber get() = Mono.Grey // attention / waiting
    val Crimson get() = if (Mono.dark) Color(0xFFFF4D5E) else Color(0xFFD7263D) // error / delete
    val Ink get() = Mono.Black
}

/** Follows the phone: dark mode → black theme, light mode → white theme. */
@Composable
fun VoceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    Mono.dark = dark
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        // Rebuild the whole screen when the phone switches mode, so every Mono color is re-read.
        key(dark) { content() }
    }
}
