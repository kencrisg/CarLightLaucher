package com.carlight.launcher.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.provider.ContactsContract
import android.util.Log
import com.carlight.launcher.topway.TwCommandClient

/**
 * Llamadas del teléfono por Bluetooth (perfil manos libres de AOSP, que usa la radio):
 * broadcast `android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED` con un
 * `BluetoothHeadsetClientCall` (clase oculta: getState()/getNumber() por reflexión).
 * Contestar / colgar: centro de comandos de Topway (btAccept / btHangUP).
 */
object CallMonitor {

    enum class State { INCOMING, DIALING, ACTIVE, ENDED }

    data class Call(val state: State, val number: String, val name: String?, val activeSince: Long)

    private const val TAG = "Calls"
    private const val ACTION = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
    private const val EXTRA = "android.bluetooth.headsetclient.extra.CALL"

    @Volatile var current: Call? = null
        private set
    private val listeners = mutableListOf<(Call?) -> Unit>()
    private var app: Context? = null

    fun addListener(l: (Call?) -> Unit) { listeners += l }
    fun removeListener(l: (Call?) -> Unit) { listeners -= l }

    fun start(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        TwCommandClient.bind(a)
        a.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onCall(i.getParcelableExtra<Parcelable>(EXTRA))
        }, IntentFilter(ACTION))
    }

    fun accept() { TwCommandClient.send { it.btAccept() } }
    fun hangUp() { TwCommandClient.send { it.btHangUP() } }

    private fun onCall(call: Parcelable?) {
        if (call == null) return
        val (stateCode, number) = try {
            val st = call.javaClass.getMethod("getState").invoke(call) as Int
            val num = call.javaClass.getMethod("getNumber").invoke(call) as String?
            st to num.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "BluetoothHeadsetClientCall", e)
            return
        }
        // AOSP: 0 ACTIVE, 1 HELD, 2 DIALING, 3 ALERTING, 4 INCOMING, 5 WAITING, 6 HELD_BY_RH, 7 TERMINATED
        val state = when (stateCode) {
            0, 1, 6 -> State.ACTIVE
            2, 3 -> State.DIALING
            4, 5 -> State.INCOMING
            else -> State.ENDED
        }
        Log.i(TAG, "llamada estado=$stateCode")
        val prev = current
        val since = if (state == State.ACTIVE) prev?.takeIf { it.state == State.ACTIVE }?.activeSince
            ?: System.currentTimeMillis() else 0L
        val c = if (state == State.ENDED) null
        else Call(state, number, prev?.takeIf { it.number == number }?.name ?: lookupName(number), since)
        current = c
        listeners.toList().forEach { it(c) }
    }

    /** Nombre del contacto si la agenda del teléfono se sincronizó (requiere READ_CONTACTS). */
    private fun lookupName(number: String): String? {
        val a = app ?: return null
        if (number.isBlank() || a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            a.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }
}
