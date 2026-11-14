package com.carlight.launcher.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/**
 * Dibuja el fondo de pantalla UNA vez (gradiente + orbes de luz + skyline).
 * Varios estilos ([STYLES], elegibles en Ajustes), cada uno con versión de día y de noche.
 */
object WallpaperRenderer {

    class Orb(val x: Float, val y: Float, val r: Float, val color: Int)

    class Palette(
        val bgStart: Int, val bgMid: Int, val bgEnd: Int,
        val orbs: List<Orb>,
        val far: Int, val near: Int,
        val window1: Int, val window2: Int,
        val glow: Int,
        val neon: IntArray,
    ) {
        val gradient get() = intArrayOf(bgStart, bgMid, bgEnd)
    }

    /** Estilo de fondo elegible en Ajustes: versión de día (clara) y de noche (oscura). */
    class Style(val name: String, val day: Palette, val night: Palette) {
        fun palette(night: Boolean) = if (night) this.night else day
    }

    private fun c(v: Long) = v.toInt()

    private fun orbs(vararg o: Orb) = o.toList()

    val STYLES = listOf(
        Style(
            "Violeta",
            day = Palette(
                c(0xFFC9DEF9), c(0xFFE2EBFB), c(0xFFEFE7FB),
                orbs(Orb(0.16f, 0.22f, 0.30f, c(0xCCB4D2FF)), Orb(0.45f, 0.18f, 0.18f, c(0xB3E6D4FF)),
                    Orb(0.10f, 0.78f, 0.22f, c(0x99DCC8FF)), Orb(0.88f, 0.82f, 0.32f, c(0xB3C9DCFF)),
                    Orb(0.70f, 0.30f, 0.20f, c(0x80FFE1F0))),
                c(0xFFD8E1F7), c(0xFFC3CFEF), c(0x99FFFFFF), c(0x66E9D5FF), c(0x66FFFFFF), intArrayOf(),
            ),
            night = Palette(
                c(0xFF120A2E), c(0xFF2A1052), c(0xFF4C1265),
                orbs(Orb(0.15f, 0.20f, 0.32f, c(0x997B3CFF)), Orb(0.82f, 0.30f, 0.30f, c(0x80FF2E93)),
                    Orb(0.62f, 0.62f, 0.22f, c(0x6619C8FF)), Orb(0.35f, 0.90f, 0.25f, c(0x66B026FF))),
                c(0xFF2A1450), c(0xFF170A30), c(0xCC22D3EE), c(0xCCFF4FB0), c(0x80FF2E93),
                intArrayOf(c(0xFFFF2E93), c(0xFF22D3EE), c(0xFFB26BFF)),
            ),
        ),
        Style(
            "Océano",
            day = Palette(
                c(0xFFC6E6FA), c(0xFFDFF1FB), c(0xFFE6F6F3),
                orbs(Orb(0.15f, 0.22f, 0.30f, c(0xCCA8DBFA)), Orb(0.50f, 0.15f, 0.18f, c(0xB3C3F0E6)),
                    Orb(0.10f, 0.80f, 0.22f, c(0x99B5D5F5)), Orb(0.88f, 0.80f, 0.32f, c(0xB3CDEBFF)),
                    Orb(0.72f, 0.30f, 0.20f, c(0x80D2F5EC))),
                c(0xFFD0E5F1), c(0xFFBAD6E8), c(0x99FFFFFF), c(0x66CDEFF7), c(0x66FFFFFF), intArrayOf(),
            ),
            night = Palette(
                c(0xFF051626), c(0xFF0A2844), c(0xFF0D3A57),
                orbs(Orb(0.15f, 0.20f, 0.32f, c(0x993B82F6)), Orb(0.82f, 0.30f, 0.30f, c(0x8022D3EE)),
                    Orb(0.62f, 0.64f, 0.22f, c(0x6614B8A6)), Orb(0.35f, 0.90f, 0.25f, c(0x664F46E5))),
                c(0xFF0E2840), c(0xFF07182A), c(0xCC67E8F9), c(0xCC93C5FD), c(0x6622D3EE),
                intArrayOf(c(0xFF22D3EE), c(0xFF60A5FA), c(0xFF2DD4BF)),
            ),
        ),
        Style(
            "Grafito",
            day = Palette(
                c(0xFFE6E9EE), c(0xFFF2F3F5), c(0xFFE3E6EA),
                orbs(Orb(0.16f, 0.22f, 0.30f, c(0x99D1D5DB)), Orb(0.50f, 0.15f, 0.18f, c(0x80E5E7EB)),
                    Orb(0.10f, 0.80f, 0.22f, c(0x80CBD5E1)), Orb(0.88f, 0.80f, 0.32f, c(0x99DDE3EA))),
                c(0xFFD9DCE1), c(0xFFC8CDD4), c(0x99FFFFFF), c(0x66E5E7EB), c(0x66FFFFFF), intArrayOf(),
            ),
            night = Palette(
                c(0xFF0C0E11), c(0xFF15181D), c(0xFF1D2127),
                orbs(Orb(0.15f, 0.20f, 0.32f, c(0x66374151)), Orb(0.82f, 0.30f, 0.30f, c(0x552B3A55)),
                    Orb(0.62f, 0.64f, 0.22f, c(0x444B5563)), Orb(0.35f, 0.90f, 0.25f, c(0x441F2937))),
                c(0xFF1A1E24), c(0xFF101216), c(0x99CBD5E1), c(0x669CA3AF), c(0x33475569),
                intArrayOf(c(0xFF94A3B8), c(0xFFCBD5E1)),
            ),
        ),
        Style(
            "Bosque",
            day = Palette(
                c(0xFFD3EEDF), c(0xFFE6F5EC), c(0xFFEDF6E4),
                orbs(Orb(0.16f, 0.22f, 0.30f, c(0xB3BBF7D0)), Orb(0.50f, 0.15f, 0.18f, c(0x99CCFBF1)),
                    Orb(0.10f, 0.80f, 0.22f, c(0x88D9F99D)), Orb(0.88f, 0.80f, 0.32f, c(0x99C7EBD9))),
                c(0xFFD0E6D9), c(0xFFB9D7C6), c(0x99FFFFFF), c(0x66E7F8D2), c(0x66FFFFFF), intArrayOf(),
            ),
            night = Palette(
                c(0xFF06140D), c(0xFF0C2218), c(0xFF122E22),
                orbs(Orb(0.15f, 0.20f, 0.32f, c(0x7710B981)), Orb(0.82f, 0.30f, 0.30f, c(0x5522C55E)),
                    Orb(0.62f, 0.64f, 0.22f, c(0x5514B8A6)), Orb(0.35f, 0.90f, 0.25f, c(0x4465A30D))),
                c(0xFF0E271C), c(0xFF07160F), c(0xAAA7F3D0), c(0x99FDE68A), c(0x5510B981),
                intArrayOf(c(0xFF34D399), c(0xFFA3E635)),
            ),
        ),
        Style(
            "Atardecer",
            day = Palette(
                c(0xFFFCE1D0), c(0xFFFDEDE1), c(0xFFFBE5EF),
                orbs(Orb(0.16f, 0.22f, 0.30f, c(0xB3FED7AA)), Orb(0.50f, 0.15f, 0.18f, c(0x99FECDD3)),
                    Orb(0.10f, 0.80f, 0.22f, c(0x88FDE68A)), Orb(0.88f, 0.80f, 0.32f, c(0x99FBCFE8))),
                c(0xFFF4DACC), c(0xFFEBC6B6), c(0x99FFFFFF), c(0x66FFE4D6), c(0x66FFFFFF), intArrayOf(),
            ),
            night = Palette(
                c(0xFF180A10), c(0xFF2C0F17), c(0xFF41141A),
                orbs(Orb(0.15f, 0.20f, 0.32f, c(0x77F97316)), Orb(0.82f, 0.30f, 0.30f, c(0x66EF4444)),
                    Orb(0.62f, 0.64f, 0.22f, c(0x55F59E0B)), Orb(0.35f, 0.90f, 0.25f, c(0x55BE185D))),
                c(0xFF2A1117), c(0xFF17080C), c(0xCCFDBA74), c(0xAAFCA5A5), c(0x66F97316),
                intArrayOf(c(0xFFFB923C), c(0xFFF43F5E), c(0xFFFBBF24)),
            ),
        ),
    )

    fun style(index: Int) = STYLES.getOrElse(index) { STYLES[0] }

    /** Esfera decorativa: centro (px de la ventana), radio (px) e índice de color en la paleta. */
    class Sphere(val x: Float, val y: Float, val r: Float, val colorIndex: Int)

    fun render(
        w: Int, h: Int, night: Boolean, skyline: Boolean = true, style: Int = 0,
        spheres: List<Sphere> = emptyList(),
    ): Bitmap {
        val p = style(style).palette(night)
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
        if (spheres.isNotEmpty()) drawSpheres(c, spheres, p, paint)
        return bmp
    }

    /**
     * Esferas brillantes en 3D ancladas a las esquinas de las tarjetas (se ven a través del
     * vidrio y asoman entre ellas). Gradiente radial desplazado (volumen) + reflejo especular.
     */
    private fun drawSpheres(c: Canvas, spheres: List<Sphere>, p: Palette, paint: Paint) {
        for (sp in spheres) {
            val base = p.orbs[sp.colorIndex % p.orbs.size].color or 0xFF000000.toInt()
            val cx = sp.x
            val cy = sp.y
            val r = sp.r
            paint.shader = RadialGradient(
                cx - r * 0.35f, cy - r * 0.35f, r * 1.35f,
                intArrayOf(lighten(base, 0.55f), base, darken(base, 0.45f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, paint)
            // Reflejo especular
            paint.shader = RadialGradient(cx - r * 0.38f, cy - r * 0.42f, r * 0.42f, 0xB3FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx - r * 0.38f, cy - r * 0.42f, r * 0.42f, paint)
        }
        paint.shader = null
    }

    private fun lighten(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0xFF shl 24) or ((r + (255 - r) * f).toInt() shl 16) or ((g + (255 - g) * f).toInt() shl 8) or (b + (255 - b) * f).toInt()
    }

    private fun darken(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0xFF shl 24) or ((r * (1 - f)).toInt() shl 16) or ((g * (1 - f)).toInt() shl 8) or (b * (1 - f)).toInt()
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
