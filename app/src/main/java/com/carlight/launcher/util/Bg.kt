package com.carlight.launcher.util

import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Un solo hilo de fondo para todo el launcher (carga de apps e íconos). */
object Bg {
    val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "carlight-bg")
    }
    val main = Handler(Looper.getMainLooper())
}
