package com.pianyu.studio.collage.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import java.io.OutputStream
import com.pianyu.studio.collage.model.CollageRatio
import com.pianyu.studio.collage.model.CollageStyle
import com.pianyu.studio.collage.model.CollageTemplate
import com.pianyu.studio.collage.model.PhotoTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Renders the same geometry, centre crop, zoom and relative pan shown in the editor. */
object CollageExporter {
    data class Request(
        val photoUris: List<Uri>,
        val ratio: CollageRatio,
        val template: CollageTemplate,
        val transforms: List<PhotoTransform>,
        val style: CollageStyle = CollageStyle(),
        val maxLongSide: Int? = null,
    )

    fun export(
        resolver: ContentResolver,
        destination: Uri,
        request: Request,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ) {
        resolver.openOutputStream(destination, "w")?.use { stream ->
            export(resolver, stream, request, onProgress)
        } ?: error("无法打开保存位置")
    }

    fun export(
        resolver: ContentResolver,
        stream: OutputStream,
        request: Request,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ) {
        require(request.photoUris.size == request.template.photoCount)
        require(request.transforms.size == request.photoUris.size)

        onProgress(0.08f, "正在分析照片")
        val longestSide = outputLongestSide(resolver, request)
        val width = if (request.ratio.value >= 1f) longestSide else (longestSide * request.ratio.value).roundToInt()
        val height = if (request.ratio.value >= 1f) (longestSide / request.ratio.value).roundToInt() else longestSide
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(if (request.style.blackSpacing) Color.BLACK else Color.WHITE)
            val gap = min(width, height) * request.style.spacingPercent.coerceIn(0, 8) / 100f
            // Spacing applies only to shared cell edges, never the canvas perimeter.
            val contentWidth = width.toFloat()
            val contentHeight = height.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            request.template.cells.forEachIndexed { index, cell ->
                onProgress(0.12f + 0.68f * index / request.photoUris.size, "正在拼接第 ${index + 1} 张照片")
                val left = cell.left * contentWidth + if (cell.left > 0.0001f) gap / 2f else 0f
                val top = cell.top * contentHeight + if (cell.top > 0.0001f) gap / 2f else 0f
                val right = (cell.left + cell.width) * contentWidth -
                    if (cell.left + cell.width < 0.9999f) gap / 2f else 0f
                val bottom = (cell.top + cell.height) * contentHeight -
                    if (cell.top + cell.height < 0.9999f) gap / 2f else 0f
                val bounds = RectF(left, top, right, bottom)
                val source = decodeSampled(
                    resolver,
                    request.photoUris[index],
                    bounds.width().roundToInt(),
                    bounds.height().roundToInt(),
                )
                try {
                    drawPhoto(canvas, source, bounds, request.transforms[index], paint)
                } finally {
                    source.recycle()
                }
                onProgress(0.12f + 0.68f * (index + 1) / request.photoUris.size, "已拼接 ${index + 1}/${request.photoUris.size} 张照片")
            }

            onProgress(0.88f, "正在编码 PNG 图片")
            check(output.compress(Bitmap.CompressFormat.PNG, 100, stream))
            onProgress(0.96f, "正在写入相册")
        } finally {
            output.recycle()
        }
    }

    private fun outputLongestSide(resolver: ContentResolver, request: Request): Int {
        val sourceDemand = request.photoUris.mapIndexed { index, uri ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val input = resolver.openInputStream(uri) ?: error("无法读取照片：$uri")
            input.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "照片格式不受支持" }
            val cell = request.template.cells[index]
            val zoom = request.transforms[index].zoom.coerceIn(1f, 5f)
            val sideways = request.transforms[index].clockwiseQuarterTurns % 2 != 0
            val sourceWidth = if (sideways) bounds.outHeight else bounds.outWidth
            val sourceHeight = if (sideways) bounds.outWidth else bounds.outHeight
            max(sourceWidth / cell.width, sourceHeight / cell.height) / zoom
        }.maxOrNull() ?: 2048f
        val runtime = Runtime.getRuntime()
        val heapBytes = runtime.maxMemory()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        val availableBytes = (heapBytes - usedBytes).coerceAtLeast(16L * 1024 * 1024)
        val canvasBudget = min((heapBytes * 0.30).toLong(), (availableBytes * 0.55).toLong())
        val areaFactor = if (request.ratio.value >= 1f) 1f / request.ratio.value else request.ratio.value
        val memoryLimitedSide = sqrt(canvasBudget / (4.0 * areaFactor)).toInt()
        val safeLimit = min(6144, memoryLimitedSide).coerceAtLeast(2048)
        return sourceDemand.roundToInt().coerceIn(2048, min(safeLimit, request.maxLongSide ?: safeLimit))
    }

    private fun decodeSampled(resolver: ContentResolver, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("无法打开照片：$uri")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "照片格式不受支持" }
        val heapBytes = Runtime.getRuntime().maxMemory()
        val maxPixels = min(12_000_000L, heapBytes / 32L).coerceAtLeast(2_000_000L)
        var sample = 1
        while (
            bounds.outWidth.toLong() / sample * (bounds.outHeight.toLong() / sample) > maxPixels ||
            (bounds.outWidth / (sample * 2) >= targetWidth &&
                bounds.outHeight / (sample * 2) >= targetHeight)
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("无法解码照片：$uri")
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(270f)
            }
        }
        if (matrix.isIdentity) return decoded
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun drawPhoto(
        canvas: Canvas,
        photo: Bitmap,
        cell: RectF,
        transform: PhotoTransform,
        paint: Paint,
    ) {
        val cellWidth = cell.width()
        val cellHeight = cell.height()
        val quarterTurns = ((transform.clockwiseQuarterTurns % 4) + 4) % 4
        val sideways = quarterTurns % 2 != 0
        val visibleSourceWidth = if (sideways) photo.height else photo.width
        val visibleSourceHeight = if (sideways) photo.width else photo.height
        val cropScale = max(cellWidth / visibleSourceWidth, cellHeight / visibleSourceHeight)
        val zoom = transform.zoom.coerceIn(1f, 5f)
        val imageWidth = visibleSourceWidth * cropScale * zoom
        val imageHeight = visibleSourceHeight * cropScale * zoom
        val maxPanX = ((imageWidth - cellWidth) / 2f).coerceAtLeast(0f)
        val maxPanY = ((imageHeight - cellHeight) / 2f).coerceAtLeast(0f)
        val panX = (transform.offsetX * cellWidth).coerceIn(-maxPanX, maxPanX)
        val panY = (transform.offsetY * cellHeight).coerceIn(-maxPanY, maxPanY)
        val centreX = cell.centerX() + panX
        val centreY = cell.centerY() + panY
        val drawWidth = if (sideways) imageHeight else imageWidth
        val drawHeight = if (sideways) imageWidth else imageHeight

        canvas.save()
        canvas.clipRect(cell)
        canvas.translate(centreX, centreY)
        canvas.rotate(quarterTurns * 90f)
        canvas.drawBitmap(
            photo,
            null,
            RectF(-drawWidth / 2f, -drawHeight / 2f, drawWidth / 2f, drawHeight / 2f),
            paint,
        )
        canvas.restore()
    }
}
