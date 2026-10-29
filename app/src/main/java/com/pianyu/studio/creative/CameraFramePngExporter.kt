package com.pianyu.studio.creative

import android.graphics.*
import com.pianyu.studio.watermark.photo.ImportedPhoto
import com.pianyu.studio.watermark.photo.PhotoRepository
import com.pianyu.studio.watermark.photo.StreamingPngWriter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.*

/** Writes the composite in rows, decoding only the original pixels needed by each stripe. */
internal object CameraFramePngExporter {
    @Suppress("DEPRECATION")
    suspend fun render(photo:ImportedPhoto, preview:Bitmap, camera:Bitmap, ratio:Float, crop:Boolean,
                       blur:Float, info:String, offsetX:Float, offsetY:Float, file:File,
                       onProgress:suspend (Float)->Unit = {}) {
        val width=photo.width
        val height=if(ratio>0) (width/ratio).roundToInt() else (photo.height+width*.64f).roundToInt()
        val bottom=if(ratio==0f) width*.64f else height*.5f
        val split=height-bottom
        val scale=if(crop) max(width.toFloat()/photo.width,split/photo.height) else min(width.toFloat()/photo.width,split/photo.height)
        val imageWidth=photo.width*scale;val imageHeight=photo.height*scale
        val left=(width-imageWidth)/2+if(crop) offsetX*(imageWidth-width).coerceAtLeast(0f)/2 else 0f
        val top=(split-imageHeight)/2+if(crop) offsetY*(imageHeight-split).coerceAtLeast(0f)/2 else 0f
        val decoder=BitmapRegionDecoder.newInstance(photo.file.path,false)
            ?: error("此照片格式暂不支持分块导出，请选择 JPG、PNG 或 WebP 原图。")
        fun source(x:Int,y:Int):Pair<Int,Int> = when(photo.orientation) {
            2 -> decoder.width-1-x to y
            3 -> decoder.width-1-x to decoder.height-1-y
            4 -> x to decoder.height-1-y
            5 -> y to x
            6 -> y to decoder.height-1-x
            7 -> decoder.width-1-y to decoder.height-1-x
            8 -> decoder.width-1-y to x
            else -> x to y
        }
        fun orientedPoint(x:Int,y:Int):Pair<Int,Int> = when(photo.orientation) {
            2 -> decoder.width-1-x to y
            3 -> decoder.width-1-x to decoder.height-1-y
            4 -> x to decoder.height-1-y
            5 -> y to x
            6 -> decoder.height-1-y to x
            7 -> decoder.height-1-y to decoder.width-1-x
            8 -> y to decoder.width-1-x
            else -> x to y
        }
        try {
            file.parentFile?.mkdirs()
            val stripeHeight=(500_000/width).coerceIn(1,128)
            file.outputStream().use { stream ->
                StreamingPngWriter(stream,width,height).use { png ->
                    var y=0
                    while(y<height) {
                        currentCoroutineContext().ensureActive()
                        val bottomY=min(height,y+stripeHeight)
                        val tile=Bitmap.createBitmap(width,bottomY-y,Bitmap.Config.ARGB_8888)
                        try {
                            val canvas=Canvas(tile);canvas.translate(0f,-y.toFloat())
                            drawCameraCard(canvas,preview,width.toFloat(),height.toFloat(),ratio,crop,blur,camera,info,offsetX,offsetY)
                            // Replace the preview in the main photo with original-resolution pixels.
                            val x0=floor((0f-left)/scale).toInt().coerceIn(0,photo.width)
                            val x1=ceil((width-left)/scale).toInt().coerceIn(0,photo.width)
                            val y0=floor((max(y.toFloat(),0f)-top)/scale).toInt().minus(1).coerceIn(0,photo.height)
                            val y1=ceil((min(bottomY.toFloat(),split)-top)/scale).toInt().plus(1).coerceIn(0,photo.height)
                            if(y < split && x1>x0 && y1>y0) {
                                val corners=listOf(source(x0,y0),source(x1-1,y0),source(x0,y1-1),source(x1-1,y1-1))
                                // WebP decoders may round the top-left crop to an even pixel.
                                val rawLeft=corners.minOf {it.first} and -2
                                val rawTop=corners.minOf {it.second} and -2
                                val rawRight=min(decoder.width,(corners.maxOf {it.first}+2) and -2)
                                val rawBottom=min(decoder.height,(corners.maxOf {it.second}+2) and -2)
                                val rect=Rect(rawLeft,rawTop,rawRight,rawBottom)
                                val bounds=listOf(orientedPoint(rawLeft,rawTop),orientedPoint(rawRight-1,rawTop),orientedPoint(rawLeft,rawBottom-1),orientedPoint(rawRight-1,rawBottom-1))
                                val ox0=bounds.minOf {it.first};val oy0=bounds.minOf {it.second}
                                val ox1=bounds.maxOf {it.first}+1;val oy1=bounds.maxOf {it.second}+1
                                var raw:Bitmap?=null;var oriented:Bitmap?=null
                                try {
                                    raw=decoder.decodeRegion(rect,BitmapFactory.Options().apply {inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888}) ?: error("照片分块读取失败。")
                                    oriented=PhotoRepository.orient(raw,photo.orientation)
                                    check(oriented.width==ox1-ox0 && oriented.height==oy1-oy0) {"照片方向校正失败：${oriented.width}×${oriented.height}，预期 ${ox1-ox0}×${oy1-oy0}，方向 ${photo.orientation}。"}
                                    val save=canvas.save();canvas.clipRect(0f,0f,width.toFloat(),split)
                                    canvas.drawBitmap(oriented,null,RectF(left+ox0*scale,top+oy0*scale,left+ox1*scale,top+oy1*scale),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                    canvas.restoreToCount(save)
                                } finally {oriented?.recycle();if(raw!==oriented) raw?.recycle()}
                            }
                            png.writeBitmap(tile)
                        } finally {tile.recycle()}
                        y=bottomY;onProgress(y.toFloat()/height)
                    }
                }
                stream.fd.sync()
            }
        } catch(t:Throwable) {file.delete();throw t} finally {decoder.recycle()}
    }
}
