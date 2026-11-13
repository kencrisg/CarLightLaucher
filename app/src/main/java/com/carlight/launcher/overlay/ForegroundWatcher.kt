package com.carlight.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad mínimo: solo escucha qué Activity pasa al frente
 * (TYPE_WINDOW_STATE_CHANGED). No lee contenido de pantallas.
 */
class ForegroundWatcher : AccessibilityService() {

    override fun onServiceConnected() {
        DashboardController.watcherConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        DashboardController.onWindowStateChanged(this, pkg, cls)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        DashboardController.watcherConnected = false
        super.onDestroy()
    }
}
