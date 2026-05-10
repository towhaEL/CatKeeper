package com.catkeeper.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CatKeeperColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = MatteBlack,
    primaryContainer = OrangeVariant,
    onPrimaryContainer = TextPrimary,
    secondary = OrangeLight,
    onSecondary = MatteBlack,
    secondaryContainer = MatteSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    background = MatteBlack,
    onBackground = TextPrimary,
    surface = MatteSurface,
    onSurface = TextPrimary,
    surfaceVariant = MatteSurfaceVariant,
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
