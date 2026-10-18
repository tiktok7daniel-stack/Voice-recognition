package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Danghwa theme (MANHWA design system).
private val Danghwa = darkColorScheme(
    primary = Color(0xFFFF3B4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8E0F1F),
    onPrimaryContainer = Color.White,
    background = Color(0xFF07070A),
    onBackground = Color(0xFFF1E9EA),
    surface = Color(0xFF07070A),
    onSurface = Color(0xFFF1E9EA),
    surfaceContainer = Color(0xFF161116),
    surfaceContainerHighest = Color(0xFF271C22),
)

// Status accents: fixed meaning, never decoration.
object Accent {
    val Ember = Color(0xFFFF6A2B) // working now
    val Mint = Color(0xFF3DDC97) // success
    val Amber = Color(0xFFFFB020) // attention / waiting
    val Crimson = Color(0xFFE5173F) // error / delete
    val Ink = Color(0xFF050507)
}

@Composable
fun VoceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Danghwa, content = content)
}
