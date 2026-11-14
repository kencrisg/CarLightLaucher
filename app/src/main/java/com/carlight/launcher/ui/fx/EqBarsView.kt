package com.carlight.launcher.ui.fx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** Ecualizador animado (4 barras) mientras suena la música. ~15 fps, solo al sonar. */
class EqBarsView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()
    private var playing = false
    var animate = true
    private var t = 0f

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPlaying(p: Boolean) {
        playing = p
        visibility = if (p) VISIBLE else INVISIBLE
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val n = 4
        val gap = width * 0.12f
        val bw = (width - gap * (n - 1)) / n
        val rad = bw / 2
        for (i in 0 until n) {
            val f = if (animate) 0.25f + 0.75f * abs(sin(t * (1.3f + i * 0.37f) + i * 1.7f)) else 0.35f + 0.2f * (i % 2)
            val bh = height * f
            val x = i * (bw + gap)
            bar.set(x, height - bh, x + bw, height.toFloat())
            c.drawRoundRect(bar, rad, rad, paint)
        }
        if (playing && animate) {
            t += 0.22f
            postInvalidateDelayed(66)
        }
    }
}
