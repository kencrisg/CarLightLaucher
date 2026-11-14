package com.carlight.launcher.ui.fx

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.carlight.launcher.R
import kotlin.math.min

/**
 * "Disco" de la música: resplandor del acento, vinilo con surcos y reflejo, etiqueta central con
 * nota musical. Gira lento mientras suena (solo rota la capa ya pintada: casi sin costo).
 */
class DiscView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var accentStart = 0xFF6D5BFF.toInt()
    private var accentEnd = 0xFFB26BFF.toInt()
    var animate = true

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x22FFFFFF }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val note = ctx.getDrawable(R.drawable.glyph_music)!!.mutate().apply { setTint(Color.WHITE) }

    private val spin = ObjectAnimator.ofFloat(this, ROTATION, 0f, 360f).apply {
        duration = 7000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAccent(start: Int, end: Int) {
        accentStart = start
        accentEnd = end
        if (width > 0) onSizeChanged(width, height, width, height)
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        when {
            playing && animate -> if (spin.isPaused) spin.resume() else if (!spin.isStarted) spin.start()
            else -> if (spin.isRunning) spin.pause()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f
        glow.shader = RadialGradient(cx, cy, r, intArrayOf(accentEnd and 0x00FFFFFF or 0x88000000.toInt(), accentStart and 0x00FFFFFF), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        val dr = r * 0.78f
        disc.shader = RadialGradient(cx - dr * 0.3f, cy - dr * 0.3f, dr * 1.4f, intArrayOf(0xFF3A3A48.toInt(), 0xFF15151C.toInt()), null, Shader.TileMode.CLAMP)
        rim.strokeWidth = r * 0.05f
        rim.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), accentStart, accentEnd, Shader.TileMode.CLAMP)
        groove.strokeWidth = r * 0.012f
        // Reflejo diagonal: hace visible el giro.
        sheen.shader = LinearGradient(cx - dr, cy - dr, cx + dr, cy + dr,
            intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.2f, 0.38f, 0.62f, 0.8f, 1f), Shader.TileMode.CLAMP)
        val lr = r * 0.32f
        label.shader = LinearGradient(cx - lr, cy - lr, cx + lr, cy + lr, accentStart, accentEnd, Shader.TileMode.CLAMP)
        val n = (r * 0.34f).toInt()
        note.setBounds((cx - n / 2f).toInt(), (cy - n / 2f).toInt(), (cx + n / 2f).toInt(), (cy + n / 2f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val dr = r * 0.78f
        c.drawCircle(cx, cy, r, glow)
        c.drawCircle(cx, cy, dr, disc)
        for (f in floatArrayOf(0.48f, 0.6f, 0.72f, 0.86f)) c.drawCircle(cx, cy, dr * f, groove)
        c.drawCircle(cx, cy, dr, sheen)
        c.drawCircle(cx, cy, dr, rim)
        c.drawCircle(cx, cy, r * 0.32f, label)
        note.draw(c)
    }

    override fun onDetachedFromWindow() {
        spin.cancel()
        super.onDetachedFromWindow()
    }
}
