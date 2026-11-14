package com.carlight.launcher.ui.fx

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Ilustración junto a la hora: sol con nube (día) o luna con estrellas (noche). Estática. */
class SkyBadgeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var night = false
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        if (night) drawMoon(c, s) else drawSun(c, s)
    }

    private fun drawSun(c: Canvas, s: Float) {
        val cx = s * 0.42f
        val cy = s * 0.40f
        p.shader = RadialGradient(cx, cy, s * 0.48f, intArrayOf(0x88FFD166.toInt(), 0x00FFD166), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s * 0.48f, p)
        p.shader = RadialGradient(cx - s * 0.08f, cy - s * 0.08f, s * 0.3f, intArrayOf(0xFFFFF1A8.toInt(), 0xFFFFB627.toInt(), 0xFFFF8A1F.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s * 0.24f, p)
        // Nube
        path.reset()
        val by = s * 0.74f
        path.addCircle(s * 0.50f, by - s * 0.10f, s * 0.15f, Path.Direction.CW)
        path.addCircle(s * 0.68f, by - s * 0.13f, s * 0.18f, Path.Direction.CW)
        path.addCircle(s * 0.84f, by - s * 0.04f, s * 0.12f, Path.Direction.CW)
        path.addRoundRect(s * 0.40f, by - s * 0.10f, s * 0.95f, by + s * 0.08f, s * 0.09f, s * 0.09f, Path.Direction.CW)
        p.shader = LinearGradient(0f, by - s * 0.3f, 0f, by + s * 0.08f, 0xFFFFFFFF.toInt(), 0xFFDCE6F5.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }

    private fun drawMoon(c: Canvas, s: Float) {
        val cx = s * 0.50f
        val cy = s * 0.46f
        val r = s * 0.27f
        p.shader = RadialGradient(cx, cy, s * 0.5f, intArrayOf(0x66C7D2FE, 0x00C7D2FE), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s * 0.5f, p)
        val moon = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.9f, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFFBEB.toInt(), 0xFFCBD5E1.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(moon, p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        star(c, s * 0.82f, s * 0.22f, s * 0.07f)
        star(c, s * 0.18f, s * 0.80f, s * 0.05f)
        star(c, s * 0.86f, s * 0.70f, s * 0.04f)
    }

    /** Destello de 4 puntas. */
    private fun star(c: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r)
        path.quadTo(x, y, x + r, y)
        path.quadTo(x, y, x, y + r)
        path.quadTo(x, y, x - r, y)
        path.quadTo(x, y, x, y - r)
        c.drawPath(path, p)
    }
}
