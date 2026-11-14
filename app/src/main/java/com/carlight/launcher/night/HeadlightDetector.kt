package com.carlight.launcher.night

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log

/**
 * Escucha los mensajes del MCU de Topway (`android.tw.john.TWUtil`, /dev/tw) para saber si las
 * luces del auto están encendidas.
 *
 * El mensaje exacto de los faros no está documentado, así que se APRENDE en Ajustes:
 * el usuario marca "luces apagadas" y luego "luces encendidas"; comparamos el último valor de
 * cada mensaje en ambos momentos y nos quedamos con el que cambió (id + campo + valores).
 */
object HeadlightDetector {

    private const val TAG = "Headlights"

    /** Último valor visto por id de mensaje: (arg1, arg2). */
    private val latest = LinkedHashMap<Int, IntArray>()
    private val log = ArrayDeque<String>()
    private var tw: Any? = null
    private var started = false
    private var learned: Learned? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** Señal aprendida: mensaje [id], campo [field] (1 = arg1, 2 = arg2), valor con luces encendidas. */
    data class Learned(val id: Int, val field: Int, val onValue: Int) {
        fun encode() = "$id:$field:$onValue"
        companion object {
            fun decode(s: String?): Learned? = s?.split(':')?.takeIf { it.size == 3 }
                ?.let { runCatching { Learned(it[0].toInt(), it[1].toInt(), it[2].toInt()) }.getOrNull() }
        }
    }

    /** null = desconocido (sin señal aprendida o sin mensajes aún). */
    @Volatile var lightsOn: Boolean? = null
        private set

    val available get() = tw != null
    val recentLog: List<String> get() = log.toList()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun setLearned(l: Learned?) {
        learned = l
        recompute()
    }

    fun snapshot(): Map<Int, IntArray> = latest.mapValues { it.value.copyOf() }

    /** Compara dos instantáneas (apagadas → encendidas) y devuelve la señal que cambió. */
    fun learn(off: Map<Int, IntArray>, on: Map<Int, IntArray>): Learned? {
        for ((id, v) in on) {
            val before = off[id] ?: continue
            if (before[0] != v[0]) return Learned(id, 1, v[0])
            if (before[1] != v[1]) return Learned(id, 2, v[1])
        }
        // Mensaje que solo apareció al encender las luces
        return on.keys.firstOrNull { it !in off }?.let { Learned(it, 1, on.getValue(it)[0]) }
    }

    fun start(ctx: Context) {
        if (started) return
        started = true
        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) = onMcu(msg.what and 0xFFFF, msg.arg1, msg.arg2)
        }
        try {
            val cls = Class.forName("android.tw.john.TWUtil")
            val obj = cls.getConstructor().newInstance()
            val open = cls.getMethod("open", ShortArray::class.java)
            // Rango amplio de mensajes de estado del MCU; si el driver lo rechaza, uno más acotado.
            val r = (open.invoke(obj, candidates(wide = true)) as Int).takeIf { it == 0 }
                ?: open.invoke(obj, candidates(wide = false)) as Int
            if (r != 0) {
                Log.w(TAG, "TWUtil.open = $r")
                return
            }
            cls.getMethod("start").invoke(obj)
            cls.getMethod("addHandler", String::class.java, Handler::class.java).invoke(obj, "CarLightLights", handler)
            tw = obj
            Log.i(TAG, "escuchando el MCU")
        } catch (e: Throwable) {
            Log.w(TAG, "TWUtil no disponible", e)
        }
    }

    private fun candidates(wide: Boolean): ShortArray {
        val ids = mutableListOf<Int>()
        if (wide) {
            ids += 0x0100..0x03FF
            ids += 0x0500..0x05FF
        } else {
            ids += listOf(0x0106, 0x010A, 0x0112, 0x0201, 0x0203, 0x020B, 0x0301, 0x0302,
                0x0501, 0x0505, 0x0507, 0x0508, 0x0509)
        }
        ids += 0xFF00..0xFF1F
        return ShortArray(ids.size) { ids[it].toShort() }
    }

    private fun onMcu(id: Int, a1: Int, a2: Int) {
        val prev = latest[id]
        if (prev != null && prev[0] == a1 && prev[1] == a2) return
        latest[id] = intArrayOf(a1, a2)
        log.addFirst("0x%04X  arg1=%d  arg2=%d".format(id, a1, a2))
        while (log.size > 30) log.removeLast()
        recompute()
    }

    private fun recompute() {
        val l = learned
        val v = l?.let { latest[it.id] }
        val on = if (l == null || v == null) null else (if (l.field == 1) v[0] else v[1]) == l.onValue
        if (on != lightsOn) {
            lightsOn = on
            listeners.toList().forEach { it() }
        }
    }
}
