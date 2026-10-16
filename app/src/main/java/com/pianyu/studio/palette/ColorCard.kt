package com.pianyu.studio.palette

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pianyu.studio.ui.*
import com.pianyu.studio.watermark.signature.SignatureViewModel
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.pianyu.studio.media.RecentWorks
import com.pianyu.studio.watermark.photo.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.Locale
import kotlin.math.*

/** Deterministic weighted RGB clustering over a bounded sample, ignoring transparent pixels. */
internal fun extractColors(bitmap: Bitmap, count: Int): List<Int> {
    val bins = linkedMapOf<Int, Int>()
    val step = max(1, sqrt(bitmap.width.toDouble() * bitmap.height / 18000).toInt())
    for(y in 0 until bitmap.height step step) for(x in 0 until bitmap.width step step) {
        val p=bitmap.getPixel(x,y)
        if(android.graphics.Color.alpha(p)<128) continue
        val key=((p shr 19 and 31) shl 10) or ((p shr 11 and 31) shl 5) or (p shr 3 and 31)
        bins[key]=(bins[key] ?: 0)+1
    }
    require(bins.isNotEmpty()) { "图片完全透明，无法提取颜色。" }
    val points=bins.keys.map { doubleArrayOf(((it shr 10 and 31)*8+4).toDouble(), ((it shr 5 and 31)*8+4).toDouble(), ((it and 31)*8+4).toDouble()) }
    val weights=bins.values.toList()
    fun distance(a: DoubleArray,b: DoubleArray)=a.indices.sumOf { (a[it]-b[it]).pow(2) }
    val centers=mutableListOf(points[weights.indices.maxBy { weights[it] }].copyOf())
    repeat(min(count,points.size)-1) {
        centers.add(points[points.indices.maxBy { i -> centers.minOf { distance(points[i],it) } * sqrt(weights[i].toDouble()) }].copyOf())
    }
    repeat(12) {
        val sums=Array(centers.size) { DoubleArray(4) }
        points.forEachIndexed { i,p ->
            val j=centers.indices.minBy { distance(p,centers[it]) }
            for(c in 0..2) sums[j][c]+=p[c]*weights[i]
            sums[j][3]+=weights[i]
        }
        centers.indices.forEach { j -> if(sums[j][3]>0) for(c in 0..2) centers[j][c]=sums[j][c]/sums[j][3] }
    }
    return centers.map { android.graphics.Color.rgb(it[0].roundToInt(),it[1].roundToInt(),it[2].roundToInt()) }
        .distinct().sortedBy { .2126*android.graphics.Color.red(it)+.7152*android.graphics.Color.green(it)+.0722*android.graphics.Color.blue(it) }
}

private val cardRatios = linkedMapOf("自适应" to 0f, "1:1" to 1f, "3:4" to .75f, "4:5" to .8f, "4:3" to 4f/3f, "16:9" to 16f/9f)

private fun renderCard(photo: Bitmap, colors: List<Int>, ratio: Float, signature: Bitmap?): Bitmap {
    val width = 1600
    val height = if (ratio > 0) (width / ratio).roundToInt() else
        (1504f * photo.height / photo.width).roundToInt().coerceIn(400, 2100) + 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(248,249,246))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val photoHeight = height - 360f
    val scale = min(1504f / photo.width, photoHeight / photo.height)
    val w = photo.width * scale; val h = photo.height * scale
    canvas.drawBitmap(photo, null, RectF((width-w)/2,48+(photoHeight-h)/2,(width+w)/2,48+(photoHeight+h)/2),paint)
    val paletteLeft = if (signature == null) 48f else 650f
    val cell = (1552f-paletteLeft)/colors.size
    colors.forEachIndexed { i, color ->
        val cx = paletteLeft + (i+.5f)*cell
        paint.color = color
        canvas.drawCircle(cx, height-185f, min(62f,cell*.32f),paint)
        paint.color = android.graphics.Color.rgb(42,56,58)
        paint.typeface = Typeface.MONOSPACE; paint.textSize = min(30f,cell/4.6f)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.US,"#%06X",color and 0xFFFFFF),cx,height-85f,paint)
    }
    signature?.let {
        val scaleSign = min(500f/it.width,160f/it.height)
        val sw = it.width*scaleSign; val sh = it.height*scaleSign
        paint.colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.rgb(42,56,58),android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(it,null,RectF(64f,height-190f-sh/2,64f+sw,height-190f+sh/2),paint)
        paint.colorFilter = null
    }
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL); paint.textSize = 20f
    paint.color = android.graphics.Color.rgb(97,116,113)
    canvas.drawText("PIANYU STUDIO / COLOR STUDY",48f,height-28f,paint)
    return bitmap
}

@Composable
fun ColorCardScreen(onBack: () -> Unit, signatures: SignatureViewModel) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val signatureState by signatures.state.collectAsState()
    var ratioName by rememberSaveable { mutableStateOf("自适应") }
    var includeSignature by rememberSaveable { mutableStateOf(false) }
    var choosingSignature by remember { mutableStateOf(false) }
    val selectedInk = if(includeSignature) signatureState.selectedBitmap else null
    val signatureReady = !includeSignature || (selectedInk != null && !signatureState.busy)
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableIntStateOf(5) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<SavedPhoto?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if(it!=null) uri=it.toString() }
    LaunchedEffect(uri,count,ratioName,includeSignature,selectedInk,signatureState.busy) {
        val input=uri ?: return@LaunchedEffect
        busy=true; saved=null; card=null
        if (!signatureReady) { busy=false; return@LaunchedEffect }
        try {
            card=withContext(Dispatchers.IO) {
                val photo=PhotoRepository(File(context.cacheDir,"color-card")).import(context.contentResolver,Uri.parse(input))
                try { renderCard(photo.preview,extractColors(photo.preview,count),cardRatios.getValue(ratioName),selectedInk) }
                finally { photo.preview.recycle(); photo.file.delete() }
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "生成色卡失败" }
        finally { busy=false }
    }
    fun save() {
        val output=card ?: return
        if(saving) return
        saving=true
        scope.launch {
            try {
                saved=withContext(Dispatchers.IO) {
                    val file=File(context.cacheDir,"exports/Pianyu_Color_${UUID.randomUUID()}.png")
                    file.parentFile?.mkdirs()
                    try {
                        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG,100,it)) }
                        PhotoExporter(context).saveToAlbum(ExportedPhoto(file,output.width,output.height,PhotoFormat.PNG),"色彩取样卡").also {
                            RecentWorks.add(context,it.uri,it.name,"色彩取样卡")
                        }
                    } finally { file.delete() }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error=e.message ?: "保存失败" }
            finally { saving=false }
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it) save() else error="保存需要相册存储权限。"
    }
    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            StudioToolbar("色彩取样卡",onBack)
            Text("把照片的颜色留下来",style=MaterialTheme.typography.headlineSmall)
            Text("自动提取代表色，按明暗排列。色块与 HEX 色号随作品一起导出。",color=StudioPalette.Muted)
            StudioAction(if(uri==null) "选择照片" else "更换照片",Modifier.fillMaxWidth(),primary=true,enabled=!busy&&!saving,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (4..6).forEach { n -> FilterChip(selected=count==n,onClick={count=n},enabled=!busy&&!saving,label={Text("$n 色")}) }
            }
            Text("卡片比例",style=MaterialTheme.typography.titleSmall)
            cardRatios.keys.toList().chunked(3).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { label -> FilterChip(selected=ratioName==label,onClick={ratioName=label},enabled=!saving,label={Text(label)}) }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("加上签名",style=MaterialTheme.typography.titleMedium)
                    Text("左下署名 · 右下圆形色卡",style=MaterialTheme.typography.bodySmall,color=StudioPalette.Muted)
                }
                Switch(checked=includeSignature,enabled=!saving,onCheckedChange={
                    includeSignature=it
                    if(it && signatureState.selectedBitmap==null) choosingSignature=true
                })
            }
            if(includeSignature) {
                StudioAction(signatureState.selected?.name ?: "从签名库选择",Modifier.fillMaxWidth(),enabled=!saving&&!signatureState.busy,onClick={choosingSignature=true})
                Text("与签名水印共用签名库，色卡中统一使用深色墨迹。",style=MaterialTheme.typography.bodySmall,color=StudioPalette.Muted)
                if(signatureState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(!signatureReady) Text("请选择签名后生成色卡。",color=StudioPalette.Muted)
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            card?.let { output ->
                Image(output.asImageBitmap(),"照片与代表色色卡预览",Modifier.fillMaxWidth().aspectRatio(output.width.toFloat()/output.height))
                Text("PNG · ${output.width} × ${output.height} · 接近纯色的照片可能提取到较少颜色",style=MaterialTheme.typography.bodySmall,color=StudioPalette.Muted)
                StudioAction(if(saving) "正在保存…" else "保存色卡",Modifier.fillMaxWidth(),primary=true,enabled=!saving&&!busy,onClick={
                    if(Build.VERSION.SDK_INT<29 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else save()
                })
            }
            saved?.let { result ->
                Text("已保存到相册",color=StudioPalette.Accent)
                StudioAction("分享色卡",Modifier.fillMaxWidth(),onClick={runCatching { RecentWorks.share(context,result.uri) }.onFailure { error="无法打开分享应用。" }})
            }
        }
    }
    if(choosingSignature) AlertDialog(onDismissRequest={choosingSignature=false},title={Text("签名库")},text={
        if(signatureState.assets.isEmpty()) Text("暂无签名，请先在签名水印的签名库中添加。")
        else LazyColumn(Modifier.heightIn(max=360.dp)) {
            items(signatureState.assets,key={it.id}) { asset ->
                TextButton(enabled=!signatureState.busy,onClick={
                    signatures.select(asset) { choosingSignature=false }
                }) { Text(if(asset.id==signatureState.selected?.id) "✓ ${asset.name}" else asset.name) }
            }
        }
    },confirmButton={TextButton(onClick={choosingSignature=false}) {Text("关闭")}})
    error?.let { message -> AlertDialog(onDismissRequest={error=null},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("知道了")}}) }
}
