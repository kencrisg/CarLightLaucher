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
    private val wallpaper = WallpaperHoleDrawable(ctx.resources)
    private val glass = GlassBackground(ctx, night, prefs.skyline) { wallpaper.setBitmap(it) }
    private val accent = Accent.current(prefs)
    private val unit = prefs.speedUnit
    private val drawer: AppDrawer
    private val dock: DockController

    private val holeInWindow = Rect()
    private val islandRects = listOf(Rect(), Rect())
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
        listOf(b.cardSpeed, b.cardClock, b.cardBt, b.cardRadio, b.dock).forEach { glass.attach(it, radius) }
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
    }

    /** Panel oculto: detener todo (GPS incluido). */
    fun stop() {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(timeTickReceiver) }
        radio.stop()
        bt.stop()
        speed.stop()
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
        listOf(b.mapFullscreen, b.mapReload).forEachIndexed { i, v ->
            v.getLocationInWindow(tmp)
            islandRects[i].set(tmp[0], tmp[1], tmp[0] + v.width, tmp[1] + v.height)
        }
        callbacks.onHoleChanged(holeInWindow, islandRects)
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
