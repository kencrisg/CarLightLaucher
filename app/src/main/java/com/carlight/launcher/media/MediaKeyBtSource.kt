package com.carlight.launcher.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.carlight.launcher.util.AppLauncher

/**
 * Respaldo: envía teclas multimedia estándar. Android las entrega a la sesión activa,
 * que con BT es `com.android.bluetooth/A2dpMediaBrowserService` (AVRCP → teléfono).
 * No hay metadatos: el estado play/pausa es optimista.
 */
class MediaKeyBtSource(private val ctx: Context) : BtSource {

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var playing = false
    private var listener: ((BtState) -> Unit)? = null

    override fun start(onState: (BtState) -> Unit) {
        listener = onState
        emit()
    }

    override fun stop() { listener = null }

    override fun playPause() {
        sendKey(if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
        playing = !playing
        emit()
    }

    override fun next() = sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    override fun prev() = sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    override fun open() { AppLauncher.launchPackage(ctx, BtSource.TW_BT_PKG) }

    private fun sendKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun emit() = listener?.invoke(BtState(title = null, artist = null, playing = playing))
}
