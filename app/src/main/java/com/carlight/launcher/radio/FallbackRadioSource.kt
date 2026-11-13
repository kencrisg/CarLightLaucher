package com.carlight.launcher.radio

import android.content.Context
import com.carlight.launcher.util.AppLauncher

/** Nivel C: sin integración con Topway; todos los controles abren la radio de fábrica. */
class FallbackRadioSource(private val ctx: Context) : RadioSource {
    override fun start(onState: (RadioState) -> Unit) = onState(RadioState(frequency = null))
    override fun stop() {}
    override fun next() = open()
    override fun prev() = open()
    override fun seekUp() = open()
    override fun seekDown() = open()
    override fun open() { AppLauncher.launchPackage(ctx, RadioSource.TW_RADIO_PKG) }
}
