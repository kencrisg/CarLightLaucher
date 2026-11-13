package com.carlight.launcher.ui.glass

import android.graphics.Bitmap
import kotlin.math.max

/** Desenfoque de caja (3 pasadas ≈ gaussiano) sobre una copia reducida. Se ejecuta una sola vez. */
object Blur {

    fun downscaled(src: Bitmap, factor: Int, radius: Int): Bitmap {
        val w = max(1, src.width / factor)
        val h = max(1, src.height / factor)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        val out = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            fun px(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            for (i in -r..r) {
                val c = px(i)
                sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = px(i + r + 1)
                val rem = px(i - r)
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
