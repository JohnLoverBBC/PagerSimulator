package com.example.pager

import android.graphics.Bitmap
import android.util.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 128 x 256, 1 bit per pixel, row-major, MSB first (4096 bytes), sent as base64. */
object PagerImg {
    const val W = 128
    const val H = 256

    fun encode(bits: BooleanArray): String {
        val out = ByteArray(W * H / 8)
        for (i in bits.indices) {
            if (bits[i]) out[i shr 3] = (out[i shr 3].toInt() or (0x80 shr (i and 7))).toByte()
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decode(s: String): BooleanArray? = try {
        val raw = Base64.decode(s, Base64.NO_WRAP)
        if (raw.size < W * H / 8) null
        else BooleanArray(W * H) { i -> (raw[i shr 3].toInt() and (0x80 shr (i and 7))) != 0 }
    } catch (e: Exception) { null }

    /** set pixels = [ink], unset pixels = transparent so the screen colour shows through */
    fun toBitmap(bits: BooleanArray, ink: Int): Bitmap {
        val px = IntArray(W * H) { if (bits[it]) ink else 0 }
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
    }

    /** Debug image: frame, ruler ticks every 16px, diagonals, circles, and a bar at the very bottom. */
    fun testPattern(): String {
        val b = BooleanArray(W * H)
        fun set(x: Int, y: Int) { if (x in 0 until W && y in 0 until H) b[y * W + x] = true }
        for (x in 0 until W) { set(x, 0); set(x, 1); set(x, H - 1); set(x, H - 2) }
        for (y in 0 until H) { set(0, y); set(1, y); set(W - 1, y); set(W - 2, y) }
        for (y in 0 until H step 16) {
            val len = if (y % 64 == 0) 22 else 8
            for (x in 0 until len) set(x, y)
        }
        for (i in 0 until W) { set(i, i * 2); set(i, i * 2 + 1); set(W - 1 - i, i * 2) }
        fun circle(cx: Int, cy: Int, r: Int) {
            for (t in 0 until 720) {
                val a = t * PI / 360.0
                set((cx + r * cos(a)).roundToInt(), (cy + r * sin(a)).roundToInt())
            }
        }
        circle(64, 64, 30); circle(64, 128, 40); circle(64, 128, 20); circle(64, 192, 30)
        for (y in 236 until 250) for (x in 8 until W - 8) set(x, y)
        return encode(b)
    }
}
