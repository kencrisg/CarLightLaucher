package com.carlight.launcher.prefs

import android.content.Context
import com.carlight.launcher.nav.MapsLauncher
import com.carlight.launcher.ui.ThemeMode

enum class SpeedUnit { KMH, MPH }

/** Qué activa el modo noche (tema automático + brillo nocturno). */
enum class NightTrigger { SCHEDULE, HEADLIGHTS }

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

    /** Índice en [com.carlight.launcher.ui.glass.WallpaperRenderer.STYLES] (color del fondo). */
    var background: Int
        get() = sp.getInt(KEY_BACKGROUND, 0)
        set(v) = sp.edit().putInt(KEY_BACKGROUND, v).apply()

    /** Transparencia del vidrio de las tarjetas (por defecto: Transparente). */
    var glassLevel: com.carlight.launcher.ui.glass.GlassLevel
        get() = runCatching { com.carlight.launcher.ui.glass.GlassLevel.valueOf(sp.getString(KEY_GLASS, null)!!) }
            .getOrDefault(com.carlight.launcher.ui.glass.GlassLevel.TRANSPARENT)
        set(v) = sp.edit().putString(KEY_GLASS, v.name).apply()

    var iconStyle: com.carlight.launcher.apps.IconStyle
        get() = runCatching { com.carlight.launcher.apps.IconStyle.valueOf(sp.getString(KEY_ICONS, null)!!) }
            .getOrDefault(com.carlight.launcher.apps.IconStyle.COLOR)
        set(v) = sp.edit().putString(KEY_ICONS, v.name).apply()

    /** Efectos visuales completos (esferas, disco girando, ecualizador) o mínimos. */
    var effects: Boolean
        get() = sp.getBoolean(KEY_EFFECTS, true)
        set(v) = sp.edit().putBoolean(KEY_EFFECTS, v).apply()

    /** Ciudad (skyline) en el fondo. */
    var skyline: Boolean
        get() = sp.getBoolean(KEY_SKYLINE, true)
        set(v) = sp.edit().putBoolean(KEY_SKYLINE, v).apply()

    // ---------- Noche y brillo ----------

    var nightTrigger: NightTrigger
        get() = runCatching { NightTrigger.valueOf(sp.getString(KEY_NIGHT_TRIGGER, null)!!) }.getOrDefault(NightTrigger.SCHEDULE)
        set(v) = sp.edit().putString(KEY_NIGHT_TRIGGER, v.name).apply()

    /** El launcher ajusta el brillo de la pantalla de día y de noche. */
    var brightnessControl: Boolean
        get() = sp.getBoolean(KEY_BRIGHTNESS_CTRL, true)
        set(v) = sp.edit().putBoolean(KEY_BRIGHTNESS_CTRL, v).apply()

    /** Brillo en % (5..100). */
    var dayBrightness: Int
        get() = sp.getInt(KEY_DAY_BRIGHT, 90)
        set(v) = sp.edit().putInt(KEY_DAY_BRIGHT, v).apply()

    var nightBrightness: Int
        get() = sp.getInt(KEY_NIGHT_BRIGHT, 30)
        set(v) = sp.edit().putInt(KEY_NIGHT_BRIGHT, v).apply()

    /** Señal de luces aprendida ("id:campo:valor"), ver HeadlightDetector. */
    var headlightSignal: String?
        get() = sp.getString(KEY_HEADLIGHT, null)
        set(v) = sp.edit().putString(KEY_HEADLIGHT, v).apply()

    // ---------- Conducción ----------

    /** Alerta de velocidad en km/h (0 = desactivada). */
    var speedLimitKmh: Int
        get() = sp.getInt(KEY_SPEED_LIMIT, 0)
        set(v) = sp.edit().putInt(KEY_SPEED_LIMIT, v).apply()

    var speedAlertSound: Boolean
        get() = sp.getBoolean(KEY_SPEED_SOUND, true)
        set(v) = sp.edit().putBoolean(KEY_SPEED_SOUND, v).apply()

    /** Mostrar las llamadas en una tarjeta sobre el mapa (sin perder la navegación). */
    var callsOverMap: Boolean
        get() = sp.getBoolean(KEY_CALLS, true)
        set(v) = sp.edit().putBoolean(KEY_CALLS, v).apply()

    /** Mostrar la próxima maniobra de Organic Maps sobre el mapa. */
    var showManeuver: Boolean
        get() = sp.getBoolean(KEY_MANEUVER, true)
        set(v) = sp.edit().putBoolean(KEY_MANEUVER, v).apply()

    val tripMeters get() = sp.getFloat(KEY_TRIP_M, 0f)
    val tripMovingMs get() = sp.getLong(KEY_TRIP_MS, 0L)
    val tripMaxKmh get() = sp.getInt(KEY_TRIP_MAX, 0)

    fun saveTrip(meters: Float, movingMs: Long, maxKmh: Int) =
        sp.edit().putFloat(KEY_TRIP_M, meters).putLong(KEY_TRIP_MS, movingMs).putInt(KEY_TRIP_MAX, maxKmh).apply()

    /** Los recordatorios ya se alinearon con el primer kilometraje ingresado. */
    var carItemsInit: Boolean
        get() = sp.getBoolean("car_items_init", false)
        set(v) = sp.edit().putBoolean("car_items_init", v).apply()

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
        "$showManeuver|$accent|$background|$glassLevel|$iconStyle|$effects|$skyline|$speedUnit|$gaugeMax|$clock24|${sp.getString(KEY_DOCK, "")}"

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
        private const val KEY_SPEED_LIMIT = "speed_limit"
        private const val KEY_SPEED_SOUND = "speed_sound"
        private const val KEY_CALLS = "calls_over_map"
        private const val KEY_MANEUVER = "show_maneuver"
        private const val KEY_TRIP_M = "trip_m"
        private const val KEY_TRIP_MS = "trip_ms"
        private const val KEY_TRIP_MAX = "trip_max"
        private const val KEY_NIGHT_TRIGGER = "night_trigger"
        private const val KEY_BRIGHTNESS_CTRL = "brightness_ctrl"
        private const val KEY_DAY_BRIGHT = "day_brightness"
        private const val KEY_NIGHT_BRIGHT = "night_brightness"
        private const val KEY_HEADLIGHT = "headlight_signal"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_NIGHT_START = "night_start"
        private const val KEY_ACCENT = "accent"
        private const val KEY_SKYLINE = "skyline"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_GLASS = "glass_level"
        private const val KEY_ICONS = "icon_style"
        private const val KEY_EFFECTS = "effects"
        private const val KEY_UNIT = "speed_unit"
        private const val KEY_GAUGE_MAX = "gauge_max"
        private const val KEY_CLOCK24 = "clock24"
    }
}
