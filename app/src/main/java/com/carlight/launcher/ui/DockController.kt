package com.carlight.launcher.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.carlight.launcher.R
import com.carlight.launcher.apps.AppRepository
import com.carlight.launcher.apps.IconPack
import com.carlight.launcher.databinding.ItemDockBinding
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.util.AppLauncher

/**
 * Fila de apps: 3 ranuras configurables + botón fijo "Apps".
 * Toque: abre la app. Pulsación larga: elegir otra app para la ranura.
 */
class DockController(
    private val container: LinearLayout,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val pickApp: (onPicked: (String) -> Unit) -> Unit,
    private val openDrawer: () -> Unit,
    private val onLaunch: () -> Unit,
) {
    private val ctx = container.context
    private val inflater = LayoutInflater.from(ctx)
    private val iconPx = ctx.resources.getDimensionPixelSize(R.dimen.dock_icon)
    private var row: LinearLayout? = null
    private val iconStyle = prefs.iconStyle
    private val accent = Accent.current(prefs)

    fun render() {
        container.removeAllViews()
        row = null
        prefs.dock().take(VISIBLE_SLOTS).forEachIndexed { index, pkg -> addSlot(index, pkg) }
        addItem(ctx.getString(R.string.dock_apps)) { ib ->
            IconPack.bindAppsTile(ib.icon, iconPx, iconStyle, accent)
            ib.root.setOnClickListener { openDrawer() }
        }
    }

    private fun addSlot(index: Int, pkg: String?) {
        val entry = pkg?.let { repo.find(it) }
        val ib = when {
            entry != null -> addItem(shortLabel(entry.pkg, entry.label)) { ib ->
                IconPack.bind(ib.icon, entry.pkg, entry.component, iconPx, iconStyle, accent)
                ib.root.setOnClickListener {
                    onLaunch()
                    AppLauncher.launch(ctx, entry.component)
                }
            }
            // Las apps aún no se cargan: celda vacía para no mostrar "+" un instante.
            !repo.loaded -> addItem("") { }
            else -> addItem(ctx.getString(R.string.dock_add)) { ib ->
                IconPack.bindEmptySlot(ib.icon, iconPx)
                ib.label.alpha = 0.7f
                ib.root.setOnClickListener { pick(index) }
            }
        }
        ib.root.setOnLongClickListener {
            pick(index)
            true
        }
    }

    private fun pick(index: Int) = pickApp { pkg ->
        prefs.setDockSlot(index, pkg)
        render()
    }

    private inline fun addItem(label: String, bind: (ItemDockBinding) -> Unit): ItemDockBinding {
        val r = row?.takeIf { it.childCount < COLUMNS } ?: LinearLayout(ctx).also {
            it.orientation = LinearLayout.HORIZONTAL
            container.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            row = it
        }
        val ib = ItemDockBinding.inflate(inflater, r, false)
        ib.label.text = label
        bind(ib)
        r.addView(ib.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        return ib
    }

    private companion object {
        /** Una sola fila: 3 accesos configurables + "Apps". */
        const val COLUMNS = 4
        const val VISIBLE_SLOTS = 3

        /** Nombres cortos para la cuadrícula (240 dp / 3 columnas: ~9 caracteres caben). */
        private val SHORT = mapOf(
            "app.organicmaps.web" to "Mapa",
            "app.organicmaps" to "Mapa",
            "com.android.settings" to "Ajustes",
            "com.tw.media" to "Música",
            "com.tw.radio" to "Radio",
            "com.carlight.launcher" to "Mi auto",
            "com.tw.bt" to "Bluetooth",
            "com.tw.eq" to "EQ",
            "com.dofun.carsetting" to "Vehículo",
            "com.android.chrome" to "Chrome",
        )

        fun shortLabel(pkg: String, label: String) = SHORT[pkg] ?: label
    }
}
