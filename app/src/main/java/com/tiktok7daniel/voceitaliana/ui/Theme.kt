package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Danghwa theme (MANHWA design system), night variant.
private val DanghwaDark = darkColorScheme(
    primary = Color(0xFFFF3B4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8E0F1F),
    onPrimaryContainer = Color.White,
    background = Color(0xFF030304),
    onBackground = Color(0xFFF1E9EA),
    surface = Color(0xFF07070A),
    onSurface = Color(0xFFF1E9EA),
    surfaceContainer = Color(0xFF161116),
    surfaceContainerHighest = Color(0xFF271C22),
)

// Day variant: frosted white glass over a blush backdrop, same red neon.
private val DanghwaLight = lightColorScheme(
    primary = Color(0xFFE32238),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF5C0612),
    background = Color(0xFFFFF6F7),
    onBackground = Color(0xFF1C1114),
    surface = Color(0xFFFFFBFB),
    onSurface = Color(0xFF1C1114),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFF0F2),
)

// Status accents: fixed meaning, never decoration.
object Accent {
    val Ember = Color(0xFFFF6A2B) // working now
    val Mint = Color(0xFF2FC787) // success
    val Amber = Color(0xFFF2A10C) // attention / waiting
    val Crimson = Color(0xFFE5173F) // error / delete
}

/** Main text color for the current theme; pass alpha for secondary text (0.7) or hints (0.5). */
@Composable
@ReadOnlyComposable
fun ink(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

/** Follows the phone: dark phone → dark app, light phone → light app. */
@Composable
fun VoceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DanghwaDark else DanghwaLight, content = content)
}
