package com.pianyu.studio.creative

import android.graphics.*
import android.os.SystemClock
import android.util.Log
import com.pianyu.studio.watermark.photo.ImportedPhoto
import com.pianyu.studio.watermark.photo.PhotoRepository
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CreativeExportTiming(val decodeMs:Long,val effectMs:Long,val encodeMs:Long)

/** Decode JPEG once, edit in small strips, then use Android's native JPEG encoder. */
internal object CreativeJpegExporter {
    suspend fun render(photo:ImportedPhoto,path:Path,file:File,
                       onProgress:suspend (Float,String)->Unit = {_,_->}):CreativeExportTiming {
        var decoded:Bitmap?=null
        var image:Bitmap?=null
        val start=SystemClock.elapsedRealtime()
        try {
            val input=BitmapFactory.decodeFile(photo.file.path,BitmapFactory.Options().apply {
                inSampleSize=1;inScaled=false;inMutable=true;inPreferredConfig=Bitmap.Config.ARGB_8888
            }) ?: error("无法读取原图")
            decoded=input
            val oriented=PhotoRepository.orient(input,photo.orientation)
            image=oriented
            if(oriented!==input) {input.recycle();decoded=null}
            val full=if(oriented.isMutable) oriented else oriented.copy(Bitmap.Config.ARGB_8888,true)
                ?: error("无法创建可编辑图片")
            image=full
            if(full!==oriented) {oriented.recycle();if(decoded===oriented) decoded=null}
            val decodedAt=SystemClock.elapsedRealtime()
            onProgress(.15f,"正在处理留彩效果")
            val canvas=Canvas(full)
            val height=(500_000/full.width).coerceIn(1,128)
            val strip=Bitmap.createBitmap(full.width,height,Bitmap.Config.ARGB_8888)
            val copyCanvas=Canvas(strip)
            val copyPaint=Paint().apply {xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC)}
            try {
                var y=0
                while(y<full.height) {
                    currentCoroutineContext().ensureActive()
                    val count=minOf(height,full.height-y)
                    copyCanvas.drawBitmap(full,Rect(0,y,full.width,y+count),Rect(0,0,full.width,count),copyPaint)
                    val checkpoint=canvas.save()
                    canvas.clipRect(0,y,full.width,y+count)
                    canvas.translate(0f,y.toFloat())
                    drawEffect(canvas,strip,Path(path).apply {offset(0f,-y.toFloat())})
                    canvas.restoreToCount(checkpoint)
                    y+=count
                    onProgress(.15f+.45f*y/full.height,"正在处理留彩效果")
                }
            } finally {strip.recycle()}
            // JPEG has no alpha channel. Composite transparent input over white explicitly.
            canvas.drawColor(Color.WHITE,PorterDuff.Mode.DST_OVER)
            val processedAt=SystemClock.elapsedRealtime()
            onProgress(.65f,"正在编码 JPEG · 质量 80%")
            file.parentFile?.mkdirs()
            file.outputStream().use {out ->
                check(full.compress(Bitmap.CompressFormat.JPEG,80,out)) {"JPEG 编码失败"}
                out.fd.sync()
            }
            return CreativeExportTiming(decodedAt-start,processedAt-decodedAt,SystemClock.elapsedRealtime()-processedAt).also {
                Log.i("CreativeExport","JPEG80 ${full.width}x${full.height}: decode=${it.decodeMs}ms effect=${it.effectMs}ms encode_write=${it.encodeMs}ms bytes=${file.length()}")
            }
        } catch(e:Throwable) {file.delete();throw e}
        finally {
            image?.recycle()
            if(decoded!==image) decoded?.recycle()
        }
    }
}
