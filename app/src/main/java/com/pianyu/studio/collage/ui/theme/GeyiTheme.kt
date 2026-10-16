package com.pianyu.studio.collage.ui.theme

import com.pianyu.studio.ui.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val GeyiGraphite = StudioPalette.Background
val GeyiDeepBlue = StudioPalette.Background
val GeyiPanel = StudioPalette.Container
val GeyiSurface = StudioPalette.Surface
val GeyiSurfaceVariant = StudioPalette.Container
val GeyiIce = StudioPalette.Accent
val GeyiTextPrimary = StudioPalette.Ink
val GeyiTextSecondary = StudioPalette.Muted
val GeyiOutline = StudioPalette.Outline
val GeyiGlass = Color.White.copy(alpha = 0.13f)
val GeyiGlassBorder = Color.White.copy(alpha = 0.22f)

private val GeyiColorScheme = lightColorScheme(
    primary = GeyiIce,
    onPrimary = GeyiGraphite,
    secondary = GeyiTextSecondary,
    onSecondary = GeyiGraphite,
    background = GeyiGraphite,
    onBackground = GeyiTextPrimary,
    surface = GeyiSurface,
    onSurface = GeyiTextPrimary,
    surfaceVariant = GeyiSurfaceVariant,
    onSurfaceVariant = GeyiTextSecondary,
    outline = GeyiOutline,
)

@Composable
fun GeyiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeyiColorScheme,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalContentColor provides GeyiTextPrimary, content = content)
    }
}
