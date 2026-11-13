package com.carlight.launcher.ui

import android.graphics.drawable.GradientDrawable
import com.carlight.launcher.prefs.Prefs

/** Color de acento elegible en Ajustes (botón ▶, velocímetro, selección en ajustes). */
data class Accent(val name: String, val start: Int, val end: Int) {

    /** Degradado del velocímetro: inicio → fin → un tono más cálido para velocidades altas. */
    val gauge: IntArray get() = intArrayOf(start, end, HOT)

    fun pill(radiusPx: Float, oval: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(start, end),
    ).apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = radiusPx
    }

    companion object {
        private const val HOT = 0xFFFF3D7F.toInt()

        val ALL = listOf(
            Accent("Violeta", 0xFF6D5BFF.toInt(), 0xFFB26BFF.toInt()),
            Accent("Azul", 0xFF2563EB.toInt(), 0xFF38BDF8.toInt()),
            Accent("Cian", 0xFF0891B2.toInt(), 0xFF2DD4BF.toInt()),
            Accent("Verde", 0xFF16A34A.toInt(), 0xFF84CC16.toInt()),
            Accent("Naranja", 0xFFEA580C.toInt(), 0xFFFBBF24.toInt()),
            Accent("Rosa", 0xFFDB2777.toInt(), 0xFFF472B6.toInt()),
        )

        fun current(prefs: Prefs) = ALL.getOrElse(prefs.accent) { ALL[0] }
    }
}
