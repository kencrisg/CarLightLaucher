package com.carlight.launcher.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.carlight.launcher.nav.MapsLauncher
import com.carlight.launcher.ui.HomeActivity

/**
 * Decide cuándo se ve el panel flotante:
 *  - Home pulsado (HomeActivity al frente) -> traer Organic Maps al frente y mostrar el panel.
 *  - Organic Maps al frente -> panel visible (salvo que el usuario pidió el mapa a pantalla completa).
 *  - Cualquier otra Activity al frente (radio, BT, llamada, cámara de retroceso...) -> ocultar.
 * Los cambios de app al frente llegan desde [ForegroundWatcher] (servicio de accesibilidad).
 */
object DashboardController {

    private const val TAG = "Dashboard"

    private var overlay: DashboardOverlay? = null
    private var mapFullscreen = false
    /** Última Activity al frente según [ForegroundWatcher]. */
    private var foregroundPkg: String? = null
    private val activityCache = HashMap<String, Boolean>()

    /** true si el servicio de accesibilidad está conectado (detección fiable de la app al frente). */
    @Volatile var watcherConnected = false

    fun overlayMode(ctx: Context): Boolean =
        overlay(ctx).canShow() && MapsLauncher.installedPackage(ctx) != null

    private fun overlay(ctx: Context) = overlay ?: DashboardOverlay(ctx.applicationContext).also {
        overlay = it
        com.carlight.launcher.night.NightController.start(ctx)
        com.carlight.launcher.drive.TripComputer.start(ctx)
        com.carlight.launcher.calls.CallOverlay.start(ctx)
        com.carlight.launcher.night.NightController.addListener { _ -> it.onNightChanged() }
    }

    /** HomeActivity visible (botón Home / arranque). */
    fun onHomeShown(ctx: Context) {
        mapFullscreen = false
        bringMapToFront(ctx)
        overlay(ctx).show()
    }

    fun onLaunchingApp() {
        overlay?.hide()
    }

    fun openMapFullscreen(ctx: Context) {
        mapFullscreen = true
        overlay?.hide()
        bringMapToFront(ctx)
    }

    /** Botón ⟳: si el mapa ya está al frente solo re-muestra el panel; si no, lo trae. */
    fun reloadMap(ctx: Context) {
        val mapPkg = MapsLauncher.installedPackage(ctx)
        if (watcherConnected && foregroundPkg == mapPkg) {
            overlay?.hide()
            overlay(ctx).show()
        } else {
            bringMapToFront(ctx)
        }
    }

    fun bringMapToFront(ctx: Context) {
        val pkg = MapsLauncher.installedPackage(ctx) ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir Organic Maps", e)
        }
    }

    fun restartSpeed() = overlay?.restartSpeed()

    // ---------- Llamadas sobre el mapa ----------

    private var inCall = false
    private var lastReclaim = 0L

    fun onCallStarted(ctx: Context) {
        inCall = true
        if (foregroundPkg == "com.tw.bt") reclaimMap(ctx)
    }

    fun onCallEnded() { inCall = false }

    /** Durante una llamada, la app BT de fábrica se abre sola: volvemos al mapa (la tarjeta flota encima). */
    private fun reclaimMap(ctx: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastReclaim < 1500) return
        lastReclaim = now
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (inCall) bringMapToFront(ctx) }, 400)
    }

    fun openSettings(ctx: Context, car: Boolean = false) {
        overlay?.hide()
        ctx.startActivity(
            Intent(ctx, com.carlight.launcher.ui.SettingsActivity::class.java)
                .putExtra(com.carlight.launcher.ui.SettingsActivity.EXTRA_CAR, car)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    /** Llamado por [ForegroundWatcher] en cada TYPE_WINDOW_STATE_CHANGED. */
    fun onWindowStateChanged(ctx: Context, pkg: String, cls: String) {
        if (!isActivity(ctx, pkg, cls)) return   // diálogos, toasts, paneles de volumen, etc.
        foregroundPkg = pkg
        if (inCall && pkg == "com.tw.bt") { reclaimMap(ctx); return }
        val mapPkg = MapsLauncher.installedPackage(ctx)
        when {
            pkg == ctx.packageName ->
                if (cls == HomeActivity::class.java.name) onHomeShown(ctx) else overlay?.hide()   // Ajustes
            pkg == mapPkg -> if (mapFullscreen) overlay?.hide() else overlay(ctx).show()
            else -> {
                mapFullscreen = false
                overlay?.hide()
            }
        }
    }

    private fun isActivity(ctx: Context, pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        return activityCache.getOrPut(key) {
            try {
                ctx.packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
