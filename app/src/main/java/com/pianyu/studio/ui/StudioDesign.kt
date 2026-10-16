package com.pianyu.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object StudioPalette {
    val Background = Color(0xFFE9EFED)
    val Ink = Color(0xFF263437)
    val Muted = Color(0xFF627578)
    val Accent = Color(0xFF3B696B)
    val Surface = Color(0xFFF7F9F8)
    val Container = Color(0xFFDDE8E3)
    val Outline = Color(0xFFB9CCC5)
}

fun Modifier.studioGlass(strong: Boolean = false): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return clip(shape).background(Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if(strong) .94f else .78f),
        Color.White.copy(alpha = if(strong) .72f else .48f)
    ))).border(1.dp, Color.White.copy(alpha=.9f), shape)
}

@Composable
fun StudioBackground() {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
        Color(0xFFF4F7F5), StudioPalette.Background, Color(0xFFDCE8E3)
    ))))
}

@Composable
fun StudioAction(label: String, modifier: Modifier = Modifier, primary: Boolean = false,
                 enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .98f else 1f,spring(dampingRatio=.85f,stiffness=600f),label="action press")
    val animated = modifier.graphicsLayer {scaleX=scale;scaleY=scale}.heightIn(min=52.dp)
    if(primary) Button(onClick, animated, enabled=enabled, interactionSource=interaction, shape=RoundedCornerShape(18.dp)) { Text(label) }
    else OutlinedButton(onClick, animated, enabled=enabled, interactionSource=interaction, shape=RoundedCornerShape(18.dp)) { Text(label) }
}

@Composable
fun StudioToolbar(title: String, onBack: () -> Unit, action: String? = null,
                  enabled: Boolean = true, backEnabled: Boolean = true, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp), verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=onBack, enabled=backEnabled, contentPadding=PaddingValues(8.dp)) { Text("‹ 返回") }
        Text(title, Modifier.weight(1f).padding(horizontal=8.dp), style=MaterialTheme.typography.titleLarge)
        if(action != null) FilledTonalButton(onClick=onAction, enabled=enabled, shape=RoundedCornerShape(16.dp)) { Text(action) }
    }
}
