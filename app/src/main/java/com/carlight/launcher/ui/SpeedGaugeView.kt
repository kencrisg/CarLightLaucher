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

/** Velocímetro de arco (240°), compacto. Tamaños proporcionales al diámetro. Solo redibuja al cambiar la velocidad. */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Máximo de la escala, en la unidad mostrada. */
    var maxValue = 180f
    /** "km/h" o "mph". */
    var unit = "km/h"
        set(v) { field = v; invalidate() }
    /** Alerta de velocidad: arco en rojo. */
    var alert = false
        set(v) {
            if (field == v) return
            field = v
            if (width > 0) onSizeChanged(width, height, width, height)
            invalidate()
        }

    /** 3 colores del arco (acento). null = colores del tema. */
    var colors: IntArray? = null
        set(v) { field = v; if (width > 0) onSizeChanged(width, height, width, height); invalidate() }

    private var stroke = 0f

    private val trackPaint = arcPaint().apply { color = context.getColor(R.color.gauge_track) }
    private val progressPaint = arcPaint()
    /** Resplandor suave: dos capas translúcidas un poco más anchas que el arco. */
    private val glowOuter = arcPaint().apply { alpha = 28 }
    private val glowInner = arcPaint().apply { alpha = 55 }
    private val numberPaint = textPaint(R.color.text_primary, Typeface.create("sans-serif-light", Typeface.NORMAL))
    private val unitPaint = textPaint(R.color.text_secondary, Typeface.DEFAULT)

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_hint)
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()
    private var shown = 0f
    private var speed: Int? = null
    private var status = ""
    private var animator: ValueAnimator? = null

    /** @param value velocidad YA convertida a la unidad mostrada. */
    fun setSpeed(value: Int?, statusText: String) {
        val kmh = value
        status = statusText
        speed = kmh
        val target = (kmh ?: 0).toFloat().coerceIn(0f, maxValue)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val d = min(w, h).toFloat()
        stroke = d * 0.085f
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        glowOuter.strokeWidth = stroke * 1.9f
        glowInner.strokeWidth = stroke * 1.4f
        tickPaint.strokeWidth = d * 0.012f
        numberPaint.textSize = d * 0.34f
        unitPaint.textSize = d * 0.12f
        val size = d - stroke
        val left = (w - size) / 2f
        val top = (h - size) / 2f + stroke / 2
        arc.set(left, top, left + size, top + size)
        val cs = if (alert) intArrayOf(0xFFF97316.toInt(), 0xFFEF4444.toInt(), 0xFFDC2626.toInt()) else colors ?: intArrayOf(
            context.getColor(R.color.gauge_1),
            context.getColor(R.color.gauge_2),
            context.getColor(R.color.gauge_3),
        )
        // El degradado arranca 20° antes del arco: la punta redondeada del inicio queda en el
        // primer color, y el tramo fuera del arco vuelve suave al primer color (sin mancha rosa).
        val pre = 20f / 360f
        val end = pre + SWEEP / 360f
        val g = SweepGradient(
            arc.centerX(), arc.centerY(),
            intArrayOf(cs[0], cs[0], cs[1], cs[2], cs[2], cs[0]),
            floatArrayOf(0f, pre, pre + (end - pre) / 2f, end, end + 0.08f, 1f),
        )
        g.setLocalMatrix(Matrix().apply { setRotate(START - 20f, arc.centerX(), arc.centerY()) })
        progressPaint.shader = g
        glowOuter.shader = g
        glowInner.shader = g
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(arc, START, SWEEP, false, trackPaint)
        val frac = shown / maxValue
        if (frac > 0.003f) {
            canvas.drawArc(arc, START, SWEEP * frac, false, glowOuter)
            canvas.drawArc(arc, START, SWEEP * frac, false, glowInner)
            canvas.drawArc(arc, START, SWEEP * frac, false, progressPaint)
        }

        val cx = arc.centerX()
        val cy = arc.centerY()

        // Marcas de escala por dentro del arco: cada 1/12 de la escala (largas cada 1/4).
        val rOuter = arc.width() / 2 - stroke * 0.95f
        for (i in 0..12) {
            val long = i % 3 == 0
            val a = Math.toRadians((START + SWEEP * i / 12f).toDouble())
            val rInner = rOuter - stroke * (if (long) 0.75f else 0.4f)
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            tickPaint.alpha = if (long) 200 else 110
            canvas.drawLine(cx + cos * rInner, cy + sin * rInner, cx + cos * rOuter, cy + sin * rOuter, tickPaint)
        }
        val number = if (speed == null) "--" else shown.roundToInt().toString()
        canvas.drawText(number, cx, cy + numberPaint.textSize * 0.25f, numberPaint)
        // Línea base por encima del borde: que no se corten los descendentes (la "p" de mph).
        canvas.drawText(unit, cx, arc.bottom - stroke * 0.1f - unitPaint.descent(), unitPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun arcPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun textPaint(color: Int, tf: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = tf
        this.color = context.getColor(color)
    }

    private companion object {
        const val START = 150f
        const val SWEEP = 240f
    }
}
