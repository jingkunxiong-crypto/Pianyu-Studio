package com.pianyu.studio.watermark.ui

import android.graphics.Bitmap
import android.content.res.Resources
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pianyu.studio.watermark.photo.*

private class DemoPreviewAssets(resources: Resources) {
    val photo: Bitmap by lazy {
        decodeDemoPhoto(resources)
    }
    val signature: Bitmap by lazy { createDemoSignatureBitmap() }
}

@Composable
fun PhotoCanvas(photo: ImportedPhoto?, signature: Bitmap?, pose: WatermarkPose,
                opacity: Float, ink: Int, editable: Boolean = false,
                onPose: (WatermarkPose) -> Unit = {}) {
    val context = LocalContext.current
    val demo = remember(context) { DemoPreviewAssets(context.resources) }
    val image = photo?.preview ?: demo.photo
    val mark = signature ?: demo.signature
    val currentPose by rememberUpdatedState(pose)
    val currentRatio by rememberUpdatedState(mark.width.toFloat() / mark.height)
    val updatePose by rememberUpdatedState(onPose)
    val currentEditable by rememberUpdatedState(editable)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = photo?.let { it.width.toFloat() / it.height } ?: (image.width.toFloat() / image.height)
        val width = minOf(maxWidth, 420.dp * aspect)
        Canvas(Modifier.width(width).aspectRatio(aspect).clip(RoundedCornerShape(12.dp))
            .testTag("photoCanvas").semantics {
                contentDescription = if (photo == null) "示例照片水印预览" else "照片水印预览"
                stateDescription = "位置 ${(pose.x * 100).toInt()}% ${(pose.y * 100).toInt()}% · 大小 ${(pose.width * 100).toInt()}%"
            }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val first = WatermarkGeometry.resolve(size.width.toFloat(), size.height.toFloat(), currentRatio, currentPose)
                    if (!currentEditable || !first.contains(down.position.x, down.position.y, 18.dp.toPx())) return@awaitEachGesture
                    down.consume()
                    var gesturePose = currentPose
                    do {
                        val event = awaitPointerEvent()
                        val bounds = WatermarkGeometry.resolve(size.width.toFloat(), size.height.toFloat(), currentRatio, gesturePose)
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        if (pan.x != 0f || pan.y != 0f || zoom != 1f) {
                            val next = gesturePose.copy(x = (bounds.x + pan.x) / size.width,
                                y = (bounds.y + pan.y) / size.height,
                                width = (bounds.width / size.width * zoom).coerceIn(.08f, .65f))
                            val fitted = WatermarkGeometry.resolve(size.width.toFloat(), size.height.toFloat(), currentRatio, next)
                            gesturePose = next.copy(x = fitted.x / size.width, y = fitted.y / size.height,
                                width = fitted.width / size.width)
                            updatePose(gesturePose)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }) {
            val canvas = drawContext.canvas.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(image, null, RectF(0f, 0f, size.width, size.height), paint)
            val bounds = WatermarkRenderer.draw(canvas, size.width, size.height, mark, pose, opacity, ink)
            val rect = RectF(bounds.x - bounds.width/2, bounds.y - bounds.height/2,
                bounds.x + bounds.width/2, bounds.y + bounds.height/2)
            if (editable) {
                paint.alpha = 170; paint.colorFilter = null; paint.color = 0xFFC4DFEF.toInt()
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.dp.toPx()
                canvas.drawRect(rect, paint)
            }
        }
    }
}
