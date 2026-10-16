package com.pianyu.studio.watermark.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pianyu.studio.watermark.signature.*
import com.pianyu.studio.watermark.ui.theme.*
import kotlin.math.abs

@Composable
fun ExtractionControls(model: ExtractionViewModel,state: ExtractionState) {
    val source=state.source ?: return
    Text("在原图上拖出一个方框，只保留签名区域。",color=Muted,style=MaterialTheme.typography.bodyMedium)
    var selection by remember(state.settings.crop) {mutableStateOf(state.settings.crop)}
    val latestSettings by rememberUpdatedState(state.settings)
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val aspect=source.width.toFloat()/source.height
        Box(Modifier.width(minOf(maxWidth,340.dp*aspect)).aspectRatio(aspect).clip(RoundedCornerShape(12.dp))) {
            Image(source.asImageBitmap(),"签名来源照片",Modifier.fillMaxSize())
            Canvas(Modifier.matchParentSize().testTag("extractionCrop").semantics {contentDescription="签名区域选框"}
                .pointerInput(source) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false);down.consume()
                        val start=Offset((down.position.x/size.width).coerceIn(0f,1f),(down.position.y/size.height).coerceIn(0f,1f))
                        var valid=false
                        do {
                            val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id} ?: break
                            val end=Offset((change.position.x/size.width).coerceIn(0f,1f),(change.position.y/size.height).coerceIn(0f,1f))
                            if(abs(start.x-end.x)>=.03f && abs(start.y-end.y)>=.03f) {
                                selection=ExtractionCrop(minOf(start.x,end.x),minOf(start.y,end.y),maxOf(start.x,end.x),maxOf(start.y,end.y));valid=true
                            }
                            change.consume()
                        } while(change.pressed)
                        if(valid) model.update(latestSettings.copy(crop=selection))
                    }
                }) {
                val c=selection;val l=c.left*size.width;val t=c.top*size.height;val r=c.right*size.width;val b=c.bottom*size.height
                val shade=Color.Black.copy(alpha=.48f)
                drawRect(shade,Offset.Zero,androidx.compose.ui.geometry.Size(size.width,t))
                drawRect(shade,Offset(0f,b),androidx.compose.ui.geometry.Size(size.width,size.height-b))
                drawRect(shade,Offset(0f,t),androidx.compose.ui.geometry.Size(l,b-t))
                drawRect(shade,Offset(r,t),androidx.compose.ui.geometry.Size(size.width-r,b-t))
                drawRect(Ice,Offset(l,t),androidx.compose.ui.geometry.Size(r-l,b-t),style=Stroke(2.dp.toPx()))
            }
        }
    }
    GlassButton("使用整张照片",Modifier.fillMaxWidth()) {model.update(state.settings.copy(crop=ExtractionCrop()))}
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                GlassButton("智能色度抠图",Modifier.weight(1f),primary=state.settings.colorKey) {model.update(state.settings.copy(colorKey=true,automatic=true,cutoff=240f))}
                GlassButton("明暗提取",Modifier.weight(1f),primary=!state.settings.colorKey) {model.update(state.settings.copy(colorKey=false,cutoff=210f))}
            }
            if(state.settings.colorKey) {
                val detected=state.background
                Text(if(detected?.samples==0) "已检测到透明边缘，将保留原有透明度。" else if(state.settings.automatic) "自动识别：${if(detected?.light!=false) "浅底黑字" else "深底白字"}" else "手动指定底色",color=Ice,style=MaterialTheme.typography.bodyMedium)
                GlassButton(if(state.settings.automatic) "自动识别 ✓" else "自动识别",Modifier.fillMaxWidth(),primary=state.settings.automatic) {model.update(state.settings.copy(automatic=true))}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                GlassButton("浅底深色签名",Modifier.weight(1f),primary=(!state.settings.colorKey || !state.settings.automatic)&&!state.settings.lightInk) {model.update(state.settings.copy(lightInk=false,automatic=false))}
                GlassButton("深底浅色签名",Modifier.weight(1f),primary=(!state.settings.colorKey || !state.settings.automatic)&&state.settings.lightInk) {model.update(state.settings.copy(lightInk=true,automatic=false))}
            }
            ValueSlider("去底强度", "${((250f-state.settings.cutoff)/190f*100).toInt()}%",
                310f-state.settings.cutoff,{model.update(state.settings.copy(cutoff=310f-it))},60f..250f)
            Text(if(state.settings.colorKey) "按底色差异去底；向右增加容差，笔画过淡时向左调。" else "向右减少背景残留；笔画过淡时向左调。",color=Muted,style=MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=state.settings.removeSpecks,onCheckedChange={model.update(state.settings.copy(removeSpecks=it))})
                Text("去除小杂点",style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text("透明签名预览",style=MaterialTheme.typography.titleMedium)
    Checkerboard(Modifier.fillMaxWidth().height(150.dp),dark=true) {
        state.result?.let {Image(it.asImageBitmap(),"提取的透明签名",Modifier.fillMaxSize().padding(16.dp))}
            ?: Text(if(state.processing) "正在提取…" else state.error ?: "没有提取到笔迹，请调整选区或强度。",
                Modifier.align(Alignment.Center).padding(16.dp),color=SoftWhite)
    }
    Text("适合白底黑字、黑底白字及接近纯色的底图。自动模式从图片边缘估计底色；复杂背景或大面积笔迹占据边缘时，可手动切换底色。小杂点选项可能去掉标点。",color=Muted,style=MaterialTheme.typography.bodyMedium)
}
