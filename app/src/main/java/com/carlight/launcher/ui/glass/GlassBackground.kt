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
    private val onWallpaper: (Bitmap) -> Unit,
) {

    private val cards = mutableListOf<Pair<View, GlassDrawable>>()
    private val loc = IntArray(2)

    fun attach(view: View, radiusDp: Float, roundBottom: Boolean = true) {
        val d = GlassDrawable(context.resources, radiusDp * context.resources.displayMetrics.density, roundBottom)
        view.background = d
        cards += view to d
        cache?.takeIf { it.night == night }?.let { d.setSource(it.blur, it.scale) }
    }

    private var released = false

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
        if (c != null && c.w == w && c.h == h && c.night == night) {
            apply(c)
            return
        }
        if (pending) return
        pending = true
        Bg.io.execute {
            val sharp = WallpaperRenderer.render(w, h, night)
            val blur = Blur.downscaled(sharp, FACTOR, RADIUS)
            val result = Cache(w, h, night, sharp, blur, w.toFloat() / blur.width)
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
        cards.forEach { it.second.setSource(c.blur, c.scale) }
    }

    private class Cache(val w: Int, val h: Int, val night: Boolean, val sharp: Bitmap, val blur: Bitmap, val scale: Float)

    private companion object {
        const val FACTOR = 8   // 1024x600 → 128x75 (≈38 KB)
        const val RADIUS = 3
        @Volatile var cache: Cache? = null
        var pending = false
    }
}
