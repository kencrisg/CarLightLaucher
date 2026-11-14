package com.carlight.launcher.apps

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import com.carlight.launcher.R
import com.carlight.launcher.ui.Accent

enum class IconStyle(val label: String) {
    /** Glifo blanco sobre degradado propio de cada app (estilo uniforme, tipo Android Auto). */
    COLOR("Elegantes"),
    /** Glifo con el color de acento sobre vidrio tenue (iconos temáticos). */
    MONO("Monocromo"),
    /** Iconos originales de cada app. */
    ORIGINAL("Originales"),
}

/**
 * Paquete de iconos de CarLight: misma forma (squircle) y glifos Material Round para las apps
 * del auto. Las apps sin glifo propio muestran su icono original dentro del mismo marco.
 */
object IconPack {

    private class Glyph(val res: Int, val from: Int, val to: Int)

    private fun g(res: Int, from: Long, to: Long) = Glyph(res, from.toInt(), to.toInt())

    private val MAP = mapOf(
        "app.organicmaps.web" to g(R.drawable.glyph_map, 0xFF0EA5E9, 0xFF14B8A6),
        "app.organicmaps" to g(R.drawable.glyph_map, 0xFF0EA5E9, 0xFF14B8A6),
        "app.organicmaps.beta" to g(R.drawable.glyph_map, 0xFF0EA5E9, 0xFF14B8A6),
        "com.tw.radio" to g(R.drawable.glyph_radio, 0xFFF97316, 0xFFFBBF24),
        "com.carlight.launcher" to g(R.drawable.glyph_car, 0xFF0D9488, 0xFF5EEAD4),
        "com.tw.bt" to g(R.drawable.glyph_bluetooth, 0xFF2563EB, 0xFF60A5FA),
        "com.tw.media" to g(R.drawable.glyph_music, 0xFFE11D48, 0xFFF472B6),
        "com.android.settings" to g(R.drawable.glyph_settings, 0xFF475569, 0xFF94A3B8),
        "com.dofun.carsetting" to g(R.drawable.glyph_car, 0xFF16A34A, 0xFF4ADE80),
        "com.tw.carchoose" to g(R.drawable.glyph_car, 0xFF16A34A, 0xFF4ADE80),
        "com.tw.eq" to g(R.drawable.glyph_equalizer, 0xFF7C3AED, 0xFFC084FC),
        "com.tw.auxin" to g(R.drawable.glyph_input, 0xFFD97706, 0xFFFCD34D),
        "com.tw.video" to g(R.drawable.glyph_movie, 0xFFDC2626, 0xFFFB7185),
        "com.tw.twfileexplore" to g(R.drawable.glyph_folder, 0xFFCA8A04, 0xFFFDE047),
        "com.android.documentsui" to g(R.drawable.glyph_folder, 0xFFCA8A04, 0xFFFDE047),
        "com.android.chrome" to g(R.drawable.glyph_globe, 0xFF0284C7, 0xFF38BDF8),
        "com.android.camera2" to g(R.drawable.glyph_camera, 0xFF334155, 0xFF64748B),
        "com.google.android.GoogleCamera" to g(R.drawable.glyph_camera, 0xFF334155, 0xFF64748B),
    )

    private val APPS_TILE = Glyph(R.drawable.glyph_apps, 0xFFFF5E62.toInt(), 0xFFFF9A3D.toInt())

    /** Icono de una app en el estilo elegido. */
    fun bind(iv: ImageView, pkg: String, component: ComponentName, sizePx: Int, style: IconStyle, accent: Accent) {
        reset(iv)
        val glyph = MAP[pkg]
        when {
            style == IconStyle.ORIGINAL -> IconCache.bind(iv, component, sizePx)
            glyph != null -> drawGlyph(iv, glyph, sizePx, style, accent)
            else -> {
                // Sin glifo: icono original dentro del mismo marco, para mantener la uniformidad.
                iv.background = tile(iv, sizePx, solid = iv.context.getColor(R.color.icon_tile))
                val pad = (sizePx * 0.14f).toInt()
                iv.setPadding(pad, pad, pad, pad)
                IconCache.bind(iv, component, sizePx)
            }
        }
    }

    /** Botón fijo "Apps" de la cuadrícula. */
    fun bindAppsTile(iv: ImageView, sizePx: Int, style: IconStyle, accent: Accent) {
        reset(iv)
        drawGlyph(iv, APPS_TILE, sizePx, if (style == IconStyle.MONO) IconStyle.MONO else IconStyle.COLOR, accent)
    }

    /** Ranura vacía: marco tenue con un "+" discreto. */
    fun bindEmptySlot(iv: ImageView, sizePx: Int) {
        reset(iv)
        iv.tag = null
        iv.background = tile(iv, sizePx, solid = iv.context.getColor(R.color.icon_tile))
        iv.setColorFilter(iv.context.getColor(R.color.text_hint))
        val pad = (sizePx * 0.28f).toInt()
        iv.setPadding(pad, pad, pad, pad)
        iv.setImageResource(R.drawable.ic_plus)
    }

    private fun drawGlyph(iv: ImageView, glyph: Glyph, sizePx: Int, style: IconStyle, accent: Accent) {
        iv.tag = null   // que una carga pendiente de IconCache no pise el glifo
        if (style == IconStyle.MONO) {
            iv.background = tile(iv, sizePx, solid = iv.context.getColor(R.color.icon_tile))
            iv.setColorFilter(accent.end)
        } else {
            iv.background = glossy(tile(iv, sizePx, from = glyph.from, to = glyph.to), sizePx)
            iv.setColorFilter(Color.WHITE)
        }
        val pad = (sizePx * 0.24f).toInt()
        iv.setPadding(pad, pad, pad, pad)
        iv.setImageResource(glyph.res)
    }

    private fun tile(iv: ImageView, sizePx: Int, solid: Int? = null, from: Int = 0, to: Int = 0): GradientDrawable {
        val d = if (solid != null) GradientDrawable().apply { setColor(solid) }
        else GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to))
        d.cornerRadius = sizePx * 0.30f
        return d
    }

    /** Reflejo superior + sombra inferior suave: volumen 3D como en el tema de fábrica. */
    private fun glossy(base: GradientDrawable, sizePx: Int): android.graphics.drawable.Drawable {
        val r = sizePx * 0.30f
        val gloss = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66FFFFFF, 0x00FFFFFF)).apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        val shade = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x33000000)).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(base, gloss, shade)).apply {
            setLayerInset(1, 0, 0, 0, sizePx / 2)
            setLayerInset(2, 0, sizePx * 2 / 3, 0, 0)
        }
    }

    private fun reset(iv: ImageView) {
        iv.background = null
        iv.clearColorFilter()
        iv.alpha = 1f
        iv.setPadding(0, 0, 0, 0)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
    }
}
