package com.pianyu.studio.creative

import android.graphics.*
import android.os.SystemClock
import com.pianyu.studio.watermark.photo.ImportedPhoto
import com.pianyu.studio.watermark.photo.PhotoRepository
import com.pianyu.studio.watermark.photo.StreamingPngWriter
import java.io.File

/** Decode only one oriented stripe at a time, retaining every source pixel. */
internal object CreativePngExporter {
    @Suppress("DEPRECATION")
    suspend fun render(photo: ImportedPhoto, path: Path, file: File, onProgress:suspend (Float)->Unit = {}):CreativeExportTiming {
        var decodeMs=0L;var effectMs=0L;var encodeMs=0L
        val decoder=BitmapRegionDecoder.newInstance(photo.file.path,false)
            ?: error("此图片格式暂不支持分块导出，请使用原尺寸 JPG、PNG 或 WebP 图片。")
        try {
            val w=photo.width;val h=photo.height
            // At most half a million pixels per bitmap; no full-image allocation.
            val stripeHeight=(500_000/w).coerceIn(1,128)
            val row=IntArray(w)
            file.parentFile?.mkdirs()
            file.outputStream().use { stream ->
                StreamingPngWriter(stream,w,h).use { png ->
                    var y=0
                    var reported=-1
                    while(y<h) {
                        val bottom=minOf(h,y+stripeHeight)
                        fun source(x:Int,yy:Int):Pair<Int,Int> = when(photo.orientation) {
                            2 -> decoder.width-1-x to yy
                            3 -> decoder.width-1-x to decoder.height-1-yy
                            4 -> x to decoder.height-1-yy
                            5 -> yy to x
                            6 -> yy to decoder.height-1-x
                            7 -> decoder.width-1-yy to decoder.height-1-x
                            8 -> decoder.width-1-yy to x
                            else -> x to yy
                        }
                        val corners=listOf(source(0,y),source(w-1,y),source(0,bottom-1),source(w-1,bottom-1))
                        val rect=Rect(corners.minOf {it.first},corners.minOf {it.second},corners.maxOf {it.first}+1,corners.maxOf {it.second}+1)
                        var decoded:Bitmap?=null;var oriented:Bitmap?=null;var output:Bitmap?=null
                        try {
                            val decodingAt=SystemClock.elapsedRealtime()
                            val tile=decoder.decodeRegion(rect,BitmapFactory.Options().apply {
                                inSampleSize=1;inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
                            }) ?: error("原图分块读取失败")
                            decoded=tile
                            val input=PhotoRepository.orient(tile,photo.orientation)
                            oriented=input
                            decodeMs+=SystemClock.elapsedRealtime()-decodingAt
                            val effectAt=SystemClock.elapsedRealtime()
                            check(input.width==w && input.height==bottom-y) {"照片方向校正失败"}
                            val result=Bitmap.createBitmap(w,bottom-y,Bitmap.Config.ARGB_8888)
                            output=result
                            val localPath=Path(path).apply {offset(0f,-y.toFloat())}
                            drawEffect(Canvas(result),input,localPath)
                            effectMs+=SystemClock.elapsedRealtime()-effectAt
                            val encodingAt=SystemClock.elapsedRealtime()
                            for(line in 0 until result.height) {
                                result.getPixels(row,0,w,0,line,w,1)
                                png.writeRow(row)
                            }
                            encodeMs+=SystemClock.elapsedRealtime()-encodingAt
                        } finally {
                            output?.recycle();oriented?.recycle()
                            if(decoded!==oriented) decoded?.recycle()
                        }
                        y=bottom
                        val percent=y*100/h
                        if(percent!=reported) {reported=percent;onProgress(y.toFloat()/h)}
                    }
                }
                stream.fd.sync()
            }
        } finally {decoder.recycle()}
        return CreativeExportTiming(decodeMs,effectMs,encodeMs)
    }
}
