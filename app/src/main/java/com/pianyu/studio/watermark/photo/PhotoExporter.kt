package com.pianyu.studio.watermark.photo

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.pianyu.studio.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.pianyu.studio.media.RecentWorks

enum class PhotoFormat(val extension: String, val mime: String, val compression: Bitmap.CompressFormat) {
    JPG("jpg","image/jpeg",Bitmap.CompressFormat.JPEG),
    PNG("png","image/png",Bitmap.CompressFormat.PNG)
}
data class PhotoExportRequest(val photo: ImportedPhoto?, val signature: Bitmap?, val pose: WatermarkPose,
                              val opacity: Float, val ink: Int, val format: PhotoFormat, val quality: Int)
data class ExportedPhoto(val file: File, val width: Int, val height: Int, val format: PhotoFormat)
data class SavedPhoto(val uri: Uri, val name: String, val width: Int, val height: Int, val format: PhotoFormat)
data class SharePhoto(val uri: Uri, val format: PhotoFormat)

class PhotoExporter(private val context: Context) {
    private val directory = File(context.cacheDir,"exports").apply {mkdirs()}

    fun render(request: PhotoExportRequest): ExportedPhoto {
        val photo = request.photo
        if (photo != null && request.format!=PhotoFormat.PNG) {
            val runtime = Runtime.getRuntime()
            val available = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
            val required = photo.width.toLong() * photo.height * 4L * (if(photo.orientation in 2..8) 2 else 1)
            require(required + 16L * 1024 * 1024 < available * .75) { "这张照片原尺寸导出所需内存较大，请切换 PNG 无损导出，PNG 支持分块处理大图。" }
        }
        val temporary = File(directory,"${UUID.randomUUID()}.tmp")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
        val file = File(directory,"Pianyu_Signed_${stamp}_${UUID.randomUUID().toString().take(8)}.${request.format.extension}")
        if(photo!=null && request.format==PhotoFormat.PNG) {
            try {
                TiledPngExporter.render(photo,request,temporary)
                check(temporary.renameTo(file)) {"生成导出文件失败，请重试。"}
                return ExportedPhoto(file,photo.width,photo.height,PhotoFormat.PNG)
            } catch(e: Throwable) {temporary.delete();file.delete();throw e}
        }
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var demoMark: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply {
                inMutable=true; inPreferredConfig=Bitmap.Config.ARGB_8888; inScaled=false
            }
            decoded = if(photo != null) BitmapFactory.decodeFile(photo.file.path,options)
                else decodeDemoPhoto(context.resources)
            val input = decoded ?: error("无法读取原照片，请重新选择。")
            oriented = PhotoRepository.orient(input,photo?.orientation ?: 1)
            if(oriented !== input) {input.recycle();decoded=null}
            val output = oriented
            val canvas = Canvas(output)
            if(request.format==PhotoFormat.JPG) canvas.drawColor(Color.WHITE,PorterDuff.Mode.DST_OVER)
            val mark = request.signature ?: createDemoSignatureBitmap().also {demoMark=it}
            WatermarkRenderer.draw(canvas,output.width.toFloat(),output.height.toFloat(),mark,
                request.pose,request.opacity,request.ink)
            temporary.outputStream().use { stream ->
                check(output.compress(request.format.compression,request.quality.coerceIn(1,100),stream)) { "照片编码失败，请重试。" }
                stream.fd.sync()
            }
            check(temporary.renameTo(file)) { "生成导出文件失败，请重试。" }
            return ExportedPhoto(file,output.width,output.height,request.format)
        } catch(error: Throwable) {
            temporary.delete();file.delete();throw error
        } finally {
            oriented?.recycle()
            if(decoded !== oriented) decoded?.recycle()
            demoMark?.recycle()
        }
    }

    fun saveToAlbum(exported: ExportedPhoto, kind: String = "签名水印"): SavedPhoto {
        if(Build.VERSION.SDK_INT < 29) return saveLegacy(exported)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,exported.file.name)
            put(MediaStore.Images.Media.MIME_TYPE,exported.format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/Pianyu Studio")
            put(MediaStore.Images.Media.IS_PENDING,1)
            put(MediaStore.Images.Media.WIDTH,exported.width);put(MediaStore.Images.Media.HEIGHT,exported.height)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("无法创建相册照片，请检查存储空间。")
        try {
            resolver.openOutputStream(uri,"w")?.use {output -> exported.file.inputStream().use {it.copyTo(output)}}
                ?: error("无法写入相册照片。")
            check(resolver.update(uri,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)==1) { "相册照片发布失败，请重试。" }
            RecentWorks.add(context, uri, exported.file.name, kind)
            return SavedPhoto(uri,exported.file.name,exported.width,exported.height,exported.format)
        } catch(error: Throwable) {
            resolver.delete(uri,null,null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(exported: ExportedPhoto): SavedPhoto {
        val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Pianyu Studio")
        check(album.isDirectory || album.mkdirs()) { "无法创建相册目录，请检查存储权限。" }
        val destination = File(album,exported.file.name)
        try {
            exported.file.inputStream().use {input -> destination.outputStream().use {output -> input.copyTo(output);output.fd.sync()}}
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA,destination.path)
                put(MediaStore.Images.Media.DISPLAY_NAME,destination.name)
                put(MediaStore.Images.Media.MIME_TYPE,exported.format.mime)
                put(MediaStore.Images.Media.WIDTH,exported.width);put(MediaStore.Images.Media.HEIGHT,exported.height)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("相册登记失败。")
            return SavedPhoto(uri,destination.name,exported.width,exported.height,exported.format)
        } catch(error: Throwable) {destination.delete();throw error}
    }

    fun sharePhoto(exported: ExportedPhoto) = SharePhoto(FileProvider.getUriForFile(context,
        "${context.packageName}.exports",exported.file),exported.format)

    companion object {
        fun shareIntent(photo: SharePhoto): Intent = Intent(Intent.ACTION_SEND).apply {
            type=photo.format.mime;putExtra(Intent.EXTRA_STREAM,photo.uri)
            clipData=ClipData.newRawUri("签名照片",photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
