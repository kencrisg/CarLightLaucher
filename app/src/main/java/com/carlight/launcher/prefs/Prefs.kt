package com.carlight.launcher.prefs

import android.content.Context
import com.carlight.launcher.nav.MapsLauncher
import com.carlight.launcher.ui.ThemeMode

enum class SpeedUnit { KMH, MPH }

/** Preferencias del launcher (ajustes + dock). */
class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("carlight", Context.MODE_PRIVATE)

    // ---------- Dock ----------

    /** Paquetes de las ranuras del dock; null = ranura vacía. */
    fun dock(): List<String?> {
        val saved = sp.getString(KEY_DOCK, null) ?: return defaultDock()
        val list = saved.split(',').map { it.ifBlank { null } }
        return List(DOCK_SLOTS) { list.getOrNull(it) }
    }

    fun setDockSlot(index: Int, pkg: String?) {
        val list = dock().toMutableList()
        list[index] = pkg
        sp.edit().putString(KEY_DOCK, list.joinToString(",") { it.orEmpty() }).apply()
    }

    fun resetDock() = sp.edit().remove(KEY_DOCK).apply()

    // ---------- Apariencia ----------

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.AUTO)
        set(v) = sp.edit().putString(KEY_THEME, v.name).apply()

    /** Minutos desde medianoche en que empieza el día / la noche (modo automático). */
    var dayStart: Int
        get() = sp.getInt(KEY_DAY_START, 6 * 60 + 30)
        set(v) = sp.edit().putInt(KEY_DAY_START, v).apply()

    var nightStart: Int
        get() = sp.getInt(KEY_NIGHT_START, 18 * 60 + 30)
        set(v) = sp.edit().putInt(KEY_NIGHT_START, v).apply()

    /** Índice en [com.carlight.launcher.ui.Accent.ALL]. */
    var accent: Int
        get() = sp.getInt(KEY_ACCENT, 0)
        set(v) = sp.edit().putInt(KEY_ACCENT, v).apply()

    /** Ciudad (skyline) en el fondo. */
    var skyline: Boolean
        get() = sp.getBoolean(KEY_SKYLINE, true)
        set(v) = sp.edit().putBoolean(KEY_SKYLINE, v).apply()

    // ---------- Unidades ----------

    var speedUnit: SpeedUnit
        get() = runCatching { SpeedUnit.valueOf(sp.getString(KEY_UNIT, null)!!) }.getOrDefault(SpeedUnit.KMH)
        set(v) = sp.edit().putString(KEY_UNIT, v.name).apply()

    /** Máximo de la escala del velocímetro, en la unidad elegida. */
    var gaugeMax: Int
        get() = sp.getInt(KEY_GAUGE_MAX, 180)
        set(v) = sp.edit().putInt(KEY_GAUGE_MAX, v).apply()

    var clock24: Boolean
        get() = sp.getBoolean(KEY_CLOCK24, true)
        set(v) = sp.edit().putBoolean(KEY_CLOCK24, v).apply()

    /**
     * Firma de todo lo que cambia el aspecto del panel: si cambia, el panel se reconstruye.
     * (El tema día/noche se compara aparte porque también depende de la hora.)
     */
    fun appearanceSignature(): String =
        "$accent|$skyline|$speedUnit|$gaugeMax|$clock24|${sp.getString(KEY_DOCK, "")}"

    private fun defaultDock(): List<String?> = listOf(
        MapsLauncher.PACKAGES.first(),
        "com.tw.radio",
        "com.tw.bt",
        "com.tw.media",
        "com.android.settings",
    )

    companion object {
        const val DOCK_SLOTS = 5
        private const val KEY_DOCK = "dock"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_NIGHT_START = "night_start"
        private const val KEY_ACCENT = "accent"
        private const val KEY_SKYLINE = "skyline"
        private const val KEY_UNIT = "speed_unit"
        private const val KEY_GAUGE_MAX = "gauge_max"
        private const val KEY_CLOCK24 = "clock24"
    }
}
