package com.carlight.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import com.carlight.launcher.R
import com.carlight.launcher.apps.AppRepository
import com.carlight.launcher.apps.IconCache
import com.carlight.launcher.databinding.ActivityHomeBinding
import com.carlight.launcher.media.BtSource
import com.carlight.launcher.media.BtState
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.prefs.SpeedUnit
import com.carlight.launcher.radio.RadioSource
import com.carlight.launcher.radio.RadioState
import com.carlight.launcher.radio.formatFrequency
import com.carlight.launcher.speed.GpsSpeedSource
import com.carlight.launcher.speed.GpsStatus
import com.carlight.launcher.speed.SpeedState
import com.carlight.launcher.ui.glass.GlassBackground
import com.carlight.launcher.ui.glass.WallpaperHoleDrawable
import com.carlight.launcher.util.AppLauncher

/**
 * Panel principal (reloj, km/h, mapa, BT, radio, dock, cajón). Independiente de dónde se muestre:
 *  - en la capa flotante sobre Organic Maps (modo normal, mapa en vivo por el hueco), o
 *  - dentro de HomeActivity (respaldo sin permiso de superposición o sin Organic Maps).
 *
 * @param ctx contexto con el tema (día/noche) ya aplicado.
 */
class Dashboard(
    private val ctx: Context,
    private val night: Boolean,
    private val mapEmbedded: Boolean,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** El hueco del mapa (coordenadas de ventana) cambió; [islands] = botones flotantes táctiles. */
        fun onHoleChanged(holeInWindow: Rect, islands: List<Rect>) {}
        /** false mientras algo nuestro tapa el hueco (cajón de apps). */
        fun onPassthroughChanged(enabled: Boolean) {}
        /** Justo antes de abrir otra app desde el panel. */
        fun onLaunchingApp() {}
        fun onMapFullscreen()
        /** Abrir la pantalla de Ajustes de CarLight. */
        fun onOpenSettings() {}
        /** Abrir "Mi auto" (mantenimiento y gasolina). */
        fun onOpenCar() {}
        fun onMapReload()
        /** El tema efectivo (día/noche) cambió: hay que reconstruir el panel. */
        fun onThemeChanged()
    }

    val b: ActivityHomeBinding = ActivityHomeBinding.inflate(LayoutInflater.from(ctx))
    val root: View get() = b.root

    private val prefs = Prefs(ctx)
    private val repo = AppRepository.get(ctx)
    private val radio = RadioSource.create(ctx)
    private val bt = BtSource.create(ctx)
    private val speed = GpsSpeedSource(ctx)
    private val bgStyle = com.carlight.launcher.ui.glass.WallpaperRenderer.style(prefs.background).palette(night)
    private val wallpaper = WallpaperHoleDrawable(ctx.resources, bgStyle.gradient)
    private val glass = GlassBackground(ctx, night, prefs.skyline, prefs.background, prefs.glassLevel, null) { wallpaper.setBitmap(it) }
    private val accent = Accent.current(prefs)
    private val unit = prefs.speedUnit
    private val drawer: AppDrawer
    private val dock: DockController

    private val holeInWindow = Rect()
    private val islandRects = listOf(Rect(), Rect(), Rect())
    private val holeInRoot = Rect()
    private val tmp = IntArray(2)
    private var started = false

    private val onAppsChanged: () -> Unit = {
        dock.render()
        drawer.refresh()
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        glass.onLayout(root)
        updateHole()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            IconCache.clear()
            repo.reload()
        }
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ThemeController.isNight(ctx) != night) callbacks.onThemeChanged()
        }
    }

    init {
        root.background = wallpaper
        val radius = ctx.resources.getDimension(R.dimen.card_radius) / ctx.resources.displayMetrics.density
        listOf(b.cardSpeed, b.cardClock, b.cardBt, b.cardRadio, b.cardCar, b.dock).forEach { glass.attach(it, radius) }
        if (!mapEmbedded) {
            glass.attach(b.cardMap, radius)
            b.mapPlaceholder.visibility = View.VISIBLE
            b.mapPlaceholder.setText(R.string.map_tap_to_open)
            b.mapHole.setOnClickListener { launching(); callbacks.onMapFullscreen() }
            b.mapReload.visibility = View.GONE
        }
        b.mapReload.setOnClickListener { callbacks.onMapReload() }
        b.mapFullscreen.setOnClickListener { launching(); callbacks.onMapFullscreen() }

        drawer = AppDrawer(
            b, repo,
            onLaunch = { launching(); AppLauncher.launch(ctx, it.component) },
            onVisibilityChanged = { open -> callbacks.onPassthroughChanged(!open) },
        )
        dock = DockController(
            container = b.dock,
            prefs = prefs,
            repo = repo,
            pickApp = { onPicked -> drawer.showPicker { onPicked(it.pkg) } },
            openDrawer = { drawer.showAll() },
            onLaunch = { launching() },
        )
        applyPreferences()
        b.openSettings.setOnClickListener { launching(); callbacks.onOpenSettings() }
        b.drawerSettings.setOnClickListener { launching(); callbacks.onOpenSettings() }
        b.cardClock.setOnLongClickListener { launching(); callbacks.onOpenSettings(); true }
        setupBtCard()
        setupRadioCard()
        setupTripCard()

        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        repo.addListener(onAppsChanged)
        dock.render()
        repo.reload()
        ctx.registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    /** Panel visible: arrancar fuentes de datos. */
    fun start() {
        if (started) return
        started = true
        ctx.registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        radio.start(::renderRadio)
        bt.start(::renderBt)
        speed.start(::renderSpeed)
        com.carlight.launcher.drive.TripComputer.addListener(tripListener)
        renderTrip()
        renderCarReminder()
        if (mapEmbedded && prefs.showManeuver) {
            com.carlight.launcher.nav.NavState.addListener(navListener)
            renderManeuver(com.carlight.launcher.nav.NavState.current)
        }
    }

    /** Panel oculto: detener todo (GPS incluido). */
    fun stop() {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(timeTickReceiver) }
        radio.stop()
        bt.stop()
        speed.stop()
        com.carlight.launcher.drive.TripComputer.removeListener(tripListener)
        com.carlight.launcher.nav.NavState.removeListener(navListener)
        b.btDisc.setPlaying(false)   // sin animaciones mientras el panel no se ve
        b.btEq.setPlaying(false)
        if (drawer.isOpen) drawer.hide()
    }

    fun destroy() {
        stop()
        glass.release()
        root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        runCatching { ctx.unregisterReceiver(packageReceiver) }
        repo.removeListener(onAppsChanged)
    }

    fun closeDrawer(): Boolean {
        if (!drawer.isOpen) return false
        drawer.hide()
        return true
    }

    /** El permiso de ubicación pudo haber cambiado. */
    fun restartSpeed() {
        if (!started) return
        speed.stop()
        speed.start(::renderSpeed)
    }

    private fun launching() {
        if (drawer.isOpen) drawer.hide()
        callbacks.onLaunchingApp()
    }

    // ---------- Mapa ----------

    private fun updateHole() {
        if (!mapEmbedded) return
        b.mapHole.getLocationInWindow(tmp)
        holeInWindow.set(tmp[0], tmp[1], tmp[0] + b.mapHole.width, tmp[1] + b.mapHole.height)
        root.getLocationInWindow(tmp)
        holeInRoot.set(holeInWindow)
        holeInRoot.offset(-tmp[0], -tmp[1])
        wallpaper.setHole(holeInRoot, ctx.resources.getDimension(R.dimen.card_radius))

        // Los botones flotantes del mapa siguen siendo nuestros (táctiles).
        listOf(b.mapFullscreen, b.mapReload, b.mapManeuver).forEachIndexed { i, v ->
            if (v.visibility != View.VISIBLE) { islandRects[i].setEmpty(); return@forEachIndexed }
            v.getLocationInWindow(tmp)
            islandRects[i].set(tmp[0], tmp[1], tmp[0] + v.width, tmp[1] + v.height)
        }
        callbacks.onHoleChanged(holeInWindow, islandRects)
    }

    // ---------- Esferas decorativas (ancladas a las tarjetas) ----------

    private fun sphereAnchors(): List<com.carlight.launcher.ui.glass.WallpaperRenderer.Sphere> {
        val d = ctx.resources.displayMetrics.density
        fun rect(v: View): android.graphics.RectF {
            v.getLocationInWindow(tmp)
            val rx = tmp[0]; val ry = tmp[1]
            root.getLocationInWindow(tmp)
            val x = (rx - tmp[0]).toFloat(); val y = (ry - tmp[1]).toFloat()
            return android.graphics.RectF(x, y, x + v.width, y + v.height)
        }
        val bt = rect(b.cardBt)
        val radio = rect(b.cardRadio)
        return listOf(
            // Asoma por la esquina superior derecha de la tarjeta BT (como la esfera del ejemplo)
            com.carlight.launcher.ui.glass.WallpaperRenderer.Sphere(bt.right - 4 * d, bt.top + 4 * d, 38 * d, 0),
            // Esquina superior izquierda de la radio
            com.carlight.launcher.ui.glass.WallpaperRenderer.Sphere(radio.left + 4 * d, radio.top + 2 * d, 32 * d, 1),
        )
    }

    // ---------- Ajustes aplicados al panel ----------

    private fun applyPreferences() {
        // Hora 24 h / 12 h: TextClock usa format24Hour o format12Hour según el sistema; forzamos ambos.
        val time = if (prefs.clock24) "HH:mm" else "h:mm"
        b.clockTime.format24Hour = time
        b.clockTime.format12Hour = time

        // Velocímetro
        b.speedGauge.unit = if (unit == SpeedUnit.MPH) "mph" else "km/h"
        b.speedGauge.maxValue = prefs.gaugeMax.toFloat()
        b.speedGauge.colors = accent.gauge

        // Cajón de apps: velo del color del fondo elegido (no morado fijo)
        b.drawer.setBackgroundColor((bgStyle.bgStart and 0x00FFFFFF) or 0xF2000000.toInt())

        // Marco del mapa: trazo fino del acento (contrasta con las calles) + resplandor exterior.
        val d = ctx.resources.displayMetrics.density
        b.cardMap.foreground = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = ctx.resources.getDimension(R.dimen.card_radius)
            setColor(0)
            setStroke(maxOf(1, (1.5f * d).toInt()), accent.end)
        }
        wallpaper.setHoleGlow(accent.end)

        // Efectos visuales
        b.skyBadge.night = night
        b.btDisc.setAccent(accent.start, accent.end)
        b.btDisc.animate = prefs.effects
        b.btEq.setColor(accent.end)
        b.btEq.animate = prefs.effects
        b.radioDial.setAccent(accent.end)

        // Acento: botón ▶ de la música
        b.btPlay.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x40FFFFFF),
            accent.pill(0f, oval = true),
            null,
        )
    }

    // ---------- Velocímetro ----------

    private fun renderSpeed(s: SpeedState) {
        b.speedGauge.setSpeed(
            s.kmh?.let { if (unit == SpeedUnit.MPH) Math.round(it * KMH_TO_MPH).toInt() else it },
            ctx.getString(
                when (s.status) {
                    GpsStatus.FIX -> R.string.gps_fix
                    GpsStatus.SEARCHING -> R.string.gps_searching
                    GpsStatus.NO_PERMISSION -> R.string.gps_no_permission
                    GpsStatus.DISABLED -> R.string.gps_disabled
                }
            ),
        )
    }

    // ---------- Computadora de viaje / alerta ----------

    private val tripListener: () -> Unit = { renderTrip() }

    private fun setupTripCard() {
        b.cardSpeed.setOnClickListener {
            val showTrip = b.tripView.visibility != View.VISIBLE
            b.tripView.visibility = if (showTrip) View.VISIBLE else View.GONE
            b.speedGauge.visibility = if (showTrip) View.GONE else View.VISIBLE
            renderTrip()
        }
        b.cardSpeed.setOnLongClickListener {
            com.carlight.launcher.drive.TripComputer.reset()
            Toast.makeText(ctx, R.string.trip_reset_done, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun renderTrip() {
        val tc = com.carlight.launcher.drive.TripComputer
        b.speedGauge.alert = tc.alerting
        if (b.tripView.visibility != View.VISIBLE) return
        val t = tc.trip
        val mph = unit == SpeedUnit.MPH
        val u = if (mph) "mph" else "km/h"
        val dist = if (mph) t.meters / 1609.34f else t.meters / 1000f
        b.tripDistance.text = "%.1f %s".format(dist, if (mph) "mi" else "km")
        val min = t.movingMs / 60_000
        b.tripTime.text = if (min >= 60) "%dh %02dm".format(min / 60, min % 60) else "$min min"
        fun conv(k: Int) = if (mph) Math.round(k * KMH_TO_MPH).toInt() else k
        b.tripAvg.text = conv(t.avgKmh).toString()
        b.tripMax.text = conv(t.maxKmh).toString()
        b.tripAvgLabel.text = ctx.getString(R.string.trip_avg, u)
        b.tripMaxLabel.text = ctx.getString(R.string.trip_max, u)
    }

    // ---------- Recordatorio de mantenimiento ----------

    /** Tarjeta "Mi auto": kilometraje + los 2 recordatorios más urgentes con barra de progreso. */
    private fun renderCarReminder() {
        b.cardCar.setOnClickListener { launching(); callbacks.onOpenCar() }
        val car = com.carlight.launcher.car.CarData(ctx)
        val list = b.carList
        list.removeAllViews()
        val d = ctx.resources.displayMetrics.density
        if (!car.hasOdometer) {
            b.carOdometer.text = ""
            list.addView(android.widget.TextView(ctx).apply {
                setText(R.string.car_setup)
                textSize = 14f
                setTextColor(ctx.getColor(R.color.text_secondary))
            })
            return
        }
        val odo = car.odometerKm
        b.carOdometer.text = "%,d km".format(odo).replace(',', '.')
        // Fracción consumida del intervalo (km o tiempo, lo que esté más cerca)
        fun used(i: com.carlight.launcher.car.CarData.Item): Float {
            val byKm = i.kmLeft(odo)?.let { 1f - it.toFloat() / i.everyKm } ?: 0f
            val period = if (i.isMonthly) 365f else i.everyMonths * 30.4f
            val byTime = i.daysLeft()?.takeIf { period > 0f }?.let { 1f - it / period } ?: 0f
            return maxOf(byKm, byTime).coerceIn(0f, 1f)
        }
        car.items().sortedByDescending { used(it) }.take(2).forEach { item ->
            val status = item.status(odo)
            val color = when (status) { 2 -> 0xFFEF4444.toInt(); 1 -> 0xFFF59E0B.toInt(); else -> accent.end }
            val kmLeft = item.kmLeft(odo)
            val days = item.daysLeft()
            val detail = when {
                status == 2 -> "vencido"
                item.isMonthly && item.month == 0 -> "elegir mes"
                kmLeft != null && (days == null || kmLeft < days * 40) -> "%,d km".format(kmLeft).replace(',', '.')
                days != null -> "$days días"
                else -> ""
            }
            val row = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            row.addView(android.widget.TextView(ctx).apply {
                text = item.name
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ctx.getColor(R.color.text_primary))
            }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(android.widget.TextView(ctx).apply {
                text = detail
                textSize = 13f
                setTextColor(if (status > 0) color else ctx.getColor(R.color.text_secondary))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            list.addView(row, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * d).toInt() })
            list.addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (used(item) * 1000).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(color)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.gauge_track))
            }, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (6 * d).toInt()).apply {
                topMargin = (4 * d).toInt()
            })
        }
    }

    // ---------- Próxima maniobra ----------

    private val navListener: (com.carlight.launcher.nav.NavState.Maneuver?) -> Unit = { renderManeuver(it) }

    private fun renderManeuver(m: com.carlight.launcher.nav.NavState.Maneuver?) {
        if (m == null) {
            b.mapManeuver.visibility = View.GONE
        } else {
            b.mapManeuver.visibility = View.VISIBLE
            b.maneuverDistance.text = m.distance
            b.maneuverStreet.text = m.street
            b.maneuverStreet.visibility = if (m.street.isEmpty()) View.GONE else View.VISIBLE
            if (m.arrow != null) {
                b.maneuverArrow.clearColorFilter()
                b.maneuverArrow.setImageBitmap(m.arrow)
            } else {
                b.maneuverArrow.setImageResource(R.drawable.glyph_map)
                b.maneuverArrow.setColorFilter(0xFFFFFFFF.toInt())
            }
        }
        root.requestLayout()   // recalcula las islas táctiles
    }

    // ---------- Bluetooth ----------

    private fun setupBtCard() {
        // Toda la tarjeta abre la app (fácil de tocar conduciendo); la fila de controles no.
        b.cardBt.setOnClickListener { launching(); bt.open() }
        b.btPlay.setOnClickListener { bt.playPause() }
        b.btNext.setOnClickListener { bt.next() }
        b.btPrev.setOnClickListener { bt.prev() }
    }

    private fun renderBt(s: BtState) {
        b.btTitle.text = when {
            s.title != null -> s.title
            !s.connected -> ctx.getString(R.string.bt_disconnected)
            else -> ctx.getString(R.string.bt_idle_title)
        }
        b.btArtist.text = when {
            s.artist != null -> s.artist
            !s.connected -> ctx.getString(R.string.bt_disconnected_hint)
            s.title != null -> ""
            else -> ctx.getString(R.string.bt_idle_subtitle)
        }
        b.btPlay.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        b.btDisc.setPlaying(s.playing && started)
        b.btEq.setPlaying(s.playing && started)
        b.btDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }

    // ---------- Radio ----------

    private fun setupRadioCard() {
        radio.onNeedsApp = { launching(); radio.open() }
        // Fase 1: los controles de radio abren la app de fábrica (nivel C) -> ocultar el panel.
        b.cardRadio.setOnClickListener { launching(); radio.open() }
        fun radioControl(action: () -> Unit) {
            if (radio.opensApp) launching() else bt.pauseForOtherSource()
            action()
        }
        b.radioNext.setOnClickListener { radioControl(radio::next) }
        b.radioPrev.setOnClickListener { radioControl(radio::prev) }
        b.radioSeekUp.setOnClickListener { radioControl(radio::seekUp) }
        b.radioSeekDown.setOnClickListener { radioControl(radio::seekDown) }
    }

    private fun renderRadio(s: RadioState) {
        val freq = s.formatFrequency()
        b.radioFreq.text = freq ?: ctx.getString(R.string.radio_unknown)
        b.radioUnit.visibility = if (freq != null) View.VISIBLE else View.GONE
        b.radioDial.setFrequency(s.frequency)
        b.radioSub.text = when {
            freq == null -> ctx.getString(R.string.radio_hint_open)
            s.preset > 0 -> ctx.getString(R.string.radio_preset_long, s.preset)
            s.preset > 0 -> ctx.getString(R.string.radio_label) + " · " + ctx.getString(R.string.radio_preset, s.preset)
            else -> ctx.getString(R.string.radio_label)
        }
        b.radioDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }

    private companion object {
        const val KMH_TO_MPH = 0.621371
    }
}
