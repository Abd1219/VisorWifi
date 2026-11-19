package com.abdapps.visorwifi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VisorPrimary,
    onPrimary = VisorBackground,
    secondary = VisorSecondary,
    tertiary = VisorHealthy,
    background = VisorBackground,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = VisorSurface,
    onSurface = androidx.compose.ui.graphics.Color.White,
    error = VisorError
)

@Composable
fun VisorWifiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
