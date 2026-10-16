package com.pianyu.studio.watermark.signature

import android.graphics.Bitmap
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt
import kotlin.math.roundToInt

data class ExtractionCrop(val left: Float=0f,val top: Float=0f,val right: Float=1f,val bottom: Float=1f)
data class ExtractionSettings(val crop: ExtractionCrop=ExtractionCrop(),val cutoff: Float=210f,
                              val lightInk: Boolean=false,val removeSpecks: Boolean=false,
                              val colorKey: Boolean=false,val automatic: Boolean=true)

data class BackgroundKey(val red: Int,val green: Int,val blue: Int,val samples: Int) {
    val light: Boolean get()=red*.2126f+green*.7152f+blue*.0722f>=127.5f
}

/** Local color-key and luminance extraction for contrasting ink on plain paper. No network or generated strokes. */
object SignatureExtractor {
    suspend fun detectBackground(source: Bitmap): BackgroundKey {
        val rs=mutableListOf<Int>();val gs=mutableListOf<Int>();val bs=mutableListOf<Int>()
        // Sample a thin perimeter band; median tolerates strokes touching a minority of the border.
        val step=maxOf(1,(source.width+source.height)/1024)
        fun sample(x: Int,y: Int) {
            val p=source.getPixel(x,y)
            if(p ushr 24>=200) {rs+=p shr 16 and 255;gs+=p shr 8 and 255;bs+=p and 255}
        }
        val band=maxOf(1,minOf(source.width,source.height)/100).coerceAtMost(8)
        for(offset in 0 until band) {
            coroutineContext.ensureActive()
            for(x in 0 until source.width step step) {sample(x,offset);sample(x,source.height-1-offset)}
            for(y in 0 until source.height step step) {sample(offset,y);sample(source.width-1-offset,y)}
        }
        if(rs.isEmpty()) return BackgroundKey(255,255,255,0)
        rs.sort();gs.sort();bs.sort()
        return BackgroundKey(rs[rs.size/2],gs[gs.size/2],bs[bs.size/2],rs.size)
    }
    private fun distance(r: Int,g: Int,b: Int,key: BackgroundKey): Float {
        val dr=(r-key.red).toFloat();val dg=(g-key.green).toFloat();val db=(b-key.blue).toFloat()
        return sqrt((dr*dr+dg*dg+db*db)/3f)
    }
    suspend fun extract(source: Bitmap, settings: ExtractionSettings): Bitmap? {
        val c=settings.crop
        require(c.left>=0 && c.top>=0 && c.right<=1 && c.bottom<=1 && c.right>c.left && c.bottom>c.top)
        val left=(c.left*source.width).toInt().coerceIn(0,source.width-1)
        val top=(c.top*source.height).toInt().coerceIn(0,source.height-1)
        val right=(c.right*source.width).toInt().coerceIn(left+1,source.width)
        val bottom=(c.bottom*source.height).toInt().coerceIn(top+1,source.height)
        val width=right-left;val height=bottom-top
        val pixels=IntArray(width*height)
        source.getPixels(pixels,0,width,left,top,width,height)
        val cutoff=settings.cutoff.coerceIn(60f,250f)
        val detected=if(settings.colorKey) detectBackground(source) else null
        val lightInk=if(settings.colorKey && settings.automatic) detected?.light==false else settings.lightInk
        val key=detected?.let {
            if(settings.automatic || it.light!=lightInk) it
            else if(lightInk) BackgroundKey(0,0,0,it.samples) else BackgroundKey(255,255,255,it.samples)
        }
        val foreground=if(lightInk) 255 else 0
        val fullDistance=key?.let {distance(foreground,foreground,foreground,it).coerceAtLeast(1f)} ?: 255f
        val tolerance=(250f-cutoff).coerceAtMost(fullDistance*.9f)
        for(y in 0 until height) {
            if(y%32==0) coroutineContext.ensureActive()
            for(x in 0 until width) {
                val i=y*width+x;val p=pixels[i]
                val r=p shr 16 and 255;val g=p shr 8 and 255;val b=p and 255
                val alpha=if(settings.colorKey && key!=null) {
                    if(key.samples==0) p ushr 24 // Already transparent border: keep existing alpha and colors.
                    else (((distance(r,g,b,key)-tolerance)/(fullDistance-tolerance)).coerceIn(0f,1f)*(p ushr 24)).roundToInt()
                } else {
                    var luma=r*.2126f+g*.7152f+b*.0722f
                    if(lightInk) luma=255-luma
                    (((cutoff-luma)/24f).coerceIn(0f,1f)*(p ushr 24)).toInt()
                }
                pixels[i]=(alpha shl 24) or (if(settings.colorKey && key?.samples==0) p and 0xFFFFFF else if(lightInk) 0xFFFFFF else 0)
            }
        }
        if(settings.removeSpecks) {
            val visited=BooleanArray(pixels.size);val queue=IntArray(pixels.size)
            for(start in pixels.indices) {
                if(start%4096==0) coroutineContext.ensureActive()
                if(visited[start] || pixels[start] ushr 24==0) continue
                var count=1;var next=0;queue[0]=start;visited[start]=true
                while(next<count) {
                    if(next%4096==0) coroutineContext.ensureActive()
                    val i=queue[next++];val x=i%width;val y=i/width
                    for(dy in -1..1) for(dx in -1..1) {
                        val xx=x+dx;val yy=y+dy
                        if(xx !in 0 until width || yy !in 0 until height) continue
                        val j=yy*width+xx
                        if(!visited[j] && pixels[j] ushr 24!=0) {visited[j]=true;queue[count++]=j}
                    }
                }
                if(count<=4) for(i in 0 until count) pixels[queue[i]]=0
            }
        }
        coroutineContext.ensureActive()
        if(pixels.none {it ushr 24!=0}) return null
        val result=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
        return try {InkRenderer.cropTransparent(result)} finally {result.recycle()}
    }
}
