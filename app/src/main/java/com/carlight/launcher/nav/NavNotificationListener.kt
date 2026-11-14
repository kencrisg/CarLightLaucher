package com.carlight.launcher.nav

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.carlight.launcher.util.Bg

/**
 * Lee la notificación de navegación de Organic Maps (título = distancia a la maniobra,
 * texto = calle, icono grande = flecha del giro) para mostrarla en el panel.
 * Se habilita por adb: cmd notification allow_listener com.carlight.launcher/.nav.NavNotificationListener
 */
class NavNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MapsLauncher.PACKAGES || !sbn.isOngoing) return
        val n = sbn.notification
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val icon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96) }.getOrNull()
        NavState.post(NavState.Maneuver(title, text, icon, sbn.key))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == NavState.current?.key) NavState.post(null)
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(b))
        return b
    }
}

/** Próxima maniobra actual (null = sin navegación activa). */
object NavState {
    data class Maneuver(val distance: String, val street: String, val arrow: Bitmap?, val key: String)

    @Volatile var current: Maneuver? = null
        private set
    private val listeners = mutableListOf<(Maneuver?) -> Unit>()

    fun addListener(l: (Maneuver?) -> Unit) { listeners += l }
    fun removeListener(l: (Maneuver?) -> Unit) { listeners -= l }

    fun post(m: Maneuver?) = Bg.main.post {
        current = m
        listeners.toList().forEach { it(m) }
    }
}
