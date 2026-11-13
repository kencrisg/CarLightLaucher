package com.carlight.launcher.ui

import android.content.Context
import android.content.res.Configuration
import com.carlight.launcher.prefs.Prefs
import java.util.Calendar

enum class ThemeMode { AUTO, DAY, NIGHT }

/** Día/noche propio del launcher (la radio no cambia el uiMode del sistema). */
object ThemeController {

    /** AUTO: noche entre las 18:30 y las 06:30. */
    fun isNight(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.AUTO -> {
            val c = Calendar.getInstance()
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            m < DAY_START || m >= NIGHT_START
        }
    }

    fun isNight(ctx: Context) = isNight(Prefs(ctx).themeMode)

    /** Configuración que fuerza values/ o values-night/. */
    fun overrideFor(base: Context, night: Boolean): Configuration = Configuration().apply {
        val type = base.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        uiMode = type or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }

    private const val DAY_START = 6 * 60 + 30
    private const val NIGHT_START = 18 * 60 + 30
}
