package com.pianyu.studio.watermark.ui

import com.pianyu.studio.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pianyu.studio.watermark.ui.theme.*

// Popup windows render independently from the activity's blurred content layer.
val LocalGlassOverlayVisibility = staticCompositionLocalOf<(Any, Boolean) -> Unit> { { _, _ -> } }

internal fun Modifier.glassOverlay(): Modifier {
    return studioGlass(true)
}

/** All app dialogs share the same translucent surface, type and action treatment. */
@Composable
fun GlassDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
                text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
                dismissButton: (@Composable () -> Unit)? = null) {
    val notifyOverlay = LocalGlassOverlayVisibility.current
    val token = remember { Any() }
    DisposableEffect(token) {
        notifyOverlay(token, true)
        onDispose { notifyOverlay(token, false) }
    }
    Dialog(onDismissRequest, properties=DialogProperties(usePlatformDefaultWidth=true)) {
        CompositionLocalProvider(LocalContentColor provides StudioPalette.Ink) {
            Column(Modifier.fillMaxWidth().heightIn(max=LocalConfiguration.current.screenHeightDp.dp * .82f)
                .glassOverlay().padding(22.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        CompositionLocalProvider(LocalContentColor provides StudioPalette.Ink.copy(alpha=.9f)) { text() }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.End),
                    verticalAlignment=Alignment.CenterVertically) {
                    if(dismissButton!=null) GlassPanel(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center) { dismissButton() }
                    }
                    GlassPanel(if(dismissButton!=null) Modifier.weight(1f) else Modifier.widthIn(min=100.dp),bright=true) {
                        Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center) { confirmButton() }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit,
                      content: @Composable ColumnScope.() -> Unit) {
    val notifyOverlay=LocalGlassOverlayVisibility.current
    val token=remember {Any()}
    DisposableEffect(expanded) {
        notifyOverlay(token,expanded)
        onDispose {notifyOverlay(token,false)}
    }
    DropdownMenu(expanded=expanded,onDismissRequest=onDismissRequest,
        modifier=Modifier.widthIn(min=160.dp).glassOverlay(),
        shape=RoundedCornerShape(20.dp),containerColor=Color.Transparent,
        tonalElevation=0.dp,shadowElevation=0.dp,content=content)
}
