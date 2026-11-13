package com.carlight.launcher.media

import android.content.Context
import com.carlight.launcher.util.AppLauncher

data class BtState(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val demo: Boolean = false,
)

interface BtSource {
    fun start(onState: (BtState) -> Unit)
    fun stop()
    fun playPause()
    fun next()
    fun prev()
    fun open()

    companion object {
        const val TW_BT_PKG = "com.tw.bt"

        /** Fase 1: teclas multimedia en la radio real; demo en el emulador. Fase 3 añadirá MediaBrowser (AVRCP). */
        fun create(ctx: Context): BtSource =
            if (AppLauncher.isInstalled(ctx, TW_BT_PKG)) MediaKeyBtSource(ctx) else DemoBtSource()
    }
}
