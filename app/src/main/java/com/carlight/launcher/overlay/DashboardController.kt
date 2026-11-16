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

    @Volatile private var reversing = false

    /** Ventana emergente de Topway (barra de volumen): dejarla ver unos segundos. */
    fun onTopwayPopup() {
        overlay?.peek()
    }

    private fun overlay(ctx: Context) = overlay ?: DashboardOverlay(ctx.applicationContext).also {
        overlay = it
        val app = ctx.applicationContext
        com.carlight.launcher.topway.TwCommandClient.bind(app)
        com.carlight.launcher.topway.TwCommandClient.onVolume = { it.peek() }
        // Respaldo: cambio de volumen estándar de Android (si Topway no avisa por su servicio).
        app.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                android.util.Log.i(TAG, "volumen cambiado (Android)")
                it.peek()
            }
        }, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        com.carlight.launcher.topway.TwCommandClient.onReverse = { on ->
            reversing = on
            if (on) it.hide()
            else if (!inCall && !mapFullscreen &&
                (foregroundPkg == app.packageName || foregroundPkg == MapsLauncher.installedPackage(app))
            ) it.show()
        }
        com.carlight.launcher.night.NightController.start(ctx)
        com.carlight.launcher.drive.TripComputer.start(ctx)
        com.carlight.launcher.calls.CallOverlay.start(ctx)
        com.carlight.launcher.night.NightController.addListener { _ -> it.onNightChanged() }
    }

    /** HomeActivity visible (botón Home / arranque). */
    /** El mapa ya se abrió alguna vez desde que arrancó el proceso (arranque de la radio). */
    var mapOpenedOnce = false
        private set

    fun onHomeShown(ctx: Context) {
        mapFullscreen = false
        if (mapOpenedOnce) {
            bringMapToFront(ctx)
        } else {
            // Primera vez tras encender: esperar al GPS para que Organic Maps abra en tu posición.
            com.carlight.launcher.gps.GpsReady.whenReady(ctx) {
                mapOpenedOnce = true
                bringMapToFront(ctx)
                com.carlight.launcher.gps.MapCentering.afterMapOpened()
            }
        }
        // Durante una llamada los controles nativos flotan sobre el mapa: el panel no los tapa.
        if (inCall || reversing) overlay?.hide() else overlay(ctx).show()
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

    // ---------- Llamadas ----------

    /** App al frente actual (la usa CallOverlay para cederle la pantalla a la app nativa). */
    val foreground: String? get() = foregroundPkg

    private val fgListeners = mutableListOf<(String) -> Unit>()
    fun addForegroundListener(l: (String) -> Unit) { fgListeners += l }
    fun removeForegroundListener(l: (String) -> Unit) { fgListeners -= l }

    /** Hay una llamada en curso (la pantalla nativa de Topway es una ventana flotante). */
    @Volatile var inCall = false
        private set

    /** Durante la llamada el usuario pulsó Home: puede ver el mapa + tarjeta compacta. */
    @Volatile var userLeftCall = false
        private set

    private const val NATIVE_BT = "com.tw.bt"

    /** Llamado por CallOverlay en cada cambio de la llamada. */
    fun onCallChanged(ctx: Context, active: Boolean) {
        if (active && !inCall) {
            inCall = true
            userLeftCall = false
            overlay?.hide()
            // Al conectar el teléfono llega una "llamada" fantasma de <1 s: esperar a que se confirme.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (inCall && !userLeftCall) showNativeCall(ctx)
            }, 800)
        } else if (!active && inCall) {
            inCall = false
            userLeftCall = false
            if (foregroundPkg == ctx.packageName || foregroundPkg == MapsLauncher.installedPackage(ctx)) overlay(ctx).show()
        }
    }

    /** Tarjeta compacta tocada: volver a la pantalla nativa (audio al celular, teclado...). */
    fun returnToNativeCall(ctx: Context) {
        userLeftCall = false
        overlay?.hide()
        showNativeCall(ctx)
    }

    private fun showNativeCall(ctx: Context) {
        com.carlight.launcher.util.AppLauncher.launchPackage(ctx.applicationContext, NATIVE_BT)
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
        fgListeners.toList().forEach { it(pkg) }
        val mapPkg = MapsLauncher.installedPackage(ctx)
        when {
            pkg == ctx.packageName ->
                if (cls == HomeActivity::class.java.name) onHomeShown(ctx) else overlay?.hide()   // Ajustes
            pkg == mapPkg -> if (mapFullscreen || inCall || reversing) overlay?.hide() else overlay(ctx).show()
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
