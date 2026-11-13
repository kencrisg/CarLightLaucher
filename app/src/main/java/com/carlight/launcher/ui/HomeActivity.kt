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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        night = ThemeController.isNight(newBase)
        applyOverrideConfiguration(ThemeController.overrideFor(newBase, night))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DashboardController.overlayMode(this)) {
            // Solo el fondo: Organic Maps + el panel flotante lo cubren en un instante.
            setContentView(View(this))
        } else {
            val d = Dashboard(this, night, mapEmbedded = false, callbacks = object : Dashboard.Callbacks {
                override fun onMapFullscreen() { com.carlight.launcher.nav.MapsLauncher.open(this@HomeActivity) }
                override fun onMapReload() {}
                override fun onThemeChanged() = recreate()
            })
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
            DashboardController.onHomeShown(this)
        } else {
            if (ThemeController.isNight(this) != night) {
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
            dashboard?.restartSpeed()
            DashboardController.restartSpeed()
        }
    }

    private companion object {
        const val REQ_LOCATION = 1
        var askedLocation = false
    }
}
