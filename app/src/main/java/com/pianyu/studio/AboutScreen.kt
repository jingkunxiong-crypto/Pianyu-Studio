package com.pianyu.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pianyu.studio.ui.*

@Composable
internal fun AboutScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val version=remember {
        @Suppress("DEPRECATION")
        val info=context.packageManager.getPackageInfo(context.packageName,0)
        info.versionName ?: "未知版本"
    }
    var linkError by remember {mutableStateOf(false)}
    fun open(path:String="") {
        runCatching {uriHandler.openUri("https://github.com/jingkunxiong-crypto/Pianyu-Studio$path")}
            .onFailure {linkError=true}
    }
    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp)) {
            StudioToolbar("关于",onBack)
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Image(painterResource(R.drawable.studio_icon),"Pianyu Studio 图标",Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
                        Text("Pianyu Studio",style=MaterialTheme.typography.headlineSmall)
                        Text("留住灵感，慢慢完成。",color=StudioPalette.Muted)
                        Text("版本 $version",style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Accent)
                    }
                }
                item {
                    AboutCard("作者") {
                        Text("Sakuya_Jack",style=MaterialTheme.typography.titleLarge,color=StudioPalette.Accent)
                        Text("把创意编辑、拼图、署名和配色收集放在同一个工作室。",color=StudioPalette.Muted)
                    }
                }
                item {
                    AboutCard("四种创作方式") {
                        AboutFeature("创意编辑","黑白之间留住色彩，自由拖动、缩放和切换留彩区域形状。")
                        AboutFeature("自由拼图","组合 2–9 张照片，调整布局、比例与图片间距。")
                        AboutFeature("签名水印","手写、提取和管理签名，为单张或多张照片署名。")
                        AboutFeature("色彩取样卡","提取照片代表色，生成带圆形色块、色号和可选署名的作品。")
                    }
                }
                item {
                    AboutCard("照片与隐私") {
                        Text("图片编辑与签名处理在设备本地完成。通过系统照片选择器读取你选择的照片，保存作品时创建新副本。",color=StudioPalette.Muted)
                        Text("局部留彩默认以 JPEG 80% 质量保存；相机画幅支持 PNG 分块无损保存和 JPEG 80%。签名库在本应用内共享，旧版独立应用的签名可通过透明 PNG 导入。",color=StudioPalette.Muted)
                    }
                }
                item {
                    AboutCard("软件信息") {
                        AboutFeature("系统要求","Android 8.0 及以上")
                        AboutFeature("版本状态","当前为预览版，功能持续完善中。")
                        AboutFeature("项目来源","融合片屿、Geyi 与 Signature Watermark Studio 的创作能力，统一界面和交互。")
                    }
                }
                item {
                    StudioAction("GitHub · 项目主页",Modifier.fillMaxWidth(),primary=true,onClick={open()})
                    Spacer(Modifier.height(8.dp))
                    StudioAction("版本与更新说明",Modifier.fillMaxWidth(),onClick={open("/releases")})
                    TextButton(onClick={open("/issues")},modifier=Modifier.fillMaxWidth()) {Text("反馈问题与建议")}
                    TextButton(onClick={open("/blob/main/docs/THIRD_PARTY_NOTICES.md")},modifier=Modifier.fillMaxWidth()) {Text("来源与许可说明")}
                }
            }
        }
    }
    if(linkError) AlertDialog(onDismissRequest={linkError=false},title={Text("无法打开链接")},
        text={Text("请检查是否已安装浏览器后重试。")},confirmButton={TextButton(onClick={linkError=false}) {Text("好的")}})
}

@Composable
private fun AboutCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().studioGlass().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun AboutFeature(title:String,description:String) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=StudioPalette.Muted)
    }
}
