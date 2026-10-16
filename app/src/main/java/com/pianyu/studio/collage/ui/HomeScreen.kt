package com.pianyu.studio.collage.ui

import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pianyu.studio.collage.ui.components.GlassPanel
import com.pianyu.studio.collage.ui.components.FrostedButton
import com.pianyu.studio.collage.ui.components.BrandMark
import com.pianyu.studio.collage.ui.theme.GeyiGlassBorder
import com.pianyu.studio.collage.ui.theme.GeyiIce
import com.pianyu.studio.collage.ui.theme.GeyiSurface
import com.pianyu.studio.collage.ui.theme.GeyiSurfaceVariant
import com.pianyu.studio.collage.ui.theme.GeyiTextPrimary
import com.pianyu.studio.collage.ui.theme.GeyiTextSecondary

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    photoUris: List<String>,
    onStart: () -> Unit,
    onRemove: (Int) -> Unit,
    onContinue: () -> Unit,
    showBrandMark: Boolean = true,
    onBrandMarkBounds: (Rect) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 22.dp),
    ) {

        Spacer(modifier = Modifier.height(20.dp))
        if (photoUris.isEmpty()) {
            EmptyHome(onStart = onStart)
        } else {
            SelectedPhotos(
                photoUris = photoUris,
                onRemove = onRemove,
                onReselect = onStart,
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun Header(showBrandMark: Boolean, onBrandMarkBounds: (Rect) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("COLLAGE / 01", color = GeyiIce, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text("拼图工作台", color = GeyiTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyHome(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("让照片组成新的画面", color=GeyiTextPrimary, fontSize=24.sp, fontWeight=FontWeight.SemiBold)
        Text("选择 2–9 张照片，然后调整布局、比例和间距。", color=GeyiTextSecondary, fontSize=14.sp)
        GlassPanel(Modifier.fillMaxWidth(), stronger=true) {
            Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                FrostedButton("选择照片", Modifier.fillMaxWidth(), onClick=onStart)
                Text("支持替换、旋转和缩放每张照片。完成后可继续加签名。", color=GeyiTextSecondary, fontSize=13.sp)
            }
        }
    }

}

@Composable
private fun GlassCollagePreview() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewPane(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxSize(),
            colors = listOf(Color(0xFF607486), Color(0xFF344856)),
            label = "光影",
        )
        Column(
            modifier = Modifier.weight(0.85f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = listOf(Color(0xFF536572), GeyiSurfaceVariant),
            )
            PreviewPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = listOf(Color(0xFF415564), GeyiSurface),
                label = "＋",
            )
        }
    }
}

@Composable
private fun PreviewPane(
    modifier: Modifier,
    colors: List<Color>,
    label: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape),
        contentAlignment = if (label == "＋") Alignment.Center else Alignment.BottomStart,
    ) {
        if (label != null) {
            Text(
                text = label,
                modifier = if (label == "＋") Modifier else Modifier.padding(16.dp),
                color = Color.White,
                fontSize = if (label == "＋") 30.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SelectedPhotos(
    photoUris: List<String>,
    onRemove: (Int) -> Unit,
    onReselect: () -> Unit,
    onContinue: () -> Unit,
) {
    val canContinue = photoUris.size in 2..9
    Column(modifier = Modifier.fillMaxSize()) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "已选择 ${photoUris.size} 张",
                        color = GeyiTextPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            photoUris.size < 2 -> "至少还需要 1 张照片"
                            photoUris.size > 9 -> "最多支持 9 张照片"
                            else -> "可以进入布局编辑"
                        },
                        color = GeyiTextSecondary,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = onReselect) {
                    Text(text = "重新选择", color = GeyiIce)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(photoUris, key = { _, uri -> uri }) { index, uri ->
                SelectedPhotoTile(
                    index = index,
                    uri = uri,
                    onRemove = { onRemove(index) },
                )
            }
        }

        FrostedButton(
            text = if (photoUris.size > 9) "请保留 2–9 张照片" else "下一步：编辑拼图",
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            onClick = onContinue,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SelectedPhotoTile(
    index: Int,
    uri: String,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, GeyiGlassBorder, shape),
    ) {
        AsyncImage(
            model = uri.toUri(),
            contentDescription = "已选择的第 ${index + 1} 张照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        NumberBadge(index = index, modifier = Modifier.align(Alignment.TopStart))
        TextButton(
            onClick = onRemove,
            modifier = Modifier
                .padding(4.dp)
                .size(36.dp)
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = "×", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun NumberBadge(index: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(25.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${index + 1}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}


