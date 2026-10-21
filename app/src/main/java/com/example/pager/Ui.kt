package com.example.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val Mono = FontFamily.Monospace

// ---------------------------------------------------------------------------------------------
// state that lives as long as the pager on screen
// ---------------------------------------------------------------------------------------------

class PagerUi {
    var composing by mutableStateOf(false)
    var text by mutableStateOf("")
    var toIdx by mutableIntStateOf(0)

    // CM Paper Pager canvas: 128 x 256, 1 bit
    val bits = BooleanArray(PagerImg.W * PagerImg.H)
    var bitsVer by mutableIntStateOf(0)
    var rollAcc = 0f

    fun recips(): List<String> = (listOf("ALL") + Mesh.peers.values + PagerCore.messages.map { it.from })
        .filter { it != PagerCore.callsign }.distinct()

    fun to(): String = recips().getOrElse(toIdx) { "ALL" }

    fun nextTo() {
        val r = recips()
        toIdx = (toIdx + 1) % r.size.coerceAtLeast(1)
        composing = true
        PagerCore.flash("TO:${to()}")
    }

    fun onKey(e: KeyEv) {
        when (e) {
            is KeyEv.Ch -> { if (text.length < 120) text += e.c; composing = true }
            is KeyEv.Rep -> { text = text.dropLast(1) + e.c; composing = true }
            KeyEv.Back -> { text = text.dropLast(1); composing = true }
            KeyEv.Enter -> sendText()
        }
    }

    fun sendText() {
        if (text.isBlank()) { composing = false; return }
        val r = Mesh.send(to(), text.trim())
        PagerCore.flash(r)
        if (r.startsWith("SENT")) { text = ""; composing = false }
    }

    // ---- stylus drawing ----
    fun beginPen() { composing = true; PagerCore.stylusOut = true }

    private fun stamp(x: Int, y: Int) {
        for (dx in 0..1) for (dy in 0..1) {
            val px = x + dx; val py = y + dy
            if (px in 0 until PagerImg.W && py in 0 until PagerImg.H) bits[py * PagerImg.W + px] = true
        }
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            stamp(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        bitsVer++
    }

    fun clearCanvas() { bits.fill(false); bitsVer++ }
    fun clearInput() { text = ""; clearCanvas() }

    /** Send the note as a 128x256 image. The stylus goes back in its compartment once it is sent. */
    fun sendDrawing() {
        if (bits.none { it }) { PagerCore.flash("NOTE IS EMPTY"); return }
        val r = Mesh.send(to(), "", false, PagerImg.encode(bits))
        PagerCore.flash(r)
        if (r.startsWith("SENT")) {
            clearCanvas(); composing = false
            PagerCore.stylusOut = false
        }
    }
}

private class Btn(val label: String, val act: () -> Unit)

/** Everything one frame of the pager needs. */
private class Ctx(
    val m: Model, val ui: PagerUi, val idx: Int, val cur: Msg?, val total: Int,
    val scroll: ScrollState, val scope: CoroutineScope,
    val unread: Boolean, val ledA: Float, val land: Boolean,
    val onFunc: () -> Unit, val onRot: () -> Unit
) {
    val ledC: Color
        get() = when {
            m.countLed -> countLedColor(total)
            m.feature == Feature.LED_COLOR -> LED_COLORS[PagerCore.ledIdx % LED_COLORS.size]
            else -> LED_COLORS[0]
        }
    val ledLit: Boolean get() = if (m.countLed) total > 0 else unread
    private val bl: Boolean get() = m.feature == Feature.LCD_BACKLIGHT && PagerCore.lcdLight
    val lt: Color get() = if (bl) m.litTop else m.lcdTop
    val lb: Color get() = if (bl) m.litBottom else m.lcdBottom
    val ink: Color get() = if (bl) m.litInk else m.ink
    val hasImg: Boolean get() = cur?.img != null

    fun scrollImg(dir: Int) { scope.launch { scroll.animateScrollBy(dir * 150f) } }

    /** scroll wheel: scrolls a received image, or steps through messages when there is no image */
    fun roll(d: Float) {
        if (hasImg && !ui.composing) { scope.launch { scroll.scrollBy(d * 2.5f) }; return }
        ui.rollAcc += d
        if (ui.rollAcc > 45f) { ui.rollAcc = 0f; if (idx < total - 1) PagerCore.index = idx + 1 }
        else if (ui.rollAcc < -45f) { ui.rollAcc = 0f; if (idx > 0) PagerCore.index = idx - 1 }
    }

    fun featureLabel(): String? = when (m.feature) {
        Feature.LED_COLOR -> "LED"; Feature.KB_BACKLIGHT -> "LIGHT"; Feature.LCD_BACKLIGHT -> "BKLT"; Feature.NONE -> null
    }

    fun toggleFeature() {
        when (m.feature) {
            Feature.LED_COLOR -> { PagerCore.ledIdx = (PagerCore.ledIdx + 1) % LED_COLORS.size; PagerCore.flash("LED: ${LED_NAMES[PagerCore.ledIdx]}") }
            Feature.KB_BACKLIGHT -> { PagerCore.kbLight = (PagerCore.kbLight + 1) % 3; PagerCore.flash("KEYLIGHT ${listOf("OFF", "DIM", "BRIGHT")[PagerCore.kbLight]}") }
            Feature.LCD_BACKLIGHT -> { PagerCore.lcdLight = !PagerCore.lcdLight; PagerCore.flash("BACKLIGHT ${if (PagerCore.lcdLight) "ON" else "OFF"}") }
            Feature.NONE -> {}
        }
        PagerCore.saveMeta()
    }

    /** Buttons for models that have a keyboard. Wheel models scroll with the wheel, the others get arrow keys. */
    fun buttons(): List<Btn> {
        val l = mutableListOf<Btn>()
        l += Btn("◄") { ui.composing = false; if (idx < total - 1) PagerCore.index = idx + 1 }
        l += Btn("►") { ui.composing = false; if (idx > 0) PagerCore.index = idx - 1 }
        if (!m.wheel) {
            l += Btn("▲") { scrollImg(-1) }
            l += Btn("▼") { scrollImg(1) }
        }
        l += Btn("DEL") { if (ui.composing) ui.text = "" else PagerCore.delete(idx) }
        l += Btn("TO") { ui.nextTo() }
        featureLabel()?.let { fl -> l += Btn(fl) { toggleFeature() } }
        l += Btn("ROT", onRot)
        l += Btn("FUNC", onFunc)
        return l
    }
}

// ---------------------------------------------------------------------------------------------
// top level screen
// ---------------------------------------------------------------------------------------------

@Composable
fun PagerScreen(onFunc: () -> Unit) {
    val m = PagerCore.model
    val msgs = PagerCore.messages
    val total = msgs.size
    val idx = PagerCore.index.coerceIn(0, maxOf(0, total - 1))
    val cur = msgs.getOrNull(idx)
    val ui = remember(m.id) { PagerUi() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(cur?.id, cur?.read) { if (cur != null && !cur.read) PagerCore.markRead(idx) }
    LaunchedEffect(cur?.id) { scroll.scrollTo(0) }
    val unread = msgs.any { !it.read }
    val blink = rememberInfiniteTransition(label = "led")
    val ledA by blink.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    val spin = remember { Animatable(0f) }
    var firstLayout by remember { mutableStateOf(true) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A3E2B), Color(0xFF2E1F15)))).systemBarsPadding()
    ) {
        val land = maxWidth > maxHeight

        // the device turns when the screen turns
        LaunchedEffect(land) {
            if (firstLayout) firstLayout = false
            else {
                spin.snapTo(if (land) -90f else 90f)
                spin.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            }
        }

        val c = Ctx(m, ui, idx, cur, total, scroll, scope, unread, ledA, land, onFunc,
            onRot = {
                PagerCore.orientMode = if (land) 1 else 2
                PagerCore.saveMeta()
                PagerCore.flash(if (land) "PORTRAIT" else "LANDSCAPE")
            })

        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = spin.value
                val k = 1f - 0.4f * abs(s) / 90f
                rotationZ = s; scaleX = k; scaleY = k; alpha = 1f - 0.5f * abs(s) / 90f
            },
            contentAlignment = Alignment.Center
        ) {
            when {
                m.kb == KB.PAPER -> PaperBody(c)
                m.landscapeNative -> {
                    if (land) P1Body(c)
                    else {
                        // wide device on a tall screen: lay it on its side. ROT turns the phone itself.
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            Box(Modifier.requiredSize(maxHeight, maxWidth).graphicsLayer { rotationZ = 90f }) { P1Body(c) }
                        }
                    }
                }
                land -> StdLandscape(c)
                else -> StdPortrait(c)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// standard pagers
// ---------------------------------------------------------------------------------------------

@Composable
private fun BodyShell(m: Model, modifier: Modifier, pad: Dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(m.corner.dp)
    Column(
        modifier
            .shadow(24.dp, shape)
            .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), shape)
            .border(2.dp, Brush.verticalGradient(listOf(m.edgeLight, m.edgeDark)), shape)
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun StdPortrait(c: Ctx) {
    val m = c.m
    val showImg = c.hasImg && !c.ui.composing
    val lcdH by animateDpAsState(if (showImg) 250.dp else 164.dp, label = "lcdH")
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BodyShell(m, Modifier.fillMaxWidth(0.92f), 18.dp) {
            Header(c)
            LcdRow(c, Modifier.fillMaxWidth().height(lcdH))
            ButtonRows(c, c.buttons())
            PagerKeyboard(m, if (m.feature == Feature.KB_BACKLIGHT) PagerCore.kbLight else 0, c.ui::onKey)
            Grille()
        }
    }
}

@Composable
private fun StdLandscape(c: Ctx) {
    val m = c.m
    val shape = RoundedCornerShape((m.corner * 0.8f).dp)
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(20.dp, shape)
            .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), shape)
            .border(2.dp, Brush.verticalGradient(listOf(m.edgeLight, m.edgeDark)), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(c)
            LcdRow(c, Modifier.fillMaxWidth().weight(1f))
            ButtonRows(c, c.buttons())
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PagerKeyboard(m, if (m.feature == Feature.KB_BACKLIGHT) PagerCore.kbLight else 0, c.ui::onKey)
                Grille()
            }
        }
    }
}

@Composable
private fun Grille() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(14) {
            Box(Modifier.padding(horizontal = 3.dp).width(4.dp).height(18.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0x5526282C))), RoundedCornerShape(2.dp)))
        }
    }
}

/** brand on the left, link indicator and LED on the right */
@Composable
private fun Header(c: Ctx) {
    val m = c.m
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        Text(if (Mesh.on) "◉${Mesh.peers.size}" else "○", color = m.brandColor, fontFamily = Mono,
            fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        val ls = if (m.feature == Feature.LED_COLOR) 22.dp else 16.dp
        LedLens(c, ls, ls)
    }
}

@Composable
private fun LedLens(c: Ctx, w: Dp, h: Dp) {
    val col = c.ledC
    val lit = c.ledLit
    val shape = RoundedCornerShape(minOf(w, h) / 2)
    Box(
        Modifier.size(w, h)
            .drawBehind { if (lit) drawCircle(col.copy(alpha = 0.30f), radius = size.minDimension) }
            .alpha(if (c.unread) c.ledA else 1f)
            .background(
                if (lit) Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), col))
                else Brush.radialGradient(listOf(col.copy(alpha = 0.25f), Color(0xFF140808))), shape)
            .border(1.dp, Color(0xFF0B0B0C), shape)
    )
}

@Composable
private fun ButtonRows(c: Ctx, btns: List<Btn>, keyH: Dp = 40.dp, single: Boolean = false) {
    val m = c.m
    val per = if (single || btns.size <= 6) btns.size else (btns.size + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        btns.chunked(per).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b ->
                    Cap(b.label, Modifier.weight(1f).height(keyH), m.keyTop, m.keyBottom, m.keyText, fontSize = 11.sp, onClick = b.act)
                }
                repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// LCD (text and scrolling images) + scroll wheel
// ---------------------------------------------------------------------------------------------

@Composable
private fun LcdRow(c: Ctx, modifier: Modifier) {
    val m = c.m
    val ui = c.ui
    val cur = c.cur
    val ink = c.ink
    val compact = c.land
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF0D0E10), RoundedCornerShape(14.dp)).padding(7.dp)) {
            val ls = RoundedCornerShape(8.dp)
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(c.lt, c.lb)), ls)
                    .border(2.dp, ink.copy(alpha = 0.35f), ls)
                    .drawBehind {
                        var y = 0f
                        while (y < size.height) { drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f); y += 4f }
                    }
                    .padding(if (compact) 8.dp else 10.dp)
            ) {
                val img = cur?.img
                if (ui.composing) {
                    Row {
                        Text("TO:${ui.to()}", color = ink, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${ui.text.length}/120", color = ink, fontFamily = Mono, fontSize = 12.sp)
                    }
                    Text(ui.text + "_", color = ink, fontFamily = Mono, fontSize = if (compact) 14.sp else 16.sp,
                        modifier = Modifier.padding(top = 6.dp).weight(1f), overflow = TextOverflow.Ellipsis)
                } else if (cur == null) {
                    Spacer(Modifier.weight(1f))
                    Text("NO MESSAGES", color = ink, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.weight(1f))
                } else if (img != null) {
                    Row {
                        Text("%02d/%02d %s".format(c.idx + 1, c.total, cur.from).uppercase(), color = ink, fontFamily = Mono,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (cur.urgent) Text("▲", color = ink, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    ImageView(img, ink, c.scroll, Modifier.weight(1f).fillMaxWidth().padding(vertical = 3.dp))
                } else {
                    Row {
                        Text("MSG %02d/%02d".format(c.idx + 1, c.total), color = ink, fontFamily = Mono,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (cur.urgent) Text("▲URGENT", color = ink, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("FROM:${cur.from}".uppercase(), color = ink, fontFamily = Mono, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text(cur.body.uppercase(), color = ink, fontFamily = Mono, fontSize = if (compact) 13.sp else 15.sp,
                        maxLines = if (compact) 3 else 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).weight(1f))
                }
                val f = PagerCore.flashMsg
                Text(f ?: if (!ui.composing && cur != null) SimpleDateFormat("HH:mm  dd/MM/yy", Locale.US).format(Date(cur.time)) else "",
                    color = ink, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (m.wheel) Roller(Modifier.fillMaxHeight(), m.keyTop, m.keyBottom, m.keyText, onRoll = c::roll)
    }
}

/** A received 128x256 image, pixel-doubled, with a draggable scroll bar on the right. */
@Composable
private fun ImageView(img: String, ink: Color, scroll: ScrollState, modifier: Modifier) {
    var viewPx by remember { mutableIntStateOf(0) }
    Row(modifier.onSizeChanged { viewPx = it.height }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            val k = ((maxWidth.value) / 128f).toInt().coerceIn(1, 2)
            PixelImage(img, ink, Modifier.size((128 * k).dp, (256 * k).dp))
        }
        ScrollBar(scroll, viewPx, ink)
    }
}

@Composable
private fun PixelImage(img: String, ink: Color, modifier: Modifier) {
    val bmp = remember(img, ink) { PagerImg.decode(img)?.let { PagerImg.toBitmap(it, ink.toArgb()).asImageBitmap() } }
    if (bmp == null) Text("BAD IMAGE", color = ink, fontFamily = Mono, fontSize = 12.sp, modifier = modifier)
    else Image(bmp, null, modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
}

@Composable
private fun ScrollBar(state: ScrollState, viewPx: Int, ink: Color) {
    val scope = rememberCoroutineScope()
    val max = state.maxValue
    Box(
        Modifier.width(10.dp).fillMaxHeight()
            .background(ink.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .pointerInput(max, viewPx) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    if (viewPx > 0) {
                        val ratio = (viewPx + max).toFloat() / viewPx
                        scope.launch { state.scrollBy(dy * ratio) }
                    }
                }
            }
            .drawBehind {
                val h = size.height
                val content = (viewPx + state.maxValue).toFloat().coerceAtLeast(1f)
                val th = (h * viewPx / content).coerceIn(16f, h)
                val top = if (state.maxValue > 0) (h - th) * state.value / state.maxValue else 0f
                drawRoundRect(ink.copy(alpha = 0.8f), Offset(1f, top), Size(size.width - 2f, th), CornerRadius(8f, 8f))
            }
    )
}

/** Scroll roller: drag it up and down. Ridges move with your finger. */
@Composable
private fun Roller(modifier: Modifier, top: Color, bottom: Color, ridge: Color, onRoll: (Float) -> Unit) {
    var phase by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.width(30.dp)
            .background(Brush.horizontalGradient(listOf(bottom, top, bottom)), shape)
            .border(1.dp, Color(0x99000000), shape)
            .draggable(state = rememberDraggableState { d -> phase += d; onRoll(d) }, orientation = Orientation.Vertical)
            .drawBehind {
                val sp = 9.dp.toPx()
                var y = ((phase % sp) + sp) % sp - sp
                while (y < size.height) {
                    drawLine(ridge.copy(alpha = 0.55f), Offset(3f, y), Offset(size.width - 3f, y), 2.5f)
                    drawLine(Color(0x33FFFFFF), Offset(3f, y + 3f), Offset(size.width - 3f, y + 3f), 1f)
                    y += sp
                }
            }
    )
}

// ---------------------------------------------------------------------------------------------
// CM P1 : wide landscape pager
// ---------------------------------------------------------------------------------------------

@Composable
private fun P1Body(c: Ctx) {
    val m = c.m
    val shape = RoundedCornerShape(m.corner.dp)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val lcdH = (maxHeight - 236.dp).coerceIn(106.dp, 200.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)
                .shadow(20.dp, shape)
                .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), shape)
                .border(2.dp, Brush.verticalGradient(listOf(m.edgeLight, m.edgeDark)), shape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // LED in the middle, CM on the right
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(if (Mesh.on) "◉${Mesh.peers.size}" else "○", color = m.brandColor, fontFamily = Mono, fontSize = 12.sp)
                }
                LedLens(c, 44.dp, 14.dp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, letterSpacing = 3.sp)
                }
            }
            LcdRow(c, Modifier.fillMaxWidth().height(lcdH))
            // buttons sit right under the screen
            ButtonRows(c, c.buttons(), keyH = 30.dp, single = true)
            PagerKeyboard(m, if (PagerCore.lcdLight) 1 else 0, c.ui::onKey)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// CM Paper Pager : resistive touchscreen, e-ink 128x256, buttons on the sides only
// ---------------------------------------------------------------------------------------------

@Composable
private fun PaperBody(c: Ctx) {
    val m = c.m
    val ui = c.ui
    val land = c.land
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val btnW = 46.dp
        val bodyW = if (land) maxWidth - 20.dp else maxWidth * 0.92f
        val availW = bodyW - 28.dp - btnW * 2 - 16.dp - 14.dp
        val availH = maxHeight - 130.dp
        val sc = minOf(1.5f, availW.value / 128f, if (land) availH.value / 256f else 9f).coerceAtLeast(0.7f)
        val dw = (128 * sc).dp
        val dh = (256 * sc).dp

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BodyShell(m, Modifier.width(bodyW), 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 2.sp)
                    }
                    LedLens(c, 40.dp, 14.dp)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Text(if (Mesh.on) "◉${Mesh.peers.size}" else "○", color = m.brandColor, fontFamily = Mono, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SideButtons(c, listOf(
                        Btn("◄") { ui.composing = false; if (c.idx < c.total - 1) PagerCore.index = c.idx + 1 },
                        Btn("►") { ui.composing = false; if (c.idx > 0) PagerCore.index = c.idx - 1 },
                        Btn("DEL") { if (ui.composing) ui.clearInput() else PagerCore.delete(c.idx) },
                        Btn("TO") { ui.nextTo() }
                    ), btnW, dh + 14.dp)
                    EinkScreen(c, dw, dh)
                    SideButtons(c, listOf(
                        Btn("PEN") { ui.composing = !ui.composing },
                        Btn("CLR") { ui.clearCanvas() },
                        Btn("SEND") { ui.sendDrawing() },
                        Btn("ROT", c.onRot),
                        Btn("FUNC", c.onFunc)
                    ), btnW, dh + 14.dp)
                }
                val line = PagerCore.flashMsg ?: when {
                    ui.composing -> "TO:${ui.to()}" + if (PagerCore.stylusOut) "  PEN" else ""
                    c.cur == null -> "NO MESSAGES"
                    else -> "%02d/%02d %s".format(c.idx + 1, c.total, c.cur.from).uppercase()
                }
                Text(line, color = m.brandColor, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (!land) StylusSlot(c)
            }
        }
    }
}

@Composable
private fun SideButtons(c: Ctx, btns: List<Btn>, w: Dp, h: Dp) {
    Column(Modifier.width(w).height(h), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        btns.forEach { b ->
            Cap(b.label, Modifier.fillMaxWidth().height(34.dp), c.m.keyTop, c.m.keyBottom, c.m.keyText, fontSize = 10.sp, onClick = b.act)
        }
    }
}

@Composable
private fun EinkScreen(c: Ctx, dw: Dp, dh: Dp) {
    val m = c.m
    val ui = c.ui
    val cur = c.cur
    val ink = m.ink
    Box(Modifier.size(dw + 14.dp, dh + 14.dp).background(Color(0xFF0D0E10), RoundedCornerShape(8.dp)).padding(7.dp)) {
        Box(
            Modifier.size(dw, dh)
                .background(Brush.verticalGradient(listOf(m.lcdTop, m.lcdBottom)))
                .drawBehind {
                    var y = 0f
                    while (y < size.height) { drawLine(Color(0x0F000000), Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
                }
                // resistive touch: pressing or drawing takes the stylus out
                .pointerInput(dw, dh) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val sx = PagerImg.W.toFloat() / size.width
                        val sy = PagerImg.H.toFloat() / size.height
                        ui.beginPen()
                        var px = (down.position.x * sx).toInt()
                        var py = (down.position.y * sy).toInt()
                        ui.line(px, py, px, py)
                        drag(down.id) { ch ->
                            val nx = (ch.position.x * sx).toInt()
                            val ny = (ch.position.y * sy).toInt()
                            ui.line(px, py, nx, ny)
                            px = nx; py = ny
                            ch.consume()
                        }
                    }
                }
        ) {
            val img = cur?.img
            if (ui.composing) {
                val bmp = remember(ui.bitsVer, ink) { PagerImg.toBitmap(ui.bits, ink.toArgb()).asImageBitmap() }
                Image(bmp, null, Modifier.size(dw, dh), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            } else if (cur == null) {
                Text("NO MESSAGES\n\nTOUCH TO\nWRITE", color = ink, fontFamily = Mono, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            } else if (img != null) {
                PixelImage(img, ink, Modifier.size(dw, dh))
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text("FROM:${cur.from}".uppercase(), color = ink, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(cur.body.uppercase(), color = ink, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** Small slot with the stylus in it. The stylus disappears when you press or draw, and comes back when the note is sent. */
@Composable
private fun StylusSlot(c: Ctx) {
    val m = c.m
    val out = PagerCore.stylusOut
    val slot = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("STYLUS", color = m.brandColor, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(end = 8.dp))
        Box(
            Modifier.width(150.dp).height(16.dp)
                .background(Color(0xFF050506), slot)
                .border(1.dp, m.edgeDark, slot)
                .clickable { c.ui.beginPen() },
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedVisibility(
                visible = !out,
                enter = fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it },
                exit = fadeOut(tween(250)) + slideOutHorizontally(tween(300)) { it }
            ) {
                Row(Modifier.padding(horizontal = 3.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFE8EAEE), Color(0xFF8A8F99))),
                            RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                    Box(Modifier.width(14.dp).height(6.dp)
                        .background(Color(0xFF222428), RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)))
                }
            }
        }
    }
}
