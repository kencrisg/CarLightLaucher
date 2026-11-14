package com.carlight.launcher.ui.glass

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.carlight.launcher.R

/**
 * Tarjeta de "vidrio esmerilado" SIN blur en tiempo real:
 * pinta la porción del fondo que queda detrás de la vista (mezcla del fondo NÍTIDO y del
 * DESENFOCADO según la [GlassLevel]), más un tinte, un brillo superior y un borde de cristal
 * que brilla arriba y se desvanece abajo.
 */
class GlassDrawable(
    res: Resources,
    private val radius: Float,
    private val roundBottom: Boolean = true,
    private val level: GlassLevel = GlassLevel.TRANSPARENT,
) : Drawable() {

    private val density = res.displayMetrics.density
    private val tint = scaleAlpha(res.getColor(R.color.glass_tint, null), level.tint)
    private val highlight = scaleAlpha(res.getColor(R.color.glass_highlight, null), level.highlight)
    private val strokeColor = res.getColor(R.color.glass_stroke, null)

    private val sharpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = (255 * (1f - level.clarity)).toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = res.getColor(R.color.glass_pressed, null) }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }

    private val rect = RectF()
    private val path = Path()
    private val shaderMatrix = Matrix()
    private var shader: BitmapShader? = null
    private var sharpShader: BitmapShader? = null
    private val sharpMatrix = Matrix()
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var pressed = false

    /**
     * @param sharp fondo nítido (tamaño ventana); solo se usa si el nivel deja ver algo de él.
     * @param blur fondo desenfocado (pequeño); @param scale tamaño ventana / tamaño de [blur].
     */
    fun setSource(sharp: Bitmap?, blur: Bitmap?, scale: Float) {
        this.scale = scale
        shader = blur?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        blurPaint.shader = shader
        sharpShader = if (level.clarity > 0f) sharp?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) } else null
        sharpPaint.shader = sharpShader
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
        shader?.let {
            shaderMatrix.setScale(scale, scale)
            shaderMatrix.postTranslate(-offX, -offY)
            it.setLocalMatrix(shaderMatrix)
        }
        sharpShader?.let {
            sharpMatrix.setTranslate(-offX, -offY)
            it.setLocalMatrix(sharpMatrix)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        rect.inset(density / 2, density / 2)
        val b = if (roundBottom) radius else 0f
        path.reset()
        path.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, b, b, b, b), Path.Direction.CW)
        highlightPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.45f,
            highlight, highlight and 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        // Borde de cristal: brillante arriba, tenue abajo.
        strokePaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(strokeColor, scaleAlpha(strokeColor, 0.55f), scaleAlpha(strokeColor, 0.25f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (sharpShader != null) canvas.drawPath(path, sharpPaint)
        if (shader != null) canvas.drawPath(path, blurPaint)
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, highlightPaint)
        if (pressed) canvas.drawPath(path, pressedPaint)
        canvas.drawPath(path, strokePaint)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        invalidateSelf()
        return true
    }

    private fun scaleAlpha(color: Int, f: Float): Int {
        val a = ((color ushr 24) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable API")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
