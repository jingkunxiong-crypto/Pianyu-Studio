package com.pianyu.studio.watermark.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

data class ImportedPhoto(val file: File, val name: String, val width: Int, val height: Int,
                         val preview: Bitmap, val orientation: Int)

/** Keep the original bytes for later export; only the editing preview is sampled. */
class PhotoRepository(private val directory: File) {
    fun import(resolver: ContentResolver, uri: Uri): ImportedPhoto {
        directory.mkdirs()
        val file = File(directory, "${UUID.randomUUID()}.source")
        try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 512L * 1024 * 1024) { "照片超过 512 MB，请选择较小的文件。" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取照片，请重新选择。")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别这张照片，请选择 JPG、PNG、WebP 或设备支持的图片。" }
            require(bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "照片超过 1 亿像素，暂不支持。" }
            val orientation = try {
                ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("照片解码失败，请重新选择。")
            val preview = orient(decoded, orientation)
            if (preview !== decoded) decoded.recycle()
            val swaps = orientation in 5..8
            val name = try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (_: Exception) { null }
            return ImportedPhoto(file, name?.take(100) ?: "已导入照片",
                if (swaps) bounds.outHeight else bounds.outWidth,
                if (swaps) bounds.outWidth else bounds.outHeight, preview, orientation)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    companion object {
        fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                2 -> matrix.setScale(-1f, 1f)
                3 -> matrix.setRotate(180f)
                4 -> matrix.setScale(1f, -1f)
                5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                6 -> matrix.setRotate(90f)
                7 -> { matrix.setRotate(90f); matrix.postScale(1f, -1f) }
                8 -> matrix.setRotate(-90f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
