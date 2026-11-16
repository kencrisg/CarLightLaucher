package com.carlight.launcher.radio

import android.content.Context
import android.os.SystemClock
import com.carlight.launcher.topway.TwAudioSource
import com.carlight.launcher.topway.TwCommandClient
import com.carlight.launcher.util.AppLauncher

/**
 * Radio real de Topway:
 *  - Controles por el centro de comandos (radioNext/radioPre/radioSearchNext/radioSearchPre),
 *    que los reenvía a la app de radio de fábrica.
 *  - Frecuencia y presintonía leídas del log ([LogcatRadioWatcher]); se recuerdan entre arranques.
 * Antes de cada control se cambia la fuente de audio a radio ([TwAudioSource]) para callar el BT.
 * Los controles NUNCA abren la app (solo si el centro de comandos no existe).
 */
class TopwayRadioSource(private val ctx: Context) : RadioSource {

    private val prefs = ctx.getSharedPreferences("radio", Context.MODE_PRIVATE)
    private val watcher = LogcatRadioWatcher(ctx)
    private var listener: ((RadioState) -> Unit)? = null
    private var freq: Int? = prefs.getInt(KEY_FREQ, 0).takeIf { it > 0 }
    private var preset = prefs.getInt(KEY_PRESET, 0)

    override var onNeedsApp: (() -> Unit)? = null

    override fun start(onState: (RadioState) -> Unit) {
        listener = onState
        TwCommandClient.bind(ctx)
        emit()
        watcher.start { f, p ->
            if (f != null) {
                lastTuneAt = SystemClock.elapsedRealtime()
                freq = f
                prefs.edit().putInt(KEY_FREQ, f).apply()
            }
            // Tras una búsqueda la radio sigue reportando la última presintonía aunque la emisora
            // encontrada no sea una de ellas: en ese caso no mostrar presintonía.
            if (f != null && SystemClock.elapsedRealtime() - seekAt < SEEK_WINDOW_MS) {
                preset = 0
                prefs.edit().putInt(KEY_PRESET, 0).apply()
            }
            if (p != null && SystemClock.elapsedRealtime() - seekAt >= SEEK_WINDOW_MS) {
                preset = p
                prefs.edit().putInt(KEY_PRESET, p).apply()
            }
            emit()
        }
    }

    override fun stop() {
        listener = null
        watcher.stop()
    }

    override fun next() { seekAt = 0L; command { it.radioNext() } }
    override fun prev() { seekAt = 0L; command { it.radioPre() } }
    override fun seekUp() { seekAt = SystemClock.elapsedRealtime(); command { it.radioSearchNext() } }
    override fun seekDown() { seekAt = SystemClock.elapsedRealtime(); command { it.radioSearchPre() } }

    private var seekAt = 0L
    override fun open() { AppLauncher.launchPackage(ctx, RadioSource.TW_RADIO_PKG) }

    private var lastTuneAt = 0L
    private var waking = false
    private var pending: ((com.tw.service.xt.aidl.ITWCommandAidl) -> Unit)? = null

    private fun command(block: (com.tw.service.xt.aidl.ITWCommandAidl) -> Unit) {
        TwAudioSource.request(TwAudioSource.RADIO)
        // Topway cierra la app de radio al entrar en standby: sin ella nadie recibe la orden.
        if (!radioRunning()) {
            wakeRadioApp(block)
            return
        }
        if (!TwCommandClient.send(block)) {
            onNeedsApp?.invoke() ?: open()
            return
        }
        // Estado desconocido (log rotado): si en 1,2 s no sintoniza, despertarla.
        if (watcher.radioAlive == null && watcher.hasPermission()) {
            val sentAt = android.os.SystemClock.elapsedRealtime()
            com.carlight.launcher.util.Bg.main.postDelayed({ if (lastTuneAt < sentAt) wakeRadioApp(block) }, 1200)
        }
    }

    /** Según el log del sistema (inicios/cierres de com.tw.radio). Desconocido = se asume viva. */
    private fun radioRunning(): Boolean = watcher.radioAlive != false

    /**
     * Abre la app de radio para despertarla; en cuanto su pantalla aparece, manda la orden y vuelve
     * al mapa (sin tiempos fijos). Respaldo de 2,5 s por si no llega el aviso de pantalla.
     */
    private fun wakeRadioApp(block: (com.tw.service.xt.aidl.ITWCommandAidl) -> Unit) {
        pending = block
        if (waking) return
        waking = true
        android.util.Log.i("RadioLog", "app de radio cerrada: despertándola")
        com.carlight.launcher.overlay.DashboardController.addForegroundListener(onFg)
        AppLauncher.launchPackage(ctx, RadioSource.TW_RADIO_PKG)
        com.carlight.launcher.util.Bg.main.postDelayed(finishWake, 2500)
    }

    private val onFg: (String) -> Unit = { pkg ->
        if (waking && pkg == RadioSource.TW_RADIO_PKG) {
            // La radio registra su receptor de órdenes al crearse: un respiro corto y listo.
            com.carlight.launcher.util.Bg.main.removeCallbacks(finishWake)
            com.carlight.launcher.util.Bg.main.postDelayed(finishWake, 350)
        }
    }

    private val finishWake = Runnable {
        if (!waking) return@Runnable
        waking = false
        com.carlight.launcher.overlay.DashboardController.removeForegroundListener(onFg)
        pending?.let { TwCommandClient.send(it) }
        pending = null
        ctx.startActivity(
            android.content.Intent(ctx, com.carlight.launcher.ui.HomeActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun emit() = listener?.invoke(RadioState(freq, preset))

    private companion object {
        const val KEY_FREQ = "freq"
        const val KEY_PRESET = "preset"
        /** La búsqueda puede tardar varios segundos recorriendo la banda. */
        const val SEEK_WINDOW_MS = 15000L
    }
}
