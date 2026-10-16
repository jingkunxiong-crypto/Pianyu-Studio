package com.pianyu.studio.collage.ui.components

import com.pianyu.studio.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pianyu.studio.collage.ui.theme.GeyiGlass
import com.pianyu.studio.collage.ui.theme.GeyiGlassBorder
import com.pianyu.studio.collage.ui.theme.GeyiGraphite
import com.pianyu.studio.collage.ui.theme.GeyiIce

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    stronger: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.studioGlass(stronger), content = content)
}

@Composable
fun FrostedButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    StudioAction(text, modifier, primary=true, enabled=enabled, onClick=onClick)
}
