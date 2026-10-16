package com.pianyu.studio.watermark.ui

import com.pianyu.studio.ui.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.PickVisualMediaRequest
import com.pianyu.studio.watermark.photo.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.pianyu.studio.R
import com.pianyu.studio.watermark.ui.theme.*
import com.pianyu.studio.watermark.signature.*
import kotlinx.coroutines.launch

private val rootRoutes = setOf("home", "library", "settings")

@Composable
fun StudioApp(signatures: SignatureViewModel, photos: PhotoViewModel, extraction: ExtractionViewModel,
              batch: BatchStudioViewModel, onExit: () -> Unit = {}, inputUri: String? = null,
              onInputConsumed: () -> Unit = {}) {
    val batchState by batch.state.collectAsState()
    val presets by batch.presets.collectAsState()
    val batchMessage by batch.message.collectAsState()
    var batchDetails by rememberSaveable {mutableStateOf(false)}
    var showRepeatBatch by rememberSaveable {mutableStateOf(false)}
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var deletePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var presetsSource by rememberSaveable { mutableStateOf("editor") }
    val extractionState by extraction.state.collectAsState()
    var discardExtraction by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val photoState by photos.state.collectAsState()
    val signatureState by signatures.state.collectAsState()
    var route by rememberSaveable { mutableStateOf("home") }
    var hadRealPhoto by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf("home") }
    var createSource by rememberSaveable { mutableStateOf("home") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("透明度") }
    var opacity by rememberSaveable { mutableFloatStateOf(.8f) }
    var signatureWidth by rememberSaveable { mutableFloatStateOf(.18f) }
    var corner by rememberSaveable { mutableIntStateOf(3) }
    var centerX by rememberSaveable { mutableFloatStateOf(.88f) }
    var centerY by rememberSaveable { mutableFloatStateOf(.88f) }
    var ink by rememberSaveable { mutableIntStateOf(2) }
    var quality by rememberSaveable { mutableFloatStateOf(100f) }
    var format by rememberSaveable { mutableStateOf("PNG") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pageScroll = rememberScrollState()
    val glassOverlays = remember { mutableStateMapOf<Any, Boolean>() }
    LaunchedEffect(route) { pageScroll.scrollTo(0) }
    LaunchedEffect(signatureState.message) {
        signatureState.message?.let { message ->
            scope.launch { snackbar.showSnackbar(message) }
            signatures.consumeMessage()
        }
    }
    LaunchedEffect(photoState.message) {
        photoState.message?.let { message ->
            scope.launch { snackbar.showSnackbar(message) }
            photos.consumeMessage()
        }
    }
    fun exportRequest() = PhotoExportRequest(photoState.photo,signatureState.selectedBitmap,
        WatermarkPose(centerX,centerY,signatureWidth),opacity,ink,PhotoFormat.valueOf(format),100)
    val batchPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) batch.start(exportRequest()) else photos.reportMessage("批量保存需要存储权限。")
    }
    fun startBatch() {
        if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            batchPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else batch.start(exportRequest())
    }
    fun requestBatchSave() {
        if(batchState.finished && batchState.results.any {it.saved!=null}) showRepeatBatch=true else startBatch()
    }
    fun applyPreset(p: WatermarkPreset) {
        val asset=signatureState.assets.firstOrNull {it.id==p.signatureId}
        if(p.signatureId!=null && asset==null) {photos.reportMessage("该预设的签名已被删除，请先重新选择签名。 ");return}
        fun applySettings() {
            centerX=p.pose.x;centerY=p.pose.y;signatureWidth=p.pose.width;opacity=p.opacity;ink=p.ink
            format=if(p.quality<100) "PNG" else p.format.name;quality=100f
            corner=when {
                p.pose.x==.5f && p.pose.y==1f -> 4
                p.pose.x==0f && p.pose.y==0f -> 0
                p.pose.x==1f && p.pose.y==0f -> 1
                p.pose.x==0f && p.pose.y==1f -> 2
                p.pose.x==1f && p.pose.y==1f -> 3
                else -> -1
            }
            route=presetsSource
        }
        if(asset!=null) signatures.select(asset) {applySettings()} else {signatures.useDemo();applySettings()}
    }
    LaunchedEffect(batchMessage) {batchMessage?.let { scope.launch {snackbar.showSnackbar(it)};batch.consumeMessage() }}
    val batchPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if(uris.isNotEmpty()) {batch.select(uris);route="batch"}
    }
    fun pickBatch() {batchPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) photos.export(exportRequest(),true)
        else photos.reportMessage("保存到相册需要存储权限，你也可以直接分享照片。")
    }
    fun savePhoto() {
        if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else photos.export(exportRequest(),true)
    }
    LaunchedEffect(photoState.pendingShare) {
        photoState.pendingShare?.let { photo ->
            photos.consumeShare()
            try {context.startActivity(Intent.createChooser(PhotoExporter.shareIntent(photo),"分享签名照片"))}
            catch (_: Exception) {photos.reportMessage("没有可用的分享应用，请先保存到相册。")}
        }
    }
    LaunchedEffect(extractionState.message) {
        extractionState.message?.let {message -> scope.launch {snackbar.showSnackbar(message)};extraction.consumeMessage()}
    }
    var smartExtractionRequested by rememberSaveable {mutableStateOf(false)}
    val pngExportPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri ->if(uri!=null) extraction.exportPng(uri)}
    val extractionPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) extraction.importPhoto(uri,smart=smartExtractionRequested) {route="extract"}
    }
    fun pickExtraction(smart: Boolean=false) {smartExtractionRequested=smart;extractionPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photos.importPhoto(uri) { hadRealPhoto = true; route = "editor" }
    }
    fun pickPhoto() { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val pngPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) signatures.importPng(uri)
    }
    fun notice(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    LaunchedEffect(Unit) {
        if(quality<100f) format="PNG"
        quality=100f
        if(route=="batch" && batchState.uris.isEmpty()) {route="home";notice("上次批量任务已结束，请重新选择照片。 ")}
        if (hadRealPhoto && photoState.photo == null) {
            hadRealPhoto = false; route = "home"
            notice("上次照片编辑已结束，请重新选择照片。")
        }
    }
    fun back() {
        if(signatureState.busy || photoState.busy || extractionState.loading || extractionState.exporting || batchState.busy || batchState.loading) return
        if(route=="extract" && extractionState.source!=null) {discardExtraction=true;return}
        if(route=="create" && signatureState.strokes.isNotEmpty()) {showDiscard=true;return}
        route = when (route) { "export" -> "editor"; "create" -> createSource; "choose" -> source; "presets" -> presetsSource; else -> "home" }
    }
    BackHandler(enabled = route != "home", onBack = ::back)
    BackHandler(enabled = route == "home", onBack = onExit)

    LaunchedEffect(inputUri) {
        val value = inputUri ?: return@LaunchedEffect
        photos.importPhoto(Uri.parse(value)) { hadRealPhoto = true; route = "editor" }
        onInputConsumed()
    }

    CompositionLocalProvider(LocalGlassOverlayVisibility provides { token, open ->
        if(open) glassOverlays[token]=true else glassOverlays.remove(token)
    }) {
    Box(Modifier.fillMaxSize().blur(if(glassOverlays.isNotEmpty()) 14.dp else 0.dp)) {
        StudioBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (route in rootRoutes) {
                StudioToolbar("签名水印", onExit)
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "工作台", "library" to "签名素材", "settings" to "选项").forEach { (target, label) ->
                        FilterChip(selected=route==target, onClick={route=target}, label={Text(label)})
                    }
                }
            }
            if(signatureState.busy || photoState.busy || extractionState.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(pageScroll)
                .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when(route) {
                    "home" -> {
                        Text("为作品添加署名", style=MaterialTheme.typography.headlineSmall)
                        Text("选择照片，然后调整签名的位置与样式。", color=Muted)
                        GlassPanel(Modifier.fillMaxWidth(), bright=true) {
                            Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                GlassButton("选择照片", Modifier.fillMaxWidth(), primary=true, icon="photo", enabled=!photoState.busy) {pickPhoto()}
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    GlassButton("批量处理", Modifier.weight(1f), enabled=!batchState.busy&&!batchState.loading) {pickBatch()}
                                    GlassButton("水印预设", Modifier.weight(1f)) {presetsSource="home";route="presets"}
                                }
                            }
                        }
                        TextButton(onClick = { route = "editor" }) { Text(if(photoState.photo == null) "试用示例照片" else "继续编辑当前照片") }
                        Text(if(photoState.photo == null) "选择照片，为作品添加个人签名" else "当前照片：${photoState.photo!!.name}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        SectionHeader("我的签名", "查看全部") { route = "library" }
                        if(signatureState.loadFailed) GlassButton("重新读取签名库",Modifier.fillMaxWidth()) {signatures.reload()}
                        if(!signatureState.loaded) CircularProgressIndicator()
                        else if(signatureState.assets.isEmpty()) EmptySignatures { createSource = "home"; showCreate = true }
                        else {
                            SignatureCards(signatureState.assets.take(2),signatures,signatureState.busy,
                                {signatures.select(it) {ink=2;route=if(route=="choose") source else "editor"}},{renameId=it.id},{deleteId=it.id})
                            GlassButton("创建签名",Modifier.fillMaxWidth(),icon="plus",enabled=!signatureState.busy) {createSource="home";showCreate=true}
                        }
                    }
                    "library", "choose" -> {
                        if(route == "choose") PageHeader("选择签名", ::back)
                        else SectionHeader("我的签名", "新建") { createSource = "library"; showCreate = true }
                        if(signatureState.loadFailed) GlassButton("重新读取签名库",Modifier.fillMaxWidth()) {signatures.reload()}
                        if(!signatureState.loaded) CircularProgressIndicator()
                        else if(signatureState.assets.isEmpty()) EmptySignatures { createSource = route; showCreate = true }
                        else {
                            SignatureCards(signatureState.assets,signatures,signatureState.busy,
                                {signatures.select(it) {ink=2;route=if(route=="choose") source else "editor"}},{renameId=it.id},{deleteId=it.id})
                            GlassButton("创建签名",Modifier.fillMaxWidth(),icon="plus",enabled=!signatureState.busy) {createSource=route;showCreate=true}
                        }
                        Text("示例素材", style = MaterialTheme.typography.titleMedium)
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Checkerboard(Modifier.fillMaxWidth().height(116.dp)) {
                                    DemoSignature(Modifier.align(Alignment.Center).width(180.dp).height(78.dp), Graphite)
                                }
                                Text("Lin · 示例签名", style = MaterialTheme.typography.titleMedium)
                                Text("用于查看页面效果，不属于你的签名库。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                                GlassButton("使用示例预览", Modifier.fillMaxWidth(), icon = "pen",enabled=!signatureState.busy) { signatures.useDemo();route = if(route=="choose") source else "editor" }
                            }
                        }
                    }
                    "create" -> {
                        PageHeader("手写签名", ::back, "保存",signatureState.strokes.isNotEmpty()&&!signatureState.busy) {signatures.prepareHandwritten()}
                        HandwritingCanvas(signatures,signatureState)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("撤销", Modifier.weight(1f),enabled=signatureState.strokes.isNotEmpty()&&!signatureState.busy) {signatures.undo()}
                            GlassButton("重做", Modifier.weight(1f),enabled=signatureState.redo.isNotEmpty()&&!signatureState.busy) {signatures.redo()}
                            GlassButton("清空", Modifier.weight(1f),enabled=signatureState.strokes.isNotEmpty()&&!signatureState.busy) {showClear=true}
                        }
                        ValueSlider("笔粗", "${signatureState.penWidth.toInt()}", signatureState.penWidth,signatures::setPenWidth,3f..24f)
                        Text("笔粗调整影响接下来书写的笔画。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                StudioIcon("pen"); Spacer(Modifier.width(12.dp))
                                Text("保存为透明背景", Modifier.weight(1f)); Text("PNG", color = Ice)
                            }
                        }
                    }
                    "extract" -> {
                        PageHeader(if(extractionState.settings.colorKey) "智能抠图" else "提取签名", ::back, "保存",extractionState.result!=null&&!extractionState.processing&&!extractionState.exporting&&!signatureState.busy) {
                            extractionState.result?.let {signatures.prepareExtracted(it,extractionState.wasResized)}
                        }
                        if(extractionState.source!=null) ExtractionControls(extraction,extractionState)
                        GlassButton(if(extractionState.exporting) "正在导出…" else "导出透明 PNG",Modifier.fillMaxWidth(),icon="save",
                            enabled=extractionState.result!=null&&!extractionState.processing&&!extractionState.exporting) {
                            pngExportPicker.launch("SigMark-signature-${System.currentTimeMillis()}.png")
                        }
                        GlassButton(if(extractionState.source==null) "选择签名照片" else "重新选择照片",Modifier.fillMaxWidth(),icon="photo",enabled=!extractionState.loading&&!extractionState.exporting&&!signatureState.busy) {pickExtraction(extractionState.settings.colorKey)}
                    }
                    "editor" -> {
                        PageHeader("编辑照片", ::back, "导出") { route = "export" }
                        PhotoCanvas(photoState.photo, signatureState.selectedBitmap,
                            WatermarkPose(centerX, centerY, signatureWidth), opacity, ink,
                            editable = route == "editor" && !photoState.busy) { pose ->
                            centerX = pose.x; centerY = pose.y; signatureWidth = pose.width; corner = -1
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(photoState.photo?.name ?: "示例照片", maxLines=2, style=MaterialTheme.typography.bodyMedium)
                                Text(photoState.photo?.let { "${it.width} × ${it.height} · 原图保留" } ?: "可导入自己的照片", color=Muted, style=MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { pickPhoto() }, enabled = !photoState.busy) { Text("更换照片") }
                        }
                        Text("按住签名自由拖动 · 双指缩放", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if(signatureState.selectedBitmap != null) Image(signatureState.selectedBitmap!!.asImageBitmap(),"当前签名",
                                    Modifier.width(62.dp).height(36.dp),contentScale=ContentScale.Fit,colorFilter=if(ink==2) null else ColorFilter.tint(if(ink==0) SoftWhite else Color.Black))
                                else DemoSignature(Modifier.width(62.dp).height(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(signatureState.selected?.name ?: "Lin · 示例签名");
                                    Text(if(signatureState.selected==null) "不会写入你的签名库" else "你的个人签名", color = Muted, style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { source = "editor"; route = "choose" }) { Text("更换") }
                            }
                        }
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    listOf("位置", "大小", "颜色", "透明度").forEach { item ->
                                        Column(Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = tab == item }.clickable(role = Role.Tab) { tab = item }
                                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(item, color = if(tab == item) Ice else Muted)
                                            Spacer(Modifier.height(8.dp))
                                            Box(Modifier.height(2.dp).width(24.dp).background(if(tab == item) Ice else Color.Transparent))
                                        }
                                    }
                                }
                                when(tab) {
                                    "透明度" -> ValueSlider("透明度", "${(opacity*100).toInt()}%", opacity, {opacity=it}, 0f..1f)
                                    "大小" -> ValueSlider("照片宽度占比", "${(signatureWidth*100).toInt()}%", signatureWidth, {signatureWidth=it}, .08f.. .65f)
                                    "颜色" -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        listOf("白色", "黑色", "原色").forEachIndexed { index, label ->
                                            GlassButton(if(ink == index) "$label ✓" else label, Modifier.weight(1f), primary = ink == index) { ink=index }
                                        }
                                    }
                                    "位置" -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("左上", "右上", "左下", "右下").forEachIndexed { index, label ->
                                                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(if(corner==index) Ice.copy(alpha=.16f) else Color.White.copy(alpha=.04f))
                                                    .border(1.dp, if(corner==index) Ice else Color.White.copy(alpha=.12f), RoundedCornerShape(10.dp))
                                                    .semantics { selected = corner==index }.clickable(role = Role.Button) {corner=index; centerX=if(index%2==0) 0f else 1f; centerY=if(index<2) 0f else 1f}, contentAlignment = Alignment.Center) {
                                                    Text(label, color = if(corner==index) Ice else Muted)
                                                }
                                            }
                                        }
                                        GlassButton(if(corner==4) "底部居中 ✓" else "底部居中",
                                            Modifier.fillMaxWidth().semantics { selected = corner==4 }, primary=corner==4) {
                                            corner=4; centerX=.5f; centerY=1f
                                        }
                                        Text("也可直接按住照片中的签名，自由拖动到喜欢的位置。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            GlassButton("保存预设",Modifier.weight(1f),enabled=!signatureState.busy) {showSavePreset=true}
                            GlassButton("我的预设",Modifier.weight(1f)) {presetsSource="editor";route="presets"}
                        }
                        if(batchState.uris.isNotEmpty()) GlassButton("返回批量处理",Modifier.fillMaxWidth()) {route="batch"}
                        GlassButton("按当前设置批量导出",Modifier.fillMaxWidth()) {pickBatch()}
                        TextButton(onClick = { opacity=.8f; signatureWidth=.18f; corner=3; centerX=.88f; centerY=.88f; ink=2 }) { Text("恢复默认效果") }
                    }
                    "export" -> {
                        PageHeader("导出照片", ::back)
                        PhotoCanvas(photoState.photo, signatureState.selectedBitmap,
                            WatermarkPose(centerX, centerY, signatureWidth), opacity, ink,
                            editable = route == "editor" && !photoState.busy) { pose ->
                            centerX = pose.x; centerY = pose.y; signatureWidth = pose.width; corner = -1
                        }
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("导出设置", style = MaterialTheme.typography.titleMedium)
                                DetailRow("尺寸", photoState.photo?.let { "${it.width} × ${it.height}" } ?: "示例照片原尺寸")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("格式", Modifier.weight(1f))
                                    listOf("JPG", "PNG").forEach { item -> TextButton(onClick = {format=item},enabled=!photoState.busy,
                                        modifier=Modifier.semantics {contentDescription="导出格式：$item";selected=format==item}) { Text(if(format==item) "$item ✓" else item) } }
                                }
                                if(format=="JPG") Text("JPG · 100% 画质，仍为有损编码。无损保存请选择 PNG。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                                else Text("PNG · 100% 无损，保留原尺寸；文件通常更大。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text("保留原尺寸，生成新照片，原图不会被覆盖。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if(photoState.photo==null) Text("当前导出的是示例照片。",color=Ice,style=MaterialTheme.typography.bodyMedium)
                        if(signatureState.selectedBitmap==null) Text("当前使用 Lin 示例签名，可返回编辑页更换为自己的签名。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                        photoState.status?.let {Text(it,color=Ice)}
                        photoState.lastSaved?.let {saved ->
                            GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("上次保存",style=MaterialTheme.typography.titleMedium)
                                    Text("${saved.width} × ${saved.height} · ${saved.format.name}",color=Ice)
                                    Text(saved.name,color=Muted,style=MaterialTheme.typography.bodyMedium)
                                    GlassButton("查看保存照片",enabled=!photoState.busy) {
                                        try {context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved.uri,saved.format.mime)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
                                        catch (_: Exception) {notice("没有可用的看图应用，请到相册中查看。")}
                                    }
                                }
                            }
                        }
                        GlassButton("保存到相册", Modifier.fillMaxWidth(), primary = true, icon = "save",enabled=!photoState.busy&&!signatureState.busy) { savePhoto() }
                        GlassButton("分享", Modifier.fillMaxWidth(), icon = "share",enabled=!photoState.busy&&!signatureState.busy) { photos.export(exportRequest(),false) }
                    }
                    "batch" -> {
                        PageHeader("批量加签名", ::back,backEnabled=!batchState.busy&&!batchState.loading)
                        Text("已选择 ${batchState.uris.size} 张照片",style=MaterialTheme.typography.titleLarge)
                        if(batchState.loading) Text("正在读取首张预览…",color=Ice)
                        batchState.previewError?.let {Text(it,color=Muted)}
                        batchState.preview?.let {photo ->
                            PhotoCanvas(photo,signatureState.selectedBitmap,WatermarkPose(centerX,centerY,signatureWidth),opacity,ink,
                                editable=!batchState.busy&&!signatureState.busy) {pose ->centerX=pose.x;centerY=pose.y;signatureWidth=pose.width;corner=-1}
                            Text("首张预览 · 拖动或双指缩放签名，统一应用到所有照片。",color=Muted)
                        }
                        Text("沿用当前水印设置，每张保留原尺寸，保存到相册的 Pianyu Studio 文件夹。",color=Muted)
                        if(opacity==0f) Text("当前透明度为 0%，导出照片中不会显示签名。",color=Ice)
                        if(batchState.results.isNotEmpty() || batchState.finished) {
                            GlassPanel(Modifier.fillMaxWidth(),bright=true) {
                                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Text(if(batchState.busy) "处理进度" else if(batchState.stopping) "已停止处理" else "上次处理结果",style=MaterialTheme.typography.titleMedium)
                                    Text("成功 ${batchState.results.count {it.saved!=null}} · 失败 ${batchState.results.count {it.error!=null}} · 未处理 ${batchState.uris.size-batchState.results.size}",color=Ice)
                                    batchState.results.forEach {result ->
                                        Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                                            Text(result.name,maxLines=2,overflow=TextOverflow.Ellipsis)
                                            Text(result.saved?.let {"已保存 · ${it.width} × ${it.height}"} ?: "失败：${result.error}",color=Muted,style=MaterialTheme.typography.bodyMedium)
                                            result.saved?.let {saved -> TextButton(onClick={
                                                try {context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved.uri,saved.format.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
                                                catch (_: Exception) {notice("没有可用的看图应用，请到相册中查看。")}
                                            },enabled=!batchState.busy) {Text("查看照片")} }
                                        }
                                    }
                                }
                            }
                        }
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                DetailRow("当前签名",signatureState.selected?.name ?: "Lin · 示例签名")
                                DetailRow("水印样式","${(signatureWidth*100).toInt()}% 大小 · ${(opacity*100).toInt()}% 透明度")
                                DetailRow("导出",if(format=="JPG") "JPG · ${quality.toInt()}% 画质" else "PNG · 100% 无损")
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    GlassButton("更换签名",Modifier.weight(1f),enabled=!batchState.busy&&!signatureState.busy) {source="batch";route="choose"}
                                    GlassButton("使用预设",Modifier.weight(1f),enabled=!batchState.busy&&!signatureState.busy) {presetsSource="batch";route="presets"}
                                }
                            }
                        }
                        if(!batchState.busy) {
                            GlassButton(if(batchDetails) "收起详细设置" else "展开详细设置",Modifier.fillMaxWidth()) {batchDetails=!batchDetails}
                            if(batchDetails) GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    ValueSlider("照片宽度占比","${(signatureWidth*100).toInt()}%",signatureWidth,{signatureWidth=it},.08f.. .65f)
                                    ValueSlider("透明度","${(opacity*100).toInt()}%",opacity,{opacity=it},0f..1f)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        listOf("白色","黑色","原色").forEachIndexed {i,label -> GlassButton(if(ink==i) "$label ✓" else label,Modifier.weight(1f),primary=ink==i) {ink=i} }
                                    }
                                    GlassButton("底部居中",Modifier.fillMaxWidth()) {centerX=.5f;centerY=1f;corner=4}
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        listOf("JPG","PNG").forEach {item -> GlassButton(if(format==item) "$item ✓" else item,Modifier.weight(1f),primary=format==item) {format=item} }
                                    }
                                    if(format=="JPG") Text("JPG · 100% 画质，仍为有损编码。大图推荐 PNG 分块无损导出。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                                    else Text("PNG · 100% 无损，保留原尺寸；文件通常更大。",color=Muted,style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                            GlassButton("保存当前预设",Modifier.fillMaxWidth(),enabled=!signatureState.busy) {showSavePreset=true}
                            TextButton(onClick={pickBatch()},enabled=!batchState.loading) {Text("重新选择照片")}
                        }
                    }
                    "presets" -> {
                        PageHeader("我的预设",::back)
                        Text("保存你常用的签名和水印设置，下次一键套用。",color=Muted)
                        if(presets.isEmpty()) Text("还没有预设，可在编辑照片页保存当前设置。",color=Ice)
                        presets.forEach {p ->
                            val available=p.signatureId==null || signatureState.assets.any {it.id==p.signatureId}
                            val presetFormat=if(p.quality<100) PhotoFormat.PNG else p.format
                            GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Text(p.name,style=MaterialTheme.typography.titleMedium)
                                    Text((if(p.signatureId==null) "Lin · 示例签名" else signatureState.assets.firstOrNull {it.id==p.signatureId}?.name ?: "原签名已删除")+" · ${presetFormat.name}",color=Muted)
                                    if(!available && signatureState.loaded) Text("签名已删除，此预设暂时无法套用。",color=Ice,style=MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        GlassButton("套用",Modifier.weight(1f).semantics {contentDescription="套用预设：${p.name}"},primary=true,enabled=available&&signatureState.loaded&&!signatureState.busy) {applyPreset(p)}
                                        TextButton(onClick={renamePresetId=p.id}) {Text("重命名")}
                                        TextButton(onClick={deletePresetId=p.id}) {Text("删除")}
                                    }
                                }
                            }
                        }
                    }
                    "settings" -> {
                        Text("设置", style = MaterialTheme.typography.headlineSmall)
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                DetailRow("外观", "半透明磨砂")
                                DetailRow("照片处理", "本地处理")
                                Text("你的照片和签名无需上传。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        GlassButton("关于 Pianyu Studio", Modifier.fillMaxWidth()) {showAbout=true}
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("使用提示",style=MaterialTheme.typography.titleMedium)
                                Text("在照片上拖动签名，双指调整大小。常用效果可保存为预设。",color=Muted)
                                Text("批量一次最多 20 张，按照片比例应用水印；处理期间请保持应用打开。",color=Muted)
                                DetailRow("版本","1.0")
                            }
                        }
                    }
                }
            }
            if(route=="batch") {
                GlassPanel(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),bright=true) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(batchState.busy) {
                            Text("正在处理 ${batchState.current} / ${batchState.uris.size}",color=Ice,style=MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(progress={batchState.results.size.toFloat()/batchState.uris.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth(),color=Ice)
                            GlassButton(if(batchState.stopping) "正在完成当前照片…" else "停止处理",Modifier.fillMaxWidth(),enabled=!batchState.stopping) {batch.stop()}
                        } else {
                            GlassButton(if(batchState.finished) "再次批量保存" else "开始批量保存",Modifier.fillMaxWidth(),primary=true,icon="save",
                                enabled=batchState.uris.isNotEmpty()&&!batchState.loading&&!signatureState.busy) {requestBatchSave()}
                        }
                    }
                }
            }

        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { data ->
            Column(Modifier.fillMaxWidth().glassOverlay()) {
                Text(data.visuals.message,Modifier.padding(horizontal=20.dp,vertical=16.dp),color=StudioPalette.Ink,
                    style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if(showRepeatBatch) GlassDialog(onDismissRequest={showRepeatBatch=false},title={Text("再次保存这组照片？")},
        text={Text("上次保存的照片会保留，按当前设置再生成 ${batchState.uris.size} 张新照片。")},
        confirmButton={TextButton(onClick={showRepeatBatch=false;startBatch()}) {Text("再次保存")}},
        dismissButton={TextButton(onClick={showRepeatBatch=false}) {Text("取消")}})
    if(showSavePreset) PresetNameDialog("保存水印预设","常用预设 ${presets.size+1}",{showSavePreset=false}) {name ->
        batch.savePreset(WatermarkPreset(java.util.UUID.randomUUID().toString(),name,signatureState.selected?.id,
            WatermarkPose(centerX,centerY,signatureWidth),opacity,ink,PhotoFormat.valueOf(format),100)) {showSavePreset=false}
    }
    presets.firstOrNull {it.id==renamePresetId}?.let {p ->
        PresetNameDialog("重命名预设",p.name,{renamePresetId=null}) {name ->batch.savePreset(p.copy(name=name)) {renamePresetId=null}}
    }
    presets.firstOrNull {it.id==deletePresetId}?.let {p ->
        GlassDialog(onDismissRequest={deletePresetId=null},title={Text("删除预设？")},text={Text("将删除“${p.name}”，签名和照片会保留。")},
            confirmButton={TextButton(onClick={batch.deletePreset(p);deletePresetId=null}) {Text("删除")}},
            dismissButton={TextButton(onClick={deletePresetId=null}) {Text("取消")}})
    }
    if(showCreate) GlassDialog(
        onDismissRequest = {showCreate=false}, title={Text("创建签名")},
        text={Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("手写、导入透明 PNG，或将有底色的签名智能抠成透明 PNG。")
            GlassButton("智能抠图导入",Modifier.fillMaxWidth(),icon="photo",enabled=!extractionState.loading&&!signatureState.busy) {showCreate=false;pickExtraction(true)}
            GlassButton("从照片提取",Modifier.fillMaxWidth(),icon="photo",enabled=!extractionState.loading&&!signatureState.busy) {showCreate=false;pickExtraction()}
        }},
        confirmButton={TextButton(onClick={showCreate=false; route="create"},enabled=!signatureState.busy) {Text("手写签名")}},
        dismissButton={TextButton(onClick={showCreate=false;pngPicker.launch(arrayOf("image/png"))},enabled=!signatureState.busy) {Text("导入 PNG")}}
    )
    if(signatureState.pending != null) SignatureNameDialog("保存签名","我的签名 ${signatureState.assets.size+1}",signatureState.busy,
        signatureState.pending,signatures::dismissPending) { name ->
        signatures.savePending(name) {
            ink=2
            if(signatureState.pendingOrigin=="提取") extraction.clear()
            route=when(createSource) {"choose" -> source; "editor" -> "editor"; else -> "library"}
        }
    }
    signatureState.assets.firstOrNull {it.id==renameId}?.let { asset ->
        SignatureNameDialog("重命名签名",asset.name,signatureState.busy,onCancel={renameId=null}) {name ->signatures.rename(asset,name) {renameId=null}}
    }
    signatureState.assets.firstOrNull {it.id==deleteId}?.let {asset ->
        GlassDialog(onDismissRequest={if(!signatureState.busy) deleteId=null},title={Text("删除签名？")},
            text={Text("将从签名库删除“${asset.name}”。已进入编辑页的预览副本会保留。")},
            confirmButton={TextButton(onClick={signatures.delete(asset) {deleteId=null}},enabled=!signatureState.busy) {Text("删除")}},
            dismissButton={TextButton(onClick={deleteId=null},enabled=!signatureState.busy) {Text("取消")}})
    }
    if(showDiscard) GlassDialog(onDismissRequest={showDiscard=false},title={Text("放弃本次签名？")},text={Text("当前笔迹尚未保存。")},
        confirmButton={TextButton(onClick={showDiscard=false;signatures.clearDraft();route=createSource}) {Text("放弃")}},
        dismissButton={TextButton(onClick={showDiscard=false}) {Text("继续编辑")}})
    if(discardExtraction) GlassDialog(onDismissRequest={discardExtraction=false},title={Text("放弃本次提取？")},text={Text("提取的签名尚未保存。")},
        confirmButton={TextButton(onClick={discardExtraction=false;extraction.clear();route=createSource}) {Text("放弃")}},
        dismissButton={TextButton(onClick={discardExtraction=false}) {Text("继续编辑")}})
    if(showClear) GlassDialog(onDismissRequest={showClear=false},title={Text("清空当前签名？")},text={Text("将移除画布上的全部笔画。")},
        confirmButton={TextButton(onClick={showClear=false;signatures.clearDraft()}) {Text("清空")}},
        dismissButton={TextButton(onClick={showClear=false}) {Text("取消")}})
    if(showAbout) GlassDialog(onDismissRequest={showAbout=false}, title={Text("Pianyu Studio")},
        text={Text("Pianyu Studio\n为摄影作品添加个人签名。\n\n支持手写、PNG 导入和照片提取签名。\n支持原尺寸导出、分享、批量处理和常用预设。")},
        confirmButton={TextButton(onClick={showAbout=false}) {Text("知道了")}})
    }
}

@Composable
private fun PageHeader(title: String, back: () -> Unit, action: String? = null, actionEnabled: Boolean = true,backEnabled: Boolean = true,onAction: () -> Unit = {}) {
    StudioToolbar(title, back, action, actionEnabled, backEnabled, onAction)
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        TextButton(onClick=onClick) {Text(action)}
    }
}

@Composable
private fun EmptySignatures(create: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("还没有自己的签名",style=MaterialTheme.typography.titleMedium)
            Text("创建一次，下次给作品署名时直接使用。",color=Muted,style=MaterialTheme.typography.bodyMedium)
            GlassButton("创建签名",Modifier.fillMaxWidth(),icon="plus",onClick=create)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ValueSlider(label: String, value: String, number: Float, change: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth()) {Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(value,color=Ice)}
        Slider(value=number,onValueChange=change,valueRange=range,enabled=enabled,
            modifier=Modifier.semantics {contentDescription=label},
            thumb = {
                Box(Modifier.size(20.dp).background(Ice, CircleShape)
                    .border(1.dp, Color.White.copy(alpha=.6f), CircleShape))
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val fraction = (number-range.start)/(range.endInclusive-range.start)
                    drawLine(StudioPalette.Outline, Offset(0f,size.height/2), Offset(size.width,size.height/2), size.height, StrokeCap.Round)
                    drawLine(Ice, Offset(0f,size.height/2), Offset(size.width*fraction,size.height/2), size.height, StrokeCap.Round)
                }
            },
            colors=SliderDefaults.colors(thumbColor=Ice,activeTrackColor=Ice,inactiveTrackColor=StudioPalette.Outline))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.width(88.dp),style=MaterialTheme.typography.bodyMedium)
        Text(value,Modifier.weight(1f),color=Muted,textAlign=TextAlign.End,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
    }
}
