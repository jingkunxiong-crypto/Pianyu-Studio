package com.pianyu.studio.watermark.ui

import com.pianyu.studio.ui.*
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pianyu.studio.R
import com.pianyu.studio.watermark.ui.theme.*

@Composable
fun StudioBackdrop() {
    StudioBackground()
}

// A translucent layer over the blurred app backdrop. The photo canvas is never blurred.
@Composable
fun GlassPanel(modifier: Modifier = Modifier, bright: Boolean = false,
               content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.studioGlass(bright), content = content)
}

@Composable
fun GlassButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false,
                icon: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    StudioAction(label, modifier, primary, enabled, onClick)
}

@Composable
fun StudioIcon(name: String, modifier: Modifier = Modifier, tint: Color = StudioPalette.Ink) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.65f)
            fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(tint, Offset(x,y), Offset(xx,yy), 1.65f)
            fun path(vararg points: Float) {
                val p = Path().apply { moveTo(points[0],points[1]); for(i in 2 until points.size step 2) lineTo(points[i],points[i+1]) }
                drawPath(p, tint, style = stroke)
            }
            when(name) {
                "home" -> { path(3f,11f,12f,3f,21f,11f); path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f) }
                "pen" -> { path(5f,16f,16f,5f,20f,9f,9f,20f,4f,21f,5f,16f); line(13f,8f,17f,12f) }
                "photo" -> { drawRoundRect(tint, Offset(3f,4f), androidx.compose.ui.geometry.Size(18f,16f), androidx.compose.ui.geometry.CornerRadius(2f), style=stroke); path(4f,17f,9f,11f,13f,15f,17f,10f,20f,14f); drawCircle(tint,1.5f,Offset(8f,8f)) }
                "plus" -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
                "back" -> path(15f,4f,7f,12f,15f,20f)
                "arrow" -> path(9f,5f,16f,12f,9f,19f)
                "save" -> { line(12f,3f,12f,15f); path(7f,10f,12f,15f,17f,10f); path(4f,15f,4f,21f,20f,21f,20f,15f) }
                "share" -> { line(7f,11f,17f,5f); line(7f,13f,17f,19f); drawCircle(tint,2.7f,Offset(5f,12f),style=stroke); drawCircle(tint,2.7f,Offset(19f,4f),style=stroke); drawCircle(tint,2.7f,Offset(19f,20f),style=stroke) }
                "undo" -> { path(8f,4f,3f,9f,8f,14f); path(3f,9f,14f,9f,19f,12f,19f,17f,15f,20f,9f,20f) }
                "clear" -> { line(4f,6f,20f,6f); path(8f,6f,8f,3f,16f,3f,16f,6f); path(6f,6f,7f,21f,17f,21f,18f,6f); line(10f,10f,10f,17f);line(14f,10f,14f,17f) }
                "settings" -> { drawCircle(tint,7f,Offset(12f,12f),style=stroke); drawCircle(tint,2.5f,Offset(12f,12f),style=stroke); for(i in 0..7) { val a=i*Math.PI/4; line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) } }
                else -> drawCircle(tint, 8f, Offset(12f,12f),style=stroke)
            }
        }
    }
}

@Composable
fun DemoSignature(modifier: Modifier = Modifier, color: Color = SoftWhite) {
    Canvas(modifier.semantics { contentDescription = "绀轰緥鎵嬪啓绛惧悕 Lin" }) {
        scale(size.width / 160f, size.height / 70f, Offset.Zero) {
            val p = Path().apply {
                moveTo(32f,62f); cubicTo(42f,40f,61f,7f,65f,5f)
                cubicTo(68f,2f,40f,56f,16f,60f); cubicTo(1f,64f,20f,48f,48f,43f)
                cubicTo(70f,39f,89f,30f,93f,30f); cubicTo(86f,44f,84f,53f,94f,45f)
                cubicTo(108f,29f,105f,32f,103f,45f); cubicTo(117f,26f,120f,31f,115f,43f)
                cubicTo(127f,29f,130f,34f,128f,42f); cubicTo(124f,49f,140f,43f,154f,39f)
            }
            drawPath(p, color, style = Stroke(2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(color,1.5f,Offset(96f,23f))
        }
    }
}

@Composable
fun Checkerboard(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(if(dark) Color(0xFF74818C) else Color(0xFFBFC9CF))) {
        Canvas(Modifier.matchParentSize()) {
            val cell = 10.dp.toPx()
            for (y in 0..(size.height / cell).toInt()) for (x in 0..(size.width / cell).toInt()) {
                if ((x+y)%2 == 0) drawRect(Color.White.copy(alpha = if(dark) .12f else .25f), Offset(x*cell,y*cell), androidx.compose.ui.geometry.Size(cell,cell))
            }
        }
        content()
    }
}
