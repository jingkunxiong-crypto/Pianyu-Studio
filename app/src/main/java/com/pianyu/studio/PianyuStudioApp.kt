package com.pianyu.studio

import com.pianyu.studio.ui.*
import com.pianyu.studio.palette.ColorCardScreen
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pianyu.studio.media.RecentWorks
import com.pianyu.studio.collage.ui.GeyiApp
import com.pianyu.studio.creative.CreativeEditScreen
import com.pianyu.studio.watermark.ui.*
import com.pianyu.studio.watermark.ui.theme.*
import com.pianyu.studio.watermark.signature.*
import com.pianyu.studio.watermark.photo.*

@Composable
fun PianyuStudioApp(signatures: SignatureViewModel, photos: PhotoViewModel,
                    extraction: ExtractionViewModel, batch: BatchStudioViewModel) {
    var route by rememberSaveable { mutableStateOf("home") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editSession by rememberSaveable { mutableIntStateOf(0) }
    var signatureInput by rememberSaveable { mutableStateOf<String?>(null) }
    var homeRefresh by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    fun leaveFeature() { route = "home"; homeRefresh++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photoUri = uri.toString(); editSession++; route = "edit" }
    }
    fun sign(uri: Uri) { signatureInput = uri.toString(); route = "signature" }
    BackHandler(enabled = route != "home") { route = "home" }
    var opened by rememberSaveable { mutableStateOf(false) }
    val opening = remember { Animatable(if(opened) 1f else 0f) }
    LaunchedEffect(Unit) {
        if(!opened) { opening.animateTo(1f,tween(1000,easing=FastOutSlowInEasing)); opened=true }
    }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha=((opening.value-.25f)/.75f).coerceIn(0f,1f) }) {
    AnimatedContent(targetState=route, transitionSpec={
        val returning = targetState=="home"
        (fadeIn(tween(260,60)) + slideInHorizontally(tween(340,easing=FastOutSlowInEasing)) { if(returning) -it/16 else it/12 }) togetherWith
            (fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { if(returning) it/12 else -it/16 })
    },label="studio navigation") { page ->
    holder.SaveableStateProvider(page) {
        when (page) {
            "palette" -> ColorCardScreen(onBack = ::leaveFeature, signatures = signatures)
            "works" -> RecentWorksScreen(onBack=::leaveFeature,onSign=::sign)
            "signature" -> StudioApp(signatures, photos, extraction, batch,
                onExit = ::leaveFeature, inputUri = signatureInput,
                onInputConsumed = { signatureInput = null })
            "collage" -> GeyiApp(onExit = ::leaveFeature, onSign = ::sign)
            "edit" -> photoUri?.let { uri ->
                key(editSession) {
                    CreativeEditScreen(uri, onBack = ::leaveFeature, onSign = ::sign)
                }
            }
            else -> key(homeRefresh) { StudioHome(
                onEdit = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCollage = { route = "collage" }, onSignature = { route = "signature" }, onPalette = { route = "palette" }, onWorks = { route = "works" })
            }
        }
    }
    }
    }
    if(!opened) OpeningMark(opening.value)
    }
}

@Composable
private fun OpeningMark(progress:Float) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=22.dp,vertical=12.dp)) {
        val size = 96.dp - 58.dp*progress
        val x = (maxWidth-96.dp)/2*(1-progress)
        val y = (maxHeight-96.dp)/2*(1-progress)
        Image(painterResource(R.drawable.studio_icon),"Pianyu Studio",
            Modifier.offset(x,y).size(size).graphicsLayer { rotationZ=-8f*(1-progress) }.clip(RoundedCornerShape(20.dp)))
        Text("PIANYU STUDIO",Modifier.align(Alignment.Center).offset(y=80.dp).graphicsLayer {alpha=(1-progress*2).coerceIn(0f,1f)},
            color=StudioPalette.Accent,letterSpacing=3.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun StudioHome(onEdit: () -> Unit, onCollage: () -> Unit, onSignature: () -> Unit,
                       onPalette: () -> Unit, onWorks: () -> Unit) {
    val context = LocalContext.current
    val works = remember { RecentWorks.list(context) }
    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=22.dp,vertical=12.dp)) {
            val tileSize = minOf((maxWidth-12.dp)/2, maxHeight*.195f, 156.dp)
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.studio_icon),null,Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("PIANYU STUDIO",fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                        Text("留住灵感，慢慢完成。",style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Muted)
                    }
                }
                Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        HomeTool("01","创意编辑","黑白之间 · 留住色彩",Modifier.size(tileSize),onEdit)
                        HomeTool("02","自由拼图","组合你的瞬间",Modifier.size(tileSize),onCollage)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        HomeTool("03","签名水印","给作品留下署名",Modifier.size(tileSize),onSignature)
                        HomeTool("04","色彩取样卡","收集照片的颜色",Modifier.size(tileSize),onPalette)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable(onClick=onWorks),verticalAlignment=Alignment.CenterVertically) {
                    Text("最近作品",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                    Text("查看全部 ${works.size}  →",style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Accent)
                }
                Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick=onWorks)) {
                    if(works.isEmpty()) Box(Modifier.fillMaxSize().studioGlass(),contentAlignment=Alignment.Center) {
                        Text("完成并保存的作品会出现在这里",color=StudioPalette.Muted,style=MaterialTheme.typography.bodyMedium)
                    } else works.take(3).reversed().forEach { work ->
                        val index = works.indexOf(work)
                        Box(Modifier.fillMaxSize().padding(start=(index*8).dp,end=(index*8).dp,top=((2-index)*7).dp,bottom=(index*7).dp)
                            .clip(RoundedCornerShape(20.dp)).background(StudioPalette.Surface).border(1.dp,Color.White,RoundedCornerShape(20.dp))) {
                            AsyncImage(work.uri,if(index==0) "最新作品" else "较早作品",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            if(index==0) {
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xA0263437)))))
                                Text("最新 · ${work.kind}",Modifier.align(Alignment.BottomStart).padding(14.dp),color=Color.White,style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTool(number:String,title:String,subtitle:String,modifier:Modifier,onClick:()->Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .965f else 1f,spring(dampingRatio=.8f,stiffness=550f),label="tool press")
    Box(modifier.graphicsLayer {scaleX=scale;scaleY=scale}.studioGlass()
        .clickable(interactionSource=interaction,indication=null,onClick=onClick)) {
        ToolPattern(number,Modifier.align(Alignment.TopEnd).padding(10.dp).size(80.dp),pressed)
        Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Text(number,color=StudioPalette.Accent,style=MaterialTheme.typography.labelMedium)
            Column {
                Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp,maxLines=1)
                Text(subtitle,color=StudioPalette.Muted,fontSize=13.sp,maxLines=2,lineHeight=18.sp)
            }
        }
    }
}

/** Each drawing describes the action: light contours, assembled frames, ink, sampled hues. */
@Composable
private fun ToolPattern(kind:String,modifier:Modifier,pressed:Boolean) {
    val emphasis by animateFloatAsState(if(pressed) 1f else .65f,tween(180),label="pattern response")
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val ink=StudioPalette.Accent.copy(alpha=.42f*emphasis)
        val stroke=Stroke(width=1.4.dp.toPx(),cap=StrokeCap.Round)
        when(kind) {
            "01" -> {
                repeat(4) { i ->
                    val path=Path().apply {
                        moveTo(w*.05f,h*(.38f+i*.13f))
                        cubicTo(w*.35f,h*(-.12f+i*.15f),w*.5f,h*(.88f+i*.03f),w*.98f,h*(.20f+i*.15f))
                    }
                    drawPath(path,ink,style=stroke)
                }
                drawCircle(ink,w*.10f,Offset(w*.78f,h*.15f),style=stroke)
            }
            "02" -> {
                val gap=w*.08f; val side=w*.38f
                listOf(Offset(gap,gap),Offset(side+gap*2,gap),Offset(gap,side+gap*2),Offset(side+gap*2,side+gap*2)).forEachIndexed { i,point ->
                    drawRoundRect(ink.copy(alpha=ink.alpha*(.5f+i*.15f)),point,Size(side,if(i==1) side*.72f else side),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.06f),style=stroke)
                }
            }
            "03" -> {
                val path=Path().apply {
                    moveTo(w*.12f,h*.77f); cubicTo(w*.85f,-h*.12f,w*.7f,h*.08f,w*.25f,h*.78f)
                    cubicTo(w*.1f,h*.98f,w*.42f,h*.35f,w*.55f,h*.54f)
                    cubicTo(w*.7f,h*.85f,w*.72f,h*.26f,w*.83f,h*.48f)
                    lineTo(w*.96f,h*.41f)
                }
                drawPath(path,ink,style=stroke)
                drawLine(ink,Offset(w*.16f,h*.94f),Offset(w*.9f,h*.74f),strokeWidth=1.dp.toPx())
            }
            else -> {
                listOf(Color(0xFF5B8584),Color(0xFF98ABA1),Color(0xFFC8B9A0),Color(0xFF7C929C)).forEachIndexed { i,c ->
                    val point=Offset(w*(.28f+(i%2)*.4f),h*(.25f+(i/2)*.4f))
                    drawCircle(c.copy(alpha=.38f*emphasis),w*.23f,point)
                    drawCircle(ink,w*.23f,point,style=stroke)
                }
            }
        }
    }
}

@Composable
private fun RecentWorksScreen(onBack:()->Unit,onSign:(Uri)->Unit) {
    val context=LocalContext.current
    val works=remember { RecentWorks.list(context) }
    var selected by remember { mutableStateOf<com.pianyu.studio.media.RecentWork?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            StudioToolbar("最近作品",onBack)
            Text("最新保存的作品排在最前面",color=StudioPalette.Muted,style=MaterialTheme.typography.bodyMedium)
            if(works.isEmpty()) Text("还没有保存的作品",Modifier.padding(vertical=24.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                items(works,key={it.uri}) { work ->
                    Column(Modifier.fillMaxWidth().studioGlass().clickable {selected=work}.padding(10.dp)) {
                        AsyncImage(work.uri,work.name,Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Fit)
                        Text(work.kind,Modifier.padding(top=8.dp),fontWeight=FontWeight.Medium)
                        Text(work.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Muted)
                    }
                }
            }
        }
    }
    selected?.let { work -> AlertDialog(onDismissRequest={selected=null},title={Text(work.kind)},text={
        AsyncImage(work.uri,work.name,Modifier.fillMaxWidth().heightIn(max=420.dp),contentScale=ContentScale.Fit)
    },confirmButton={TextButton(onClick={onSign(Uri.parse(work.uri))}) {Text("加签名")}},dismissButton={
        Row {
            TextButton(onClick={runCatching {RecentWorks.share(context,Uri.parse(work.uri))}.onFailure {error="无法分享这张作品"}}) {Text("分享")}
            TextButton(onClick={selected=null}) {Text("关闭")}
        }
    }) }
    error?.let { AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("知道了")}}) }
}
