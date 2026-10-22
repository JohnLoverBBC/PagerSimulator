package com.example.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Notification LED with glow; blinks when there is unread mail. */
@Composable
fun Led(color: Color, on: Boolean, blink: Boolean, d: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "led")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    Box(
        modifier.size(d).alpha(if (on && blink) a else 1f)
            .drawBehind { if (on) drawCircle(color.copy(alpha = 0.35f), radius = this.size.minDimension * 0.95f) }
            .background(
                if (on) Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), color, color.copy(alpha = 0.7f)))
                else Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color(0xFF120808))), CircleShape)
            .border(1.dp, Color(0xFF0B0B0C), CircleShape)
    )
}

/** Ridged scroll roller. Drag it up/down. */
@Composable
fun Wheel(modifier: Modifier, base: Color, onDelta: (Float) -> Unit) {
    var off by remember { mutableFloatStateOf(0f) }
    val st = rememberDraggableState { d -> off += d; onDelta(d) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.shadow(4.dp, shape).clip(shape).draggable(st, Orientation.Vertical)
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(Color(0xFF060606), base, Color(0xFFA0A5AF), base, Color(0xFF040404))))
                val sp = 14f
                var y = ((off % sp) + sp) % sp
                while (y < this.size.height) {
                    drawLine(Color(0x77000000), Offset(0f, y), Offset(this.size.width, y), 3f)
                    drawLine(Color(0x33FFFFFF), Offset(0f, y + 3f), Offset(this.size.width, y + 3f), 2f)
                    y += sp
                }
            }
    )
}

@Composable
fun ScrollBar(scroll: ScrollState, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val mx = scroll.maxValue
        drawRect(color.copy(alpha = 0.15f))
        if (mx > 0) {
            val view = size.height
            val th = (view * view / (view + mx)).coerceAtLeast(14f)
            val y = (view - th) * scroll.value / mx
            drawRoundRect(color.copy(alpha = 0.8f), Offset(0f, y), Size(size.width, th), CornerRadius(size.width / 2))
        }
    }
}

/** Scrollable 128x256 image viewer with a scroll bar. Pixels are drawn in the device's ink colour. */
@Composable
fun ImageView(img: String, ink: Color, scroll: ScrollState, modifier: Modifier) {
    val bmp = remember(img, ink) { Sketch.decode(img, ink) }
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bmp, null, Modifier.widthIn(max = 192.dp).fillMaxWidth().padding(end = 10.dp).aspectRatio(0.5f),
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
        }
        ScrollBar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp), ink)
    }
}

/** Resistive touch sketch pad (draws into the 128x256 Sketch bitmap). */
@Composable
fun DrawPad(modifier: Modifier, erase: Boolean, onTouch: () -> Unit) {
    val eraseNow by rememberUpdatedState(erase)
    val touch by rememberUpdatedState(onTouch)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                touch()
                fun px(o: Offset) = Pair(
                    (o.x / size.width * Sketch.W).toInt().coerceIn(0, Sketch.W - 1),
                    (o.y / size.height * Sketch.H).toInt().coerceIn(0, Sketch.H - 1))
                var p = px(down.position)
                Sketch.dot(p.first, p.second, eraseNow)
                do {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull()
                    if (c != null && c.pressed) {
                        val q = px(c.position)
                        Sketch.line(p.first, p.second, q.first, q.second, eraseNow)
                        p = q; c.consume()
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
    ) {
        if (Sketch.tick >= 0) drawImage(Sketch.image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
    }
}

/** Little recessed stylus slot. The stylus slides out when used and back in when the note is sent. */
@Composable
fun StylusSlot(inside: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.height(18.dp).background(Color(0xFF07080A), shape).border(1.dp, Color.Black, shape)
            .clickable(onClick = onTap).padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(inside, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            Box(Modifier.fillMaxWidth().height(10.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFD9DDE4), Color(0xFF666C79))), RoundedCornerShape(5.dp)))
        }
    }
}
