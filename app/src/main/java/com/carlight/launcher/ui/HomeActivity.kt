package com.carlight.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import com.carlight.launcher.R
import com.carlight.launcher.apps.AppRepository
import com.carlight.launcher.apps.IconCache
import com.carlight.launcher.databinding.ActivityHomeBinding
import com.carlight.launcher.media.BtSource
import com.carlight.launcher.media.BtState
import com.carlight.launcher.nav.MapsLauncher
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.radio.RadioSource
import com.carlight.launcher.radio.RadioState
import com.carlight.launcher.radio.formatFrequency
import com.carlight.launcher.speed.GpsSpeedSource
import com.carlight.launcher.speed.GpsStatus
import com.carlight.launcher.speed.SpeedState
import com.carlight.launcher.ui.glass.GlassBackground
import com.carlight.launcher.util.AppLauncher

/** Pantalla principal (HOME). Única Activity del launcher. */
class HomeActivity : Activity() {

    private lateinit var b: ActivityHomeBinding
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var radio: RadioSource
    private lateinit var bt: BtSource
    private lateinit var speed: GpsSpeedSource
    private lateinit var dock: DockController
    private lateinit var drawer: AppDrawer
    private lateinit var glass: GlassBackground

    /** Tema con el que se crearon los recursos de esta instancia. */
    private var night = false
    private var askedLocation = false

    private val onAppsChanged: () -> Unit = {
        dock.render()
        drawer.refresh()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            IconCache.clear()
            repo.reload()
            refreshNavTile()
        }
    }

    /** Cada minuto: en modo AUTO, cambiar a día/noche cuando toca. */
    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyThemeIfChanged()
        }
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { glass.onLayout(window.decorView) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        night = ThemeController.isNight(newBase)
        applyOverrideConfiguration(ThemeController.overrideFor(newBase, night))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        prefs = Prefs(this)
        repo = AppRepository.get(this)
        radio = RadioSource.create(this)
        bt = BtSource.create(this)
        speed = GpsSpeedSource(this)

        glass = GlassBackground(this, night)
        listOf(b.cardClock, b.cardBt, b.cardRadio, b.cardDrive, b.dock).forEach { glass.attach(it, 22f) }
        b.root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        drawer = AppDrawer(b, repo) { AppLauncher.launch(this, it.component) }
        dock = DockController(
            container = b.dock,
            prefs = prefs,
            repo = repo,
            pickApp = { onPicked -> drawer.showPicker { onPicked(it.pkg) } },
            openDrawer = { drawer.showAll() },
        )

        setupThemeToggle()
        setupBtCard()
        setupRadioCard()
        b.navTile.setOnClickListener { MapsLauncher.open(this) }
        refreshNavTile()

        repo.addListener(onAppsChanged)
        dock.render()
        repo.reload()

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onStart() {
        super.onStart()
        if (applyThemeIfChanged()) return
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        radio.start(::renderRadio)
        bt.start(::renderBt)
        startSpeed()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(timeTickReceiver) }
        radio.stop()
        bt.stop()
        speed.stop()
        super.onStop()
    }

    override fun onDestroy() {
        b.root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        unregisterReceiver(packageReceiver)
        repo.removeListener(onAppsChanged)
        super.onDestroy()
    }

    /** Botón Home estando ya en el launcher: cerrar el cajón. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (drawer.isOpen) drawer.hide()
    }

    @Deprecated("Activity framework API (sin AndroidX)")
    override fun onBackPressed() {
        // El launcher nunca se cierra con "atrás".
        if (drawer.isOpen) drawer.hide()
    }

    // ---------- Tema día / noche ----------

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
                this,
                when (next) {
                    ThemeMode.AUTO -> R.string.theme_auto
                    ThemeMode.DAY -> R.string.theme_day
                    ThemeMode.NIGHT -> R.string.theme_night
                },
                Toast.LENGTH_SHORT,
            ).show()
            if (!applyThemeIfChanged()) setupThemeToggle()
        }
    }

    /** @return true si se recreó la Activity con el otro tema. */
    private fun applyThemeIfChanged(): Boolean {
        if (ThemeController.isNight(this) == night) return false
        recreate()
        return true
    }

    // ---------- Velocímetro ----------

    private fun startSpeed() {
        if (!speed.hasPermission() && !askedLocation) {
            askedLocation = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
        }
        speed.start(::renderSpeed)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_LOCATION) {
            speed.stop()
            speed.start(::renderSpeed)
        }
    }

    private fun renderSpeed(s: SpeedState) {
        b.speedGauge.setSpeed(
            s.kmh,
            getString(
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
        b.btInfo.setOnClickListener { bt.open() }
        b.btPlay.setOnClickListener { bt.playPause() }
        b.btNext.setOnClickListener { bt.next() }
        b.btPrev.setOnClickListener { bt.prev() }
    }

    private fun renderBt(s: BtState) {
        b.btTitle.text = s.title ?: getString(R.string.bt_idle_title)
        b.btArtist.text = s.artist ?: getString(R.string.bt_idle_subtitle)
        b.btPlay.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        b.btDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }

    // ---------- Radio ----------

    private fun setupRadioCard() {
        b.radioInfo.setOnClickListener { radio.open() }
        b.radioNext.setOnClickListener { radio.next() }
        b.radioPrev.setOnClickListener { radio.prev() }
        b.radioSeekUp.setOnClickListener { radio.seekUp() }
        b.radioSeekDown.setOnClickListener { radio.seekDown() }
    }

    private fun renderRadio(s: RadioState) {
        val freq = s.formatFrequency()
        b.radioFreq.text = freq ?: getString(R.string.radio_unknown)
        b.radioUnit.visibility = if (freq != null) View.VISIBLE else View.GONE
        b.radioSub.text = when {
            freq == null -> getString(R.string.radio_hint_open)
            s.preset > 0 -> getString(R.string.radio_label) + " · " + getString(R.string.radio_preset, s.preset)
            else -> getString(R.string.radio_label)
        }
        b.radioDemo.visibility = if (s.demo) View.VISIBLE else View.GONE
    }

    // ---------- Navegación ----------

    private fun refreshNavTile() {
        b.navSubtitle.setText(
            if (MapsLauncher.installedPackage(this) != null) R.string.nav_subtitle else R.string.nav_missing
        )
    }

    private companion object {
        const val REQ_LOCATION = 1
    }
}
