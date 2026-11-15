package com.carlight.launcher.calls

import android.content.Context
import com.carlight.launcher.overlay.DashboardController
import com.carlight.launcher.prefs.Prefs

/**
 * Llamadas: al empezar una llamada se oculta el panel y se muestra la pantalla nativa de Topway
 * a pantalla completa (tiene audio al celular, teclado, colgar...). Al colgar vuelve el panel.
 * No se dibuja ninguna tarjeta propia.
 */
object CallOverlay {

    private var app: Context? = null

    fun start(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        CallMonitor.start(ctx)
        CallMonitor.addListener { call ->
            val a = app ?: return@addListener
            if (Prefs(a).callsOverMap) DashboardController.onCallChanged(a, call != null)
        }
        // Valor de fábrica de la supresión del chip (versiones anteriores lo cambiaban).
        runCatching { ctx.getSystemService(android.media.AudioManager::class.java).setParameters("bt_headset_nrec=off") }
    }
}
