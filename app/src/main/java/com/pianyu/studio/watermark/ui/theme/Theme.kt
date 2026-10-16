package com.pianyu.studio.watermark.ui.theme

import com.pianyu.studio.ui.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val Ice = StudioPalette.Accent
val SoftWhite = Color(0xFFF3F6F8)
val Muted = StudioPalette.Muted
val Graphite = Color(0xFF141A20)

private val StudioColors = lightColorScheme(
    primary = Ice, onPrimary = Color.White, secondary = Muted,
    primaryContainer = StudioPalette.Container, onPrimaryContainer = StudioPalette.Ink,
    secondaryContainer = StudioPalette.Container, onSecondaryContainer = StudioPalette.Ink,
    tertiary = Ice, tertiaryContainer = StudioPalette.Container, onTertiaryContainer = StudioPalette.Ink,
    surfaceTint = Ice, outlineVariant = StudioPalette.Outline,
    background = StudioPalette.Background, onBackground = StudioPalette.Ink,
    surface = StudioPalette.Surface, onSurface = StudioPalette.Ink,
    surfaceVariant = StudioPalette.Container, onSurfaceVariant = Muted,
    outline = StudioPalette.Outline
)

@Composable
fun PianyuStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudioColors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides StudioPalette.Ink, content = content)
    }
}
