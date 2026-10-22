package com.example.pager

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace
private class Ctl(val label: String, val act: () -> Unit)

@Composable
private fun ControlRow(m: Model, items: List<Ctl>, h: Dp) {
    val fs = if (items.size > 7) 9.sp else if (items.size > 6) 10.sp else 11.sp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { c -> Cap(c.label, Modifier.weight(1f).height(h), m.keyTop, m.keyBottom, m.keyText, fontSize = fs, onClick = c.act) }
    }
}

@Composable
private fun SideCol(m: Model, items: List<Ctl>, h: Dp) {
    Column(Modifier.width(58.dp).height(h), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { c -> Cap(c.label, Modifier.fillMaxWidth().weight(1f), m.keyTop, m.keyBottom, m.keyText, fontSize = 11.sp, onClick = c.act) }
    }
}

@Composable
private fun LcdFrame(modifier: Modifier, lt: Color, lb: Color, ink: Color, scan: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.background(Color(0xFF0D0E10), RoundedCornerShape(14.dp)).padding(7.dp)) {
        val s = RoundedCornerShape(8.dp)
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lt, lb)), s)
                .border(2.dp, ink.copy(alpha = 0.35f), s)
                .drawBehind {
                    if (scan) {
                        var y = 0f
                        while (y < size.height) { drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f); y += 4f }
                    }
                }
                .padding(10.dp),
            content = content
        )
    }
}

@Composable
private fun ColumnScope.LcdBody(
    ink: Color, cur: Msg?, idx: Int, total: Int, composing: Boolean, text: String, to: String,
    scroll: ScrollState, footer: String?, showFooter: Boolean = true
) {
    if (composing) {
        Row {
            Text("TO:$to", color = ink, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${text.length}/120", color = ink, fontFamily = Mono, fontSize = 12.sp)
        }
        Text(text + "_", color = ink, fontFamily = Mono, fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp).weight(1f), overflow = TextOverflow.Ellipsis)
    } else if (cur == null) {
        Spacer(Modifier.weight(1f))
        Text("NO MESSAGES", color = ink, fontFamily = Mono, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
    } else {
        Row {
            Text("MSG %02d/%02d".format(idx + 1, total), color = ink, fontFamily = Mono,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (cur.urgent) Text("▲URGENT", color = ink, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("FROM:${cur.from}".uppercase(), color = ink, fontFamily = Mono, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (cur.img != null) {
            ImageView(cur.img, ink, scroll, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
        } else {
            Text(cur.body.uppercase(), color = ink, fontFamily = Mono, fontSize = 15.sp, maxLines = 4,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).weight(1f))
        }
    }
    if (showFooter) {
        val f = footer ?: if (!composing && cur != null) SimpleDateFormat("HH:mm  dd/MM/yy", Locale.US).format(Date(cur.time)) else ""
        Text(f, color = ink, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun PagerScreen(onFunc: () -> Unit) {
    val m = PagerCore.model
    val msgs = PagerCore.messages
    val idx = PagerCore.index.coerceIn(0, maxOf(0, msgs.size - 1))
    val cur = msgs.getOrNull(idx)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var composing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var toIdx by remember { mutableIntStateOf(0) }
    var drawMode by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var stylusIn by remember { mutableStateOf(true) }
    var wheelAcc by remember { mutableFloatStateOf(0f) }

    val recips = (listOf("ALL") + Mesh.peers.values + msgs.map { it.from })
        .filter { it != PagerCore.callsign }.distinct()
    val to = recips.getOrElse(toIdx) { "ALL" }
    val unread = msgs.any { !it.read }
    val hasImg = cur?.img != null

    LaunchedEffect(cur?.id, cur?.read) { if (cur != null && !cur.read) PagerCore.markRead(idx) }
    LaunchedEffect(cur?.id) { scroll.scrollTo(0) }

    val (ledC, ledOn, ledBlink) = when (m.led) {
        LedMode.SIMPLE -> Triple(LED_COLORS[0], unread, unread)
        LedMode.PICKER -> Triple(LED_COLORS[PagerCore.ledIdx % LED_COLORS.size], unread, unread)
        LedMode.COUNT -> Triple(countColor(msgs.size), msgs.isNotEmpty(), unread)
    }
    val lit = m.feature == Feature.LCD_BACKLIGHT && PagerCore.lcdLight
    val lt = if (lit) m.litTop else m.lcdTop
    val lb = if (lit) m.litBottom else m.lcdBottom
    val ink = if (lit) m.litInk else m.ink
    val kbl = when (m.feature) {
        Feature.KB_BACKLIGHT -> PagerCore.kbLight
        Feature.LCD_BACKLIGHT -> if (PagerCore.lcdLight) 1 else 0
        else -> 0
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A3E2B), Color(0xFF2E1F15)))).systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val land = maxWidth > maxHeight

        // ----- actions -----
        fun older() { if (idx < msgs.size - 1) PagerCore.index = idx + 1 }
        fun newer() { if (idx > 0) PagerCore.index = idx - 1 }
        fun scrollImg(px: Float) { if (hasImg) scope.launch { scroll.animateScrollBy(px) } }
        fun wheel(d: Float) {
            if (hasImg && !composing && !drawMode) scroll.dispatchRawDelta(-d)
            else { wheelAcc += d; if (abs(wheelAcc) > 48f) { if (wheelAcc > 0) older() else newer(); wheelAcc = 0f } }
        }
        fun cycleTo() {
            toIdx = (toIdx + 1) % recips.size.coerceAtLeast(1)
            if (m.kb != KB.NONE) composing = true
            PagerCore.flash("TO:${recips.getOrElse(toIdx) { "ALL" }}")
        }
        fun rotate() { PagerCore.orient = if (land) 1 else 2; PagerCore.saveMeta() }
        fun sendText() {
            if (text.isBlank()) { composing = false; return }
            val r = Mesh.send(to, text.trim())
            PagerCore.flash(r)
            if (r.startsWith("SENT")) { text = ""; composing = false }
        }
        fun sendNote() {
            if (Sketch.empty) { PagerCore.flash("DRAW FIRST"); return }
            val r = Mesh.send(to, "[IMAGE]", img = Sketch.toBase64())
            PagerCore.flash(r)
            if (r.startsWith("SENT")) { Sketch.clear(); stylusIn = true; drawMode = false }
        }
        fun onKey(e: KeyEv) {
            when (e) {
                is KeyEv.Ch -> { if (text.length < 120) text += e.c; composing = true }
                is KeyEv.Rep -> { text = text.dropLast(1) + e.c; composing = true }
                KeyEv.Back -> { text = text.dropLast(1); composing = true }
                KeyEv.Enter -> sendText()
            }
        }
        fun feature() {
            when (m.feature) {
                Feature.LED_COLOR -> { PagerCore.ledIdx = (PagerCore.ledIdx + 1) % LED_COLORS.size; PagerCore.flash("LED: ${LED_NAMES[PagerCore.ledIdx]}") }
                Feature.KB_BACKLIGHT -> { PagerCore.kbLight = (PagerCore.kbLight + 1) % 3; PagerCore.flash("KEYLIGHT ${listOf("OFF", "DIM", "BRIGHT")[PagerCore.kbLight]}") }
                Feature.LCD_BACKLIGHT -> { PagerCore.lcdLight = !PagerCore.lcdLight; PagerCore.flash("BACKLIGHT ${if (PagerCore.lcdLight) "ON" else "OFF"}") }
                Feature.NONE -> {}
            }
            PagerCore.saveMeta()
        }
        val featLabel = when (m.feature) {
            Feature.LED_COLOR -> "LED"; Feature.KB_BACKLIGHT -> "KEYS"; Feature.LCD_BACKLIGHT -> "LIGHT"; Feature.NONE -> null
        }
        val info = "◆${PagerCore.gems}" + if (Mesh.on) "  ◉${Mesh.peers.size}" else ""

        // ----- device swings when the screen rotates -----
        val rot = remember { Animatable(0f) }
        var prevLand by remember { mutableStateOf(land) }
        LaunchedEffect(land) {
            if (land != prevLand) {
                prevLand = land
                rot.snapTo(if (land) -90f else 90f)
                rot.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
            }
        }

        val bodyW = when (m.layout) {
            Layout.WIDE -> maxWidth * 0.96f
            Layout.PAPER -> minOf(maxWidth * 0.94f, 420.dp)
            Layout.STANDARD -> minOf(maxWidth * 0.92f, 440.dp)
        }
        val shape = RoundedCornerShape(m.corner.dp)

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.width(bodyW)
                    .graphicsLayer { rotationZ = rot.value; val s = 1f - 0.25f * (abs(rot.value) / 90f); scaleX = s; scaleY = s }
                    .shadow(24.dp, shape)
                    .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), shape)
                    .border(2.dp, Brush.verticalGradient(listOf(m.edgeLight, m.edgeDark)), shape)
                    .padding(if (m.layout == Layout.WIDE) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (m.layout == Layout.WIDE) 8.dp else 14.dp)
            ) {
                when (m.layout) {
                    // ================= portrait devices with a keyboard =================
                    Layout.STANDARD -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                            Text(info, color = m.brandColor, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                            Led(ledC, ledOn, ledBlink, if (m.led == LedMode.PICKER) 22.dp else 16.dp)
                        }
                        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LcdFrame(Modifier.weight(1f).fillMaxHeight(), lt, lb, ink, true) {
                                LcdBody(ink, cur, idx, msgs.size, composing, text, to, scroll, PagerCore.flashMsg)
                            }
                            if (m.wheel) Wheel(Modifier.width(30.dp).fillMaxHeight(), m.keyTop, ::wheel)
                        }
                        ControlRow(m, buildList<Ctl> {
                            add(Ctl("◄") { composing = false; older() })
                            add(Ctl("►") { composing = false; newer() })
                            if (!m.wheel) { add(Ctl("▲") { scrollImg(-160f) }); add(Ctl("▼") { scrollImg(160f) }) }
                            add(Ctl("DEL") { if (composing) text = "" else PagerCore.delete(idx) })
                            add(Ctl("TO") { cycleTo() })
                            featLabel?.let { add(Ctl(it) { feature() }) }
                            add(Ctl("FUNC", onFunc))
                        }, 40.dp)
                        PagerKeyboard(m, kbl, 60.dp, ::onKey)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            repeat(14) {
                                Box(Modifier.padding(horizontal = 3.dp).width(4.dp).height(18.dp)
                                    .background(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0x5526282C))), RoundedCornerShape(2.dp)))
                            }
                        }
                    }

                    // ================= CM P1: wide landscape device =================
                    Layout.WIDE -> {
                        Box(Modifier.fillMaxWidth().height(26.dp)) {
                            Text(info, color = m.brandColor, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart))
                            Led(ledC, ledOn, ledBlink, 20.dp, Modifier.align(Alignment.Center))
                            Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                letterSpacing = 3.sp, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                        val lcdH = if (land) maxHeight * 0.27f else 130.dp
                        Row(Modifier.fillMaxWidth().height(lcdH), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LcdFrame(Modifier.weight(1f).fillMaxHeight(), lt, lb, ink, true) {
                                LcdBody(ink, cur, idx, msgs.size, composing, text, to, scroll, PagerCore.flashMsg)
                            }
                            Wheel(Modifier.width(34.dp).fillMaxHeight(), m.keyTop, ::wheel)
                        }
                        ControlRow(m, listOf(
                            Ctl("◄") { composing = false; older() },
                            Ctl("►") { composing = false; newer() },
                            Ctl("DEL") { if (composing) text = "" else PagerCore.delete(idx) },
                            Ctl("TO") { cycleTo() },
                            Ctl("LIGHT") { feature() },
                            Ctl("ROTATE") { rotate() },
                            Ctl("FUNC", onFunc)
                        ), 34.dp)
                        val kbH = if (land) ((maxHeight - lcdH - 130.dp) / 5f).coerceIn(18.dp, 44.dp) else 44.dp
                        PagerKeyboard(m, kbl, kbH, ::onKey)
                    }

                    // ================= CM Paper: e-ink touch pad, buttons on the sides =================
                    Layout.PAPER -> {
                        Box(Modifier.fillMaxWidth().height(26.dp)) {
                            Text(info, color = m.brandColor, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart))
                            Led(ledC, ledOn, ledBlink, 20.dp, Modifier.align(Alignment.Center))
                            Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                        StylusSlot(stylusIn, Modifier.width(130.dp).align(Alignment.End)) { stylusIn = !stylusIn }
                        val dispW = minOf(bodyW - 172.dp, (maxHeight - 132.dp) / 2f + 10.dp).coerceAtLeast(90.dp)
                        val blockH = (dispW - 10.dp) * 2f + 26.dp
                        val footer = PagerCore.flashMsg ?: if (drawMode) "${if (erase) "ERASE" else "PEN"}  TO:$to"
                            else if (cur != null) SimpleDateFormat("HH:mm dd/MM", Locale.US).format(Date(cur.time)) else ""
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                            SideCol(m, listOf(
                                Ctl("◄") { older() },
                                Ctl("►") { newer() },
                                Ctl("▲") { scrollImg(-160f) },
                                Ctl("▼") { scrollImg(160f) },
                                Ctl("TO") { cycleTo() }
                            ), blockH)
                            Column(Modifier.width(dispW).background(Color(0xFF0D0E10), RoundedCornerShape(10.dp)).padding(5.dp)) {
                                val touchWatch = if (!drawMode) Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        stylusIn = false
                                    }
                                } else Modifier
                                Box(Modifier.fillMaxWidth().aspectRatio(0.5f).background(m.lcdTop).then(touchWatch)) {
                                    if (drawMode) DrawPad(Modifier.fillMaxSize(), erase) { stylusIn = false }
                                    else Column(Modifier.fillMaxSize().padding(6.dp)) {
                                        LcdBody(ink, cur, idx, msgs.size, false, "", to, scroll, null, showFooter = false)
                                    }
                                }
                                Text(footer, color = Color(0xFF9AA0AA), fontFamily = Mono, fontSize = 10.sp,
                                    maxLines = 1, modifier = Modifier.height(16.dp).padding(top = 3.dp))
                            }
                            SideCol(m, listOf(
                                Ctl(if (drawMode) "VIEW" else "DRAW") { drawMode = !drawMode },
                                Ctl(if (erase) "PEN" else "ERASE") { erase = !erase; drawMode = true },
                                Ctl("CLR") { Sketch.clear() },
                                Ctl("SEND") { sendNote() },
                                Ctl("FUNC", onFunc)
                            ), blockH)
                        }
                    }
                }
            }
        }
    }
}
