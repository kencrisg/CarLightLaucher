package com.carlight.launcher.prefs

import android.content.Context
import com.carlight.launcher.nav.MapsLauncher
import com.carlight.launcher.ui.ThemeMode

class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("carlight", Context.MODE_PRIVATE)

    /** Paquetes de las ranuras del dock; null = ranura vacía. */
    fun dock(): List<String?> {
        val saved = sp.getString(KEY_DOCK, null) ?: return defaultDock()
        val list = saved.split(',').map { it.ifBlank { null } }
        return List(DOCK_SLOTS) { list.getOrNull(it) }
    }

    fun setDockSlot(index: Int, pkg: String?) {
        val list = dock().toMutableList()
        list[index] = pkg
        sp.edit().putString(KEY_DOCK, list.joinToString(",") { it.orEmpty() }).apply()
    }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.AUTO)
        set(v) = sp.edit().putString(KEY_THEME, v.name).apply()

    private fun defaultDock(): List<String?> = listOf(
        MapsLauncher.PACKAGES.first(),
        "com.tw.radio",
        "com.tw.bt",
        "com.tw.media",
        "com.android.settings",
    )

    companion object {
        const val DOCK_SLOTS = 5
        private const val KEY_DOCK = "dock"
        private const val KEY_THEME = "theme_mode"
    }
}
