package com.carlight.launcher.media

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import com.carlight.launcher.topway.TwAudioSource
import com.carlight.launcher.topway.TwCommandClient
import com.carlight.launcher.util.AppLauncher
import com.carlight.launcher.util.Bg

/**
 * Música Bluetooth real. La radio usa el stack Bluetooth estándar de AOSP (A2DP Sink + AVRCP
 * Controller): la sesión `com.android.bluetooth/A2dpMediaBrowserService` (exportada) trae título,
 * artista y estado del teléfono, y acepta play/pausa/siguiente/anterior (AVRCP).
 * Si esa sesión no está disponible, los controles van al centro de comandos de Topway.
 * Título/artista: el stack Bluetooth de Topway NO los pone en la sesión; los envía en el broadcast
 * `com.android.getelementattrrsp` (extras `trackTitle`, `artist`, respuesta AVRCP). Respaldo:
 * `com.tw.btmusic.info` (`musicTitle`, `musicaArtist`) que reenvía la app BT de fábrica.
 * Antes de reproducir se cambia la fuente de audio del MCU a BT ([TwAudioSource]) para que la
 * radio se calle, igual que al abrir la app BT de fábrica.
 * Estado ▶/⏸: la sesión de Topway no lo refleja (siempre state=0); se usa el estado del perfil
 * A2DP Sink (broadcast PLAYING_STATE_CHANGED: 10 = sonando, 11 = no) + consulta inicial al perfil.
 */
class A2dpBtSource(private val ctx: Context) : BtSource {

    private var listener: ((BtState) -> Unit)? = null
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
        override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
        override fun onSessionDestroyed() {
            controller = null
            emit()
        }
    }

    private val connectionCallback = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            val b = browser ?: return
            controller = MediaController(ctx, b.sessionToken).also {
                it.registerCallback(controllerCallback, Bg.main)
            }
            emit()
        }

        override fun onConnectionFailed() {
            Log.w(TAG, "No se pudo conectar con A2dpMediaBrowserService")
            emit()
        }

        override fun onConnectionSuspended() {
            controller = null
            emit()
        }
    }

    private val btListener: (Boolean) -> Unit = { emit() }

    private val prefs = ctx.getSharedPreferences("bt", Context.MODE_PRIVATE)
    private var twTitle: String? = prefs.getString(KEY_TITLE, null)
    private var twArtist: String? = prefs.getString(KEY_ARTIST, null)

    /** Audio A2DP fluyendo desde el teléfono. null = aún no se sabe. */
    private var a2dpPlaying: Boolean? = null

    private val twMusicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == A2DP_SINK_PLAYING_STATE) {
                when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                    STATE_PLAYING -> a2dpPlaying = true
                    STATE_NOT_PLAYING -> a2dpPlaying = false
                }
                emit()
                return
            }
            val avrcp = intent.action == AVRCP_ELEMENT_ATTR
            twTitle = intent.getStringExtra(if (avrcp) "trackTitle" else "musicTitle")?.trim()?.takeIf { it.isNotEmpty() }
            twArtist = intent.getStringExtra(if (avrcp) "artist" else "musicaArtist")?.trim()?.takeIf { it.isNotEmpty() }   // sic
            prefs.edit().putString(KEY_TITLE, twTitle).putString(KEY_ARTIST, twArtist).apply()
            emit()
        }
    }

    override fun start(onState: (BtState) -> Unit) {
        listener = onState
        TwCommandClient.bind(ctx)
        TwCommandClient.addBtListener(btListener)
        ctx.registerReceiver(twMusicReceiver, IntentFilter().apply {
            addAction(AVRCP_ELEMENT_ATTR)
            addAction(TW_MUSIC_INFO)
            addAction(A2DP_SINK_PLAYING_STATE)
        })
        queryA2dpPlaying()
        browser = MediaBrowser(ctx, SERVICE, connectionCallback, null).also {
            runCatching { it.connect() }.onFailure { e -> Log.w(TAG, "connect", e) }
        }
        emit()
    }

    override fun stop() {
        listener = null
        TwCommandClient.removeBtListener(btListener)
        runCatching { ctx.unregisterReceiver(twMusicReceiver) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        runCatching { browser?.disconnect() }
        browser = null
    }

    override fun playPause() {
        val c = controller
        if (playingNow()) {
            c?.transportControls?.pause() ?: sendKeyViaTopway()
            a2dpPlaying = false          // optimista; el broadcast lo confirma
        } else {
            ensureBtSource()
            c?.transportControls?.play() ?: sendKeyViaTopway()
            a2dpPlaying = true
        }
        emit()
    }

    private fun playingNow(): Boolean = a2dpPlaying ?: (controller?.let { isPlaying(it) } ?: false)

    /** Estado inicial: BluetoothA2dpSink.isA2dpPlaying(device) (API oculta, reflexión). */
    private fun queryA2dpPlaying() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        runCatching {
            adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching {
                        val m = proxy.javaClass.getMethod("isA2dpPlaying", BluetoothDevice::class.java)
                        val playing = proxy.connectedDevices.any { m.invoke(proxy, it) as Boolean }
                        Bg.main.post {
                            if (a2dpPlaying == null) a2dpPlaying = playing
                            emit()
                        }
                    }.onFailure { Log.w(TAG, "isA2dpPlaying", it) }
                    adapter.closeProfileProxy(A2DP_SINK, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, A2DP_SINK)
        }.onFailure { Log.w(TAG, "perfil A2DP Sink", it) }
    }

    override fun next() {
        ensureBtSource()
        controller?.transportControls?.skipToNext() ?: TwCommandClient.send { it.btNext() }
    }

    override fun prev() {
        ensureBtSource()
        controller?.transportControls?.skipToPrevious() ?: TwCommandClient.send { it.btPre() }
    }

    override fun pauseForOtherSource() {
        if (playingNow()) controller?.transportControls?.pause()
        // Al pedir la fuente radio, Topway ya pausa el teléfono por AVRCP y corta la salida A2DP.
        a2dpPlaying = false
        emit()
    }

    /** Callar la radio (u otra fuente) y pasar el audio a BT, sin abrir la app de fábrica. */
    private fun ensureBtSource() {
        TwAudioSource.request(TwAudioSource.BT_MUSIC)
    }

    private fun sendKeyViaTopway() {
        TwCommandClient.send { it.btPlayMusic() }
    }

    override fun open() { AppLauncher.launchPackage(ctx, BtSource.TW_BT_PKG) }

    private fun isPlaying(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun emit() {
        val l = listener ?: return
        val c = controller
        val state = c?.playbackState
        val md = c?.metadata
        // STATE_ERROR = "Audio Bluetooth desconectado" (sin teléfono).
        val connected = when {
            state?.state == PlaybackState.STATE_ERROR -> false
            TwCommandClient.btConnected != null -> TwCommandClient.btConnected!!
            else -> state != null
        }
        val avrcpTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val avrcpArtist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.takeIf { it.isNotBlank() }
        l(
            BtState(
                title = if (connected) avrcpTitle ?: twTitle else null,
                artist = if (connected) (if (avrcpTitle != null) avrcpArtist else twArtist) else null,
                playing = connected && playingNow(),
                connected = connected,
            )
        )
    }

    private companion object {
        const val TAG = "A2dpBt"
        const val TW_MUSIC_INFO = "com.tw.btmusic.info"
        const val AVRCP_ELEMENT_ATTR = "com.android.getelementattrrsp"
        // BluetoothA2dpSink (API oculta)
        const val A2DP_SINK = 11
        const val A2DP_SINK_PLAYING_STATE = "android.bluetooth.a2dpsink.profile.action.PLAYING_STATE_CHANGED"
        const val STATE_PLAYING = 10
        const val STATE_NOT_PLAYING = 11
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        val SERVICE = ComponentName("com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService")
    }
}
