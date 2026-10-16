package com.pianyu.studio.photoedit

import com.pianyu.studio.ui.*
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pianyu.studio.media.RecentWorks
import com.pianyu.studio.photoedit.editing.*
import com.pianyu.studio.watermark.ui.*
import com.pianyu.studio.watermark.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun PhotoEditScreen(uri: String, model: PhotoEditViewModel, onBack: () -> Unit, onSign: (Uri) -> Unit) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var adjustmentName by rememberSaveable { mutableStateOf(Adjustment.Exposure.name) }
    var groupName by rememberSaveable { mutableStateOf(AdjustmentGroup.Light.name) }
    var original by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val adjustment = Adjustment.valueOf(adjustmentName)
    fun back() { if (!state.busy) { if (state.settings != EditSettings() && state.saved == null) discard = true else onBack() } }
    BackHandler(onBack = ::back)
    LaunchedEffect(uri) { model.open(uri) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) model.export() else model.reportError("保存照片需要存储权限。")
    }
    fun save() {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else model.export()
    }
    Box(Modifier.fillMaxSize()) {
        StudioBackdrop()
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                StudioToolbar("照片编辑", ::back, "重置", !state.busy) {model.update(EditSettings())}
            }
            item {
                val preview = if (original) state.photo?.preview else state.preview
                if (preview != null) Image(preview.asImageBitmap(), "照片编辑预览", Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    if (state.busy) CircularProgressIndicator() else Text("照片未能加载")
                }
                if (state.rendering || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { original = !original }) { Text(if (original) "查看编辑效果" else "对比原图") }
            }
            if (state.photo != null) {
                item {
                    Text("本地滤镜", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BuiltInFilters.presets.forEach { preset -> FilterChip(selected = state.settings.filterId == preset.id,
                            onClick = { model.update(state.settings.applyPreset(preset)) }, label = { Text(preset.name) }, enabled = !state.busy) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustmentGroup.entries.forEach { group -> FilterChip(selected = groupName == group.name,
                            onClick = { groupName = group.name; adjustmentName = Adjustment.entries.first { it.group == group }.name }, label = { Text(group.label) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Adjustment.entries.filter { it.group.name == groupName }.forEach { option ->
                            FilterChip(selected = adjustment == option, onClick = { adjustmentName = option.name }, label = { Text(option.label) })
                        }
                    }
                    Text("${adjustment.label}  ${state.settings.valueFor(adjustment)}", color = Ice)
                    Slider(value = state.settings.valueFor(adjustment).toFloat(),
                        onValueChange = { model.update(state.settings.withValue(adjustment, it.roundToInt())) },
                        valueRange = adjustment.minimum.toFloat()..adjustment.maximum.toFloat(), enabled = !state.busy)
                }
                item {
                    Text("裁剪与旋转", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CropAspect.entries.forEach { aspect -> FilterChip(selected = state.settings.cropAspect == aspect,
                            onClick = { model.update(state.settings.copy(cropAspect = aspect)) }, label = { Text(aspect.label) }, enabled = !state.busy) }
                    }
                    TextButton(onClick = { model.update(state.settings.copy(rotationDegrees = (state.settings.rotationDegrees + 90) % 360)) }, enabled = !state.busy) { Text("向右旋转 90°") }
                    Text("按画面中心裁剪。导出 JPG 副本，长边最多 4096 像素。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                item { GlassButton(if (state.busy) "正在保存…" else "保存副本", Modifier.fillMaxWidth(), primary = true, enabled = !state.busy, onClick = ::save) }
                state.saved?.let { saved -> item {
                    Text("已保存 · ${saved.width} × ${saved.height}", color = Ice)
                    GlassButton("继续加签名", Modifier.fillMaxWidth(), onClick = { onSign(saved.uri) })
                    TextButton(onClick = { runCatching { RecentWorks.share(context, saved.uri) }.onFailure { model.reportError("无法打开分享应用。") } }) { Text("分享作品") }
                } }
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError, text = { Text(message) },
        confirmButton = { TextButton(onClick = model::clearError) { Text("知道了") } }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("返回创作首页？") },
        text = { Text("当前编辑还没有保存为照片。") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("返回首页") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
