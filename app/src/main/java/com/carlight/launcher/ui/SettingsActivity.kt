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
import com.carlight.launcher.prefs.NightTrigger
import com.carlight.launcher.night.HeadlightDetector
import com.carlight.launcher.night.NightController
import com.carlight.launcher.ui.glass.GlassBackground
import com.carlight.launcher.ui.glass.GlassLevel
import com.carlight.launcher.apps.IconStyle
import com.carlight.launcher.ui.glass.WallpaperHoleDrawable
import com.carlight.launcher.ui.glass.WallpaperRenderer

/**
 * Ajustes de CarLight. Mismo fondo y vidrio que el panel. Controles grandes (≥ 52 dp) para usar
 * en el auto. Cada cambio se guarda al instante; el panel se reconstruye al volver.
 */
class SettingsActivity : Activity() {

    /** [car] = pertenece a la pantalla "Mi auto" (misma Activity, abierta por el alias CarActivity). */
    private enum class Category(val title: Int, val icon: Int, val car: Boolean = false) {
        APPEARANCE(R.string.cat_appearance, R.drawable.ic_palette),
        DISPLAY(R.string.cat_display, R.drawable.ic_brightness),
        DRIVE(R.string.cat_drive, R.drawable.ic_drive),
        UNITS(R.string.cat_units, R.drawable.ic_speed),
        PANEL(R.string.cat_panel, R.drawable.ic_grid),
        SYSTEM(R.string.cat_system, R.drawable.ic_info),
        MAINTENANCE(R.string.cat_maintenance, R.drawable.ic_wrench, car = true),
        FUEL(R.string.cat_fuel, R.drawable.ic_fuel, car = true),
    }

    private val carMode by lazy {
        intent.component?.className?.endsWith(".CarActivity") == true || intent.getBooleanExtra(EXTRA_CAR, false)
    }
    private val categories by lazy { Category.entries.filter { it.car == carMode } }
    private lateinit var car: com.carlight.launcher.car.CarData

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
        val palette = WallpaperRenderer.style(prefs.background).palette(night)
        window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR, palette.gradient))
        val wallpaper = WallpaperHoleDrawable(resources, palette.gradient)
        root.background = wallpaper
        glass = GlassBackground(this, night, prefs.skyline, prefs.background, prefs.glassLevel, null) { wallpaper.setBitmap(it) }
        val radius = resources.getDimension(R.dimen.card_radius) / dp
        glass.attach(findViewById(R.id.navCard), radius)
        glass.attach(findViewById(R.id.contentCard), radius)
        root.viewTreeObserver.addOnGlobalLayoutListener { glass.onLayout(root) }

        findViewById<TextView>(R.id.done).apply {
            background = Accent.current(prefs).pill(16 * dp)
            setOnClickListener { finish() }
        }

        car = com.carlight.launcher.car.CarData(this)
        if (carMode) findViewById<TextView>(R.id.settingsTitle).setText(R.string.car_title)
        val saved = savedInstanceState?.getInt(KEY_CATEGORY, -1)?.takeIf { it >= 0 }?.let { Category.entries[it] }
        show(saved?.takeIf { it in categories } ?: categories.first())
    }

    override fun onDestroy() {
        glass.release()
        super.onDestroy()
    }

    private var current = Category.APPEARANCE
    private fun current() = current

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
            Category.MAINTENANCE -> maintenance()
            Category.FUEL -> fuel()
            Category.APPEARANCE -> appearance()
            Category.DISPLAY -> display()
            Category.DRIVE -> drive()
            Category.UNITS -> units()
            Category.PANEL -> panel()
            Category.SYSTEM -> system()
        }
    }

    private fun renderNav() {
        navList.removeAllViews()
        val accent = Accent.current(prefs)
        categories.forEach { cat ->
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
                textSize = 16f
                setTextColor(getColor(if (selected) R.color.text_primary else R.color.text_secondary))
                typeface = if (selected) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) })
            navList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50)).apply {
                bottomMargin = px(4)
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

        if (prefs.themeMode == ThemeMode.AUTO) hint(R.string.set_theme_auto_hint)

        label(R.string.set_background)
        backgroundTiles()

        label(R.string.set_glass)
        segmented(GlassLevel.entries.map { it.label }, prefs.glassLevel.ordinal) {
            prefs.glassLevel = GlassLevel.entries[it]
            recreate()   // esta pantalla también usa el vidrio: se ve el cambio al instante
        }

        label(R.string.set_accent)
        swatches()

        label(R.string.set_wallpaper)
        segmented(listOf(R.string.set_wallpaper_city, R.string.set_wallpaper_plain), if (prefs.skyline) 0 else 1) {
            prefs.skyline = it == 0
            recreate()
        }

        label(R.string.set_effects)
        segmented(listOf(R.string.set_effects_full, R.string.set_effects_min), if (prefs.effects) 0 else 1) {
            prefs.effects = it == 0
            recreate()
        }
        hint(R.string.set_effects_hint)
    }

    private var offSnapshot: Map<Int, IntArray>? = null

    private fun display() {
        header(R.string.cat_display)

        label(R.string.set_night_trigger)
        segmented(listOf(R.string.set_trigger_schedule, R.string.set_trigger_lights), prefs.nightTrigger.ordinal) {
            prefs.nightTrigger = NightTrigger.entries[it]
            NightController.evaluate(force = true)
            restartIfThemeChanged() || run { show(Category.DISPLAY); true }
        }

        if (prefs.nightTrigger == NightTrigger.SCHEDULE) {
            timeStepper(R.string.set_day_from, prefs.dayStart, min = 4 * 60, max = prefs.nightStart - 60) {
                prefs.dayStart = it
                NightController.evaluate()
                restartIfThemeChanged()
            }
            timeStepper(R.string.set_night_from, prefs.nightStart, min = prefs.dayStart + 60, max = 22 * 60) {
                prefs.nightStart = it
                NightController.evaluate()
                restartIfThemeChanged()
            }
        } else {
            lightsSection()
        }

        label(R.string.set_brightness)
        segmented(listOf(R.string.set_brightness_auto, R.string.set_brightness_off), if (prefs.brightnessControl) 0 else 1) {
            prefs.brightnessControl = it == 0
            NightController.applyBrightness(this)
            show(Category.DISPLAY)
        }
        if (prefs.brightnessControl) {
            if (!Settings.System.canWrite(this)) {
                status(R.string.perm_brightness, false)
                actionButton(R.string.set_brightness_grant) {
                    runCatching {
                        startActivity(android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            android.net.Uri.parse("package:$packageName")))
                    }
                }
            }
            stepper(R.string.set_brightness_day, prefs.dayBrightness, 20, 100, 10, { "$it %" }) {
                prefs.dayBrightness = it
                if (!NightController.isNightNow(this)) NightController.applyBrightness(this, false)
            }
            stepper(R.string.set_brightness_night, prefs.nightBrightness, 5, 80, 5, { "$it %" }) {
                prefs.nightBrightness = it
                if (NightController.isNightNow(this)) NightController.applyBrightness(this, true)
            }
            hint(R.string.set_brightness_hint)
        }
    }

    /** Estado de las luces y asistente para aprender la señal del auto. */
    private fun lightsSection() {
        val learned = prefs.headlightSignal != null
        val on = HeadlightDetector.lightsOn
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(18), 0, px(18), 0)
        }
        row.addView(TextView(this).apply {
            setText(R.string.lights_state)
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            setText(when {
                !HeadlightDetector.available -> R.string.lights_unavailable
                !learned -> R.string.lights_not_learned
                on == true -> R.string.lights_on
                on == false -> R.string.lights_off
                else -> R.string.lights_waiting
            })
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)).apply { bottomMargin = px(8) })

        if (!learned || offSnapshot != null) {
            hint(R.string.lights_learn_hint)
            if (offSnapshot == null) {
                actionButton(R.string.lights_step1) {
                    offSnapshot = HeadlightDetector.snapshot()
                    show(Category.DISPLAY)
                }
            } else {
                actionButton(R.string.lights_step2) {
                    val l = HeadlightDetector.learn(offSnapshot!!, HeadlightDetector.snapshot())
                    offSnapshot = null
                    if (l != null) {
                        prefs.headlightSignal = l.encode()
                        HeadlightDetector.setLearned(l)
                        NightController.evaluate(force = true)
                        Toast.makeText(this, getString(R.string.lights_learned, "0x%04X".format(l.id)), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, R.string.lights_learn_failed, Toast.LENGTH_LONG).show()
                    }
                    show(Category.DISPLAY)
                }
            }
        } else {
            actionButton(R.string.lights_forget) {
                prefs.headlightSignal = null
                HeadlightDetector.setLearned(null)
                show(Category.DISPLAY)
            }
        }
        // Diagnóstico: últimos mensajes del MCU (sirve para ver la señal al encender las luces)
        val recent = HeadlightDetector.recentLog.take(4)
        if (recent.isNotEmpty()) hint(getString(R.string.lights_recent) + "\n" + recent.joinToString("\n"))
    }

    private val nightListener: (Boolean) -> Unit = {
        if (restartIfThemeChanged().not() && current == Category.DISPLAY) show(Category.DISPLAY)
    }

    override fun onStart() {
        super.onStart()
        NightController.start(this)
        NightController.addListener(nightListener)
    }

    override fun onStop() {
        NightController.removeListener(nightListener)
        super.onStop()
    }

    // ---------------------------------------------------------------- Mi auto

    private fun maintenance() {
        header(R.string.cat_maintenance)
        label(R.string.car_odometer)
        if (!car.hasOdometer) hint(R.string.car_odometer_hint)
        valueRow(R.string.car_odometer_now, if (car.hasOdometer) km(car.odometerKm) else "—") {
            numberDialog(R.string.car_odometer_now, if (car.hasOdometer) car.odometerKm.toDouble() else null, decimals = false) {
                car.setOdometer(it.toInt())
                // Primera vez: los recordatorios cuentan desde el kilometraje ingresado.
                if (!prefs.carItemsInit) {
                    car.saveItems(car.items().map { item -> item.copy(lastKm = it.toInt()) })
                    prefs.carItemsInit = true
                }
                show(Category.MAINTENANCE)
            }
        }
        if (!car.hasOdometer) return

        label(R.string.car_reminders)
        val odo = car.odometerKm
        car.items().forEach { item ->
            if (item.isMonthly) {
                val days = item.daysLeft()
                val detail = when {
                    item.month == 0 -> getString(R.string.car_pick_month_hint)
                    days == null -> ""
                    days < 0 -> getString(R.string.car_plate_overdue, monthName(item.month))
                    else -> getString(R.string.car_plate_in, monthName(item.month), days)
                }
                maintenanceRow(item, detail, if (item.month == 0) 1 else item.status(odo))
                return@forEach
            }
            val parts = mutableListOf<String>()
            item.kmLeft(odo)?.let { parts += if (it > 0) getString(R.string.car_in_km, km(it)) else getString(R.string.car_overdue_km, km(-it)) }
            item.daysLeft()?.let { parts += if (it > 0) getString(R.string.car_in_days, it) else getString(R.string.car_overdue) }
            maintenanceRow(item, parts.joinToString(" · "), item.status(odo))
        }
        hint(R.string.car_reminders_hint2)
    }

    private fun fuel() {
        header(R.string.cat_fuel)
        if (!car.hasOdometer) {
            hint(R.string.car_need_odometer)
            actionButton(R.string.car_go_odometer) { show(Category.MAINTENANCE) }
            return
        }
        actionButton(R.string.car_add_fill, accent = true) { fillDialog() }

        label(R.string.car_stats)
        val kpg = car.kmPerGallon()
        valueRow(R.string.car_consumption, kpg?.let { "%.1f km/gal".format(it) } ?: getString(R.string.car_need_fills))
        valueRow(R.string.car_range, car.rangeKm()?.let { "≈ ${km(it)}" } ?: "—")
        valueRow(R.string.car_cost_km, car.costPerKm()?.let { "$%.3f".format(it) } ?: "—")
        valueRow(R.string.car_month, "$%.2f".format(car.monthSpend()))
        valueRow(R.string.car_tank, "%.1f gal".format(car.tankGallons)) {
            numberDialog(R.string.car_tank, car.tankGallons, decimals = true) {
                car.tankGallons = it
                show(Category.FUEL)
            }
        }

        val fills = car.fills()
        if (fills.isNotEmpty()) {
            label(R.string.car_history)
            val df = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            fills.takeLast(6).reversed().forEach { f ->
                valueRow(df.format(java.util.Date(f.date)) + " · " + km(f.km),
                    "%.2f gal · $%.2f%s".format(f.gallons, f.price, if (f.full) "" else " · " + getString(R.string.car_partial)))
            }
            actionButton(R.string.car_undo_fill) {
                car.removeLastFill()
                show(Category.FUEL)
            }
        }
        hint(R.string.car_fuel_hint)
    }

    private fun km(v: Int) = "%,d km".format(v).replace(',', '.')

    /** Fila "Etiqueta ......... valor" (opcionalmente tocable). */
    private fun valueRow(text: Int, value: String, onClick: (() -> Unit)? = null) = valueRow(getString(text), value, onClick)

    private fun valueRow(text: String, value: String, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(18), 0, px(18), 0)
            onClick?.let { c -> setOnClickListener { c() } }
        }
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            this.text = value + if (onClick != null) "  ›" else ""
            textSize = 17f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)).apply { bottomMargin = px(8) })
    }

    /** Recordatorio: nombre, cuánto falta (color por estado), "Hecho"; tocar = cambiar intervalo. */
    private fun maintenanceRow(item: com.carlight.launcher.car.CarData.Item, detail: String, status: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(14), px(8), px(8), px(8))
            setOnClickListener { if (item.isMonthly) monthDialog(item) else intervalDialog(item) }
        }
        row.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when (status) { 2 -> 0xFFEF4444.toInt(); 1 -> 0xFFF59E0B.toInt(); else -> 0xFF22C55E.toInt() })
            }
        }, LinearLayout.LayoutParams(px(12), px(12)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = item.name
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        texts.addView(TextView(this).apply {
            text = detail
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) })
        row.addView(TextView(this).apply {
            setText(R.string.car_done)
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = Accent.current(prefs).pill(14 * dp)
            setOnClickListener {
                car.markDone(item.id)
                Toast.makeText(this@SettingsActivity, getString(R.string.car_done_toast, item.name), Toast.LENGTH_SHORT).show()
                show(Category.MAINTENANCE)
            }
        }, LinearLayout.LayoutParams(px(96), px(48)))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(8) })
    }

    private fun input(hint: Int, value: Double?, decimals: Boolean) = android.widget.EditText(this).apply {
        setHint(hint)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            (if (decimals) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        textSize = 22f
        value?.let { setText(if (decimals) "%.2f".format(java.util.Locale.US, it) else it.toInt().toString()) }
        setSelectAllOnFocus(true)
    }

    private fun parse(e: android.widget.EditText) = e.text.toString().replace(',', '.').toDoubleOrNull()

    private fun dialogBox(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(24), px(8), px(24), 0)
        views.forEach { addView(it) }
    }

    private fun numberDialog(title: Int, initial: Double?, decimals: Boolean, onOk: (Double) -> Unit) {
        val e = input(title, initial, decimals)
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setView(dialogBox(e))
            .setPositiveButton(R.string.car_save) { _, _ -> parse(e)?.takeIf { it >= 0 }?.let(onOk) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun monthName(m: Int): String =
        java.text.DateFormatSymbols.getInstance().months[m - 1].replaceFirstChar { it.uppercase() }

    /** Matriculación: elegir el mes del año en que toca. */
    private fun monthDialog(item: com.carlight.launcher.car.CarData.Item) {
        val names = Array(12) { monthName(it + 1) }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.car_pick_month)
            .setSingleChoiceItems(names, item.month - 1) { d, which ->
                car.setMonth(item.id, which + 1)
                d.dismiss()
                show(Category.MAINTENANCE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun intervalDialog(item: com.carlight.launcher.car.CarData.Item) {
        val kmIn = input(R.string.car_every_km, item.everyKm.toDouble(), false)
        val moIn = input(R.string.car_every_months, item.everyMonths.toDouble(), false)
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(item.name)
            .setMessage(R.string.car_interval_msg)
            .setView(dialogBox(kmIn, moIn))
            .setPositiveButton(R.string.car_save) { _, _ ->
                car.updateInterval(item.id, parse(kmIn)?.toInt() ?: item.everyKm, parse(moIn)?.toInt() ?: item.everyMonths)
                show(Category.MAINTENANCE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fillDialog() {
        val gal = input(R.string.car_fill_gallons, null, true)
        val price = input(R.string.car_fill_price, null, true)
        val kmIn = input(R.string.car_fill_km, car.odometerKm.toDouble(), false)
        val full = android.widget.CheckBox(this).apply {
            setText(R.string.car_fill_full)
            isChecked = true
            textSize = 17f
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.car_add_fill)
            .setView(dialogBox(gal, price, kmIn, full))
            .setPositiveButton(R.string.car_save) { _, _ ->
                val g = parse(gal) ?: return@setPositiveButton
                val k = parse(kmIn)?.toInt() ?: car.odometerKm
                // Si el usuario corrige el km, se ajusta también el odómetro.
                if (k != car.odometerKm) car.setOdometer(k)
                car.addFill(com.carlight.launcher.car.CarData.Fill(System.currentTimeMillis(), k, g, parse(price) ?: 0.0, full.isChecked))
                show(Category.FUEL)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun drive() {
        header(R.string.cat_drive)
        val mph = prefs.speedUnit == SpeedUnit.MPH
        val u = if (mph) "mph" else "km/h"
        fun show(k: Int) = if (k == 0) getString(R.string.set_speed_alert_off)
            else "${if (mph) Math.round(k * 0.621371) else k.toLong()} $u"

        label(R.string.set_speed_alert)
        stepper(R.string.set_speed_limit, prefs.speedLimitKmh, 0, 160, 10, ::show) { prefs.speedLimitKmh = it }
        if (prefs.speedLimitKmh > 0) {
            label(R.string.set_speed_sound)
            segmented(listOf(R.string.set_on, R.string.set_off), if (prefs.speedAlertSound) 0 else 1) {
                prefs.speedAlertSound = it == 0
                show(Category.DRIVE)
            }
        }
        hint(R.string.set_speed_alert_hint)

        label(R.string.set_trip)
        hint(R.string.set_trip_hint)
        actionButton(R.string.set_trip_reset) {
            com.carlight.launcher.drive.TripComputer.reset()
            Toast.makeText(this, R.string.trip_reset_done, Toast.LENGTH_SHORT).show()
        }

        label(R.string.set_calls)
        segmented(listOf(R.string.set_on, R.string.set_off), if (prefs.callsOverMap) 0 else 1) {
            prefs.callsOverMap = it == 0
            show(Category.DRIVE)
        }
        hint(R.string.set_calls_hint)

        label(R.string.set_maneuver)
        segmented(listOf(R.string.set_on, R.string.set_off), if (prefs.showManeuver) 0 else 1) {
            prefs.showManeuver = it == 0
            show(Category.DRIVE)
        }
        hint(R.string.set_maneuver_hint)
        if (prefs.showManeuver && !notificationAccess()) status(R.string.perm_notifications, false)
    }

    private fun notificationAccess(): Boolean {
        val me = ComponentName(this, com.carlight.launcher.nav.NavNotificationListener::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').any { it.equals(me, ignoreCase = true) }
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
        label(R.string.set_icons)
        segmented(IconStyle.entries.map { it.label }, prefs.iconStyle.ordinal) {
            prefs.iconStyle = IconStyle.entries[it]
            show(Category.PANEL)
        }
        iconPreview()

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
        hint(R.string.perm_tap_hint)
        permission(R.string.perm_overlay, Settings.canDrawOverlays(this)) {
            openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true)
        }
        permission(R.string.perm_a11y, accessibilityEnabled()) { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        permission(R.string.perm_location, granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_PERM)
        }
        permission(R.string.perm_brightness, Settings.System.canWrite(this)) {
            openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, withPackage = true)
        }
        permission(R.string.perm_notifications, notificationAccess()) {
            openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        permission(R.string.perm_contacts, granted(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_PERM)
        }
        permission(R.string.perm_logs, granted(Manifest.permission.READ_LOGS)) {
            // Android no permite concederlo desde una pantalla: solo por adb.
            android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.perm_logs)
                .setMessage(getString(R.string.perm_logs_adb, packageName))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        label(R.string.set_about)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        hint(getString(R.string.set_version, version))
        credit()
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
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(10)
        })
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

    /** Vista previa del estilo de iconos con las apps del auto. */
    private fun iconPreview() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(14), 0, 0)
        }
        val accent = Accent.current(prefs)
        val size = px(52)
        listOf(
            "app.organicmaps.web" to "Mapa", "com.tw.radio" to "Radio", "com.tw.bt" to "Bluetooth",
            "com.tw.media" to "Música", "com.dofun.carsetting" to "Vehículo", "com.android.settings" to "Ajustes",
        ).forEach { (pkg, name) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val iv = ImageView(this)
            cell.addView(iv, LinearLayout.LayoutParams(size, size))
            val launch = packageManager.getLaunchIntentForPackage(pkg)?.component
                ?: ComponentName(pkg, "$pkg.Main")
            com.carlight.launcher.apps.IconPack.bind(iv, pkg, launch, size, prefs.iconStyle, accent)
            cell.addView(TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6) })
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(10)
        })
    }

    /**
     * Estilos de fondo: miniaturas con el degradado y las luces del estilo, en la versión
     * (día/noche) que se está viendo ahora. El elegido lleva borde y ✓.
     */
    private fun backgroundTiles() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        WallpaperRenderer.STYLES.forEachIndexed { i, style ->
            val p = style.palette(night)
            val selected = i == prefs.background
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener {
                    if (i != prefs.background) {
                        prefs.background = i
                        recreate()   // el fondo de esta pantalla también cambia
                    }
                }
            }
            val tile = FrameLayout(this).apply {
                background = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        GradientDrawable(GradientDrawable.Orientation.TL_BR, p.gradient).apply { cornerRadius = 14 * dp },
                        // Dos "luces" del estilo, como en el fondo real
                        GradientDrawable().apply {
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            gradientRadius = 40 * dp
                            setGradientCenter(0.25f, 0.3f)
                            colors = intArrayOf(p.orbs[0].color, p.orbs[0].color and 0x00FFFFFF)
                            cornerRadius = 14 * dp
                        },
                        GradientDrawable().apply {
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            gradientRadius = 44 * dp
                            setGradientCenter(0.85f, 0.75f)
                            colors = intArrayOf(p.orbs[1].color, p.orbs[1].color and 0x00FFFFFF)
                            cornerRadius = 14 * dp
                        },
                        GradientDrawable().apply {
                            cornerRadius = 14 * dp
                            setColor(0)
                            if (selected) setStroke(px(3), getColor(R.color.text_primary))
                            else setStroke(px(1), getColor(R.color.glass_stroke))
                        },
                    )
                )
            }
            if (selected) {
                tile.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(if (night) 0xFFFFFFFF.toInt() else getColor(R.color.text_primary))
                }, FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER))
            }
            cell.addView(tile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60)))
            cell.addView(TextView(this).apply {
                text = style.name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(getColor(if (selected) R.color.text_primary else R.color.text_secondary))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6)
            })
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = if (i == 0) 0 else px(10)
            })
        }
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Fila "Etiqueta   [−]  06:30  [+]" con pasos de 30 min. */
    private fun timeStepper(text: Int, minutes: Int, min: Int, max: Int, onChange: (Int) -> Unit) =
        stepper(text, minutes, min, max, 30, ::hhmm, onChange)

    /** Fila "Etiqueta   [−]  valor  [+]". */
    private fun stepper(text: Int, current: Int, min: Int, max: Int, step: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
        val minutes = current
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
            this.text = format(minutes)
            textSize = 20f
            maxLines = 1
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
                    show(current())
                }
            }
        }
        row.addView(step(-step), LinearLayout.LayoutParams(px(52), px(52)))
        row.addView(value, LinearLayout.LayoutParams(px(120), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(step(+step), LinearLayout.LayoutParams(px(52), px(52)))
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

    /** Fila de permiso: si falta, tocarla lleva a concederlo. */
    private fun permission(text: Int, ok: Boolean, grant: () -> Unit) {
        status(text, ok)
        if (!ok) content.getChildAt(content.childCount - 1).apply {
            isClickable = true
            setOnClickListener { grant() }
        }
    }

    private fun openSettings(action: String, withPackage: Boolean = false) {
        val i = android.content.Intent(action)
        if (withPackage) i.data = android.net.Uri.parse("package:$packageName")
        runCatching { startActivity(i) }.onFailure {
            Toast.makeText(this, R.string.perm_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun credit() {
        content.addView(TextView(this).apply {
            text = getString(R.string.set_credit)
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, wrap().apply { topMargin = px(4) })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_PERM) show(current)
    }

    private var resumedOnce = false

    /** Al volver de una pantalla del sistema (permisos), refrescar los estados. */
    override fun onResume() {
        super.onResume()
        if (resumedOnce) show(current)
        resumedOnce = true
    }

    private fun actionButton(text: Int, accent: Boolean = false, onClick: () -> Unit) {
        content.addView(TextView(this).apply {
            setText(text)
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(if (accent) 0xFFFFFFFF.toInt() else getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = if (accent) Accent.current(prefs).pill(16 * dp) else getDrawable(R.drawable.bg_segment)
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
    }

    /** Esferas en las esquinas de las tarjetas de Ajustes. */
    private fun sphereAnchors(): List<WallpaperRenderer.Sphere> {
        val root = findViewById<View>(R.id.root)
        val loc = IntArray(2)
        fun rect(v: View): android.graphics.RectF {
            v.getLocationInWindow(loc)
            val x = loc[0]; val y = loc[1]
            root.getLocationInWindow(loc)
            return android.graphics.RectF((x - loc[0]).toFloat(), (y - loc[1]).toFloat(), (x - loc[0] + v.width).toFloat(), (y - loc[1] + v.height).toFloat())
        }
        val nav = rect(findViewById(R.id.navCard))
        val content = rect(findViewById(R.id.contentCard))
        return listOf(
            WallpaperRenderer.Sphere(nav.right - 4 * dp, nav.top + 70 * dp, 40 * dp, 0),
            WallpaperRenderer.Sphere(content.right - 30 * dp, content.bottom - 6 * dp, 30 * dp, 1),
        )
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
        const val EXTRA_CAR = "car_mode"
        private const val REQ_PERM = 7
    }
}
