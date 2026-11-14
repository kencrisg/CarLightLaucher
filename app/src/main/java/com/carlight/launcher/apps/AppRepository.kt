package com.carlight.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.carlight.launcher.util.Bg
import java.text.Collator

data class AppEntry(val label: String, val component: ComponentName) {
    val pkg: String get() = component.packageName
}

/** Lista de apps lanzables. Se carga en segundo plano y se recarga al instalar o desinstalar. */
class AppRepository private constructor(private val ctx: Context) {

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set

    var loaded = false
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun find(pkg: String): AppEntry? = apps.firstOrNull { it.pkg == pkg }

    fun reload() {
        Bg.io.execute {
            val list = query()
            Bg.main.post {
                apps = list
                loaded = true
                listeners.toList().forEach { it() }
            }
        }
    }

    private fun query(): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            // Del propio launcher solo se listan sus "apps" (Mi auto), no la pantalla principal.
            .filter { it.activityInfo.packageName != ctx.packageName || !it.activityInfo.name.endsWith(".HomeActivity") }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString().trim(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    companion object {
        @Volatile private var instance: AppRepository? = null
        fun get(ctx: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(ctx.applicationContext).also { instance = it }
            }
    }
}
