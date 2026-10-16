package com.pianyu.studio.watermark.ui

import com.pianyu.studio.ui.*
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pianyu.studio.watermark.signature.*
import com.pianyu.studio.watermark.ui.theme.*

@Composable
fun HandwritingCanvas(model: SignatureViewModel, state: SignatureState) {
    Box(Modifier.fillMaxWidth().aspectRatio(InkRenderer.WIDTH/InkRenderer.HEIGHT)
        .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2F0EB))) {
        if(state.strokes.isEmpty()) Text("请在此处书写",Modifier.padding(20.dp),color=Color(0xFF777D80))
        Canvas(Modifier.matchParentSize().testTag("handwriting").semantics {contentDescription="手写签名画布"}
            .pointerInput(model) {
                fun normalize(x: Float,y: Float) = InkPoint(
                    (x/size.width*InkRenderer.WIDTH).coerceIn(0f,InkRenderer.WIDTH),
                    (y/size.height*InkRenderer.HEIGHT).coerceIn(0f,InkRenderer.HEIGHT))
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    down.consume()
                    model.beginStroke(normalize(down.position.x,down.position.y))
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        change.historical.forEach { model.appendPoint(normalize(it.position.x,it.position.y)) }
                        model.appendPoint(normalize(change.position.x,change.position.y))
                        change.consume()
                        if(!change.pressed) break
                    }
                }
            }) {
            drawIntoCanvas { InkRenderer.draw(it.nativeCanvas,state.strokes,size.width,size.height) }
        }
    }
}

@Composable
fun SignatureCards(assets: List<SignatureAsset>, model: SignatureViewModel, busy: Boolean,
                   onSelect: (SignatureAsset) -> Unit, onRename: (SignatureAsset) -> Unit,
                   onDelete: (SignatureAsset) -> Unit) {
    assets.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            row.forEach { asset ->
                key(asset.id) {
                    var menu by remember {mutableStateOf(false)}
                    var visible by remember {mutableStateOf(false)}
                    val view=LocalView.current
                    val preview by produceState<Pair<Boolean,Bitmap?>>(false to null,asset.id,visible) {
                        value=if(visible) true to model.thumbnail(asset.id) else false to null
                    }
                    val bitmap=preview.second
                    GlassPanel(Modifier.weight(1f).onGloballyPositioned {
                        val bounds=it.boundsInWindow()
                        visible=bounds.height>0 && bounds.bottom>0 && bounds.top<view.height
                    }) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Checkerboard(Modifier.fillMaxWidth().height(90.dp),dark=true) {
                                if(bitmap != null) Image(bitmap!!.asImageBitmap(),"签名预览：${asset.name}",
                                    Modifier.fillMaxSize().padding(10.dp),contentScale=ContentScale.Fit)
                                else Text(if(preview.first) "读取失败" else "读取中",Modifier.align(Alignment.Center),color=SoftWhite)
                            }
                            Text(asset.name,style=MaterialTheme.typography.bodyLarge)
                            Box(Modifier.fillMaxWidth()) {
                                TextButton(onClick={menu=true},enabled=!busy,
                                    modifier=Modifier.align(Alignment.CenterEnd).semantics {contentDescription="管理签名 ${asset.name}"}) {Text("管理")}
                                GlassDropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                    DropdownMenuItem(text={Text("重命名")},onClick={menu=false;onRename(asset)})
                                    DropdownMenuItem(text={Text("删除")},onClick={menu=false;onDelete(asset)})
                                }
                            }
                            GlassButton("使用签名",Modifier.fillMaxWidth().semantics {contentDescription="使用签名：${asset.name}"},enabled=!busy) {onSelect(asset)}
                        }
                    }
                }
            }
            if(row.size==1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun SignatureNameDialog(title: String, initialName: String, busy: Boolean,
                        pending: ImportedSignature? = null, onCancel: () -> Unit,
                        onSave: (String) -> Unit) {
    var name by rememberSaveable(initialName) {mutableStateOf(initialName)}
    val valid=name.trim().isNotEmpty() && name.trim().length<=40
    GlassDialog(onDismissRequest={if(!busy) onCancel()},title={Text(title)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(pending != null) {
                    Checkerboard(Modifier.fillMaxWidth().height(100.dp),dark=true) {
                        Image(pending.bitmap.asImageBitmap(),"待保存签名",Modifier.fillMaxSize().padding(10.dp),contentScale=ContentScale.Fit)
                    }
                    if(!pending.hasTransparency) Text("这张 PNG 没有透明背景，原背景会一并保留。",color=Ice,style=MaterialTheme.typography.bodyMedium)
                    if(pending.wasResized) Text("大尺寸图片已缩小至最大边 2048 像素。",style=MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(value=name,onValueChange={name=it},enabled=!busy,label={Text("签名名称")},
                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedContainerColor=Color.White.copy(alpha=.06f),unfocusedContainerColor=Color.White.copy(alpha=.04f),
                        focusedTextColor=StudioPalette.Ink,unfocusedTextColor=StudioPalette.Ink,disabledTextColor=Muted,
                        focusedBorderColor=Ice,unfocusedBorderColor=Color.White.copy(alpha=.25f),
                        focusedLabelColor=Ice,unfocusedLabelColor=Muted,cursorColor=Ice),
                    singleLine=true,isError=name.trim().length>40,supportingText={Text("名称最多 40 个字符")})
            }
        },confirmButton={TextButton(onClick={onSave(name.trim())},enabled=valid&&!busy) {Text(if(busy) "保存中…" else "保存")}},
        dismissButton={TextButton(onClick=onCancel,enabled=!busy) {Text("取消")}})
}
