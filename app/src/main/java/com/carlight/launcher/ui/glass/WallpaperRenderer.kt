package com.carlight.launcher.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/**
 * Dibuja el fondo de pantalla UNA vez (gradiente + orbes de luz + skyline) al estilo
 * del tema de fábrica. Día: cielo pastel. Noche: violeta con neón magenta/cian.
 */
object WallpaperRenderer {

    private class Orb(val x: Float, val y: Float, val r: Float, val color: Int)

    private class Palette(
        val bgStart: Int, val bgMid: Int, val bgEnd: Int,
        val orbs: List<Orb>,
        val far: Int, val near: Int,
        val window1: Int, val window2: Int,
        val glow: Int,
        val neon: IntArray,
    )

    private val DAY = Palette(
        bgStart = 0xFFC9DEF9.toInt(), bgMid = 0xFFE2EBFB.toInt(), bgEnd = 0xFFEFE7FB.toInt(),
        orbs = listOf(
            Orb(0.16f, 0.22f, 0.30f, 0xCCB4D2FF.toInt()),
            Orb(0.45f, 0.18f, 0.18f, 0xB3E6D4FF.toInt()),
            Orb(0.10f, 0.78f, 0.22f, 0x99DCC8FF.toInt()),
            Orb(0.88f, 0.82f, 0.32f, 0xB3C9DCFF.toInt()),
            Orb(0.70f, 0.30f, 0.20f, 0x80FFE1F0.toInt()),
        ),
        far = 0xFFD8E1F7.toInt(), near = 0xFFC3CFEF.toInt(),
        window1 = 0x99FFFFFF.toInt(), window2 = 0x66E9D5FF,
        glow = 0x66FFFFFF,
        neon = intArrayOf(),
    )

    private val NIGHT = Palette(
        bgStart = 0xFF120A2E.toInt(), bgMid = 0xFF2A1052.toInt(), bgEnd = 0xFF4C1265.toInt(),
        orbs = listOf(
            Orb(0.15f, 0.20f, 0.32f, 0x997B3CFF.toInt()),
            Orb(0.82f, 0.30f, 0.30f, 0x80FF2E93.toInt()),
            Orb(0.62f, 0.62f, 0.22f, 0x6619C8FF),
            Orb(0.35f, 0.90f, 0.25f, 0x66B026FF),
        ),
        far = 0xFF2A1450.toInt(), near = 0xFF170A30.toInt(),
        window1 = 0xCC22D3EE.toInt(), window2 = 0xCCFF4FB0.toInt(),
        glow = 0x80FF2E93.toInt(),
        neon = intArrayOf(0xFFFF2E93.toInt(), 0xFF22D3EE.toInt(), 0xFFB26BFF.toInt()),
    )

    fun render(w: Int, h: Int, night: Boolean, skyline: Boolean = true): Bitmap {
        val p = if (night) NIGHT else DAY
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        paint.shader = LinearGradient(0f, 0f, wf, hf, intArrayOf(p.bgStart, p.bgMid, p.bgEnd), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, paint)

        for (o in p.orbs) {
            val cx = o.x * wf
            val cy = o.y * hf
            val r = o.r * wf
            paint.shader = RadialGradient(cx, cy, r, o.color, o.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, paint)
        }

        // Resplandor del horizonte
        paint.shader = LinearGradient(0f, hf * 0.55f, 0f, hf, p.glow and 0x00FFFFFF, p.glow, Shader.TileMode.CLAMP)
        c.drawRect(0f, hf * 0.55f, wf, hf, paint)
        paint.shader = null

        if (skyline) drawSkyline(c, w, h, p, paint)
        return bmp
    }

    private fun drawSkyline(c: Canvas, w: Int, h: Int, p: Palette, paint: Paint) {
        val rnd = Random(7)
        val hf = h.toFloat()

        // Capa lejana
        paint.color = p.far
        var x = -10f
        while (x < w) {
            val bw = rnd.nextInt(30, 72).toFloat()
            val bh = hf * rnd.range(0.14f, 0.40f)
            c.drawRect(x, hf - bh, x + bw, hf, paint)
            x += bw + rnd.nextInt(0, 6)
        }

        // Capa cercana con ventanas y bordes de neón (noche)
        x = -6f
        while (x < w) {
            val bw = rnd.nextInt(26, 64).toFloat()
            val bh = hf * rnd.range(0.06f, 0.26f)
            val top = hf - bh
            paint.color = p.near
            c.drawRect(x, top, x + bw, hf, paint)

            var wy = top + 8f
            while (wy < hf - 6f) {
                var wx = x + 5f
                while (wx < x + bw - 6f) {
                    if (rnd.nextFloat() < 0.32f) {
                        paint.color = if (rnd.nextBoolean()) p.window1 else p.window2
                        c.drawRect(wx, wy, wx + 3f, wy + 4f, paint)
                    }
                    wx += 8f
                }
                wy += 10f
            }

            if (p.neon.isNotEmpty() && rnd.nextFloat() < 0.45f) {
                paint.color = p.neon[rnd.nextInt(p.neon.size)]
                c.drawRect(x, top, x + bw, top + 2f, paint)
            }
            x += bw + rnd.nextInt(2, 10)
        }
    }

    private fun Random.range(from: Float, to: Float) = from + nextFloat() * (to - from)
}
