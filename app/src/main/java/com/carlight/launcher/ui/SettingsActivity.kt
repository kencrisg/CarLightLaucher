package com.carlight.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.carlight.launcher.R
import com.carlight.launcher.overlay.ForegroundWatcher
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.prefs.SpeedUnit
import com.carlight.launcher.ui.glass.GlassBackground
import com.carlight.launcher.ui.glass.WallpaperHoleDrawable

/**
 * Ajustes de CarLight. Mismo fondo y vidrio que el panel. Controles grandes (≥ 52 dp) para usar
 * en el auto. Cada cambio se guarda al instante; el panel se reconstruye al volver.
 */
class SettingsActivity : Activity() {

    private enum class Category(val title: Int, val icon: Int) {
        APPEARANCE(R.string.cat_appearance, R.drawable.ic_palette),
        UNITS(R.string.cat_units, R.drawable.ic_speed),
        PANEL(R.string.cat_panel, R.drawable.ic_grid),
        SYSTEM(R.string.cat_system, R.drawable.ic_info),
    }

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private lateinit var navList: LinearLayout
    private lateinit var glass: GlassBackground
    private var night = false
    private val dp by lazy { resources.displayMetrics.density }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        night = ThemeController.isNight(newBase)
        applyOverrideConfiguration(ThemeController.overrideFor(newBase, night))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        prefs = Prefs(this)
        content = findViewById(R.id.content)
        navList = findViewById(R.id.navList)

        val root = findViewById<View>(R.id.root)
        val wallpaper = WallpaperHoleDrawable(resources)
        root.background = wallpaper
        glass = GlassBackground(this, night, prefs.skyline) { wallpaper.setBitmap(it) }
        val radius = resources.getDimension(R.dimen.card_radius) / dp
        glass.attach(findViewById(R.id.navCard), radius)
        glass.attach(findViewById(R.id.contentCard), radius)
        root.viewTreeObserver.addOnGlobalLayoutListener { glass.onLayout(root) }

        findViewById<TextView>(R.id.done).apply {
            background = Accent.current(prefs).pill(16 * dp)
            setOnClickListener { finish() }
        }

        val start = savedInstanceState?.getInt(KEY_CATEGORY) ?: intent.getIntExtra(KEY_CATEGORY, 0)
        show(Category.entries[start.coerceIn(0, Category.entries.size - 1)])
    }

    override fun onDestroy() {
        glass.release()
        super.onDestroy()
    }

    private var current = Category.APPEARANCE

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CATEGORY, current.ordinal)
    }

    // ---------------------------------------------------------------- navegación

    private fun show(cat: Category) {
        current = cat
        renderNav()
        content.removeAllViews()
        when (cat) {
            Category.APPEARANCE -> appearance()
            Category.UNITS -> units()
            Category.PANEL -> panel()
            Category.SYSTEM -> system()
        }
    }

    private fun renderNav() {
        navList.removeAllViews()
        val accent = Accent.current(prefs)
        Category.entries.forEach { cat ->
            val selected = cat == current
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14), 0, px(14), 0)
                background = if (selected) tonal(16) else getDrawable(R.drawable.bg_nav_item)
                setOnClickListener { if (cat != current) show(cat) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(cat.icon)
                if (selected) setColorFilter(accent.end)
            }, LinearLayout.LayoutParams(px(24), px(24)))
            row.addView(TextView(this).apply {
                setText(cat.title)
                textSize = 17f
                setTextColor(getColor(if (selected) R.color.text_primary else R.color.text_secondary))
                typeface = if (selected) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) })
            navList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)).apply {
                bottomMargin = px(6)
            })
        }
    }

    // ---------------------------------------------------------------- secciones

    private fun appearance() {
        header(R.string.cat_appearance)

        label(R.string.set_theme)
        segmented(
            listOf(R.string.set_theme_auto, R.string.set_theme_light, R.string.set_theme_dark),
            prefs.themeMode.ordinal,
        ) { i ->
            prefs.themeMode = ThemeMode.entries[i]
            restartIfThemeChanged() || run { show(Category.APPEARANCE); true }
        }

        if (prefs.themeMode == ThemeMode.AUTO) {
            label(R.string.set_schedule)
            timeStepper(R.string.set_day_from, prefs.dayStart, min = 4 * 60, max = prefs.nightStart - 60) {
                prefs.dayStart = it
                restartIfThemeChanged()
            }
            timeStepper(R.string.set_night_from, prefs.nightStart, min = prefs.dayStart + 60, max = 22 * 60) {
                prefs.nightStart = it
                restartIfThemeChanged()
            }
        }

        label(R.string.set_accent)
        swatches()

        label(R.string.set_wallpaper)
        segmented(listOf(R.string.set_wallpaper_city, R.string.set_wallpaper_plain), if (prefs.skyline) 0 else 1) {
            prefs.skyline = it == 0
            recreate()
        }
    }

    private fun units() {
        header(R.string.cat_units)

        label(R.string.set_speed_unit)
        segmented(listOf("km/h", "mph"), prefs.speedUnit.ordinal) { i ->
            val unit = SpeedUnit.entries[i]
            if (unit != prefs.speedUnit) {
                prefs.speedUnit = unit
                // Escala equivalente en la nueva unidad.
                prefs.gaugeMax = if (unit == SpeedUnit.MPH) 120 else 180
            }
            show(Category.UNITS)
        }

        label(R.string.set_gauge_max)
        val scales = if (prefs.speedUnit == SpeedUnit.MPH) listOf(80, 100, 120, 160) else listOf(120, 160, 180, 240)
        val unit = if (prefs.speedUnit == SpeedUnit.MPH) "mph" else "km/h"
        segmented(scales.map { "$it $unit" }, scales.indexOf(prefs.gaugeMax).coerceAtLeast(0)) {
            prefs.gaugeMax = scales[it]
            show(Category.UNITS)
        }

        label(R.string.set_clock)
        segmented(listOf(R.string.set_clock_24, R.string.set_clock_12), if (prefs.clock24) 0 else 1) {
            prefs.clock24 = it == 0
            show(Category.UNITS)
        }
    }

    private fun panel() {
        header(R.string.cat_panel)
        label(R.string.set_dock)
        hint(R.string.set_dock_hint)
        actionButton(R.string.set_dock_reset) {
            prefs.resetDock()
            Toast.makeText(this, R.string.set_dock_reset_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun system() {
        header(R.string.cat_system)
        label(R.string.set_permissions)
        status(R.string.perm_overlay, Settings.canDrawOverlays(this))
        status(R.string.perm_a11y, accessibilityEnabled())
        status(R.string.perm_location, granted(Manifest.permission.ACCESS_FINE_LOCATION))
        status(R.string.perm_logs, granted(Manifest.permission.READ_LOGS))

        label(R.string.set_about)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        hint(getString(R.string.set_version, version))
    }

    // ---------------------------------------------------------------- componentes

    private fun header(text: Int) {
        content.addView(TextView(this).apply {
            setText(text)
            textSize = 26f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, wrap().apply { bottomMargin = px(4) })
    }

    private fun label(text: Int) {
        content.addView(TextView(this).apply {
            setText(text)
            textSize = 13f
            isAllCaps = true
            letterSpacing = 0.1f
            setTextColor(getColor(R.color.text_secondary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, wrap().apply { topMargin = px(22); bottomMargin = px(10) })
    }

    private fun hint(text: Int) = hint(getString(text))

    private fun hint(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.text_secondary))
        }, wrap().apply { bottomMargin = px(12) })
    }

    @JvmName("segmentedRes")
    private fun segmented(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) =
        segmented(options.map { getString(it) }, selected, onSelect)

    /** Selector segmentado: la opción elegida es una píldora con el acento. */
    private fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val accent = Accent.current(prefs)
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(4), px(4), px(4), px(4))
        }
        options.forEachIndexed { i, text ->
            group.addView(TextView(this).apply {
                this.text = text
                gravity = Gravity.CENTER
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                if (i == selected) {
                    background = accent.pill(13 * dp)
                    setTextColor(0xFFFFFFFF.toInt())
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                } else {
                    background = getDrawable(R.drawable.bg_nav_item)
                    setTextColor(getColor(R.color.text_primary))
                }
                setOnClickListener { if (i != selected) onSelect(i) }
            }, LinearLayout.LayoutParams(0, px(52), 1f))
        }
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Muestras de color de acento (círculos de 52 dp; el elegido lleva ✓). */
    private fun swatches() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Accent.ALL.forEachIndexed { i, accent ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener {
                    if (i != prefs.accent) {
                        prefs.accent = i
                        // this@SettingsActivity: dentro de apply{} un findViewById a secas busca en la celda.
                        this@SettingsActivity.findViewById<TextView>(R.id.done).background = accent.pill(16 * dp)
                        show(Category.APPEARANCE)
                    }
                }
            }
            val dot = FrameLayout(this).apply {
                background = accent.pill(0f, oval = true).also { d ->
                    if (i == prefs.accent) d.setStroke(px(3), getColor(R.color.text_primary))
                }
            }
            if (i == prefs.accent) {
                dot.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(0xFFFFFFFF.toInt())
                }, FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER))
            }
            cell.addView(dot, LinearLayout.LayoutParams(px(52), px(52)))
            cell.addView(TextView(this).apply {
                text = accent.name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(getColor(if (i == prefs.accent) R.color.text_primary else R.color.text_secondary))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6)
            })
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Fila "Etiqueta   [−]  06:30  [+]" con pasos de 30 min. */
    private fun timeStepper(text: Int, minutes: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(18), px(4), px(4), px(4))
        }
        row.addView(TextView(this).apply {
            setText(text)
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val value = TextView(this).apply {
            this.text = hhmm(minutes)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        fun step(delta: Int) = ImageButton(this).apply {
            setImageResource(if (delta < 0) R.drawable.ic_minus else R.drawable.ic_plus)
            background = getDrawable(R.drawable.bg_tonal_button)
            setOnClickListener {
                val next = (minutes + delta).coerceIn(min, max)
                if (next != minutes) {
                    onChange(next)
                    show(Category.APPEARANCE)
                }
            }
        }
        row.addView(step(-30), LinearLayout.LayoutParams(px(52), px(52)))
        row.addView(value, LinearLayout.LayoutParams(px(96), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(step(+30), LinearLayout.LayoutParams(px(52), px(52)))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(8)
        })
    }

    private fun status(text: Int, ok: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(18), 0, px(18), 0)
        }
        row.addView(TextView(this).apply {
            setText(text)
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            setText(if (ok) R.string.perm_ok else R.string.perm_missing)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(px(12), px(4), px(12), px(4))
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(if (ok) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
            }
        })
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)).apply {
            bottomMargin = px(8)
        })
    }

    private fun actionButton(text: Int, onClick: () -> Unit) {
        content.addView(TextView(this).apply {
            setText(text)
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = getDrawable(R.drawable.bg_segment)
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
    }

    // ---------------------------------------------------------------- utilidades

    /** Si el tema efectivo cambió (claro/oscuro), recrear para aplicar values-night. */
    private fun restartIfThemeChanged(): Boolean {
        if (ThemeController.isNight(this) == night) return false
        recreate()
        return true
    }

    private fun accessibilityEnabled(): Boolean {
        val me = ComponentName(this, ForegroundWatcher::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun granted(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun tonal(radiusDp: Int) = GradientDrawable().apply {
        cornerRadius = radiusDp * dp
        setColor(getColor(R.color.tonal_bg))
    }

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    private fun px(v: Int) = (v * dp).toInt()
    private fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    companion object {
        const val KEY_CATEGORY = "category"
    }
}
