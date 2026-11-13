package com.carlight.launcher.media

import android.content.Context
import com.carlight.launcher.util.AppLauncher

data class BtState(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val demo: Boolean = false,
    /** false = no hay teléfono conectado por Bluetooth. */
    val connected: Boolean = true,
)

interface BtSource {
    fun start(onState: (BtState) -> Unit)
    fun stop()
    fun playPause()
    fun next()
    fun prev()
    fun open()

    /** Otra fuente (radio) toma el audio desde el panel: pausar el teléfono si suena. */
    fun pauseForOtherSource() {}

    companion object {
        const val TW_BT_PKG = "com.tw.bt"

        /** Sesión AVRCP real en la radio; demo en el emulador. */
        fun create(ctx: Context): BtSource =
            if (AppLauncher.isInstalled(ctx, TW_BT_PKG)) A2dpBtSource(ctx) else DemoBtSource()
    }
}
