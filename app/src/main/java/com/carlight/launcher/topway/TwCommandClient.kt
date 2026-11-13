package com.carlight.launcher.topway

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.carlight.launcher.util.AppLauncher
import com.carlight.launcher.util.Bg
import com.tw.service.xt.aidl.ITWCommandAidl
import com.tw.service.xt.aidl.ITWCommandCallbackAidl

/**
 * Cliente del "centro de comandos" de Topway (com.tw.service.xt/.CommandService, exportado y sin
 * permiso). Reenvía radioNext(), btPlayMusic()... a la app de fábrica que corresponda.
 * Las llamadas binder van al hilo de fondo para no bloquear la UI.
 */
object TwCommandClient {

    private const val TAG = "TwCommand"
    const val PKG = "com.tw.service.xt"
    private const val ACTION = "com.tw.service.xt.CommandService.Bind"

    @Volatile private var service: ITWCommandAidl? = null
    private var bound = false
    private var appCtx: Context? = null

    /** Estado BT reportado por Topway (1 = conectado). null = desconocido. */
    @Volatile var btConnected: Boolean? = null
        private set
    private val btListeners = mutableListOf<(Boolean) -> Unit>()

    fun isAvailable(ctx: Context) = AppLauncher.isInstalled(ctx, PKG)

    fun addBtListener(l: (Boolean) -> Unit) { btListeners += l }
    fun removeBtListener(l: (Boolean) -> Unit) { btListeners -= l }

    private val callback = object : ITWCommandCallbackAidl.Stub() {
        override fun onSystemVolume(v: Int) {}
        override fun onVolumeStatus(s: Int) {}
        override fun onBtPhoneStatus(s: Int) {}
        override fun onBtCallStatus(s: Int, a: String?, b: String?) {}
        override fun onBtConnectedStatus(s: Int) {
            val c = s != 0
            btConnected = c
            Bg.main.post { btListeners.toList().forEach { it(c) } }
        }
        override fun onReverseStatus(s: Int) {}
        override fun onSleepStatus(s: Int) {}
        override fun onExtendedInterface(b: Bundle?) {}
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = ITWCommandAidl.Stub.asInterface(binder)
            service = s
            Bg.io.execute {
                runCatching {
                    s.registerTWCommandCallback(callback)
                    s.btGetConnectedStatus()
                }.onFailure { Log.w(TAG, "registro de callback", it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null   // Android vuelve a conectar solo (BIND_AUTO_CREATE)
        }
    }

    fun bind(ctx: Context) {
        if (bound) return
        val app = ctx.applicationContext
        appCtx = app
        bound = runCatching {
            app.bindService(Intent(ACTION).setPackage(PKG), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) Log.w(TAG, "No se pudo conectar con $PKG")
    }

    /** Ejecuta [block] en segundo plano. @return false si el servicio no está conectado. */
    fun send(block: (ITWCommandAidl) -> Unit): Boolean {
        val s = service ?: run { appCtx?.let { bind(it) }; return false }
        Bg.io.execute {
            try {
                block(s)
            } catch (e: Exception) {
                Log.w(TAG, "Comando fallido", e)
            }
        }
        return true
    }
}
