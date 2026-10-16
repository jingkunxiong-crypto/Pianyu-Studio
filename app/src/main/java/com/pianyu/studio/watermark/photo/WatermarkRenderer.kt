package com.pianyu.studio.watermark.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF

/** Shared by the on-screen preview and the full-resolution export. */
object WatermarkRenderer {
    fun draw(canvas: Canvas, width: Float, height: Float, mark: Bitmap, pose: WatermarkPose,
             opacity: Float, ink: Int): WatermarkBounds {
        val bounds = WatermarkGeometry.resolve(width, height, mark.width.toFloat() / mark.height, pose)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
            colorFilter = when(ink) {
                0 -> PorterDuffColorFilter(0xFFF3F6F8.toInt(), PorterDuff.Mode.SRC_IN)
                1 -> PorterDuffColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
                else -> null
            }
        }
        canvas.drawBitmap(mark, null, RectF(bounds.x - bounds.width/2, bounds.y - bounds.height/2,
            bounds.x + bounds.width/2, bounds.y + bounds.height/2), paint)
        return bounds
    }
}

fun createDemoSignatureBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(640, 280, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.scale(4f, 4f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; style = Paint.Style.STROKE
        strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    val path = Path().apply {
        moveTo(32f,62f); cubicTo(42f,40f,61f,7f,65f,5f)
        cubicTo(68f,2f,40f,56f,16f,60f); cubicTo(1f,64f,20f,48f,48f,43f)
        cubicTo(70f,39f,89f,30f,93f,30f); cubicTo(86f,44f,84f,53f,94f,45f)
        cubicTo(108f,29f,105f,32f,103f,45f); cubicTo(117f,26f,120f,31f,115f,43f)
        cubicTo(127f,29f,130f,34f,128f,42f); cubicTo(124f,49f,140f,43f,154f,39f)
    }
    canvas.drawPath(path, paint)
    paint.style = Paint.Style.FILL; canvas.drawCircle(96f,23f,1.5f,paint)
    return bitmap
}
