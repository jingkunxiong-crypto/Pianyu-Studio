package com.pianyu.studio.creative

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pianyu.studio.ui.*
import com.pianyu.studio.watermark.photo.*
import com.pianyu.studio.media.RecentWorks
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.math.*

private enum class WindowShape(val label:String) {
    Rectangle("矩形"), Circle("圆形"), Rounded("圆角矩形"), Star("星形"), Heart("心形"), Diamond("菱形")
}

/** Coordinates are relative to the oriented photograph, shared by preview and export. */
private fun windowPath(w:Float,h:Float,x:Float,y:Float,scale:Float,aspect:Float,round:Float,shape:WindowShape):Path {
    val base=min(w,h)*scale
    val rw=if(shape==WindowShape.Rectangle || shape==WindowShape.Rounded) base*sqrt(aspect) else base
    val rh=if(shape==WindowShape.Rectangle || shape==WindowShape.Rounded) base/sqrt(aspect) else base
    val cx=w*x; val cy=h*y
    val rect=RectF(cx-rw/2,cy-rh/2,cx+rw/2,cy+rh/2)
    return Path().apply {
        when(shape) {
            WindowShape.Rectangle -> addRect(rect,Path.Direction.CW)
            WindowShape.Circle -> addOval(rect,Path.Direction.CW)
            WindowShape.Rounded -> addRoundRect(rect,min(rw,rh)*round/2,min(rw,rh)*round/2,Path.Direction.CW)
            WindowShape.Star -> {
                repeat(10) { i ->
                    val angle=-Math.PI/2+i*Math.PI/5
                    val r=base/2*(if(i%2==0) 1f else .45f)
                    val px=cx+cos(angle).toFloat()*r; val py=cy+sin(angle).toFloat()*r
                    if(i==0) moveTo(px,py) else lineTo(px,py)
                };close()
            }
            WindowShape.Diamond -> {moveTo(cx,rect.top);lineTo(rect.right,cy);lineTo(cx,rect.bottom);lineTo(rect.left,cy);close()}
            WindowShape.Heart -> {
                moveTo(cx,rect.bottom)
                cubicTo(rect.left-rw*.15f,cy,rect.left,rect.top-rh*.2f,cx,rect.top+rh*.22f)
                cubicTo(rect.right,rect.top-rh*.2f,rect.right+rw*.15f,cy,cx,rect.bottom);close()
            }
        }
    }
}

internal fun drawEffect(canvas:android.graphics.Canvas,photo:Bitmap,path:Path,original:Boolean=false) {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if(!original) paint.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(0f)})
    canvas.drawBitmap(photo,0f,0f,paint)
    if(!original) {
        val checkpoint=canvas.save()
        canvas.clipPath(path)
        paint.colorFilter=null
        canvas.drawBitmap(photo,0f,0f,paint)
        canvas.restoreToCount(checkpoint)
    }
}

@Composable
fun CreativeEditScreen(uri:String,onBack:()->Unit,onSign:(Uri)->Unit) {
    var selectedUri by rememberSaveable(uri) {mutableStateOf(uri)}
    var session by rememberSaveable {mutableIntStateOf(0)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if(it!=null) {selectedUri=it.toString();session++}
    }
    var mode by rememberSaveable { mutableStateOf("局部留彩") }
    var modeLocked by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal=20.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("局部留彩", "相机画幅").forEach { item ->
                FilterChip(selected=mode==item, onClick={mode=item}, enabled=!modeLocked, label={Text(item)}, modifier=Modifier.weight(1f))
            }
        }
        key(session,mode) {
            if(mode=="相机画幅") CameraFrameScreen(selectedUri,onBack,onSavingChanged={modeLocked=it},onAgain={
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) else CreativeEditor(selectedUri,onBack,onSign,onAgain={
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    }
}

@Composable
private fun CreativeEditor(uri:String,onBack:()->Unit,onSign:(Uri)->Unit,onAgain:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var photo by remember(uri) {mutableStateOf<Bitmap?>(null)}
    var loading by remember {mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var saved by remember {mutableStateOf<SavedPhoto?>(null)}
    var exportPage by remember {mutableStateOf(false)}
    var progress by remember {mutableFloatStateOf(0f)}
    var stage by remember {mutableStateOf("正在读取原图")}
    var x by rememberSaveable(uri) {mutableFloatStateOf(.5f)}
    var y by rememberSaveable(uri) {mutableFloatStateOf(.5f)}
    var scale by rememberSaveable(uri) {mutableFloatStateOf(.65f)}
    var aspect by rememberSaveable(uri) {mutableFloatStateOf(.75f)}
    var round by rememberSaveable(uri) {mutableFloatStateOf(.25f)}
    var shapeName by rememberSaveable(uri) {mutableStateOf(WindowShape.Rectangle.name)}
    var original by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    val shape=WindowShape.valueOf(shapeName)
    fun changed() {saved=null;original=false}
    fun back() {if(!saving) {if(photo!=null && saved==null) discard=true else onBack()}}
    BackHandler {if(exportPage) {if(!saving) {if(saved!=null) onBack() else exportPage=false}} else back()}
    LaunchedEffect(uri) {
        loading=true
        try {
            photo=withContext(Dispatchers.IO) {
                val imported=PhotoRepository(File(context.cacheDir,"creative-import")).import(context.contentResolver,Uri.parse(uri))
                imported.file.delete()
                imported.preview
            }
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {error=e.message ?: "照片读取失败"}
        finally {loading=false}
    }
    fun save() {
        val source=photo ?: return
        if(saving) return
        val path=windowPath(source.width.toFloat(),source.height.toFloat(),x,y,scale,aspect,round,shape)
        saving=true
        exportPage=true;progress=0f;saved=null;stage="正在读取原图"
        scope.launch {
            try {
                saved=withContext(Dispatchers.IO) {
                    val imported=PhotoRepository(File(context.cacheDir,"creative-export")).import(context.contentResolver,Uri.parse(uri))
                    imported.preview.recycle()
                    withContext(Dispatchers.Main) {progress=.05f;stage="正在读取原分辨率照片"}
                    val file=File(context.cacheDir,"exports/Pianyu_Creative_${UUID.randomUUID()}.jpg")
                    try {
                        val fullPath=Path(path).apply {
                            transform(Matrix().apply {setScale(imported.width.toFloat()/source.width,imported.height.toFloat()/source.height)})
                        }
                        CreativeJpegExporter.render(imported,fullPath,file) { amount,description ->
                            withContext(Dispatchers.Main) {progress=amount;stage=description}
                        }
                        withContext(Dispatchers.Main) {progress=.9f;stage="正在写入相册"}
                        PhotoExporter(context).saveToAlbum(ExportedPhoto(file,imported.width,imported.height,PhotoFormat.JPG),"创意编辑 · 局部留彩").also {
                            RecentWorks.add(context,it.uri,it.name,"创意编辑 · 局部留彩")
                        }
                    } finally {
                        imported.file.delete();file.delete()
                    }
                }
                progress=1f;stage="保存成功"
            } catch(e:CancellationException) {throw e}
            catch(e:OutOfMemoryError) {stage="保存失败";error="内存不足，无法按原分辨率保存。请关闭其他应用后重试，未降低画质。"}
            catch(e:Exception) {stage="保存失败";error=e.message ?: "保存失败"}
            finally {saving=false}
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it) save() else error="保存需要相册存储权限。"
    }
    if(exportPage) {
        Box(Modifier.fillMaxSize()) {
            StudioBackground()
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text(stage,style=MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                if(saving) {
                    CircularProgressIndicator(progress={progress},modifier=Modifier.size(88.dp),strokeWidth=6.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("${(progress*100).toInt()}%",style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("原图分辨率 · JPEG 质量 80%",color=StudioPalette.Muted)
                    Text("大照片需要一些时间，请保持页面打开。",color=StudioPalette.Muted,style=MaterialTheme.typography.bodyMedium)
                } else if(saved!=null) {
                    Text("作品已保存到相册",color=StudioPalette.Muted)
                    Text("${saved!!.width} × ${saved!!.height} · JPEG 80%",color=StudioPalette.Muted)
                    Spacer(Modifier.height(32.dp))
                    StudioAction("回到主页",Modifier.fillMaxWidth(),onClick=onBack)
                    Spacer(Modifier.height(12.dp))
                    StudioAction("再做一张",Modifier.fillMaxWidth(),primary=true,onClick=onAgain)
                } else {
                    Text("编辑内容仍然保留，可以返回后重试。",color=StudioPalette.Muted)
                    Spacer(Modifier.height(24.dp))
                    StudioAction("返回编辑",Modifier.fillMaxWidth(),primary=true,onClick={exportPage=false})
                }
            }
        }
    } else Box(Modifier.fillMaxSize()) {
        StudioBackground()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp)) {
            StudioToolbar("创意编辑",::back,"保存",photo!=null&&!saving,backEnabled=!saving,onAction={
                if(Build.VERSION.SDK_INT<29 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
            })
            Text("局部留彩",style=MaterialTheme.typography.titleMedium)
            Text("拖动移动色彩区域 · 双指缩放",color=StudioPalette.Muted,style=MaterialTheme.typography.bodyMedium)
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical=12.dp),contentAlignment=Alignment.Center) {
                val source=photo
                if(source==null) {
                    if(loading) CircularProgressIndicator() else Text("未能加载照片，请返回重新选择")
                } else BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val ratio=source.width.toFloat()/source.height
                    val canvasWidth=minOf(maxWidth,maxHeight*ratio)
                    Canvas(Modifier.width(canvasWidth).height(canvasWidth/ratio)
                        .pointerInput(source,saving) {
                            detectTransformGestures {centroid,pan,zoom,_ ->
                                if(!saving) {
                                    val next=(scale*zoom).coerceIn(.08f,2f)
                                    val factor=next/scale
                                    x=((centroid.x+(x*size.width-centroid.x)*factor+pan.x)/size.width).coerceIn(0f,1f)
                                    y=((centroid.y+(y*size.height-centroid.y)*factor+pan.y)/size.height).coerceIn(0f,1f)
                                    scale=next;changed()
                                }
                            }
                        }) {
                        drawIntoCanvas { c ->
                            val native=c.nativeCanvas
                            val checkpoint=native.save()
                            native.scale(size.width/source.width,size.height/source.height)
                            drawEffect(native,source,windowPath(source.width.toFloat(),source.height.toFloat(),x,y,scale,aspect,round,shape),original)
                            native.restoreToCount(checkpoint)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max=300.dp).verticalScroll(rememberScrollState()).padding(bottom=12.dp)) {
                WindowShape.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        row.forEach { option -> FilterChip(selected=shape==option,onClick={shapeName=option.name;changed()},enabled=!saving,
                            label={Text(option.label)},modifier=Modifier.weight(1f)) }
                    }
                }
                Text("区域大小 ${(scale*100).roundToInt()}%",style=MaterialTheme.typography.bodyMedium)
                StudioSlider(value=scale,onValueChange={scale=it;changed()},valueRange=.08f..2f,enabled=!saving)
                if(shape==WindowShape.Rectangle || shape==WindowShape.Rounded) {
                    Text("区域宽高比 ${String.format(java.util.Locale.US,"%.2f",aspect)}",style=MaterialTheme.typography.bodyMedium)
                    StudioSlider(value=aspect,onValueChange={aspect=it;changed()},valueRange=.3f..3f,enabled=!saving)
                }
                if(shape==WindowShape.Rounded) {
                    Text("圆角 ${(round*100).roundToInt()}%",style=MaterialTheme.typography.bodyMedium)
                    StudioSlider(value=round,onValueChange={round=it;changed()},enabled=!saving)
                }
                Row {
                    TextButton(onClick={original=!original},enabled=!saving) {Text(if(original) "查看效果" else "对比原图")}
                    TextButton(onClick={x=.5f;y=.5f;scale=.65f;aspect=.75f;round=.25f;shapeName=WindowShape.Rectangle.name;changed()},enabled=!saving) {Text("重置区域")}
                }
                Text("按原图分辨率导出 JPEG，质量 80%，不缩小尺寸。",color=StudioPalette.Muted,style=MaterialTheme.typography.bodySmall)
                if(saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                saved?.let { result ->
                    Text("已保存 · ${result.width} × ${result.height}",color=StudioPalette.Accent)
                    StudioAction("继续加签名",Modifier.fillMaxWidth(),onClick={onSign(result.uri)})
                    TextButton(onClick={runCatching {RecentWorks.share(context,result.uri)}.onFailure {error="无法打开分享应用"}}) {Text("分享作品")}
                }
            }
        }
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("返回首页？")},text={Text("当前留彩效果还没有保存。")},
        confirmButton={TextButton(onClick={discard=false;onBack()}) {Text("放弃并返回")}},dismissButton={TextButton(onClick={discard=false}) {Text("继续编辑")}})
    error?.let { AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("知道了")}}) }
}
