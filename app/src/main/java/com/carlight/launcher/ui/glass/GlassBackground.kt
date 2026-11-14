package com.carlight.launcher.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.carlight.launcher.util.Bg

/**
 * Genera el fondo y su copia desenfocada (en segundo plano, una vez por tamaño + tema)
 * y lo conecta con las tarjetas de vidrio.
 */
class GlassBackground(
    private val context: Context,
    private val night: Boolean,
    private val skyline: Boolean = true,
    private val style: Int = 0,
    private val level: GlassLevel = GlassLevel.TRANSPARENT,
    /** Esferas decorativas (en coordenadas de la vista raíz), calculadas tras el layout. */
    private val spheres: (() -> List<WallpaperRenderer.Sphere>)? = null,
    private val onWallpaper: (Bitmap) -> Unit,
) {

    private val cards = mutableListOf<Pair<View, GlassDrawable>>()
    private val loc = IntArray(2)

    fun attach(view: View, radiusDp: Float, roundBottom: Boolean = true) {
        val d = GlassDrawable(context.resources, radiusDp * context.resources.displayMetrics.density, roundBottom, level)
        view.background = d
        cards += view to d
        cache?.takeIf { it.night == night && it.skyline == skyline && it.style == style && it.spheres == (spheres != null) }?.let { d.setSource(it.sharp, it.blur, it.scale) }
    }

    private var released = false
    /** Por instancia: un panel reconstruido no debe esperar el fondo de otro estilo. */
    private var pending = false

    fun release() { released = true }

    /** Llamar tras el layout: actualiza la posición de cada tarjeta y genera el fondo si hace falta. */
    fun onLayout(decor: View) {
        for ((v, d) in cards) {
            v.getLocationInWindow(loc)
            d.setOffset(loc[0], loc[1])
        }
        val w = decor.width
        val h = decor.height
        if (w == 0 || h == 0) return
        val c = cache
        if (c != null && c.w == w && c.h == h && c.night == night && c.skyline == skyline && c.style == style && c.spheres == (spheres != null)) {
            apply(c)
            return
        }
        if (pending) return
        pending = true
        val anchors = spheres?.invoke().orEmpty()   // en el hilo UI (lee posiciones de vistas)
        Bg.io.execute {
            val sharp = WallpaperRenderer.render(w, h, night, skyline, style, anchors)
            val blur = Blur.downscaled(sharp, FACTOR, RADIUS)
            val result = Cache(w, h, night, skyline, style, spheres != null, sharp, blur, w.toFloat() / blur.width)
            Bg.main.post {
                pending = false
                cache = result
                if (!released) apply(result)
            }
        }
    }

    private var applied: Cache? = null

    private fun apply(c: Cache) {
        if (applied === c) return
        applied = c
        onWallpaper(c.sharp)
        cards.forEach { it.second.setSource(c.sharp, c.blur, c.scale) }
    }

    private class Cache(
        val w: Int, val h: Int, val night: Boolean, val skyline: Boolean, val style: Int, val spheres: Boolean,
        val sharp: Bitmap, val blur: Bitmap, val scale: Float,
    )

    private companion object {
        const val FACTOR = 8   // 1024x600 → 128x75 (≈38 KB)
        const val RADIUS = 3
        @Volatile var cache: Cache? = null
    }
}
