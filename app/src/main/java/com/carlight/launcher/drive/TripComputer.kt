package com.carlight.launcher.drive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.carlight.launcher.prefs.Prefs

/**
 * Computadora de viaje (distancia, tiempo en marcha, velocidad media y máxima) y alerta de
 * velocidad. Corre mientras vive el proceso del launcher (aunque el panel esté oculto), 1 Hz.
 * Un viaje nuevo empieza solo tras 30 min detenido, o manualmente (pulsación larga en la tarjeta).
 */
object TripComputer : LocationListener {

    data class Trip(val meters: Float, val movingMs: Long, val maxKmh: Int) {
        val avgKmh: Int get() = if (movingMs < 1000) 0 else (meters / 1000f / (movingMs / 3_600_000f)).toInt()
    }

    private var app: Context? = null
    private var last: Location? = null
    private var meters = 0f
    private var movingMs = 0L
    private var maxKmh = 0
    private var lastMoveAt = 0L
    private var overLimit = false
    private var tone: ToneGenerator? = null
    private val listeners = mutableListOf<() -> Unit>()

    @Volatile var speedKmh: Int = 0
        private set

    /** true mientras se supera el límite configurado. */
    val alerting: Boolean get() = overLimit

    val trip: Trip get() = Trip(meters, movingMs, maxKmh)

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        app = a
        val p = Prefs(a)
        meters = p.tripMeters
        movingMs = p.tripMovingMs
        maxKmh = p.tripMaxKmh
        val lm = a.getSystemService(LocationManager::class.java)
        // Se registra aunque el GPS no esté listo todavía; si falla, reintenta cada 10 s.
        fun register() {
            val ok = runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            }.isSuccess
            if (!ok) com.carlight.launcher.util.Bg.main.postDelayed({ register() }, 10_000)
        }
        register()
    }

    fun reset() {
        meters = 0f; movingMs = 0L; maxKmh = 0; last = null
        save()
        notifyListeners()
    }

    override fun onLocationChanged(loc: Location) {
        val a = app ?: return
        val now = SystemClock.elapsedRealtime()
        val prev = last
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        speedKmh = if (kmh < 3f) 0 else kmh.toInt()

        // Viaje nuevo tras 30 min detenido
        if (lastMoveAt != 0L && now - lastMoveAt > NEW_TRIP_IDLE_MS && speedKmh > 0) reset()

        if (prev != null && speedKmh > 0) {
            val d = prev.distanceTo(loc)
            val dt = loc.time - prev.time
            if (d < 200f && dt in 1..10_000) {   // descarta saltos del GPS
                meters += d
                movingMs += dt
                odoPending += d
            }
        }
        if (speedKmh > 0) lastMoveAt = now
        if (speedKmh > maxKmh) maxKmh = speedKmh
        last = loc

        checkLimit(Prefs(a))
        if (now - lastSave > 30_000) save()
        notifyListeners()
    }

    private var lastSave = 0L
    /** Metros aún no sumados al odómetro de "Mi auto". */
    private var odoPending = 0.0

    private fun checkLimit(p: Prefs) {
        val limit = p.speedLimitKmh
        val over = limit > 0 && speedKmh > limit
        if (over && !overLimit && p.speedAlertSound) beep()
        // Histéresis: deja de alertar 3 km/h por debajo del límite
        overLimit = if (overLimit) limit > 0 && speedKmh > limit - 3 else over
    }

    private fun beep() {
        runCatching {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also { tone = it }
            t.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        }
    }

    private fun save() {
        val a = app ?: return
        lastSave = SystemClock.elapsedRealtime()
        Prefs(a).saveTrip(meters, movingMs, maxKmh)
        if (odoPending > 0) {
            val car = com.carlight.launcher.car.CarData(a)
            if (car.hasOdometer) car.addGpsMeters(odoPending)
            odoPending = 0.0
        }
    }

    private fun notifyListeners() = listeners.toList().forEach { it() }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("API 27")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private const val NEW_TRIP_IDLE_MS = 30 * 60 * 1000L
}
