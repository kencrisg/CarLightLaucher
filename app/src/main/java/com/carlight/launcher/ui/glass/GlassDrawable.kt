package com.carlight.launcher.ui.glass

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.carlight.launcher.R

/**
 * Tarjeta de "vidrio esmerilado" SIN blur en tiempo real:
 * pinta la porción del fondo ya desenfocado (bitmap pequeño, escalado con filtro)
 * que queda detrás de la vista, más un tinte, un brillo superior y un borde.
 */
class GlassDrawable(res: Resources, private val radius: Float) : Drawable() {

    private val density = res.displayMetrics.density
    private val tint = res.getColor(R.color.glass_tint, null)
    private val highlight = res.getColor(R.color.glass_highlight, null)

    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = res.getColor(R.color.glass_pressed, null) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = res.getColor(R.color.glass_stroke, null)
    }

    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private var shader: BitmapShader? = null
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var pressed = false

    /** @param scale tamaño de la ventana / tamaño del bitmap desenfocado. */
    fun setSource(bitmap: Bitmap?, scale: Float) {
        this.scale = scale
        shader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        blurPaint.shader = shader
        updateMatrix()
        invalidateSelf()
    }

    /** Posición de la vista dentro de la ventana. */
    fun setOffset(x: Int, y: Int) {
        if (x.toFloat() == offX && y.toFloat() == offY) return
        offX = x.toFloat()
        offY = y.toFloat()
        updateMatrix()
        invalidateSelf()
    }

    private fun updateMatrix() {
        val s = shader ?: return
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(-offX, -offY)
        s.setLocalMatrix(shaderMatrix)
    }

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(density / 2, density / 2)
        highlightPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.45f,
            highlight, highlight and 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (shader != null) canvas.drawRoundRect(rect, radius, radius, blurPaint)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, highlightPaint)
        if (pressed) canvas.drawRoundRect(rect, radius, radius, pressedPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable API")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
