package com.carlight.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad mínimo: escucha qué Activity pasa al frente (TYPE_WINDOW_STATE_CHANGED)
 * y, al encender la radio, pulsa "Mi ubicación" en Organic Maps. No lee el contenido de otras apps.
 */
class ForegroundWatcher : AccessibilityService() {

    companion object {
        @Volatile var instance: ForegroundWatcher? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
        DashboardController.watcherConnected = true
        // El servicio mantiene vivo el proceso: aquí se escuchan las luces y se ajusta el brillo.
        com.carlight.launcher.night.NightController.start(this)
        com.carlight.launcher.drive.TripComputer.start(this)
        com.carlight.launcher.calls.CallOverlay.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        DashboardController.onWindowStateChanged(this, pkg, cls)
    }

    override fun onInterrupt() {}

    /**
     * Pulsa el botón "Mi ubicación" de Organic Maps (id `my_position`).
     * @return true si encontró el botón y lo pulsó.
     */
    fun clickMyPosition(): Boolean {
        // Todas las ventanas (nuestro panel flotante puede ser la "activa"), luego la activa.
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()) +
            listOfNotNull(rootInActiveWindow)
        for (root in roots) {
            for (pkg in com.carlight.launcher.nav.MapsLauncher.PACKAGES) {
                val nodes = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/my_position") }.getOrNull()
                val btn = nodes?.firstOrNull() ?: continue
                android.util.Log.i("MapCentering", "botón encontrado en ${root.packageName}")
                return btn.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        android.util.Log.i("MapCentering", "ventanas vistas: " + roots.joinToString { it.packageName?.toString() ?: "?" })
        return false
    }

    override fun onDestroy() {
        instance = null
        DashboardController.watcherConnected = false
        super.onDestroy()
    }
}
