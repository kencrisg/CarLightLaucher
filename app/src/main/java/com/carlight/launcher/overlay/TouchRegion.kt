package com.carlight.launcher.overlay

import android.graphics.Rect
import android.graphics.Region
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.lang.reflect.Proxy

/**
 * Región táctil de una ventana = toda la ventana MENOS el hueco del mapa, para que los toques
 * del hueco lleguen a Organic Maps (debajo). Usa la API oculta
 * ViewTreeObserver.OnComputeInternalInsetsListener (accesible por reflexión en Android 8.1).
 * La ventana debe tener FLAG_NOT_TOUCH_MODAL.
 */
class TouchRegion(private val root: View) {

    private val hole = Rect()
    private var listener: Any? = null
    private val islands = mutableListOf<Rect>()
    private var passthrough = true
    /**
     * Instalar en CADA attach: al quitar y volver a añadir la ventana (ocultar/mostrar el panel)
     * Android crea un ViewTreeObserver nuevo y el listener anterior se pierde. Sin él, la ventana
     * vuelve a ser táctil completa y el mapa deja de recibir los gestos.
     */
    fun install() {
        try {
            val listenerCls = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val infoCls = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setInsets = infoCls.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val regionField = infoCls.getField("touchableRegion")
            val proxy = Proxy.newProxyInstance(listenerCls.classLoader, arrayOf(listenerCls)) { self, method, args ->
                when (method.name) {
                    "onComputeInternalInsets" -> {
                        setInsets.invoke(args!![0], TOUCHABLE_INSETS_REGION)
                        val region = regionField.get(args[0]) as Region
                        region.set(0, 0, root.width, root.height)
                        if (passthrough && !hole.isEmpty) {
                            region.op(hole, Region.Op.DIFFERENCE)
                            islands.forEach { region.op(it, Region.Op.UNION) }
                        }
                        null
                    }
                    "equals" -> self === args!![0]
                    "hashCode" -> System.identityHashCode(self)
                    else -> null
                }
            }
            val vto = root.viewTreeObserver
            listener?.let { old ->
                runCatching {
                    ViewTreeObserver::class.java.getMethod("removeOnComputeInternalInsetsListener", listenerCls)
                        .invoke(vto, old)
                }
            }
            ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerCls)
                .invoke(vto, proxy)
            listener = proxy
            root.requestLayout()
        } catch (e: Exception) {
            Log.w("TouchRegion", "No disponible: el hueco no dejará pasar toques", e)
        }
    }

    /**
     * Hueco en coordenadas de la ventana, e [islas] dentro de él que sí son nuestras
     * (botones flotantes sobre el mapa).
     */
    fun setHole(r: Rect, islands: List<Rect>) {
        if (r == hole && islands == this.islands) return
        hole.set(r)
        this.islands.clear()
        islands.mapTo(this.islands) { Rect(it) }
        root.requestLayout()
    }

    fun setPassthrough(enabled: Boolean) {
        if (enabled == passthrough) return
        passthrough = enabled
        root.requestLayout()
    }

    private companion object {
        const val TOUCHABLE_INSETS_REGION = 3
    }
}
