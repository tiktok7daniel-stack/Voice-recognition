package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's palette. Two looks that follow the phone's dark mode:
 * dark = charcoal glass cards with an orange glow, light = white cards with black actions.
 * Both share the orange accent for small labels and "working" states.
 */
data class VoceColors(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val cardTop: Color,
    val border: Color,
    val tile: Color,
    val tileBorder: Color,
    val ink: Color,
    val action: Color,
    val onAction: Color,
    val accent: Color,
    val grey: Color,
    val bar: Color,
    val barSelected: Color,
    val onBarSelected: Color,
    val shadow: Color,
)

private val Light = VoceColors(
    dark = false,
    page = Color(0xFFECEDEF),
    card = Color(0xFFFFFFFF),
    cardTop = Color(0xFFFFFFFF),
    border = Color(0xFFE6E7EA),
    tile = Color(0xFFF4F5F7),
    tileBorder = Color(0xFFE2E3E7),
    ink = Color(0xFF111113),
    action = Color(0xFF111113),
    onAction = Color.White,
    accent = Color(0xFFE8640F),
    grey = Color(0xFF8A8D93),
    bar = Color(0xFF111113),
    barSelected = Color.White,
    onBarSelected = Color(0xFF111113),
    shadow = Color(0x33101318),
)

private val Dark = VoceColors(
    dark = true,
    page = Color(0xFF0A0A0B),
    card = Color(0xFF141416),
    cardTop = Color(0xFF1C1C1F),
    border = Color(0xFF2A2A2E),
    tile = Color(0xFF1D1D20),
    tileBorder = Color(0xFF2C2C31),
    ink = Color(0xFFF3F3F4),
    action = Color(0xFFFFA36C),
    onAction = Color(0xFF1A0E06),
    accent = Color(0xFFFF8A3D),
    grey = Color(0xFF8A8D93),
    bar = Color(0xFF18181B),
    barSelected = Color(0xFFFFA36C),
    onBarSelected = Color(0xFF1A0E06),
    shadow = Color(0x66000000),
)

val LocalVoce = staticCompositionLocalOf { Light }

/** Text and icons on cards. Lower alphas for secondary text. */
val Ink: Color
    @Composable @ReadOnlyComposable
    get() = LocalVoce.current.ink

/** Surfaces and actions of the current look (names kept from the monochrome design). */
object Mono {
    val Page: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.page
    val Card: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.card
    val Tile: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.tile
    val Line: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.tileBorder

    /** Main action colour: black in light mode, warm peach in dark mode. */
    val Black: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.action
    val OnBlack: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.onAction
    val Grey: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.grey
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.accent
}

// Status accents with a fixed meaning: orange = working, action colour = done,
// grey = waiting, red = error / delete.
object Accent {
    val Ember: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.accent
    val Mint: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.action
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalVoce.current.grey
    val Crimson = Color(0xFFE5484D)
}

@Composable
fun VoceTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAction, background = c.page, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, onSurfaceVariant = Color(0xFFA9AAB0),
            surfaceContainer = c.tile, surfaceContainerHighest = c.border, outline = Color(0xFF45464C),
            error = Accent.Crimson,
        )
    } else {
        lightColorScheme(
            primary = c.action, onPrimary = c.onAction, background = c.page, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, onSurfaceVariant = Color(0xFF5C5F66),
            surfaceContainer = c.tile, surfaceContainerHighest = c.border, outline = Color(0xFFC9CBD1),
            error = Accent.Crimson,
        )
    }
    CompositionLocalProvider(LocalVoce provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
