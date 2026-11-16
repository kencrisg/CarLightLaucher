package com.carlight.launcher.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.carlight.launcher.util.Bg
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * La app de radio de Topway no publica la frecuencia (ni broadcast ni archivo legible), pero
 * registra cada sintonía en el log del sistema:
 *    D/FMLIB_CORE( 2230): FMR_tune, [freq=1015] [ret=0]
 *    E/RadioActivity( 2230): [181]--------->4        (presintonía activa)
 * Con READ_LOGS (adb shell pm grant com.carlight.launcher android.permission.READ_LOGS)
 * seguimos esas líneas con un `logcat` filtrado por etiqueta (muy poco volumen).
 */
class LogcatRadioWatcher(private val ctx: Context) {

    fun interface Listener {
        fun onRadio(freq: Int?, preset: Int?)
    }

    /** Estado del proceso de la app de radio según el log del sistema (null = desconocido). */
    @Volatile var radioAlive: Boolean? = null
        private set

    private var process: Process? = null
    private var thread: Thread? = null

    fun hasPermission() =
        ctx.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun start(listener: Listener) {
        if (!hasPermission() || thread != null) return
        val t = Thread({ run(listener) }, "carlight-radio-log")
        thread = t
        t.start()
    }

    fun stop() {
        process?.destroy()
        process = null
        thread?.interrupt()
        thread = null
    }

    private fun run(listener: Listener) {
        try {
            // 1) Último estado conocido en el buffer actual.
            scan(exec("logcat", "-d", "-v", "brief", *FILTER)) { f, p -> Bg.main.post { listener.onRadio(f, p) } }
            // 2) En vivo.
            val proc = exec("logcat", "-T", "1", "-v", "brief", *FILTER)
            process = proc
            scan(proc) { f, p -> Bg.main.post { listener.onRadio(f, p) } }
        } catch (e: Exception) {
            if (thread != null) Log.w(TAG, "logcat terminó", e)
        }
    }

    private fun exec(vararg cmd: String): Process = ProcessBuilder(*cmd).redirectErrorStream(true).start()

    private inline fun scan(p: Process, emit: (Int?, Int?) -> Unit) {
        BufferedReader(InputStreamReader(p.inputStream)).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                when {
                    PROC_START.containsMatchIn(line) -> radioAlive = true
                    PROC_DEAD.containsMatchIn(line) -> radioAlive = false
                }
                TUNE.find(line)?.let { radioAlive = true; emit(it.groupValues[1].toInt(), null) }
                PRESET.find(line)?.let { emit(null, it.groupValues[1].toInt()) }
            }
        }
    }

    private companion object {
        const val TAG = "RadioLog"
        val FILTER = arrayOf("FMLIB_CORE:D", "RadioActivity:E", "ActivityManager:I", "*:S")
        val PROC_START = Regex("""Start proc \d+:com\.tw\.radio/""")
        val PROC_DEAD = Regex("""Force stopping com\.tw\.radio |Killing \d+:com\.tw\.radio/|Process com\.tw\.radio \(pid""")
        val TUNE = Regex("""FMR_tune, \[freq=(\d{3,4})]""")
        val PRESET = Regex("""\[181]-+>(\d+)""")
    }
}
