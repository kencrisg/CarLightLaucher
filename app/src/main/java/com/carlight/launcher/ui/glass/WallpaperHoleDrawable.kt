package com.carlight.launcher.ui.glass

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.carlight.launcher.R

/**
 * Fondo de la ventana (translúcida): dibuja el wallpaper en todas partes MENOS en el hueco
 * del mapa, por donde se ve Organic Maps corriendo detrás.
 */
class WallpaperHoleDrawable(
    res: Resources,
    /** Degradado de respaldo mientras se genera el fondo real (colores del estilo elegido). */
    private val fallback: IntArray = intArrayOf(
        res.getColor(R.color.wp_start, null),
        res.getColor(R.color.wp_center, null),
        res.getColor(R.color.wp_end, null),
    ),
) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var bitmap: Bitmap? = null
    private val holePath = Path()
    private val holeRect = RectF()
    private var radius = 0f
    private var glowColor = 0
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowRect = RectF()

    /** Halo suave del color dado alrededor del hueco (por fuera), para separar el mapa del panel. */
    fun setHoleGlow(color: Int) {
        glowColor = color
        invalidateSelf()
    }

    fun setBitmap(b: Bitmap) {
        bitmap = b
        invalidateSelf()
    }

    /** Hueco con las esquinas redondeadas (la tarjeta del mapa es todo hueco). */
    fun setHole(r: Rect?, radiusPx: Float) {
        holePath.reset()
        if (r == null || r.isEmpty) {
            holeRect.setEmpty()
        } else {
            holeRect.set(r)
            radius = radiusPx
            holePath.addRoundRect(holeRect, radius, radius, Path.Direction.CW)
        }
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        paint.shader = LinearGradient(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), fallback, null, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        if (!holeRect.isEmpty) canvas.clipOutPath(holePath)
        val b = bitmap
        if (b != null) canvas.drawBitmap(b, 0f, 0f, paint.also { it.shader = null })
        else {
            if (paint.shader == null) onBoundsChange(bounds)
            canvas.drawRect(bounds, paint)
        }
        // Halo: anillos concéntricos que se desvanecen hacia afuera (dibujados fuera del hueco).
        if (glowColor != 0 && !holeRect.isEmpty) {
            // Halo discreto: 3 anillos de 1 px que se desvanecen.
            glowPaint.strokeWidth = 1f
            for (i in 1..3) {
                val off = i.toFloat()
                glowRect.set(holeRect.left - off, holeRect.top - off, holeRect.right + off, holeRect.bottom + off)
                glowPaint.color = ((70 - i * 20) shl 24) or (glowColor and 0x00FFFFFF)
                canvas.drawRoundRect(glowRect, radius + off, radius + off, glowPaint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable API")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
