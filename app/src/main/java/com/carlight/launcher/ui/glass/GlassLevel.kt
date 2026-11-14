package com.carlight.launcher.ui.glass

/**
 * Transparencia del vidrio (Ajustes → Apariencia).
 * @param clarity cuánto se ve el fondo NÍTIDO a través (0 = solo desenfocado).
 * @param tint multiplicador del tinte blanco/oscuro de la tarjeta.
 * @param highlight multiplicador del brillo superior.
 */
enum class GlassLevel(val label: String, val clarity: Float, val tint: Float, val highlight: Float) {
    SOLID("Sólido", 0f, 1.7f, 1f),
    NORMAL("Normal", 0f, 1f, 1f),
    TRANSPARENT("Transparente", 0.35f, 0.55f, 0.8f),
    CRYSTAL("Cristal", 0.7f, 0.3f, 0.6f),
}
