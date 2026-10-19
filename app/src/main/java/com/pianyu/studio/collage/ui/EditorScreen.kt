package com.pianyu.studio.collage.ui

import com.pianyu.studio.ui.*
import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.pianyu.studio.collage.model.CollageRatio
import com.pianyu.studio.collage.model.CollageStyle
import com.pianyu.studio.collage.model.CollageTemplate
import com.pianyu.studio.collage.model.PhotoTransform
import com.pianyu.studio.collage.model.supportedCollageRatios
import com.pianyu.studio.collage.model.templatesForPhotoCount
import com.pianyu.studio.collage.ui.theme.GeyiGlassBorder
import com.pianyu.studio.collage.ui.theme.GeyiGraphite
import com.pianyu.studio.collage.ui.theme.GeyiIce
import com.pianyu.studio.collage.ui.theme.GeyiPanel
import com.pianyu.studio.collage.ui.theme.GeyiSurface
import com.pianyu.studio.collage.ui.theme.GeyiSurfaceVariant
import com.pianyu.studio.collage.ui.theme.GeyiTextPrimary
import com.pianyu.studio.collage.ui.theme.GeyiTextSecondary
import com.pianyu.studio.collage.export.CollageExporter
import com.pianyu.studio.collage.export.GallerySaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SaveProgress(val fraction: Float, val message: String)

@Composable
fun EditorScreen(
    innerPadding: PaddingValues,
    photoUris: List<String>,
    snackbarHostState: SnackbarHostState,
    onReplacePhoto: (Int, Uri) -> Unit = { _, _ -> },
    onSign: (Uri) -> Unit = {},
    saveCollage: (Context, String, CollageExporter.Request, (Float, String) -> Unit) -> Uri = GallerySaver::save,
    onNewCollage: () -> Unit = {},
    onBack: () -> Unit,
) {
    val templates = remember(photoUris.size) { templatesForPhotoCount(photoUris.size) }
    var selectedTemplateId by rememberSaveable(photoUris.size) {
        mutableStateOf(templates.firstOrNull()?.id.orEmpty())
    }
    val selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId }
        ?: templates.first()

    var selectedRatioId by rememberSaveable { mutableStateOf(supportedCollageRatios.first().id) }
    val selectedRatio = supportedCollageRatios.first { it.id == selectedRatioId }
    var spacingPercent by rememberSaveable { mutableIntStateOf(0) }
    var blackSpacing by rememberSaveable { mutableStateOf(false) }
    val collageStyle = CollageStyle(spacingPercent, blackSpacing)
    var selectedPhotoIndex by remember(photoUris.size) { mutableIntStateOf(-1) }
    var replacingPhotoIndex by rememberSaveable { mutableIntStateOf(-1) }
    var panelProgress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var selectedTool by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val photoTransforms = remember(photoUris.size) { mutableStateMapOf<Int, PhotoTransform>() }
    var previousPhotoUris by remember(photoUris.size) { mutableStateOf(photoUris) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(photoUris) {
        photoUris.indices.forEach { index ->
            if (previousPhotoUris.getOrNull(index) != photoUris[index]) photoTransforms.remove(index)
        }
        previousPhotoUris = photoUris
    }
    val replacePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val index = replacingPhotoIndex
        replacingPhotoIndex = -1
        if (uri != null && index in photoUris.indices) {
            photoTransforms.remove(index)
            onReplacePhoto(index, uri)
            selectedPhotoIndex = index
        }
    }
    var saving by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var saveProgress by remember { mutableStateOf(SaveProgress(0f, "正在准备保存")) }
    BackHandler(enabled = saving || savedUri != null) {
        if (!saving) savedUri = null
    }
    fun saveToGallery() {
        if (!saving) {
            val request = CollageExporter.Request(
                photoUris = photoUris.map(String::toUri),
                ratio = selectedRatio,
                template = selectedTemplate,
                transforms = photoUris.indices.map { photoTransforms[it] ?: PhotoTransform() },
                style = collageStyle,
            )
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            savedUri = null
            saveProgress = SaveProgress(0f, "正在准备保存")
            saving = true
            coroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        saveCollage(context, "Pianyu_Collage_$timestamp.png", request) { fraction, message ->
                            saveProgress = SaveProgress(fraction.coerceIn(0f, 1f), message)
                        }
                    }
                }
                if (result.isSuccess) {
                    saveProgress = SaveProgress(1f, "保存完成")
                    delay(450)
                    savedUri = result.getOrThrow()
                    saving = false
                } else {
                    saving = false
                    snackbarHostState.showSnackbar(
                        "保存失败：${result.exceptionOrNull()?.localizedMessage ?: "请重试"}",
                    )
                }
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) saveToGallery()
        else coroutineScope.launch { snackbarHostState.showSnackbar("需要存储权限才能保存到相册") }
    }
    fun requestSave() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveToGallery()
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        val collapsedPreviewHeight = (maxHeight * 0.57f).coerceIn(320.dp, 480.dp)
        val expandedPreviewHeight = 160.dp
        val dragRangePx = with(LocalDensity.current) {
            (collapsedPreviewHeight - expandedPreviewHeight).toPx().coerceAtLeast(1f)
        }
        val previewHeight = collapsedPreviewHeight -
            (collapsedPreviewHeight - expandedPreviewHeight) * panelProgress
        Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            EditorHeader(
                photoCount = photoUris.size,
                saving = saving,
                onBack = onBack,
                onSave = ::requestSave,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        CollagePreview(
            modifier = Modifier.padding(horizontal = 16.dp).height(previewHeight),
            ratio = selectedRatio,
            template = selectedTemplate,
            photoUris = photoUris,
            selectedPhotoIndex = selectedPhotoIndex,
            onSelectPhoto = { selectedPhotoIndex = it },
            photoTransforms = photoTransforms,
            style = collageStyle,
            onTransform = { index, transform -> photoTransforms[index] = transform },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedPhotoIndex in photoUris.indices)
                    "第 ${selectedPhotoIndex + 1} 张 · 拖动/缩放"
                else "点击照片后可裁切、替换或旋转",
                modifier = Modifier.weight(1f),
                color = GeyiTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (selectedPhotoIndex in photoUris.indices) TextAlign.Start else TextAlign.Center,
            )
            if (selectedPhotoIndex in photoUris.indices) {
                Text(
                    text = "替换",
                    modifier = Modifier
                        .testTag("replace_selected_photo")
                        .clickable(role = Role.Button) {
                            replacingPhotoIndex = selectedPhotoIndex
                            replacePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    color = GeyiIce,
                    fontSize = 14.sp,
                )
                Text(
                    text = "↻ 顺时针",
                    modifier = Modifier
                        .testTag("rotate_selected_photo")
                        .clickable(role = Role.Button) {
                            val current = photoTransforms[selectedPhotoIndex] ?: PhotoTransform()
                            photoTransforms[selectedPhotoIndex] = current.copy(
                                offsetX = 0f,
                                offsetY = 0f,
                                clockwiseQuarterTurns = (current.clockwiseQuarterTurns + 1) % 4,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    color = GeyiIce,
                    fontSize = 14.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(10.dp)
                .background(GeyiPanel)
                .testTag("editor_tool_panel")
                .pointerInput(selectedTool, gridState, dragRangePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        selectedPhotoIndex = -1
                        var deltaX = 0f
                        var deltaY = 0f
                        var draggingPanel = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            deltaX += dx
                            deltaY += dy
                            var dragStep = dy
                            if (!draggingPanel) {
                                if (abs(deltaX) > 8f && abs(deltaX) > abs(deltaY) * 1.2f) break
                                if (abs(deltaY) <= 8f || abs(deltaY) <= abs(deltaX) * 1.2f) continue
                                val canExpand = deltaY < 0f && panelProgress < 0.98f
                                val canCollapse = deltaY > 0f && panelProgress > 0.02f &&
                                    (selectedTool != 0 ||
                                        (gridState.firstVisibleItemIndex == 0 &&
                                            gridState.firstVisibleItemScrollOffset == 0))
                                if (!canExpand && !canCollapse) break
                                draggingPanel = true
                                dragStep = deltaY
                                settleJob?.cancel()
                            }
                            panelProgress = (panelProgress - dragStep / dragRangePx).coerceIn(0f, 1f)
                            change.consume()
                        }
                        if (draggingPanel) {
                            val target = if (deltaY < 0f) 1f else 0f
                            settleJob = coroutineScope.launch {
                                Animatable(panelProgress).animateTo(
                                    target,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessHigh,
                                    ),
                                ) { panelProgress = value }
                            }
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GeyiIce.copy(alpha = 0.24f)))
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag("editor_drag_handle"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GeyiTextSecondary.copy(alpha = 0.8f)),
            )
        }
        EditorToolTabs(
            selectedTool = selectedTool,
            onSelectTool = {
                selectedPhotoIndex = -1
                selectedTool = it
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            androidx.compose.animation.Crossfade(targetState=selectedTool,animationSpec=androidx.compose.animation.core.tween(220),label="collage tools") { tool ->
            when (tool) {
                0 -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("布局", color = GeyiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${selectedTemplate.title} · ${templates.size} 种", color = GeyiIce, fontSize = 14.sp)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("layout_grid"),
                        contentPadding = PaddingValues(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(templates, key = { it.id }) { template ->
                            LayoutOption(
                                template = template,
                                selected = template.id == selectedTemplate.id,
                                onClick = { selectedTemplateId = template.id },
                            )
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                    Text("成品比例", color = GeyiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    RatioSelector(
                        selectedRatio = selectedRatio,
                        onSelect = { selectedRatioId = it.id },
                    )
                }
                else -> Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                    Text("图片间距", color = GeyiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    SpacingControls(
                        spacingPercent = spacingPercent,
                        blackSpacing = blackSpacing,
                        onSpacingChange = { spacingPercent = it },
                        onBlackSpacingChange = { blackSpacing = it },
                    )
                }
            }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
        if (saving) {
            SaveProgressScreen(saveProgress)
        } else if (savedUri != null) {
            SavedCollageScreen(
                uri = savedUri!!,
                ratio = selectedRatio.value,
                onBack = { savedUri = null },
                onNewCollage = onNewCollage,
                onShare = {
                    runCatching {
                context.startActivity(Intent.createChooser(createCollageShareIntent(savedUri!!), "分享拼图"))
                    }.onFailure { error ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("无法打开分享：${error.localizedMessage ?: "请重试"}")
                        }
                    }
                },
                onSign = { onSign(savedUri!!) },
            )
        }
    }
}

@Composable
private fun SaveProgressScreen(progress: SaveProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 400),
        label = "save_progress",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GeyiGraphite)
            .testTag("save_progress_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(128.dp).testTag("save_progress_ring"),
                    color = GeyiIce,
                    trackColor = GeyiSurfaceVariant,
                    strokeWidth = 5.dp,
                )
                Text(
                    text = "${(progress.fraction * 100).roundToInt()}%",
                    color = GeyiTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text("正在保存拼图", color = GeyiTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(progress.message, color = GeyiTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("完成后会自动返回编辑器", color = GeyiTextSecondary, fontSize = 14.sp)
        }
    }
}

internal fun createCollageShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newRawUri("Geyi 拼图", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

@Composable
private fun SavedCollageScreen(
    uri: Uri,
    ratio: Float,
    onBack: () -> Unit,
    onNewCollage: () -> Unit,
    onShare: () -> Unit,
    onSign: () -> Unit,
) {
    var imageLoaded by remember(uri) { mutableStateOf(false) }
    var imageFailed by remember(uri) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeyiGraphite)
            .testTag("saved_collage_screen")
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackArrowButton(onClick = onBack, testTag = "saved_collage_back")
            Text("保存完成", color = GeyiTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onSign, modifier = Modifier.fillMaxWidth()) {
                Text("继续加签名")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("拼图已保存至相册", color = GeyiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("图片 / Pianyu Studio", color = GeyiTextSecondary, fontSize = 13.sp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val previewWidth = minOf(maxWidth, maxHeight * ratio)
            Box(
                modifier = Modifier
                    .size(previewWidth, previewWidth / ratio)
                    .background(GeyiPanel)
                    .border(1.dp, GeyiGlassBorder),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "已保存的拼图",
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit,
                    onSuccess = { imageLoaded = true },
                    onError = { imageFailed = true },
                )
                if (!imageLoaded && !imageFailed) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = GeyiIce,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "正在加载预览",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        color = GeyiTextSecondary,
                        fontSize = 14.sp,
                    )
                } else if (imageFailed) {
                    Text("预览加载失败，图片仍已保存到相册", color = GeyiTextSecondary, fontSize = 13.sp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(GeyiSurface)
                    .testTag("new_collage")
                    .clickable(role = Role.Button, onClick = onNewCollage),
                contentAlignment = Alignment.Center,
            ) {
                Text("再拼一张", color = GeyiTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(GeyiIce)
                    .testTag("share_collage")
                    .clickable(role = Role.Button, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                Text("分享", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BackArrowButton(onClick: () -> Unit, testTag: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "返回" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.2.dp.toPx()
            val tip = Offset(size.width * 0.19f, size.height * 0.5f)
            drawLine(GeyiTextPrimary, Offset(size.width * 0.83f, size.height * 0.5f), tip, stroke, StrokeCap.Round)
            drawLine(GeyiTextPrimary, tip, Offset(size.width * 0.48f, size.height * 0.20f), stroke, StrokeCap.Round)
            drawLine(GeyiTextPrimary, tip, Offset(size.width * 0.48f, size.height * 0.80f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun EditorToolTabs(selectedTool: Int, onSelectTool: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        listOf("布局", "比例", "图片间距").forEachIndexed { index, title ->
            val selected = selectedTool == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("editor_tool_$index")
                    .clickable(role = Role.Tab) { onSelectTool(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title,
                    color = if (selected) GeyiIce else GeyiTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.size(28.dp, 2.dp)
                        .background(if (selected) GeyiIce else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(
    photoCount: Int,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    StudioToolbar("拼图 · $photoCount 张", onBack, if(saving) "保存中…" else "导出", !saving, !saving, onSave)

}

@Composable
private fun CollagePreview(
    modifier: Modifier,
    ratio: CollageRatio,
    template: CollageTemplate,
    photoUris: List<String>,
    selectedPhotoIndex: Int,
    onSelectPhoto: (Int) -> Unit,
    photoTransforms: Map<Int, PhotoTransform>,
    style: CollageStyle,
    onTransform: (Int, PhotoTransform) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val previewWidth = min(maxWidth.value, maxHeight.value * ratio.value).dp
        val previewHeight = previewWidth / ratio.value
        val gap = min(previewWidth.value, previewHeight.value).dp * style.spacingPercent.coerceIn(0, 8) / 100f
        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
                .background(if (style.blackSpacing) Color.Black else Color.White)
                .testTag("collage_preview"),
        ) {
            CollageLayout(
                template = template,
                modifier = Modifier.fillMaxSize(),
                gap = gap,
            ) { index, modifier ->
                InteractivePhotoCell(
                    uri = photoUris[index],
                    index = index,
                    selected = selectedPhotoIndex == index,
                    onSelect = { onSelectPhoto(index) },
                    transform = photoTransforms[index] ?: PhotoTransform(),
                    onTransform = { onTransform(index, it) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun InteractivePhotoCell(
    uri: String,
    index: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    transform: PhotoTransform,
    onTransform: (PhotoTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    var sourceSize by remember(uri) { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val resolver = context.contentResolver
    val previewRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri.toUri())
            .size(2048, 2048)
            .build()
    }

    LaunchedEffect(uri) {
        sourceSize = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
                val orientation = resolver.openInputStream(uri.toUri())?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
                if (orientation in listOf(
                        ExifInterface.ORIENTATION_ROTATE_90,
                        ExifInterface.ORIENTATION_ROTATE_270,
                        ExifInterface.ORIENTATION_TRANSPOSE,
                        ExifInterface.ORIENTATION_TRANSVERSE,
                    )
                ) {
                    IntSize(options.outHeight, options.outWidth)
                } else {
                    IntSize(options.outWidth, options.outHeight)
                }
            }.getOrDefault(IntSize.Zero)
        }
    }

    fun maxPanFractions(zoom: Float): Pair<Float, Float> {
        val cellWidth = cellSize.width.toFloat().coerceAtLeast(1f)
        val cellHeight = cellSize.height.toFloat().coerceAtLeast(1f)
        if (sourceSize.width <= 0 || sourceSize.height <= 0) {
            val fallback = (zoom - 1f) / 2f
            return fallback to fallback
        }
        val sideways = transform.clockwiseQuarterTurns % 2 != 0
        val visibleSourceWidth = if (sideways) sourceSize.height else sourceSize.width
        val visibleSourceHeight = if (sideways) sourceSize.width else sourceSize.height
        val coverScale = max(cellWidth / visibleSourceWidth, cellHeight / visibleSourceHeight)
        val maxX = ((visibleSourceWidth * coverScale * zoom - cellWidth) / (2f * cellWidth))
            .coerceAtLeast(0f)
        val maxY = ((visibleSourceHeight * coverScale * zoom - cellHeight) / (2f * cellHeight))
            .coerceAtLeast(0f)
        return maxX to maxY
    }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        onSelect()
        val newScale = (transform.zoom * zoomChange).coerceIn(1f, 5f)
        val (maxX, maxY) = maxPanFractions(newScale)
        onTransform(
            transform.copy(
                zoom = newScale,
                offsetX = (transform.offsetX + panChange.x / cellSize.width.coerceAtLeast(1))
                    .coerceIn(-maxX, maxX),
                offsetY = (transform.offsetY + panChange.y / cellSize.height.coerceAtLeast(1))
                    .coerceIn(-maxY, maxY),
            ),
        )
    }

    val shape = RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GeyiSurface)
            .border(if (selected) 2.dp else 0.dp, if (selected) GeyiIce else Color.Transparent, shape)
            .onSizeChanged { cellSize = it }
            .testTag("collage_photo_$index")
            .semantics {
                stateDescription = "顺时针旋转 ${transform.clockwiseQuarterTurns * 90}° · 缩放 ${(transform.zoom * 100).roundToInt()}% · 偏移 ${(transform.offsetX * 100).roundToInt()},${(transform.offsetY * 100).roundToInt()} · 原图 ${sourceSize.width}×${sourceSize.height}"
            }
            .clickable(role = Role.Image, onClick = onSelect)
            .transformable(transformState),
    ) {
        AsyncImage(
            model = previewRequest,
            contentDescription = "拼图中的第 ${index + 1} 张照片，可缩放和移动",
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val cellWidth = constraints.maxWidth
                    val cellHeight = constraints.maxHeight
                    val sourceWidth = sourceSize.width.coerceAtLeast(1)
                    val sourceHeight = sourceSize.height.coerceAtLeast(1)
                    val sideways = transform.clockwiseQuarterTurns % 2 != 0
                    val visibleSourceWidth = if (sideways) sourceHeight else sourceWidth
                    val visibleSourceHeight = if (sideways) sourceWidth else sourceHeight
                    val coverScale = max(
                        cellWidth.toFloat() / visibleSourceWidth,
                        cellHeight.toFloat() / visibleSourceHeight,
                    )
                    val imageWidth = (sourceWidth * coverScale * transform.zoom).roundToInt()
                        .coerceAtLeast(1)
                    val imageHeight = (sourceHeight * coverScale * transform.zoom).roundToInt()
                        .coerceAtLeast(1)
                    val image = measurable.measure(Constraints.fixed(imageWidth, imageHeight))
                    layout(cellWidth, cellHeight) {
                        val x = ((cellWidth - imageWidth) / 2f + transform.offsetX * cellWidth).roundToInt()
                        val y = ((cellHeight - imageHeight) / 2f + transform.offsetY * cellHeight).roundToInt()
                        image.placeRelative(x, y)
                    }
                }
                .graphicsLayer { rotationZ = transform.clockwiseQuarterTurns * 90f },
            contentScale = ContentScale.FillBounds,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(7.dp)
                    .size(23.dp)
                    .align(Alignment.TopStart)
                    .testTag("selected_photo_badge_$index")
                    .background(GeyiIce, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    color = GeyiSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RatioSelector(
    selectedRatio: CollageRatio,
    onSelect: (CollageRatio) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ratio_selector"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 1.dp),
    ) {
        items(supportedCollageRatios, key = { it.id }) { ratio ->
            val selected = ratio.id == selectedRatio.id
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = if (selected) 0.13f else 0.045f))
                    .border(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) GeyiIce else GeyiGlassBorder,
                        shape,
                    )
                    .testTag("ratio_${ratio.id}")
                    .clickable(role = Role.Button) { onSelect(ratio) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .aspectRatio(ratio.value.coerceIn(0.55f, 1.8f))
                        .border(
                            1.5.dp,
                            if (selected) GeyiIce else GeyiTextSecondary,
                            RoundedCornerShape(3.dp),
                        ),
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = ratio.title,
                    color = if (selected) GeyiIce else GeyiTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SpacingControls(
    spacingPercent: Int,
    blackSpacing: Boolean,
    onSpacingChange: (Int) -> Unit,
    onBlackSpacingChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("间距大小", color = GeyiTextPrimary, fontSize = 13.sp)
        Text("$spacingPercent%", color = GeyiIce, fontSize = 14.sp)
    }
    com.pianyu.studio.ui.StudioSlider(
        value = spacingPercent.toFloat(),
        onValueChange = { onSpacingChange(it.roundToInt()) },
        valueRange = 0f..8f,
        steps = 7,
        modifier = Modifier.fillMaxWidth().testTag("spacing_slider"),
    )
    Spacer(Modifier.height(20.dp))
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, GeyiGlassBorder, shape)
            .testTag("spacing_color_toggle")
            .semantics { stateDescription = if (blackSpacing) "黑色间距" else "白色间距" }
            .clickable(role = Role.Button) { onBlackSpacingChange(!blackSpacing) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).background(
                    if (blackSpacing) Color.Black else Color.White,
                    CircleShape,
                ).border(1.dp, GeyiTextSecondary, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "间距颜色 · ${if (blackSpacing) "黑色" else "白色"}",
                color = GeyiTextPrimary,
                fontSize = 13.sp,
            )
        }
        Text("点击切换", color = GeyiTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun LayoutOption(
    template: CollageTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0xFFEAF3EF) else Color(0xFFF7F9F8))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) GeyiIce else StudioPalette.Outline,
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CollageLayout(
            template = template,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            gap = 2.dp,
        ) { index, modifier ->
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        // Every cell must remain visible on the light surface.
                        // Alternating opaque tones also distinguish adjacent slots.
                        if (selected) {
                            if (index % 2 == 0) Color(0xFF3B696B) else Color(0xFF678987)
                        } else {
                            if (index % 2 == 0) Color(0xFF536B70) else Color(0xFF819695)
                        },
                    ),
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = template.title,
            color = if (selected) GeyiIce else GeyiTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CollageLayout(
    template: CollageTemplate,
    modifier: Modifier,
    gap: Dp,
    slot: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            template.cells.indices.forEach { index ->
                slot(index, Modifier)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gapPx = gap.roundToPx()

        data class Placement(val x: Int, val y: Int, val width: Int, val height: Int)

        val placements = template.cells.map { cell ->
            val rawLeft = (cell.left * width).roundToInt()
            val rawTop = (cell.top * height).roundToInt()
            val rawRight = ((cell.left + cell.width) * width).roundToInt()
            val rawBottom = ((cell.top + cell.height) * height).roundToInt()
            val insetLeft = if (cell.left > 0.0001f) gapPx / 2 else 0
            val insetTop = if (cell.top > 0.0001f) gapPx / 2 else 0
            val insetRight = if (cell.left + cell.width < 0.9999f) gapPx - gapPx / 2 else 0
            val insetBottom = if (cell.top + cell.height < 0.9999f) gapPx - gapPx / 2 else 0
            Placement(
                x = rawLeft + insetLeft,
                y = rawTop + insetTop,
                width = (rawRight - rawLeft - insetLeft - insetRight).coerceAtLeast(1),
                height = (rawBottom - rawTop - insetTop - insetBottom).coerceAtLeast(1),
            )
        }

        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = placements[index]
            measurable.measure(Constraints.fixed(placement.width, placement.height))
        }

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}
