package com.carlight.launcher.topway

import android.util.Log
import com.carlight.launcher.util.Bg

/**
 * Cambia la FUENTE de audio del MCU de Topway sin abrir las apps de fábrica, enviando lo mismo que
 * ellas envían en onResume (extraído del bytecode):
 *    requestService:  TWUtil.write(0x9E00, fuente)
 *    requestSource:   TWUtil.write(0x9E11, 0xC0, fuente)
 * Fuentes: RADIO = 10 (com.tw.radio), BT_MUSIC = 8 (com.tw.bt).
 * Sin esto, al reproducir BT desde el panel con la radio sonando se oyen las dos.
 *
 * `android.tw.john.TWUtil` es una clase oculta del framework de Topway (/dev/tw, 0666): se usa por
 * reflexión.
 */
object TwAudioSource {

    const val RADIO = 10
    const val BT_MUSIC = 8

    private const val TAG = "TwAudioSource"
    private const val MSG_SERVICE = 0x9E00
    private const val MSG_SOURCE = 0x9E11
    private const val SOURCE_REQUEST = 0xC0

    private var tw: Any? = null
    private var write2: java.lang.reflect.Method? = null
    private var write3: java.lang.reflect.Method? = null
    private var failed = false

    /**
     * Pide al MCU cambiar a [source]. Asíncrono; devuelve false si TWUtil no está disponible.
     * Se envía SIEMPRE (no se cachea): la fuente también cambia por fuera del panel (app BT,
     * teléfono, volante) y un caché desactualizado deja sonando las dos.
     */
    fun request(source: Int): Boolean {
        if (failed) return false
        Bg.io.execute {
            if (!ensureOpen()) return@execute
            try {
                write2!!.invoke(tw, MSG_SERVICE, source)
                write3!!.invoke(tw, MSG_SOURCE, SOURCE_REQUEST, source)
                Log.i(TAG, "requestSource($source)")
            } catch (e: Exception) {
                Log.w(TAG, "write falló", e)
            }
        }
        return true
    }

    private fun ensureOpen(): Boolean {
        if (tw != null) return true
        return try {
            val cls = Class.forName("android.tw.john.TWUtil")
            val obj = cls.getConstructor().newInstance()
            val ids = shortArrayOf(MSG_SERVICE.toShort(), MSG_SOURCE.toShort())
            val r = cls.getMethod("open", ShortArray::class.java).invoke(obj, ids) as Int
            if (r != 0) {
                Log.w(TAG, "TWUtil.open devolvió $r (¿SELinux?)")
                failed = true
                return false
            }
            write2 = cls.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            write3 = cls.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            tw = obj
            true
        } catch (e: Throwable) {
            Log.w(TAG, "TWUtil no disponible", e)
            failed = true
            false
        }
    }
}
