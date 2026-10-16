package com.pianyu.studio.collage.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

/** Same 108-unit geometry and palette as the launcher foreground. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, tileProgress: Float = 1f) {
    Canvas(modifier.aspectRatio(1f)) {
        val unit = size.width / 108f
        val roundedBackground = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(24f * unit),
                ),
            )
        }
        clipPath(roundedBackground) {
            drawRect(Color(0xFF293644))
            val sheen = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, 42f * unit)
                cubicTo(76f * unit, 34f * unit, 35f * unit, 53f * unit, 0f, 67f * unit)
                close()
            }
            drawPath(sheen, Color.White.copy(alpha = 0.07f))
        }

        brandTiles.forEachIndexed { index, tile ->
            val fraction = ((tileProgress - index * 0.12f) / 0.64f).coerceIn(0f, 1f)
            val eased = fraction * fraction * (3f - 2f * fraction)
            val remaining = 1f - eased
            drawRoundRect(
                color = tile.color,
                topLeft = Offset(
                    (tile.left + tile.startOffsetX * remaining) * unit,
                    (tile.top + tile.startOffsetY * remaining) * unit,
                ),
                size = Size((tile.right - tile.left) * unit, (tile.bottom - tile.top) * unit),
                cornerRadius = CornerRadius(4f * unit),
                alpha = eased,
            )
        }
    }
}

private data class Tile(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Color,
    val startOffsetX: Float,
    val startOffsetY: Float,
)

private val brandTiles = listOf(
    Tile(31f, 31f, 53f, 53f, Color(0xFFF3F6F8), -10f, -9f),
    Tile(57f, 31f, 77f, 63f, Color(0xFFC4DFEF), 10f, -9f),
    Tile(31f, 57f, 53f, 77f, Color(0xFF71818F), -10f, 9f),
    Tile(57f, 67f, 77f, 77f, Color(0xFF5E7280), 10f, 9f),
)
