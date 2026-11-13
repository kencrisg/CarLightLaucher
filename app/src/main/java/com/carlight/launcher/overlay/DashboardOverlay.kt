package com.carlight.launcher.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import com.carlight.launcher.R
import com.carlight.launcher.ui.Dashboard
import com.carlight.launcher.ui.ThemeController

/**
 * El panel como CAPA FLOTANTE (TYPE_APPLICATION_OVERLAY) sobre Organic Maps, que queda en primer
 * plano y por lo tanto sigue dibujando (Organic Maps pausa el render cuando no está al frente).
 * La capa deja un hueco transparente y no táctil sobre la tarjeta del mapa.
 */
class DashboardOverlay(private val app: Context) {

    private val wm = app.getSystemService(WindowManager::class.java)
    private var dashboard: Dashboard? = null
    private var touch: TouchRegion? = null
    private var night = false
    private var attached = false

    val isShowing get() = attached

    fun canShow() = Settings.canDrawOverlays(app)

    fun show() {
        if (!canShow()) return
        if (dashboard == null || ThemeController.isNight(app) != night) rebuild()
        val d = dashboard ?: return
        if (!attached) {
            wm.addView(d.root, params())
            attached = true
        }
        d.start()
    }

    fun hide() {
        val d = dashboard ?: return
        d.stop()
        if (attached) {
            wm.removeViewImmediate(d.root)
            attached = false
        }
    }

    fun restartSpeed() = dashboard?.restartSpeed()

    private fun rebuild() {
        val wasAttached = attached
        dashboard?.let { old ->
            old.stop()
            if (attached) wm.removeViewImmediate(old.root)
            attached = false
            old.destroy()
        }
        night = ThemeController.isNight(app)
        val themed = ContextThemeWrapper(
            app.createConfigurationContext(ThemeController.overrideFor(app, night)),
            R.style.Theme_CarLight,
        )
        val d = Dashboard(themed, night, mapEmbedded = true, callbacks = object : Dashboard.Callbacks {
            override fun onHoleChanged(holeInWindow: Rect, islands: List<Rect>) { touch?.setHole(holeInWindow, islands) }
            override fun onPassthroughChanged(enabled: Boolean) { touch?.setPassthrough(enabled) }
            override fun onLaunchingApp() = DashboardController.onLaunchingApp()
            override fun onMapFullscreen() = DashboardController.openMapFullscreen(app)
            override fun onMapReload() = DashboardController.bringMapToFront(app)
            override fun onThemeChanged() {
                rebuild()
                show()
            }
        })
        // Una capa flotante no recibe los insets del sistema: dejar libre la barra de estado a mano.
        d.root.fitsSystemWindows = false
        d.root.setPadding(0, statusBarHeight(), 0, 0)
        touch = TouchRegion(d.root).also { region ->
            d.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { if (!region.installed) region.install() }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
        dashboard = d
        if (wasAttached) show()
    }

    private fun statusBarHeight(): Int {
        val id = app.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id != 0) app.resources.getDimensionPixelSize(id) else 0
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "CarLightDashboard"
        windowAnimations = 0
    }
}
