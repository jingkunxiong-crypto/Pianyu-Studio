package com.pianyu.studio.creative

import android.graphics.*
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pianyu.studio.watermark.photo.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

@RunWith(AndroidJUnit4::class)
class CreativeExportBenchmark {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun jpegMatchesPreviewAcrossExifOrientations() = runBlocking {
        val folder=File(context.cacheDir,"creative-orientation-check").apply {mkdirs()}
        val source=Bitmap.createBitmap(401,317,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(source);val paint=Paint()
        listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed { i,color ->
            paint.color=color
            canvas.drawRect((i%2)*201f,(i/2)*159f,(i%2+1)*201f,(i/2+1)*159f,paint)
        }
        val input=File(folder,"source.png")
        input.outputStream().use {assertTrue(source.compress(Bitmap.CompressFormat.PNG,100,it))}
        try {
            for(orientation in 1..8) {
                val oriented=PhotoRepository.orient(source,orientation)
                val w=oriented.width;val h=oriented.height
                val path=Path().apply {addOval(RectF(w*.18f,h*.18f,w*.82f,h*.82f),Path.Direction.CW)}
                val expected=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                drawEffect(Canvas(expected),oriented,path)
                val file=File(folder,"result.jpg")
                CreativeJpegExporter.render(ImportedPhoto(input,"orientation",w,h,source,orientation),path,file)
                val actual=BitmapFactory.decodeFile(file.path)
                assertEquals(w,actual.width);assertEquals(h,actual.height)
                var difference=0L
                val a=IntArray(w);val b=IntArray(w)
                for(y in 0 until h) {
                    expected.getPixels(a,0,w,0,y,w,1);actual.getPixels(b,0,w,0,y,w,1)
                    for(x in 0 until w) for(shift in listOf(0,8,16)) difference+=kotlin.math.abs((a[x] shr shift and 255)-(b[x] shr shift and 255))
                }
                val mean=difference.toDouble()/(w*h*3)
                assertTrue("orientation=$orientation average JPEG error=$mean",mean<10.0)
                actual.recycle();expected.recycle();if(oriented!==source) oriented.recycle()
            }
        } finally {source.recycle();folder.listFiles()?.forEach {it.delete()};folder.delete()}
    }

    @Test fun benchmarkOriginalSizeExports() = runBlocking {
        val folder=File(context.cacheDir,"creative-benchmark").apply {mkdirs()}
        try {
            for((w,h) in listOf(4000 to 3000,8192 to 6144)) {
                val input=File(folder,"input.jpg")
                // Deterministic, high-detail synthetic image. No private photos are accessed.
                val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                val row=IntArray(w);val random=Random(42)
                for(y in 0 until h) {
                    for(x in 0 until w) row[x]=if(y<h/8) Color.rgb(200,70,40) else (0xff000000.toInt() or random.nextInt(0x1000000))
                    bitmap.setPixels(row,0,w,0,y,w,1)
                }
                input.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,95,it))}
                bitmap.recycle()
                val tiny=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                val photo=ImportedPhoto(input,"benchmark",w,h,tiny,1)
                val mask=Path().apply {addRect(w*.25f,0f,w*.75f,h*.75f,Path.Direction.CW)}
                for(format in listOf("JPEG80","PNG")) {
                    val file=File(folder,if(format=="PNG") "out.png" else "out.jpg")
                    val started=SystemClock.elapsedRealtime()
                    val timings=if(format=="PNG") CreativePngExporter.render(photo,mask,file) else CreativeJpegExporter.render(photo,mask,file)
                    val total=SystemClock.elapsedRealtime()-started
                    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(file.path,bounds)
                    assertEquals(w,bounds.outWidth);assertEquals(h,bounds.outHeight)
                    val decoder=BitmapRegionDecoder.newInstance(file.path,false)
                    try {
                        val sample=decoder.decodeRegion(Rect(0,0,w,8),BitmapFactory.Options())!!
                        val gray=sample.getPixel(32,4);val colored=sample.getPixel(w/2,4)
                        assertTrue(kotlin.math.abs(Color.red(gray)-Color.green(gray))<=3)
                        assertTrue(kotlin.math.abs(Color.blue(gray)-Color.green(gray))<=3)
                        assertTrue(Color.red(colored)-Color.blue(colored)>100)
                        sample.recycle()
                    } finally {decoder.recycle()}
                    // Measure the same storage volume without inserting test images into the user's gallery/history.
                    val copied=File(folder,"album-copy")
                    val copyAt=SystemClock.elapsedRealtime()
                    file.inputStream().use {src -> copied.outputStream().use {dst -> src.copyTo(dst);dst.fd.sync()}}
                    val copyMs=SystemClock.elapsedRealtime()-copyAt
                    Log.i("CreativeBenchmark","${w}x${h} $format inputBytes=${input.length()} outputBytes=${file.length()} totalMs=$total decodeMs=${timings.decodeMs} effectMs=${timings.effectMs} encodeMs=${timings.encodeMs} diskCopyMs=$copyMs heapMax=${Runtime.getRuntime().maxMemory()}")
                    copied.delete();file.delete()
                }
                tiny.recycle();input.delete()
            }
        } finally {folder.listFiles()?.forEach {it.delete()};folder.delete()}
    }
}
