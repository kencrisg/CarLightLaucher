package com.carlight.launcher.night

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import com.carlight.launcher.prefs.NightTrigger
import com.carlight.launcher.prefs.Prefs
import java.util.Calendar

/**
 * Decide si es "de noche" (por horario o por las luces del auto) y aplica el brillo de la
 * pantalla configurado para día/noche. Vive mientras vive el proceso del launcher.
 */
object NightController {

    private const val TAG = "Night"
    private var app: Context? = null
    private var lastNight: Boolean? = null
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    fun addListener(l: (Boolean) -> Unit) { listeners += l }
    fun removeListener(l: (Boolean) -> Unit) { listeners -= l }

    fun start(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        val prefs = Prefs(a)
        HeadlightDetector.setLearned(HeadlightDetector.Learned.decode(prefs.headlightSignal))
        HeadlightDetector.start(a)
        HeadlightDetector.addListener { evaluate() }
        a.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = evaluate()
        }, IntentFilter(Intent.ACTION_TIME_TICK))
        evaluate(force = true)
    }

    /** ¿Es de noche según el disparador elegido? (independiente del tema claro/oscuro forzado). */
    fun isNightNow(ctx: Context): Boolean {
        val p = Prefs(ctx)
        return when (p.nightTrigger) {
            NightTrigger.HEADLIGHTS -> HeadlightDetector.lightsOn ?: bySchedule(p)
            NightTrigger.SCHEDULE -> bySchedule(p)
        }
    }

    private fun bySchedule(p: Prefs): Boolean {
        val c = Calendar.getInstance()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return m < p.dayStart || m >= p.nightStart
    }

    /** Volver a evaluar (cambió la hora, las luces o un ajuste). */
    fun evaluate(force: Boolean = false) {
        val a = app ?: return
        val night = isNightNow(a)
        if (!force && night == lastNight) return
        lastNight = night
        applyBrightness(a, night)
        listeners.toList().forEach { it(night) }
    }

    fun applyBrightness(ctx: Context, night: Boolean = isNightNow(ctx)) {
        val p = Prefs(ctx)
        if (!p.brightnessControl) return
        if (!Settings.System.canWrite(ctx)) {
            Log.w(TAG, "Sin permiso WRITE_SETTINGS: no se cambia el brillo")
            return
        }
        val pct = if (night) p.nightBrightness else p.dayBrightness
        val value = (pct.coerceIn(5, 100) * 255 / 100)
        runCatching {
            val r = ctx.contentResolver
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, value)
        }.onFailure { Log.w(TAG, "brillo", it) }
    }
}
