package com.carlight.launcher.ui.fx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.carlight.launcher.R

/** Mini dial FM 87.5–108 MHz con indicador luminoso que se desliza al sintonizar. */
class FreqDialView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.text_hint) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.getColor(R.color.text_hint)
        textAlign = Paint.Align.CENTER
    }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accent = 0xFFB26BFF.toInt()
    private var shown = -1f
    private var anim: ValueAnimator? = null

    fun setAccent(color: Int) {
        accent = color
        needle.color = color
        invalidate()
    }

    /** @param freq ×10 (1015 = 101.5 MHz); null = sin dato. */
    fun setFrequency(freq: Int?) {
        val target = freq?.toFloat() ?: -1f
        anim?.cancel()
        if (shown < 0f || target < 0f) {
            shown = target
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = h * 0.3f
        val base = h * 0.62f
        tick.strokeWidth = h * 0.045f
        text.textSize = h * 0.32f
        fun x(f: Float) = pad + (f - MIN) / (MAX - MIN) * (w - 2 * pad)
        var f = 880
        while (f <= 1080) {
            val major = f % 40 == 0
            tick.alpha = if (major) 210 else 110
            val top = if (major) base - h * 0.34f else base - h * 0.18f
            c.drawLine(x(f.toFloat()), top, x(f.toFloat()), base, tick)
            if (major) c.drawText((f / 10).toString(), x(f.toFloat()), h * 0.98f, text)
            f += 10
        }
        if (shown >= MIN) {
            val nx = x(shown.coerceIn(MIN, MAX))
            glow.shader = RadialGradient(nx, base - h * 0.2f, h * 0.55f, accent and 0x00FFFFFF or 0x77000000, accent and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(nx, base - h * 0.2f, h * 0.55f, glow)
            needle.strokeWidth = h * 0.07f
            needle.strokeCap = Paint.Cap.ROUND
            c.drawLine(nx, base - h * 0.5f, nx, base + h * 0.02f, needle)
        }
    }

    private companion object {
        const val MIN = 875f
        const val MAX = 1080f
    }
}
