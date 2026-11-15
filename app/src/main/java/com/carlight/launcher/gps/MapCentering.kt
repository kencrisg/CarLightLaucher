package com.carlight.launcher.gps

import android.util.Log
import com.carlight.launcher.overlay.ForegroundWatcher
import com.carlight.launcher.util.Bg
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Organic Maps recuerda su último modo de ubicación. Si una vez arrancó sin GPS guarda
 * "NotFollowNoPosition" y en los siguientes arranques ni busca la posición: queda en el mapamundi.
 * Tras abrirlo con GPS listo, si su modo es de "no seguir", pulsamos su botón "Mi ubicación".
 * (Si ya sigue la posición NO se pulsa: pasaría al modo brújula y el mapa giraría.)
 */
object MapCentering {

    private const val TAG = "MapCentering"
    private val MODE = Regex("""LastLocationStateMode\s*:\s*(\w+)""")
    private val SWITCH = Regex("""(?:SwitchMyPositionNextMode|SetMyPositionMode|location.?mode)[^A-Za-z]*(\w+)""", RegexOption.IGNORE_CASE)

    /** Llamar justo después de traer Organic Maps al frente. */
    fun afterMapOpened() {
        Bg.main.postDelayed({ attempt(1) }, 4000)
    }

    /**
     * Toques necesarios para llegar a "seguir mi posición" (con zoom):
     * NotFollowNoPosition → 1.º toque: NotFollow (solo muestra la flecha) → 2.º toque: Follow.
     * NotFollow → 1 toque. Follow / FollowAndRotate / PendingPosition → ninguno (un toque giraría el mapa).
     */
    private fun attempt(n: Int) {
        Bg.io.execute {
            val mode = lastMode()
            // Al arrancar, Organic Maps NO mueve la cámara aunque restaure "seguir": hace falta un toque.
            // Cada toque además alterna el modo; se elige el número que termina en "seguir, norte arriba".
            val clicks = when (mode) {
                "NotFollowNoPosition" -> 2
                "NotFollow" -> 1
                "Follow" -> 2           // Follow → FollowAndRotate → Follow (centrado)
                "FollowAndRotate" -> 1  // → Follow (centrado)
                else -> 0               // PendingPosition: ya busca solo
            }
            Log.i(TAG, "modo de ubicación de Organic Maps: $mode → $clicks toque(s) (intento $n)")
            if (clicks == 0) return@execute
            Bg.main.post { click(clicks, n) }
        }
    }

    private fun click(remaining: Int, n: Int) {
        if (ForegroundWatcher.instance == null && n < 8) {
            Log.i(TAG, "esperando el servicio de accesibilidad…")
            Bg.main.postDelayed({ attempt(n + 1) }, 2000)
            return
        }
        val ok = ForegroundWatcher.instance?.clickMyPosition() == true
        Log.i(TAG, if (ok) "pulsado «Mi ubicación» (faltan ${remaining - 1})" else "botón no encontrado")
        when {
            !ok && n < 8 -> Bg.main.postDelayed({ attempt(n + 1) }, 3000)
            ok && remaining > 1 -> Bg.main.postDelayed({ click(remaining - 1, n) }, 2500)
        }
    }

    /** Último modo que Organic Maps escribió en el log (requiere READ_LOGS). */
    private fun lastMode(): String? = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "brief", "OMcore:I", "*:S").redirectErrorStream(true).start()
        var mode: String? = null
        BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
            lines.forEach { l -> MODE.find(l)?.let { mode = it.groupValues[1] } }
        }
        mode
    }.getOrNull()
}
