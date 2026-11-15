package com.carlight.launcher.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.carlight.launcher.util.Bg

/**
 * Al encender la radio el GPS tarda en estar listo. Si Organic Maps abre antes, se queda en el
 * mapamundi sin buscar posición. Aquí se espera la primera señal (máx. [TIMEOUT_MS]) antes de
 * abrir el mapa, y se asegura que la ubicación del sistema esté encendida.
 */
object GpsReady {

    private const val TAG = "GpsReady"
    private const val TIMEOUT_MS = 120_000L
    /** Una posición más reciente que esto cuenta como "GPS listo". */
    private const val FRESH_MS = 60_000L

    @Volatile var hasFix = false
        private set

    private var waiting = false
    private val callbacks = mutableListOf<() -> Unit>()

    /** Ejecuta [onReady] cuando haya señal GPS (o al vencer la espera). Inmediato si ya la hay. */
    @SuppressLint("MissingPermission")
    fun whenReady(ctx: Context, onReady: () -> Unit) {
        val app = ctx.applicationContext
        ensureLocationOn(app)
        val lm = app.getSystemService(LocationManager::class.java)
        if (hasFix || recentFix(lm) || !permitted(app)) {
            hasFix = hasFix || recentFix(lm)
            onReady()
            return
        }
        callbacks += onReady
        if (waiting) return
        waiting = true
        Log.i(TAG, "esperando la primera señal GPS…")

        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) = finish(lm, this, "señal GPS")
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
            @Deprecated("API 27") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        }.onFailure { Log.w(TAG, "requestLocationUpdates", it) }
        Bg.main.postDelayed({ finish(lm, listener, "tiempo agotado") }, TIMEOUT_MS)
    }

    private fun finish(lm: LocationManager, l: LocationListener, why: String) {
        if (!waiting) return
        waiting = false
        if (why == "señal GPS") hasFix = true
        Log.i(TAG, "listo: $why")
        runCatching { lm.removeUpdates(l) }
        val cbs = callbacks.toList()
        callbacks.clear()
        cbs.forEach { it() }
    }

    @SuppressLint("MissingPermission")
    private fun recentFix(lm: LocationManager): Boolean {
        val last = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() ?: return false
        val ageMs = (SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos) / 1_000_000
        return ageMs in 0..FRESH_MS
    }

    private fun permitted(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Si la ubicación del sistema quedó apagada, la enciende (alta precisión).
     * Requiere WRITE_SECURE_SETTINGS (adb shell pm grant com.carlight.launcher android.permission.WRITE_SECURE_SETTINGS).
     */
    fun ensureLocationOn(ctx: Context) {
        val mode = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE, -1)
        if (mode == Settings.Secure.LOCATION_MODE_OFF) {
            runCatching {
                @Suppress("DEPRECATION")
                Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
                Log.i(TAG, "ubicación del sistema encendida")
            }.onFailure { Log.w(TAG, "No se pudo encender la ubicación (falta WRITE_SECURE_SETTINGS)") }
        }
    }

    /** Avisa cuando el GPS se enciende/apaga en el sistema (para reintentar). */
    fun providersReceiver(onChange: () -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = onChange()
    }

    val PROVIDERS_FILTER = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
}
