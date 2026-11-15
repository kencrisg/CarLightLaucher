package com.carlight.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import com.carlight.launcher.overlay.DashboardController

/**
 * Actividad HOME.
 *  - Modo normal (permiso de superposición + Organic Maps): es solo el punto de entrada.
 *    Al mostrarse trae Organic Maps al frente y el panel aparece como capa flotante encima.
 *  - Modo respaldo: muestra el panel aquí mismo, sin mapa en vivo.
 */
class HomeActivity : Activity() {

    private var night = false
    private var dashboard: Dashboard? = null
    private var waitingText: android.widget.TextView? = null
    private var signature = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        night = ThemeController.isNight(newBase)
        applyOverrideConfiguration(ThemeController.overrideFor(newBase, night))
    }

    private val nightListener: (Boolean) -> Unit = {
        if (dashboard != null && ThemeController.isNight(this) != night) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tras el standby Topway cierra CarLight a la fuerza y Android desactiva su accesibilidad.
        com.carlight.launcher.overlay.A11ySelfHeal.start(this)
        com.carlight.launcher.night.NightController.start(this)
        com.carlight.launcher.night.NightController.addListener(nightListener)
        com.carlight.launcher.drive.TripComputer.start(this)
        com.carlight.launcher.calls.CallOverlay.start(this)
        // Fondo de ventana con el color del estilo elegido (evita el destello morado al arrancar).
        val palette = com.carlight.launcher.ui.glass.WallpaperRenderer
            .style(com.carlight.launcher.prefs.Prefs(this).background).palette(night)
        window.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, palette.gradient)
        )
        if (DashboardController.overlayMode(this)) {
            // Solo el fondo: Organic Maps + el panel flotante lo cubren. Al encender la radio, mientras
            // se espera el GPS, por el hueco del mapa se ve este aviso.
            waitingText = android.widget.TextView(this).apply {
                setText(com.carlight.launcher.R.string.gps_waiting_map)
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(com.carlight.launcher.R.color.text_secondary))
            }
            setContentView(waitingText)
        } else {
            val d = Dashboard(this, night, mapEmbedded = false, callbacks = object : Dashboard.Callbacks {
                override fun onMapFullscreen() { com.carlight.launcher.nav.MapsLauncher.open(this@HomeActivity) }
                override fun onMapReload() {}
                override fun onThemeChanged() = recreate()
                override fun onOpenSettings() {
                    startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                }
                override fun onOpenCar() {
                    startActivity(Intent(this@HomeActivity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CAR, true))
                }
            })
            signature = com.carlight.launcher.prefs.Prefs(this).appearanceSignature()
            dashboard = d
            setContentView(d.root)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    override fun onStart() {
        super.onStart()
        requestLocationIfNeeded()
        val d = dashboard
        if (d == null) {
            waitingText?.visibility = if (DashboardController.mapOpenedOnce) View.INVISIBLE else View.VISIBLE
            DashboardController.onHomeShown(this)
        } else {
            if (ThemeController.isNight(this) != night ||
                com.carlight.launcher.prefs.Prefs(this).appearanceSignature() != signature
            ) {
                recreate()
                return
            }
            d.start()
        }
    }

    override fun onStop() {
        dashboard?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        com.carlight.launcher.night.NightController.removeListener(nightListener)
        dashboard?.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dashboard?.closeDrawer()
    }

    @Deprecated("Activity framework API (sin AndroidX)")
    override fun onBackPressed() {
        dashboard?.closeDrawer()   // el launcher nunca se cierra con "atrás"
    }

    private fun requestLocationIfNeeded() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && !askedLocation) {
            askedLocation = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_LOCATION) {
            com.carlight.launcher.drive.TripComputer.start(this)
            dashboard?.restartSpeed()
            DashboardController.restartSpeed()
        }
    }

    private companion object {
        const val REQ_LOCATION = 1
        var askedLocation = false
    }
}
