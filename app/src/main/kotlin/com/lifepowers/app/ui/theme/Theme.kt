package com.lifepowers.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LifePowersColorScheme = lightColorScheme(
    primary = DeepPink,
    onPrimary = White,
    primaryContainer = LightPink,
    onPrimaryContainer = Ink,
    secondary = LightPink,
    onSecondary = Ink,
    secondaryContainer = LightPink,
    onSecondaryContainer = Ink,
    tertiary = Lavender,
    onTertiary = Ink,
    tertiaryContainer = Lavender,
    onTertiaryContainer = Ink,
    background = Ground,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Ground,
    onSurfaceVariant = Muted,
    outline = HairLine,
    outlineVariant = HairLine,
    error = DeepPink,
    onError = White,
)

private val LifePowersShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun LifePowersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LifePowersColorScheme,
        typography = LifePowersTypography,
        shapes = LifePowersShapes,
        content = content,
    )
}
