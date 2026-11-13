package com.carlight.launcher.radio

/** Datos simulados para probar la UI en el emulador. */
class DemoRadioSource : RadioSource {

    private val presets = intArrayOf(881, 915, 939, 1015, 1035, 1077)
    private var preset = 4
    private var freq = presets[preset - 1]
    private var listener: ((RadioState) -> Unit)? = null

    override fun start(onState: (RadioState) -> Unit) {
        listener = onState
        emit()
    }

    override fun stop() { listener = null }

    override fun next() = selectPreset(preset % presets.size + 1)
    override fun prev() = selectPreset((preset - 2 + presets.size) % presets.size + 1)
    override fun seekUp() = tune(if (freq >= MAX) MIN else freq + 1)
    override fun seekDown() = tune(if (freq <= MIN) MAX else freq - 1)
    override fun open() {}

    private fun selectPreset(p: Int) {
        preset = p
        freq = presets[p - 1]
        emit()
    }

    private fun tune(f: Int) {
        freq = f
        preset = presets.indexOf(f) + 1
        emit()
    }

    private fun emit() = listener?.invoke(RadioState(freq, preset, demo = true))

    private companion object {
        const val MIN = 875
        const val MAX = 1080
    }
}
