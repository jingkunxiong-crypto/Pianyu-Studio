package com.pianyu.studio.creative

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pianyu.studio.ui.*
import com.pianyu.studio.R
import com.pianyu.studio.watermark.photo.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

/** Apply clockwise quarter turns after the source EXIF transform. */
internal fun rotatedExifOrientation(orientation:Int, turns:Int):Int {
    val clockwise=intArrayOf(0,6,7,8,5,2,3,4,1)
    var result=orientation.takeIf { it in 1..8 } ?: 1
    repeat(((turns%4)+4)%4) { result=clockwise[result] }
    return result
}

private data class CameraInfo(val model:String, val focal:String, val aperture:String)
private fun readInfo(file:File):CameraInfo = try {
    val e=ExifInterface(file.path)
    val model=""
    fun number(tag:String):Double? = e.getAttributeDouble(tag,0.0).takeIf { it.isFinite() && it>0 }
    fun fmt(v:Double)=if(abs(v-round(v))<.01) "%.0f".format(Locale.US,v) else "%.1f".format(Locale.US,v)
    CameraInfo(model,number(ExifInterface.TAG_FOCAL_LENGTH)?.let { "${fmt(it)}mm" }.orEmpty(),number(ExifInterface.TAG_F_NUMBER)?.let { "f/${fmt(it)}" }.orEmpty())
} catch (_:Exception) { CameraInfo("","","") }

private fun drawPhoto(c:NativeCanvas, photo:Bitmap, dest:RectF, crop:Boolean, paint:Paint, offsetX:Float=0f, offsetY:Float=0f) {
    val scale=if(crop) max(dest.width()/photo.width,dest.height()/photo.height) else min(dest.width()/photo.width,dest.height()/photo.height)
    val w=photo.width*scale; val h=photo.height*scale
    val dx=if(crop) offsetX * (w-dest.width()).coerceAtLeast(0f)/2 else 0f
    val dy=if(crop) offsetY * (h-dest.height()).coerceAtLeast(0f)/2 else 0f
    val check=c.save();c.clipRect(dest)
    c.drawBitmap(photo,null,RectF(dest.centerX()-w/2+dx,dest.centerY()-h/2+dy,dest.centerX()+w/2+dx,dest.centerY()+h/2+dy),paint)
    c.restoreToCount(check)
}

private fun cardHeight(width:Int,photo:Bitmap,ratio:Float):Int = if(ratio>0) (width/ratio).roundToInt() else (width*(photo.height.toFloat()/photo.width+.64f)).roundToInt()

/** Same drawing is used for preview and output, in normalized coordinates. */
internal fun drawCameraCard(c:NativeCanvas,photo:Bitmap,w:Float,h:Float,ratio:Float,crop:Boolean,blur:Float,camera:Bitmap,info:String,offsetX:Float,offsetY:Float) {
    val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    c.drawColor(NativeColor.rgb(230,235,232))
    val bottom=if(ratio==0f) w*.64f else h*.50f
    val split=h-bottom
    drawPhoto(c,photo,RectF(0f,0f,w,split),crop,p,offsetX,offsetY)
    // Average the photo across its width and soften adjacent rows before stretching.
    val sample=Bitmap.createScaledBitmap(photo,32,96,true)
    val pixels=IntArray(32*96);sample.getPixels(pixels,0,32,0,0,32,96)
    val stripPixels=IntArray(96)
    val radius=(18+blur*40).roundToInt()
    for(y in 0 until 96) {
        var red=0L;var green=0L;var blue=0L;var count=0
        for(row in max(0,y-radius)..min(95,y+radius)) for(x in 0 until 32) {
            val color=pixels[row*32+x];red+=NativeColor.red(color);green+=NativeColor.green(color);blue+=NativeColor.blue(color);count++
        }
        stripPixels[y]=NativeColor.rgb((red/count).toInt(),(green/count).toInt(),(blue/count).toInt())
    }
    if(sample !== photo) sample.recycle()
    val strip=Bitmap.createBitmap(stripPixels,1,96,Bitmap.Config.ARGB_8888)
    c.drawBitmap(strip,null,RectF(0f,split,w,h),p);strip.recycle()
    p.color=NativeColor.argb(65,15,34,37);c.drawRect(0f,split,w,h,p)
    val cw=w*.78f; val ch=cw*camera.height/camera.width
    val left=(w-cw)/2; val top=split+bottom*.06f
    p.color=NativeColor.WHITE
    c.drawBitmap(camera,null,RectF(left,top,left+cw,top+ch),p)
    val screen=RectF(left+cw*.226f,top+ch*.379f,left+cw*.639f,top+ch*.816f)
    p.color=NativeColor.BLACK;c.drawRect(screen,p)
    drawPhoto(c,photo,screen,crop,p,offsetX,offsetY)
    p.color=NativeColor.rgb(245,248,246);p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL)
    p.textSize=min(w*.035f,bottom*.10f);c.drawText(info.take(100),w/2,h-bottom*.10f,p)
}

@Composable
fun CameraFrameScreen(uri:String,onBack:()->Unit,onSavingChanged:(Boolean)->Unit,onAgain:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var photo by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var model by rememberSaveable(uri) { mutableStateOf("") };var focal by rememberSaveable(uri) { mutableStateOf("") };var aperture by rememberSaveable(uri) { mutableStateOf("") }
    var ratio by rememberSaveable { mutableFloatStateOf(.75f) };var crop by rememberSaveable { mutableStateOf(true) };var blur by rememberSaveable { mutableFloatStateOf(.8f) };val camera=remember { BitmapFactory.decodeResource(context.resources,R.drawable.camera_back_frame) }
    var rotation by rememberSaveable(uri) { mutableIntStateOf(0) }
    val rotatedPhoto=remember(photo,rotation) {
        photo?.let { source -> if(rotation==0) source else Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply { postRotate(rotation*90f) },true) }
    }
    DisposableEffect(rotatedPhoto,photo) { onDispose { if(rotatedPhoto != null && rotatedPhoto !== photo) rotatedPhoto.recycle() } }
    var offsetX by rememberSaveable(uri) { mutableFloatStateOf(0f) };var offsetY by rememberSaveable(uri) { mutableFloatStateOf(0f) }
    var formatName by rememberSaveable { mutableStateOf(PhotoFormat.PNG.name) }
    val format=PhotoFormat.valueOf(formatName)
    var loading by remember { mutableStateOf(true) };var saving by remember { mutableStateOf(false) };var exportPage by remember { mutableStateOf(false) };var stage by remember { mutableStateOf("") };var progress by remember { mutableFloatStateOf(0f) };var saved by remember { mutableStateOf<SavedPhoto?>(null) };var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saving) { onSavingChanged(saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(uri) {
        try {
            val pair=withContext(Dispatchers.IO) {
                val imported=PhotoRepository(File(context.cacheDir,"frame-import")).import(context.contentResolver,Uri.parse(uri))
                try { imported.preview to readInfo(imported.file) } finally { imported.file.delete() }
            }
            photo=pair.first;model=pair.second.model;focal=pair.second.focal;aperture=pair.second.aperture
        } catch(e:CancellationException) { throw e } catch(e:Exception) { error=e.message ?: "无法读取照片" } finally {loading=false}
    }
    val info=listOf(model.trim(),focal.trim(),aperture.trim()).filter { it.isNotEmpty() }.joinToString("  ·  ")
    fun save() {
        if(saving || rotatedPhoto==null) return
        saving=true;exportPage=true;saved=null;progress=.05f;stage="正在读取原图"
        scope.launch {
            try {
                saved=withContext(Dispatchers.IO) {
                    val imported=PhotoRepository(File(context.cacheDir,"frame-export")).import(context.contentResolver,Uri.parse(uri))
                    val file=File(context.cacheDir,"exports/Pianyu_Camera_${UUID.randomUUID()}.${format.extension}")
                    var decoded:Bitmap?=null;var oriented:Bitmap?=null;var output:Bitmap?=null
                    try {
                        imported.preview.recycle()
                        val turned=imported.copy(width=if(rotation%2==0) imported.width else imported.height,
                            height=if(rotation%2==0) imported.height else imported.width,
                            orientation=rotatedExifOrientation(imported.orientation,rotation))
                        val width=turned.width;val height=if(ratio>0) (width/ratio).roundToInt() else (turned.height+width*.64f).roundToInt()
                        if(format==PhotoFormat.PNG) {
                            withContext(Dispatchers.Main) {progress=.1f;stage="正在分块生成无损画幅"}
                            CameraFramePngExporter.render(turned,rotatedPhoto!!,camera,ratio,crop,blur,info,offsetX,offsetY,file) { amount ->
                                withContext(Dispatchers.Main) {progress=.1f+amount*.78f;stage="正在分块生成无损画幅"}
                            }
                        } else {
                        val available=Runtime.getRuntime().let { it.maxMemory()-it.totalMemory()+it.freeMemory() }
                        val required=turned.width.toLong()*turned.height*4L*(if(turned.orientation in 2..8) 2 else 1)+width.toLong()*height*4L
                        require(required+32L*1024*1024 < available*.8) { "JPEG 原尺寸导出需要更多内存，请返回选择 PNG 无损分块保存。" }
                        withContext(Dispatchers.Main) {progress=.2f;stage="正在生成画幅"}
                        decoded=BitmapFactory.decodeFile(imported.file.path) ?: error("原图解码失败")
                        oriented=PhotoRepository.orient(decoded!!,turned.orientation)
                        if(oriented !== decoded) { decoded!!.recycle();decoded=null }
                        output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                        drawCameraCard(NativeCanvas(output!!),oriented!!,width.toFloat(),height.toFloat(),ratio,crop,blur,camera,info,offsetX,offsetY)
                        withContext(Dispatchers.Main) {progress=.65f;stage="正在编码照片"}
                        file.parentFile?.mkdirs();file.outputStream().use { check(output!!.compress(Bitmap.CompressFormat.JPEG,80,it)) {"照片编码失败"} }
                        }
                        withContext(Dispatchers.Main) {progress=.9f;stage="正在写入相册"}
                        PhotoExporter(context).saveToAlbum(ExportedPhoto(file,width,height,format),"创意编辑 · 相机画幅")
                    } finally { output?.recycle();oriented?.recycle();if(decoded !== oriented) decoded?.recycle(); imported.file.delete();file.delete() }
                }
                stage="保存成功";progress=1f
            } catch(e:CancellationException) {throw e} catch(e:OutOfMemoryError) {stage="保存失败";error="设备内存不足，请返回选择 PNG 分块保存或关闭其他应用后重试。"} catch(e:Exception) {stage="保存失败";error=e.message ?: "保存失败"} finally {saving=false}
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) save() else error="保存需要相册存储权限。" }
    BackHandler { if(!saving) { if(exportPage && saved==null) exportPage=false else onBack() } }
    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        if(exportPage) Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(stage,style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(24.dp))
            if(saving) { CircularProgressIndicator(progress={progress},modifier=Modifier.size(88.dp));Spacer(Modifier.height(12.dp));Text("${(progress*100).toInt()}%") }
            else if(saved!=null) {Text("已保存到相册 · ${saved!!.width} × ${saved!!.height}",color=StudioPalette.Muted);Spacer(Modifier.height(24.dp));StudioAction("回到主页",Modifier.fillMaxWidth(),onClick=onBack);Spacer(Modifier.height(12.dp));StudioAction("再做一张",Modifier.fillMaxWidth(),primary=true,onClick=onAgain)}
            else StudioAction("返回编辑",Modifier.fillMaxWidth(),onClick={exportPage=false})
        } else Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal=20.dp)) {
            StudioToolbar("相机画幅",onBack,"保存",photo!=null&&!saving,onAction={if(Build.VERSION.SDK_INT<29 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()})
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical=8.dp),contentAlignment=Alignment.Center) {
                val source=rotatedPhoto
                if(source==null) { if(loading) CircularProgressIndicator() else Text("照片加载失败") }
                else BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val r=source.width.toFloat()/cardHeight(source.width,source,ratio);val width=minOf(maxWidth,maxHeight*r)
                    Canvas(Modifier.width(width).height(width/r).pointerInput(crop, source, ratio) { detectDragGestures { change, drag ->
                        if(crop) { val split=if(ratio==0f) size.height-size.width*.64f else size.height*.50f; val scale=max(size.width.toFloat()/source.width,split/source.height); val extraX=(source.width*scale-size.width).coerceAtLeast(1f); val extraY=(source.height*scale-split).coerceAtLeast(1f); offsetX=(offsetX+2*drag.x/extraX).coerceIn(-1f,1f);offsetY=(offsetY+2*drag.y/extraY).coerceIn(-1f,1f);change.consume() }
                    } }) { drawIntoCanvas { val native=it.nativeCanvas; val checkpoint=native.save(); native.clipRect(0f,0f,size.width,size.height); drawCameraCard(native,source,size.width,size.height,ratio,crop,blur,camera,info,offsetX,offsetY); native.restoreToCount(checkpoint) } }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max=310.dp).verticalScroll(rememberScrollState()).padding(bottom=12.dp)) {
                Text("填满画幅时可拖动照片调整构图",color=StudioPalette.Muted,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={rotation=(rotation+1)%4;offsetX=0f;offsetY=0f},enabled=rotatedPhoto!=null&&!saving) {Text("↻ 旋转照片 90°")}
                Text("画幅比例",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("自适应" to 0f,"3:4" to .75f,"4:5" to .8f,"9:16" to 9f/16f).forEach { (name,value) -> FilterChip(selected=ratio==value,onClick={ratio=value},label={Text(name)}) } }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { FilterChip(selected=!crop,onClick={crop=false},label={Text("完整显示")});FilterChip(selected=crop,onClick={crop=true},label={Text("填满画幅")}) }
                Text("背景柔和程度",style=MaterialTheme.typography.bodyMedium);StudioSlider(value=blur,onValueChange={blur=it})
                Text("拍摄信息 · 型号手动填写，留空则不显示",style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Muted)
                OutlinedTextField(model,{model=it},label={Text("相机型号")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedTextField(focal,{focal=it},label={Text("焦距，如 35mm")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(aperture,{aperture=it},label={Text("光圈，如 f/2.8")},singleLine=true,modifier=Modifier.weight(1f))}
                Text("保存格式",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=format==PhotoFormat.PNG,onClick={formatName=PhotoFormat.PNG.name},label={Text("PNG 无损 · 适合大图")})
                    FilterChip(selected=format==PhotoFormat.JPG,onClick={formatName=PhotoFormat.JPG.name},label={Text("JPEG 80%")})
                }
                Text(if(format==PhotoFormat.PNG) "PNG 无损分块保存，按照片宽度生成画幅。" else "JPEG 80% · 大照片可能需要选择 PNG。",color=StudioPalette.Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("知道了")}}) }
}
