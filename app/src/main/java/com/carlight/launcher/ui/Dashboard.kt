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
    private val glass = GlassBackground(ctx, night) { wallpaper.setBitmap(it) }
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
        setupThemeToggle()
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

    // ---------- Tema ----------

    private fun setupThemeToggle() {
        b.themeToggle.setImageResource(
            when (prefs.themeMode) {
                ThemeMode.AUTO -> R.drawable.ic_theme_auto
                ThemeMode.DAY -> R.drawable.ic_theme_day
                ThemeMode.NIGHT -> R.drawable.ic_theme_night
            }
        )
        b.themeToggle.setOnClickListener {
            val next = ThemeMode.entries[(prefs.themeMode.ordinal + 1) % ThemeMode.entries.size]
            prefs.themeMode = next
            Toast.makeText(
                ctx,
                when (next) {
                    ThemeMode.AUTO -> R.string.theme_auto
                    ThemeMode.DAY -> R.string.theme_day
                    ThemeMode.NIGHT -> R.string.theme_night
                },
                Toast.LENGTH_SHORT,
            ).show()
            if (ThemeController.isNight(next) != night) callbacks.onThemeChanged() else setupThemeToggle()
        }
    }

    // ---------- Velocímetro ----------

    private fun renderSpeed(s: SpeedState) {
        b.speedGauge.setSpeed(
            s.kmh,
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
        b.btTitle.text = s.title ?: ctx.getString(R.string.bt_idle_title)
        b.btArtist.text = s.artist ?: ctx.getString(R.string.bt_idle_subtitle)
        b.btPlay.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        b.btDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }

    // ---------- Radio ----------

    private fun setupRadioCard() {
        // Fase 1: los controles de radio abren la app de fábrica (nivel C) -> ocultar el panel.
        b.cardRadio.setOnClickListener { launching(); radio.open() }
        b.radioNext.setOnClickListener { if (radio.opensApp) launching(); radio.next() }
        b.radioPrev.setOnClickListener { if (radio.opensApp) launching(); radio.prev() }
        b.radioSeekUp.setOnClickListener { if (radio.opensApp) launching(); radio.seekUp() }
        b.radioSeekDown.setOnClickListener { if (radio.opensApp) launching(); radio.seekDown() }
    }

    private fun renderRadio(s: RadioState) {
        val freq = s.formatFrequency()
        b.radioFreq.text = freq ?: ctx.getString(R.string.radio_unknown)
        b.radioUnit.visibility = if (freq != null) View.VISIBLE else View.GONE
        b.radioSub.text = when {
            freq == null -> ctx.getString(R.string.radio_hint_open)
            s.preset > 0 -> ctx.getString(R.string.radio_label) + " · " + ctx.getString(R.string.radio_preset, s.preset)
            else -> ctx.getString(R.string.radio_label)
        }
        b.radioDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }
}
