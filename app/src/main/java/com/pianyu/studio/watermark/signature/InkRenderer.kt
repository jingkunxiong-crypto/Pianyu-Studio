package com.pianyu.studio.watermark.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.floor

data class InkPoint(val x: Float, val y: Float)
data class InkStroke(val points: List<InkPoint>, val width: Float)

/** The preview and saved PNG share the same normalized geometry and smoothing. */
object InkRenderer {
    const val WIDTH = 1200f
    const val HEIGHT = 1260f

    fun draw(canvas: Canvas, strokes: List<InkStroke>, width: Float, height: Float) {
        canvas.save()
        canvas.scale(width / WIDTH, height / HEIGHT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            val points = stroke.points
            if (points.isEmpty()) return@forEach
            paint.strokeWidth = stroke.width
            if (points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(points[0].x, points[0].y, stroke.width / 2, paint)
                paint.style = Paint.Style.STROKE
            } else {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val previous = points[i-1]; val current = points[i]
                        quadTo(previous.x, previous.y, (previous.x+current.x)/2, (previous.y+current.y)/2)
                    }
                    lineTo(points.last().x, points.last().y)
                }
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }

    fun export(strokes: List<InkStroke>): Bitmap {
        require(strokes.any { it.points.isNotEmpty() }) { "请先写下你的签名。" }
        val original = Bitmap.createBitmap(WIDTH.toInt(), HEIGHT.toInt(), Bitmap.Config.ARGB_8888)
        draw(Canvas(original), strokes, WIDTH, HEIGHT)
        return try { cropTransparent(original) } finally { original.recycle() }
    }

    /** Crop the actual alpha bounds; a white paper background is never introduced. */
    fun cropTransparent(bitmap: Bitmap, padding: Int = 20): Bitmap {
        var minX = bitmap.width; var minY = bitmap.height; var maxX = -1; var maxY = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) if ((row[x] ushr 24) != 0) {
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        require(maxX >= 0) { "这张图片完全透明，没有可用的签名。" }
        val left = maxOf(0, minX-padding); val top = maxOf(0, minY-padding)
        val right = minOf(bitmap.width, maxX+padding+1); val bottom = minOf(bitmap.height, maxY+padding+1)
        // Copy even when the bounds fill the image so callers own independent bitmaps.
        return Bitmap.createBitmap(bitmap, left, top, right-left, bottom-top)
            .let { if (it === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else it }
    }
}
