package com.olivermoberg.ledcoaster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App green, also the action bar colour in `Theme.LedCoaster`. */
val CoasterGreen = Color(0xFF44D62C)
val ScreenBackground = Color(0xFF222222)
val CardBackground = Color(0xFF111111)
/** Low-battery tint. */
val LowBattery = Color(0xFFE53935)

private val CoasterColors = darkColorScheme(
    primary = CoasterGreen,
    onPrimary = Color.Black,
    secondary = CoasterGreen,
    onSecondary = Color.Black,
    background = ScreenBackground,
    onBackground = Color.White,
    surface = CardBackground,
    onSurface = Color.White,
    surfaceContainer = ScreenBackground,
)

/** Always dark, matching the View screens. */
@Composable
fun CoasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CoasterColors, content = content)
}
