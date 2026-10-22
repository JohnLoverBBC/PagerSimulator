package com.example.pager

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class KeyEv {
    class Ch(val c: Char) : KeyEv()
    class Rep(val c: Char) : KeyEv()
    object Back : KeyEv()
    object Enter : KeyEv()
}

/** A physical-feeling key: gradient, drop shadow, depresses when pressed. */
@Composable
fun Cap(
    label: String, modifier: Modifier, top: Color, bottom: Color, text: Color,
    sub: String? = null, fontSize: TextUnit = 14.sp, glow: Color? = null, glowR: Float = 12f,
    radius: Dp = 10.dp, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .offset(y = if (pressed) 2.dp else 0.dp)
            .shadow(if (pressed) 1.dp else 5.dp, shape)
            .background(Brush.verticalGradient(if (pressed) listOf(bottom, top) else listOf(top, bottom)), shape)
            .border(1.dp, Color(0x99000000), shape)
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = text, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                style = TextStyle(shadow = glow?.let { Shadow(it, blurRadius = glowR) }))
            if (sub != null) Text(sub, color = text.copy(alpha = 0.7f), fontSize = fontSize * 0.55f, fontFamily = FontFamily.Monospace)
        }
    }
}

private class K(val label: String, val w: Float = 1f, val sub: String? = null, val spacer: Boolean = false, val act: () -> Unit = {})

@Composable
private fun Grid(
    rows: List<List<K>>, keyH: Dp, fs: TextUnit, m: Model,
    text: Color = m.keyText, glow: Color? = null, glowR: Float = 12f, gap: Dp = 5.dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                r.forEach { k ->
                    if (k.spacer) Spacer(Modifier.weight(k.w))
                    else Cap(k.label, Modifier.weight(k.w).height(keyH), m.keyTop, m.keyBottom, text, k.sub, fs, glow, glowR, onClick = k.act)
                }
            }
        }
    }
}

/** Each model has its own keyboard. Keys scale with the width available and never exceed maxKeyH. */
@Composable
fun PagerKeyboard(m: Model, light: Int, maxKeyH: Dp, onKey: (KeyEv) -> Unit) {
    when (m.kb) {
        KB.NONE -> {}
        KB.MULTITAP -> Multitap(m, maxKeyH, onKey)
        KB.ALPHA -> AlphaGrid(m, maxKeyH, onKey)
        KB.QWERTY -> Qwerty(m, light, maxKeyH, onKey)
        KB.NUMERIC -> Numeric(m, maxKeyH, onKey)
    }
}

private val MT = mapOf('1' to ".,?!1", '2' to "ABC2", '3' to "DEF3", '4' to "GHI4", '5' to "JKL5",
    '6' to "MNO6", '7' to "PQRS7", '8' to "TUV8", '9' to "WXYZ9", '0' to " 0")

@Composable
private fun Multitap(m: Model, maxKeyH: Dp, onKey: (KeyEv) -> Unit) {
    var last by remember { mutableStateOf('\u0000') }
    var lastT by remember { mutableLongStateOf(0L) }
    var idx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keyW = maxWidth / 3
        fun press(k: Char) {
            val chars = MT[k] ?: return
            val now = System.currentTimeMillis()
            if (k == last && now - lastT < 900) { idx = (idx + 1) % chars.length; onKey(KeyEv.Rep(chars[idx])) }
            else { idx = 0; last = k; onKey(KeyEv.Ch(chars[0])) }
            lastT = now
        }
        val rows = listOf("123", "456", "789", "*0#").map { row ->
            row.map { k ->
                when (k) {
                    '*' -> K("*", sub = "DEL", act = { last = '\u0000'; onKey(KeyEv.Back) })
                    '#' -> K("#", sub = "SEND", act = { last = '\u0000'; onKey(KeyEv.Enter) })
                    '1' -> K("1", sub = ".,?!", act = { press(k) })
                    '0' -> K("0", sub = "SPACE", act = { press(k) })
                    else -> K(k.toString(), sub = MT[k]!!.dropLast(1), act = { press(k) })
                }
            }
        }
        Grid(rows, minOf(keyW * 0.42f, maxKeyH), (keyW.value * 0.2f).sp, m, gap = 6.dp)
    }
}

@Composable
private fun AlphaGrid(m: Model, maxKeyH: Dp, onKey: (KeyEv) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keyW = maxWidth / 7
        fun ch(c: Char) = K(c.toString(), act = { onKey(KeyEv.Ch(c)) })
        val rows = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ.?").map { r -> r.map { ch(it) } } +
            listOf("1234567890".map { ch(it) }) +
            listOf(listOf(K("DEL", 2f, act = { onKey(KeyEv.Back) }),
                K("SPACE", 3f, act = { onKey(KeyEv.Ch(' ')) }),
                K("SEND", 2f, act = { onKey(KeyEv.Enter) })))
        val h = minOf(keyW * 0.75f, maxKeyH)
        Grid(rows, h, minOf(keyW.value * 0.38f, h.value * 0.5f).sp, m, gap = 4.dp)
    }
}

@Composable
private fun Qwerty(m: Model, light: Int, maxKeyH: Dp, onKey: (KeyEv) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keyW = maxWidth / 10
        fun ch(c: Char) = K(c.toString(), act = { onKey(KeyEv.Ch(c)) })
        val rows = listOf(
            "1234567890".map { ch(it) },
            "QWERTYUIOP".map { ch(it) },
            listOf(K("", 0.5f, spacer = true)) + "ASDFGHJKL".map { ch(it) } + K("", 0.5f, spacer = true),
            listOf(K("", 1.5f, spacer = true)) + "ZXCVBNM".map { ch(it) } + K("", 1.5f, spacer = true),
            listOf(K("DEL", 2f, act = { onKey(KeyEv.Back) }), K("SPACE", 5f, act = { onKey(KeyEv.Ch(' ')) }),
                K("ENTER", 3f, act = { onKey(KeyEv.Enter) }))
        )
        val lit = light > 0
        val h = minOf(keyW * 1.1f, maxKeyH)
        Grid(rows, h, minOf(keyW.value * 0.42f, h.value * 0.5f).sp, m,
            text = if (lit) m.glow else m.keyText.copy(alpha = 0.6f),
            glow = if (lit) m.glow else null, glowR = if (light >= 2) 22f else 8f, gap = 4.dp)
    }
}

@Composable
private fun Numeric(m: Model, maxKeyH: Dp, onKey: (KeyEv) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val keyW = maxWidth / 3
        fun d(c: Char) = K(c.toString(), act = { onKey(KeyEv.Ch(c)) })
        val rows = listOf("123".map { d(it) }, "456".map { d(it) }, "789".map { d(it) },
            listOf(K("CLR", act = { onKey(KeyEv.Back) }), d('0'), K("SEND", act = { onKey(KeyEv.Enter) })))
        Grid(rows, minOf(keyW * 0.4f, maxKeyH), (keyW.value * 0.22f).sp, m, gap = 8.dp)
    }
}
