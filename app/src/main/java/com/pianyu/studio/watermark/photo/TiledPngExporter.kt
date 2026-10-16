package com.pianyu.studio.watermark.photo

import android.graphics.*
import java.io.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** A PNG RGBA encoder with bounded row/chunk buffers; compression never discards pixels. */
internal class StreamingPngWriter(output: OutputStream,val width: Int,val height: Int): Closeable {
    private val out=DataOutputStream(output)
    private val pixels=IntArray(width)
    private val bytes=ByteArray(1+width*4)
    private val deflater=Deflater(1)
    private var rows=0
    init {
        require(width>0 && height>0)
        out.write(byteArrayOf(-119,80,78,71,13,10,26,10))
        val header=ByteArrayOutputStream()
        DataOutputStream(header).use {it.writeInt(width);it.writeInt(height);it.write(byteArrayOf(8,6,0,0,0))}
        chunk("IHDR",header.toByteArray())
    }
    private val chunks=object: OutputStream() {
        val buffer=ByteArray(64*1024);var count=0
        override fun write(b: Int) {buffer[count++]=b.toByte();if(count==buffer.size) flush()}
        override fun write(b: ByteArray,off: Int,len: Int) {
            var pos=off;var remaining=len
            while(remaining>0) {val n=minOf(remaining,buffer.size-count);b.copyInto(buffer,count,pos,pos+n);count+=n;pos+=n;remaining-=n;if(count==buffer.size) flush()}
        }
        override fun flush() {if(count>0) {chunk("IDAT",buffer,count);count=0}}
    }
    private val compressed=DeflaterOutputStream(chunks,deflater,64*1024)
    private fun chunk(type: String,data: ByteArray,length: Int=data.size) {
        val tag=type.toByteArray(Charsets.US_ASCII);val crc=CRC32()
        crc.update(tag);crc.update(data,0,length)
        out.writeInt(length);out.write(tag);out.write(data,0,length);out.writeInt(crc.value.toInt())
    }
    fun writeRow(row: IntArray) {
        require(rows<height && row.size>=width)
        bytes[0]=1 // PNG Sub filter, reversible: improves compression without changing pixels.
        var lastR=0;var lastG=0;var lastB=0;var lastA=0
        for(x in 0 until width) {
            val p=row[x];val r=p shr 16 and 255;val g=p shr 8 and 255;val b=p and 255;val a=p ushr 24
            val i=1+x*4;bytes[i]=(r-lastR).toByte();bytes[i+1]=(g-lastG).toByte();bytes[i+2]=(b-lastB).toByte();bytes[i+3]=(a-lastA).toByte()
            lastR=r;lastG=g;lastB=b;lastA=a
        }
        compressed.write(bytes);rows++
    }
    fun writeBitmap(bitmap: Bitmap) {
        require(bitmap.width==width)
        for(y in 0 until bitmap.height) {bitmap.getPixels(pixels,0,width,0,y,width,1);writeRow(pixels)}
    }
    override fun close() {
        try {require(rows==height) {"PNG 输出不完整。"};compressed.finish();chunks.flush();chunk("IEND",ByteArray(0));out.flush()}
        finally {deflater.end()}
    }
}

internal object TiledPngExporter {
    @Suppress("DEPRECATION")
    fun render(photo: ImportedPhoto,request: PhotoExportRequest,temporary: File) {
        val decoder=BitmapRegionDecoder.newInstance(photo.file.path,false) ?: error("这张照片不支持分块读取，请重新选择。")
        val mark=request.signature ?: createDemoSignatureBitmap()
        try {
            val w=photo.width;val h=photo.height
            val runtime=Runtime.getRuntime()
            val available=runtime.maxMemory()-runtime.totalMemory()+runtime.freeMemory()
            // Reserve space for decoder/orientation copies and the UI, even on small heaps.
            val tilePixels=(available/32L).coerceIn(w.toLong(),maxOf(w.toLong(),2_000_000L))
            val stripeHeight=minOf(512,maxOf(1,(tilePixels/w).toInt()))
            temporary.outputStream().use {file ->
                StreamingPngWriter(file,w,h).use {png ->
                    var y=0
                    while(y<h) {
                        if(Thread.currentThread().isInterrupted) throw InterruptedIOException("导出已中断。")
                        val bottom=minOf(h,y+stripeHeight)
                        fun source(x: Int,yy: Int): Pair<Int,Int> = when(photo.orientation) {
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
                        var decoded: Bitmap?=null;var oriented: Bitmap?=null;var writable: Bitmap?=null
                        try {
                            decoded=decoder.decodeRegion(rect,BitmapFactory.Options().apply {inSampleSize=1;inPreferredConfig=Bitmap.Config.ARGB_8888;inMutable=true;inScaled=false}) ?: error("照片分块解码失败。")
                            oriented=PhotoRepository.orient(decoded,photo.orientation)
                            writable=if(oriented.isMutable) oriented else oriented.copy(Bitmap.Config.ARGB_8888,true)
                            check(writable.width==w && writable.height==bottom-y) {"照片方向校正失败。"}
                            val canvas=Canvas(writable)
                            canvas.translate(0f,-y.toFloat())
                            WatermarkRenderer.draw(canvas,w.toFloat(),h.toFloat(),mark,request.pose,request.opacity,request.ink)
                            png.writeBitmap(writable)
                        } finally {
                            writable?.recycle()
                            if(oriented!==writable) oriented?.recycle()
                            if(decoded!==oriented && decoded!==writable) decoded?.recycle()
                        }
                        y=bottom
                    }
                }
                file.fd.sync()
            }
        } finally {decoder.recycle();if(request.signature==null) mark.recycle()}
    }
}
