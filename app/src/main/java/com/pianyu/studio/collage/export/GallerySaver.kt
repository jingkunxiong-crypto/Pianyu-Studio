package com.pianyu.studio.collage.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.pianyu.studio.media.RecentWorks

/** Saves a finished PNG in the shared Pictures/Pianyu Studio collection, visible to gallery apps. */
object GallerySaver {
    fun save(
        context: Context,
        displayName: String,
        request: CollageExporter.Request,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): Uri {
        var lastMemoryError: OutOfMemoryError? = null
        for (limit in listOf(request.maxLongSide, 3072, 2048).distinct()) {
            try {
                val savedUri = saveOnce(context, displayName, request.copy(maxLongSide = limit), onProgress)
                RecentWorks.add(context, savedUri, displayName, "拼图")
                onProgress(1f, "保存完成")
                return savedUri
            } catch (error: OutOfMemoryError) {
                lastMemoryError = error
                onProgress(0.05f, "照片较大，正在调整尺寸重试")
                System.gc()
            }
        }
        throw IllegalStateException("照片尺寸过大，无法完成保存", lastMemoryError)
    }

    private fun saveOnce(
        context: Context,
        displayName: String,
        request: CollageExporter.Request,
        onProgress: (Float, String) -> Unit,
    ): Uri {
        val resolver = context.contentResolver
        onProgress(0.02f, "正在准备保存")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Pianyu Studio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法在相册中创建照片")
            try {
                resolver.openOutputStream(destination, "w")?.use { stream ->
                    CollageExporter.export(resolver, stream, request, onProgress)
                } ?: error("无法写入相册照片")
                val complete = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                check(resolver.update(destination, complete, null, null) > 0) { "无法完成相册保存" }
                return destination
            } catch (error: Throwable) {
                runCatching { resolver.delete(destination, null, null) }
                throw error
            }
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Pianyu Studio",
            )
            check(directory.isDirectory || directory.mkdirs()) { "无法创建相册目录" }
            val destination = File(directory, displayName)
            try {
                FileOutputStream(destination).use { stream ->
                    CollageExporter.export(resolver, stream, request, onProgress)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destination.absolutePath),
                    arrayOf("image/png"),
                    null,
                )
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.exports",
                    destination,
                )
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
    }
}
