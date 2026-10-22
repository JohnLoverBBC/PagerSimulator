package com.example.pager

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max

/** The CM Paper's 128x256 1-bit e-ink drawing surface + image codec. */
object Sketch {
    const val W = 128
    const val H = 256
    private val PAPER = 0xFFE6E4D6.toInt()
    private val INK = 0xFF14140E.toInt()
    private val bits = BooleanArray(W * H)
    private val bmp: Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { it.eraseColor(PAPER) }
    val image: ImageBitmap = bmp.asImageBitmap()
    var tick by mutableIntStateOf(0)
    var empty = true
        private set

    private fun set(x: Int, y: Int, on: Boolean) {
        if (x < 0 || y < 0 || x >= W || y >= H) return
        bits[y * W + x] = on
        bmp.setPixel(x, y, if (on) INK else PAPER)
    }

    fun dot(x: Int, y: Int, erase: Boolean) {
        if (erase) { for (dx in -3..3) for (dy in -3..3) set(x + dx, y + dy, false) }
        else { for (dx in 0..1) for (dy in 0..1) set(x + dx, y + dy, true) }
        empty = false; tick++
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, erase: Boolean) {
        val steps = max(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) dot(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, erase)
    }

    fun clear() { bits.fill(false); bmp.eraseColor(PAPER); empty = true; tick++ }

    private fun pack(b: BooleanArray): String {
        val bytes = ByteArray(W * H / 8)
        for (i in b.indices) if (b[i]) bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 shr (i % 8))).toByte()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun toBase64(): String = pack(bits)

    /** Turn a received image into a bitmap tinted with the device's ink colour (unset pixels transparent). */
    fun decode(b64: String, ink: Color): ImageBitmap {
        val bytes = try { Base64.decode(b64, Base64.NO_WRAP) } catch (e: Exception) { ByteArray(0) }
        val px = IntArray(W * H)
        val inkI = ink.toArgb()
        for (i in px.indices) {
            val on = i / 8 < bytes.size && (bytes[i / 8].toInt() and (0x80 shr (i % 8))) != 0
            px[i] = if (on) inkI else 0
        }
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, W, 0, 0, W, H)
        return out.asImageBitmap()
    }

    /** Test pattern: frame, diagonals, circle, stripes every 32px (good for testing scrolling). */
    fun testImage(): String {
        val b = BooleanArray(W * H)
        fun p(x: Int, y: Int) { if (x in 0 until W && y in 0 until H) b[y * W + x] = true }
        for (x in 0 until W) for (t in 0..1) { p(x, t); p(x, H - 1 - t) }
        for (y in 0 until H) for (t in 0..1) { p(t, y); p(W - 1 - t, y) }
        for (i in 0 until H) { val x = i / 2; p(x, i); p(x + 1, i); p(W - 1 - x, i) }
        for (y in 16 until H step 32) for (x in 6 until W - 6) { p(x, y); p(x, y + 1) }
        for (a in 0 until 360 step 2) {
            val r = Math.toRadians(a.toDouble())
            p((64 + 36 * Math.cos(r)).toInt(), (128 + 36 * Math.sin(r)).toInt())
        }
        return pack(b)
    }
}
