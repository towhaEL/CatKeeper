package com.catkeeper.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CatKeeperColorScheme = darkColorScheme(
    primary = PurpleAccent,
    onPrimary = TextPrimary,
    primaryContainer = PurpleDark,
    secondary = OrangeAccent,
    onSecondary = TextPrimary,
    secondaryContainer = OrangeLight,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = StatusRed,
)

@Composable
fun CatKeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatKeeperColorScheme,
        typography = CatKeeperTypography,
        content = content
    )
}
