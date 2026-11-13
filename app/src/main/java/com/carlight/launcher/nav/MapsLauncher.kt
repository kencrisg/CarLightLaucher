package com.carlight.launcher.nav

import android.content.Context
import com.carlight.launcher.util.AppLauncher

object MapsLauncher {

    /** En la radio está instalado como `app.organicmaps.web` (descarga desde la web). */
    val PACKAGES = listOf("app.organicmaps.web", "app.organicmaps", "app.organicmaps.beta")

    fun installedPackage(ctx: Context): String? = PACKAGES.firstOrNull { AppLauncher.isInstalled(ctx, it) }

    fun open(ctx: Context): Boolean {
        val pkg = installedPackage(ctx) ?: return false
        return AppLauncher.launchPackage(ctx, pkg)
    }
}
