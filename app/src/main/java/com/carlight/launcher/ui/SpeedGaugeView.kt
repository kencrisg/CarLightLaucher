package com.carlight.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.carlight.launcher.R
import kotlin.math.min
import kotlin.math.roundToInt

/** Velocímetro de arco (240°). Solo redibuja cuando cambia la velocidad. */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var maxKmh = 180f

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val stroke = 16 * dp

    private val trackPaint = arcPaint().apply { color = context.getColor(R.color.gauge_track) }
    private val progressPaint = arcPaint()
    private val numberPaint = textPaint(84 * sp, R.color.text_primary, Typeface.create("sans-serif-light", Typeface.NORMAL))
    private val unitPaint = textPaint(20 * sp, R.color.text_secondary, Typeface.DEFAULT)
    private val statusPaint = textPaint(14 * sp, R.color.text_hint, Typeface.DEFAULT)

    private val arc = RectF()
    private var shown = 0f
    private var speed: Int? = null
    private var status = ""
    private var animator: ValueAnimator? = null

    fun setSpeed(kmh: Int?, statusText: String) {
        status = statusText
        speed = kmh
        val target = (kmh ?: 0).toFloat().coerceIn(0f, maxKmh)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h) - stroke
        val left = (w - size) / 2f
        val top = (h - size) / 2f + stroke / 2
        arc.set(left, top, left + size, top + size)
        val g = SweepGradient(
            arc.centerX(), arc.centerY(),
            intArrayOf(
                context.getColor(R.color.gauge_1),
                context.getColor(R.color.gauge_2),
                context.getColor(R.color.gauge_3),
                context.getColor(R.color.gauge_3),
            ),
            floatArrayOf(0f, 0.33f, 0.67f, 1f),
        )
        g.setLocalMatrix(Matrix().apply { setRotate(START - 6f, arc.centerX(), arc.centerY()) })
        progressPaint.shader = g
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(arc, START, SWEEP, false, trackPaint)
        val frac = shown / maxKmh
        if (frac > 0.003f) canvas.drawArc(arc, START, SWEEP * frac, false, progressPaint)

        val cx = arc.centerX()
        val cy = arc.centerY()
        val number = if (speed == null) "--" else shown.roundToInt().toString()
        canvas.drawText(number, cx, cy + numberPaint.textSize * 0.30f, numberPaint)
        canvas.drawText(UNIT, cx, cy + numberPaint.textSize * 0.30f + unitPaint.textSize * 1.5f, unitPaint)
        if (status.isNotEmpty()) canvas.drawText(status, cx, arc.bottom - stroke * 0.2f, statusPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun arcPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    private fun textPaint(size: Float, color: Int, tf: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size
        typeface = tf
        this.color = context.getColor(color)
    }

    private companion object {
        const val START = 150f
        const val SWEEP = 240f
        const val UNIT = "km/h"
    }
}
