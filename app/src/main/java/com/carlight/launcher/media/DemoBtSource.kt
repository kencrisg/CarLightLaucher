package com.carlight.launcher.media

/** Datos simulados para probar la UI en el emulador. */
class DemoBtSource : BtSource {

    private val songs = listOf(
        "See You Again" to "Wiz Khalifa",
        "Blinding Lights" to "The Weeknd",
        "Vivir Mi Vida" to "Marc Anthony",
    )
    private var index = 0
    private var playing = true
    private var listener: ((BtState) -> Unit)? = null

    override fun start(onState: (BtState) -> Unit) {
        listener = onState
        emit()
    }

    override fun stop() { listener = null }
    override fun playPause() { playing = !playing; emit() }
    override fun next() { index = (index + 1) % songs.size; emit() }
    override fun prev() { index = (index - 1 + songs.size) % songs.size; emit() }
    override fun open() {}

    private fun emit() {
        val (title, artist) = songs[index]
        listener?.invoke(BtState(title, artist, playing, demo = true))
    }
}
