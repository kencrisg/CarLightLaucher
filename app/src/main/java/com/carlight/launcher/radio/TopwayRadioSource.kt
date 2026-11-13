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

    private fun command(block: (com.tw.service.xt.aidl.ITWCommandAidl) -> Unit) {
        TwAudioSource.request(TwAudioSource.RADIO)
        if (!TwCommandClient.send(block)) onNeedsApp?.invoke() ?: open()
    }

    private fun emit() = listener?.invoke(RadioState(freq, preset))

    private companion object {
        const val KEY_FREQ = "freq"
        const val KEY_PRESET = "preset"
        /** La búsqueda puede tardar varios segundos recorriendo la banda. */
        const val SEEK_WINDOW_MS = 15000L
    }
}
