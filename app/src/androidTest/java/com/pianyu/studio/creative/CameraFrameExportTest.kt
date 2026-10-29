package com.pianyu.studio.creative

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pianyu.studio.watermark.photo.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CameraFrameExportTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun pngMatchesMainPhotoForAllOrientations() = runBlocking {
        val dir=File(context.cacheDir,"camera-test").apply {mkdirs()}
        val source=Bitmap.createBitmap(509,317,Bitmap.Config.ARGB_8888)
        val paint=Paint();val canvas=Canvas(source)
        listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed { i,color -> paint.color=color;canvas.drawRect((i%2)*255f,(i/2)*159f,(i%2+1)*255f,(i/2+1)*159f,paint) }
        val file=File(dir,"source.png");file.outputStream().use {source.compress(Bitmap.CompressFormat.PNG,100,it)}
        val camera=Bitmap.createBitmap(200,126,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.DKGRAY)}
        try {
            for(orientation in 1..8) for(crop in listOf(true,false)) {
                val preview=PhotoRepository.orient(source,orientation)
                val w=preview.width;val h=preview.height;val output=File(dir,"result.png")
                val imported=ImportedPhoto(file,"test",w,h,preview,orientation)
                val outputHeight=(w/.75f).toInt()
                val expected=Bitmap.createBitmap(w,outputHeight,Bitmap.Config.ARGB_8888)
                drawCameraCard(Canvas(expected),preview,w.toFloat(),outputHeight.toFloat(),.75f,crop,.8f,camera,"",.4f,-.3f)
                CameraFramePngExporter.render(imported,preview,camera,.75f,crop,.8f,"",.4f,-.3f,output)
                val actual=BitmapFactory.decodeFile(output.path)
                assertEquals(w,actual.width);assertTrue(abs(outputHeight-actual.height)<=1)
                var delta=0L;var count=0
                for(y in 0 until minOf(actual.height,expected.height)/2 step 3) for(x in 0 until w step 3) {
                    val a=actual.getPixel(x,y);val b=expected.getPixel(x,y)
                    for(shift in listOf(0,8,16)) {delta+=abs((a shr shift and 255)-(b shr shift and 255));count++}
                }
                assertTrue("orientation=$orientation crop=$crop error=${delta.toDouble()/count}",delta.toDouble()/count<2.0)
                actual.recycle();expected.recycle();if(preview!==source) preview.recycle()
            }
        } finally {source.recycle();camera.recycle();dir.listFiles()?.forEach {it.delete()};dir.delete()}
    }
    @Test fun exportsFiftyMegapixelPhotoWithoutFullSizeBitmap() = runBlocking {
        val dir=File(context.cacheDir,"camera-large-test").apply {mkdirs()}
        val input=File(dir,"large.png");val output=File(dir,"output.png")
        val w=8192;val h=6144
        val row=IntArray(w) { x -> Color.rgb((x/32)%256,100,180) }
        val preview=Bitmap.createBitmap(512,384,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(100,100,180))}
        val camera=Bitmap.createBitmap(100,63,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.DKGRAY)}
        try {
            input.outputStream().use { stream -> StreamingPngWriter(stream,w,h).use { png -> repeat(h) { png.writeRow(row) } } }
            val imported=ImportedPhoto(input,"synthetic 50MP",w,h,preview,1)
            val start=android.os.SystemClock.elapsedRealtime()
            CameraFramePngExporter.render(imported,preview,camera,.75f,true,.8f,"",0f,0f,output)
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(output.path,bounds)
            assertEquals(w,bounds.outWidth);assertEquals(kotlin.math.round(w/.75f).toInt(),bounds.outHeight)
            assertTrue(output.length()>0)
            android.util.Log.i("CameraFrameTest","50MP PNG saved: ${bounds.outWidth}x${bounds.outHeight}; ${android.os.SystemClock.elapsedRealtime()-start} ms; ${output.length()} bytes")
            Unit
        } finally {preview.recycle();camera.recycle();dir.listFiles()?.forEach {it.delete()};dir.delete()}
    }
    @Test fun pngExportAcceptsWebpRegions() = runBlocking {
        val dir=File(context.cacheDir,"camera-webp-test").apply {mkdirs()}
        val source=Bitmap.createBitmap(1448,1086,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(60,120,180))}
        val camera=Bitmap.createBitmap(100,63,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.DKGRAY)}
        val file=File(dir,"source.webp");val output=File(dir,"out.png")
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.WEBP_LOSSY,90,it) }
            val photo=ImportedPhoto(file,"webp",source.width,source.height,source,1)
            CameraFramePngExporter.render(photo,source,camera,.75f,true,.8f,"",0f,0f,output)
            assertTrue(output.exists() && output.length()>0)
        } finally {source.recycle();camera.recycle();dir.listFiles()?.forEach {it.delete()};dir.delete()}
    }
    @Test fun rotationCombinesWithEveryExifOrientation() {
        val source=Bitmap.createBitmap(19,13,Bitmap.Config.ARGB_8888)
        for(y in 0 until source.height) for(x in 0 until source.width) source.setPixel(x,y,Color.rgb(x*10,y*15,(x+y)*7))
        try {
            for(orientation in 1..8) for(turns in 0..3) {
                val exif=PhotoRepository.orient(source,orientation)
                val expected=if(turns==0) exif else Bitmap.createBitmap(exif,0,0,exif.width,exif.height,Matrix().apply {postRotate(turns*90f)},false)
                val actual=PhotoRepository.orient(source,rotatedExifOrientation(orientation,turns))
                assertEquals("width orientation=$orientation turns=$turns",expected.width,actual.width)
                assertEquals("height orientation=$orientation turns=$turns",expected.height,actual.height)
                for(y in 0 until expected.height) for(x in 0 until expected.width) assertEquals("pixel orientation=$orientation turns=$turns",expected.getPixel(x,y),actual.getPixel(x,y))
                if(expected!==exif && expected!==source) expected.recycle()
                if(exif!==source) exif.recycle()
                if(actual!==source && actual!==exif && actual!==expected) actual.recycle()
            }
        } finally {source.recycle()}
    }
}
