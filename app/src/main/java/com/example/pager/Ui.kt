package com.example.pager

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace

@Composable
fun PagerScreen(onFunc: () -> Unit) {
    val m = PagerCore.model
    val msgs = PagerCore.messages
    val idx = PagerCore.index.coerceIn(0, maxOf(0, msgs.size - 1))
    val cur = msgs.getOrNull(idx)
    var composing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var toIdx by remember { mutableIntStateOf(0) }
    val recips = (listOf("ALL") + Mesh.peers.values + msgs.map { it.from })
        .filter { it != PagerCore.callsign }.distinct()
    val to = recips.getOrElse(toIdx) { "ALL" }

    LaunchedEffect(cur?.id, cur?.read) { if (cur != null && !cur.read) PagerCore.markRead(idx) }
    val unread = msgs.any { !it.read }
    val blink = rememberInfiniteTransition(label = "led")
    val ledA by blink.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")

    val bl = m.feature == Feature.LCD_BACKLIGHT && PagerCore.lcdLight
    val lt = if (bl) Color(0xFFFFD166) else m.lcdTop
    val lb = if (bl) Color(0xFFF2A93B) else m.lcdBottom
    val ink = if (bl) Color(0xFF3A2400) else m.ink
    val ledC = if (m.feature == Feature.LED_COLOR) LED_COLORS[PagerCore.ledIdx % LED_COLORS.size] else LED_COLORS[0]

    fun send() {
        if (text.isBlank()) { composing = false; return }
        val r = Mesh.send(to, text.trim())
        PagerCore.flash(r)
        if (r.startsWith("SENT")) { text = ""; composing = false }
    }
    fun onKey(e: KeyEv) {
        when (e) {
            is KeyEv.Ch -> { if (text.length < 120) text += e.c; composing = true }
            is KeyEv.Rep -> { text = text.dropLast(1) + e.c; composing = true }
            KeyEv.Back -> { text = text.dropLast(1); composing = true }
            KeyEv.Enter -> send()
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

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A3E2B), Color(0xFF2E1F15)))).systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shape = RoundedCornerShape(m.corner.dp)
            Column(
                Modifier.fillMaxWidth(0.92f)
                    .shadow(24.dp, shape)
                    .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), shape)
                    .border(2.dp, Brush.verticalGradient(listOf(m.edgeLight, m.edgeDark)), shape)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // header: brand, link indicator, LED
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.brand, color = m.brandColor, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    Text(if (Mesh.on) "◉${Mesh.peers.size}" else "○", color = m.brandColor, fontFamily = Mono,
                        fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                    val ls = if (m.feature == Feature.LED_COLOR) 22.dp else 16.dp
                    Box(
                        Modifier.size(ls).alpha(if (unread) ledA else 1f)
                            .background(
                                if (unread) Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), ledC))
                                else Brush.radialGradient(listOf(ledC.copy(alpha = 0.25f), Color(0xFF140808))), CircleShape)
                            .border(1.dp, Color(0xFF0B0B0C), CircleShape)
                    )
                }

                // LCD
                Box(Modifier.fillMaxWidth().background(Color(0xFF0D0E10), RoundedCornerShape(14.dp)).padding(7.dp)) {
                    val ls = RoundedCornerShape(8.dp)
                    Column(
                        Modifier.fillMaxWidth().height(150.dp)
                            .background(Brush.verticalGradient(listOf(lt, lb)), ls)
                            .border(2.dp, ink.copy(alpha = 0.35f), ls)
                            .drawBehind {
                                var y = 0f
                                while (y < size.height) { drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f); y += 4f }
                            }
                            .padding(10.dp)
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
                                Text("MSG %02d/%02d".format(idx + 1, msgs.size), color = ink, fontFamily = Mono,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (cur.urgent) Text("▲URGENT", color = ink, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("FROM:${cur.from}".uppercase(), color = ink, fontFamily = Mono, fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text(cur.body.uppercase(), color = ink, fontFamily = Mono, fontSize = 15.sp, maxLines = 4,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).weight(1f))
                        }
                        val f = PagerCore.flashMsg
                        Text(f ?: if (!composing && cur != null) SimpleDateFormat("HH:mm  dd/MM/yy", Locale.US).format(Date(cur.time)) else "",
                            color = ink, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // control buttons
                val fl = when (m.feature) { Feature.LED_COLOR -> "LED"; Feature.KB_BACKLIGHT -> "LIGHT"; Feature.LCD_BACKLIGHT -> "BKLT"; Feature.NONE -> null }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    @Composable fun b(l: String, a: () -> Unit) =
                        Cap(l, Modifier.weight(1f).height(40.dp), m.keyTop, m.keyBottom, m.keyText, fontSize = 11.sp, onClick = a)
                    b("◄") { composing = false; if (idx < msgs.size - 1) PagerCore.index = idx + 1 }
                    b("►") { composing = false; if (idx > 0) PagerCore.index = idx - 1 }
                    b("DEL") { if (composing) text = "" else PagerCore.delete(idx) }
                    b("TO") { toIdx = (toIdx + 1) % recips.size.coerceAtLeast(1); composing = true; PagerCore.flash("TO:${recips.getOrElse(toIdx) { "ALL" }}") }
                    if (fl != null) b(fl) { feature() }
                    b("FUNC", onFunc)
                }

                // the model's own keyboard
                PagerKeyboard(m, PagerCore.kbLight, ::onKey)

                // speaker grille
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(14) {
                        Box(Modifier.padding(horizontal = 3.dp).width(4.dp).height(18.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0x5526282C))), RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}
