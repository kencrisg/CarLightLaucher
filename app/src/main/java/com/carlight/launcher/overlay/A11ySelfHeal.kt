package com.carlight.launcher.overlay

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Al entrar en standby, el servicio de Topway (com.tw.service) hace force-stop de CarLight y de
 * Organic Maps (no están en su lista fija de excepciones). Android, al forzar el cierre, QUITA el
 * servicio de accesibilidad de la lista de activos: sin él no se detecta la app al frente ni se
 * puede centrar el mapa. Al arrancar de nuevo, se vuelve a activar (requiere WRITE_SECURE_SETTINGS).
 */
object A11ySelfHeal {

    private const val TAG = "A11ySelfHeal"
    private var watching = false

    /**
     * Android quita el servicio unos segundos DESPUÉS del force-stop (no al instante): se vigila el
     * ajuste y se restaura cada vez que desaparezca.
     */
    fun start(ctx: Context) {
        ensureEnabled(ctx)
        if (watching) return
        watching = true
        val app = ctx.applicationContext
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ ensureEnabled(app) }, 500)
            }
        }
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        // Respaldo: comprobar también durante el primer minuto tras arrancar.
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        for (sec in listOf(3, 8, 15, 30, 60)) h.postDelayed({ ensureEnabled(app); checkBound(app) }, sec * 1000L)
    }

    private var restarts = 0

    /**
     * Tras un force-stop, Android a veces deja la conexión con el servicio "atascada": el ajuste dice
     * activo pero nunca conecta. Desactivar y reactivar nuestro propio componente lo obliga a
     * reconectar desde cero (la shell no puede hacerlo; la app sí, con sus componentes).
     */
    private fun checkBound(ctx: Context) {
        if (ForegroundWatcher.instance != null || restarts >= 3) return
        val me = ComponentName(ctx, ForegroundWatcher::class.java)
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().contains(me.flattenToString(), ignoreCase = true)
        if (!enabled) return
        restarts++
        Log.i(TAG, "servicio activo pero sin conectar: reinicio del componente ($restarts)")
        val pm = ctx.packageManager
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        runCatching {
            pm.setComponentEnabledSetting(me, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        h.postDelayed({
            runCatching {
                pm.setComponentEnabledSetting(me, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
            h.postDelayed({ ensureEnabled(ctx) }, 800)
        }, 1200)
    }

    fun ensureEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, ForegroundWatcher::class.java).flattenToString()
        val r = ctx.contentResolver
        val current = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (current.split(':').any { it.equals(me, ignoreCase = true) }) return true
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "servicio de accesibilidad desactivado y sin WRITE_SECURE_SETTINGS para reactivarlo")
            return false
        }
        return runCatching {
            val list = (current.split(':').filter { it.isNotBlank() } + me).joinToString(":")
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
            Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "servicio de accesibilidad reactivado")
            true
        }.getOrElse { Log.w(TAG, "no se pudo reactivar", it); false }
    }
}
