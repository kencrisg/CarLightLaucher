package com.carlight.launcher.speed

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.carlight.launcher.util.Bg
import kotlin.math.roundToInt

enum class GpsStatus { FIX, SEARCHING, NO_PERMISSION, DISABLED }

data class SpeedState(val kmh: Int?, val status: GpsStatus)

/**
 * Velocidad desde el GPS estándar de Android (chip GNSS Spreadtrum GE2).
 * 1 actualización/s, solo mientras el launcher está visible.
 */
class GpsSpeedSource(private val ctx: Context) : LocationListener {

    private val lm = ctx.getSystemService(LocationManager::class.java)
    private var listener: ((SpeedState) -> Unit)? = null
    private var last: Location? = null
    private var lastFixAt = 0L

    private val staleCheck = object : Runnable {
        override fun run() {
            if (listener == null) return
            if (lastFixAt != 0L && SystemClock.elapsedRealtime() - lastFixAt > STALE_MS) {
                lastFixAt = 0L
                last = null
                emit(null, GpsStatus.SEARCHING)
            }
            Bg.main.postDelayed(this, 2000)
        }
    }

    fun hasPermission() =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private var registered = false
    private val providers = com.carlight.launcher.gps.GpsReady.providersReceiver { register() }

    @SuppressLint("MissingPermission")
    fun start(onState: (SpeedState) -> Unit) {
        listener = onState
        runCatching { ctx.registerReceiver(providers, com.carlight.launcher.gps.GpsReady.PROVIDERS_FILTER) }
        register()
        Bg.main.removeCallbacks(staleCheck)
        Bg.main.postDelayed(staleCheck, 2000)
    }

    /**
     * Se registra aunque el GPS aún no esté listo (al arrancar la radio): así recibe la señal en
     * cuanto llegue. Antes, si el GPS no estaba listo, el velocímetro se quedaba en "--" para siempre.
     */
    @SuppressLint("MissingPermission")
    private fun register() {
        if (listener == null) return
        if (!hasPermission()) {
            emit(null, GpsStatus.NO_PERMISSION)
            return
        }
        com.carlight.launcher.gps.GpsReady.ensureLocationOn(ctx)
        val enabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        if (lastFixAt == 0L) emit(null, if (enabled) GpsStatus.SEARCHING else GpsStatus.DISABLED)
        if (registered) return
        registered = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        }.isSuccess
    }

    fun stop() {
        runCatching { ctx.unregisterReceiver(providers) }
        registered = false
        lm.removeUpdates(this)
        Bg.main.removeCallbacks(staleCheck)
        listener = null
        last = null
        lastFixAt = 0L
    }

    override fun onLocationChanged(loc: Location) {
        val mps = when {
            loc.hasSpeed() -> loc.speed
            else -> last?.let { prev ->
                val dt = (loc.time - prev.time) / 1000f
                if (dt > 0f) prev.distanceTo(loc) / dt else 0f
            } ?: 0f
        }
        last = loc
        lastFixAt = SystemClock.elapsedRealtime()
        val kmh = mps * 3.6f
        emit(if (kmh < MIN_KMH) 0 else kmh.roundToInt(), GpsStatus.FIX)
    }

    override fun onProviderDisabled(provider: String) { emit(null, GpsStatus.DISABLED) }
    override fun onProviderEnabled(provider: String) { if (lastFixAt == 0L) emit(null, GpsStatus.SEARCHING) }
    @Deprecated("API 27")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun emit(kmh: Int?, status: GpsStatus) {
        listener?.invoke(SpeedState(kmh, status))
    }

    private companion object {
        const val STALE_MS = 5000L
        const val MIN_KMH = 3f   // ruido del GPS estando detenido
    }
}
