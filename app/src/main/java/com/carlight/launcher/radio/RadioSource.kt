package com.carlight.launcher.radio

import android.content.Context
import com.carlight.launcher.util.AppLauncher

/**
 * @param frequency frecuencia ×10 (1015 = 101.5 MHz), null si se desconoce.
 * @param preset presintonía activa (1..n), 0 si no aplica.
 */
data class RadioState(
    val frequency: Int?,
    val preset: Int = 0,
    val demo: Boolean = false,
)

interface RadioSource {
    fun start(onState: (RadioState) -> Unit)
    fun stop()
    fun next()
    fun prev()
    fun seekUp()
    fun seekDown()
    fun open()

    /** true si los controles abren la app de fábrica (hay que ocultar el panel flotante). */
    val opensApp: Boolean get() = false

    companion object {
        const val TW_RADIO_PKG = "com.tw.radio"

        /** Fase 1: fallback (nivel C) en la radio real; demo en el emulador. Fase 4 añadirá TopwayRadioSource. */
        fun create(ctx: Context): RadioSource =
            if (AppLauncher.isInstalled(ctx, TW_RADIO_PKG)) FallbackRadioSource(ctx) else DemoRadioSource()
    }
}

fun RadioState.formatFrequency(): String? = frequency?.let { "${it / 10}.${it % 10}" }
