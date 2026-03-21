package com.bluecast.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Mist,
    secondary = VividPurple,
    onSecondary = Mist,
    tertiary = AquaGlow,
    background = Midnight,
    onBackground = Mist,
    surface = DeepBlue,
    onSurface = Mist,
    error = WarningRed
)

@Composable
fun BlueCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else DarkColors,
        typography = AppTypography,
        content = content
    )
}

