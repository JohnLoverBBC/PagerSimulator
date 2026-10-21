package com.example.pager

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
private val LcdInk = Color(0xFF1E2A1A)

@Composable
fun PagerButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .offset(y = if (pressed) 2.dp else 0.dp)
            .shadow(if (pressed) 1.dp else 6.dp, shape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(Color(0xFF3A3D42), Color(0xFF4A4E54))
                    else listOf(Color(0xFF5B6068), Color(0xFF2F3237))
                ), shape
            )
            .border(1.dp, Color(0xFF16171A), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFFD8DCE2), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

@Composable
fun PagerScreen(onDebug: () -> Unit) {
    val msgs = PagerCore.messages
    val idx = PagerCore.index.coerceIn(0, maxOf(0, msgs.size - 1))
    val cur = msgs.getOrNull(idx)
    LaunchedEffect(cur?.id, cur?.read) { if (cur != null && !cur.read) PagerCore.markRead(idx) }
    val unread = msgs.any { !it.read }

    val blink = rememberInfiniteTransition(label = "led")
    val ledA by blink.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF5A3E2B), Color(0xFF2E1F15)))
        ).systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val bodyShape = RoundedCornerShape(32.dp)
        Column(
            Modifier.fillMaxWidth(0.9f)
                .shadow(24.dp, bodyShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3E45), Color(0xFF1A1C20))), bodyShape)
                .border(2.dp, Brush.verticalGradient(listOf(Color(0xFF6A707A), Color(0xFF0E0F11))), bodyShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top: brand + LED
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PAGERTRON 3000", color = Color(0xFF9AA0AA), fontFamily = Mono,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(16.dp)
                        .alpha(if (unread) ledA else 1f)
                        .background(
                            if (unread) Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFC62828)))
                            else Brush.radialGradient(listOf(Color(0xFF4A2020), Color(0xFF2A1010))), CircleShape
                        )
                        .border(1.dp, Color(0xFF0B0B0C), CircleShape)
                )
            }

            // LCD bezel
            Box(
                Modifier.fillMaxWidth().background(Color(0xFF0D0E10), RoundedCornerShape(16.dp)).padding(8.dp)
            ) {
                val lcdShape = RoundedCornerShape(8.dp)
                Column(
                    Modifier.fillMaxWidth().height(210.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFA3B58F), Color(0xFF8DA179))), lcdShape)
                        .border(2.dp, Color(0xFF5C6B4E), lcdShape)
                        .drawBehind {
                            var y = 0f
                            while (y < size.height) {
                                drawLine(Color(0x14000000), Offset(0f, y), Offset(size.width, y), 1f)
                                y += 4f
                            }
                        }
                        .padding(12.dp)
                ) {
                    if (cur == null) {
                        Spacer(Modifier.weight(1f))
                        Text("NO MESSAGES", color = LcdInk, fontFamily = Mono, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.weight(1f))
                    } else {
                        Row {
                            Text("MSG %02d/%02d".format(idx + 1, msgs.size), color = LcdInk, fontFamily = Mono,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (cur.urgent) Text("▲URGENT", color = LcdInk, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("FROM:${cur.from}".uppercase(), color = LcdInk, fontFamily = Mono, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp))
                        Text(cur.body.uppercase(), color = LcdInk, fontFamily = Mono, fontSize = 17.sp,
                            maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).weight(1f))
                        Text(SimpleDateFormat("HH:mm  dd/MM/yy", Locale.US).format(Date(cur.time)),
                            color = LcdInk, fontFamily = Mono, fontSize = 12.sp)
                    }
                }
            }

            // Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PagerButton("◄", Modifier.weight(1f)) { if (idx < msgs.size - 1) PagerCore.index = idx + 1 }
                PagerButton("►", Modifier.weight(1f)) { if (idx > 0) PagerCore.index = idx - 1 }
                PagerButton("DEL", Modifier.weight(1f)) { PagerCore.delete(idx) }
                PagerButton("FUNC", Modifier.weight(1f), onDebug)
            }

            // Speaker grille
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(14) {
                    Box(Modifier.padding(horizontal = 3.dp).width(4.dp).height(22.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0xFF26282C))), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2D32), Color(0xFF1E2024))), shape)
            .border(1.dp, Color(0xFF3B3F46), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color(0xFF7CFC9A), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Mono)
        Switch(value, onChange)
    }
}

@Composable
fun DebugScreen(onClose: () -> Unit, requestPerm: () -> Unit) {
    var from by remember { mutableStateOf("DISPATCH") }
    var body by remember { mutableStateOf("Test page. Please respond.") }
    var urgent by remember { mutableStateOf(false) }
    val randoms = remember {
        listOf("Server down, call NOC" to "OPS", "Code blue room 4" to "HOSPITAL",
            "Pizza is here" to "FRONT DESK", "Call mom back" to "HOME", "Deploy failed on prod" to "CI-BOT")
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Color(0xFF101214)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DEBUG / SERVICE PANEL", Modifier.weight(1f), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedButton(onClick = onClose) { Text("CLOSE") }
                }

                Section("SEND MESSAGE") {
                    OutlinedTextField(from, { from = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
                    ToggleRow("Urgent", urgent) { urgent = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ PagerCore.receive(from, body, urgent) }, Modifier.weight(1f)) { Text("SEND NOW") }
                        Button({ PagerCore.receiveDelayed(5000, from, body, urgent) }, Modifier.weight(1f)) { Text("IN 5s") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ val r = randoms.random(); PagerCore.receive(r.second, r.first, false) }, Modifier.weight(1f)) { Text("RANDOM") }
                        OutlinedButton({ repeat(10) { i -> PagerCore.receive("FLOOD", "Flood message #${i + 1}", i % 4 == 0) } }, Modifier.weight(1f)) { Text("FLOOD x10") }
                    }
                }

                Section("NOTIFICATIONS") {
                    Text("Permission: ${if (PagerCore.hasNotifPermission()) "GRANTED" else "NOT GRANTED"}", fontFamily = Mono, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ PagerCore.testNotification(false) }, Modifier.weight(1f)) { Text("TEST") }
                        Button({ PagerCore.testNotification(true) }, Modifier.weight(1f)) { Text("TEST URGENT") }
                    }
                    OutlinedButton(requestPerm, Modifier.fillMaxWidth()) { Text("REQUEST PERMISSION") }
                    ToggleRow("System notifications", PagerCore.systemNotify) { PagerCore.systemNotify = it }
                    ToggleRow("Beep", PagerCore.beep) { PagerCore.beep = it }
                    ToggleRow("Vibrate", PagerCore.vibrate) { PagerCore.vibrate = it }
                }

                Section("MEMORY") {
                    Text("Stored: ${PagerCore.messages.size}  Unread: ${PagerCore.messages.count { !it.read }}", fontFamily = Mono, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ PagerCore.markAllRead() }, Modifier.weight(1f)) { Text("MARK READ") }
                        OutlinedButton({ PagerCore.clear() }, Modifier.weight(1f)) { Text("CLEAR ALL") }
                    }
                }

                Section("LOG") {
                    if (PagerCore.log.isEmpty()) Text("(empty)", fontFamily = Mono, fontSize = 11.sp)
                    PagerCore.log.take(30).forEach {
                        Text(it, fontFamily = Mono, fontSize = 11.sp, color = Color(0xFFB8E8C0))
                    }
                }
            }
        }
    }
}
