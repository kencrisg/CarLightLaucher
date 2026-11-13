package com.carlight.launcher.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.carlight.launcher.R
import com.carlight.launcher.apps.AppRepository
import com.carlight.launcher.apps.IconCache
import com.carlight.launcher.databinding.ItemAppBinding
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.util.AppLauncher

/**
 * Dock de 5 ranuras configurables + botón fijo "Apps".
 * Toque: abre la app. Pulsación larga: elegir otra app para la ranura.
 */
class DockController(
    private val container: LinearLayout,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val pickApp: (onPicked: (String) -> Unit) -> Unit,
    private val openDrawer: () -> Unit,
) {
    private val ctx = container.context
    private val inflater = LayoutInflater.from(ctx)
    private val iconPx = ctx.resources.getDimensionPixelSize(R.dimen.app_icon)

    fun render() {
        container.removeAllViews()
        prefs.dock().forEachIndexed { index, pkg -> addSlot(index, pkg) }
        addItem(ctx.getString(R.string.dock_apps)) { ib ->
            ib.icon.setBackgroundResource(R.drawable.bg_tile_apps)
            ib.icon.scaleType = android.widget.ImageView.ScaleType.CENTER
            ib.icon.setImageResource(R.drawable.ic_apps)
            ib.root.setOnClickListener { openDrawer() }
        }
    }

    private fun addSlot(index: Int, pkg: String?) {
        val entry = pkg?.let { repo.find(it) }
        when {
            entry != null -> addItem(entry.label) { ib ->
                IconCache.bind(ib.icon, entry.component, iconPx)
                ib.root.setOnClickListener { AppLauncher.launch(ctx, entry.component) }
            }
            // Las apps aún no se cargan: ranura vacía para no mostrar "+" un instante.
            !repo.loaded -> addItem("") { }
            else -> addItem(ctx.getString(R.string.dock_add)) { ib ->
                ib.icon.setImageResource(R.drawable.ic_add)
                ib.icon.alpha = 0.6f
                ib.root.setOnClickListener { pick(index) }
            }
        }
        container.getChildAt(container.childCount - 1).setOnLongClickListener {
            pick(index)
            true
        }
    }

    private fun pick(index: Int) = pickApp { pkg ->
        prefs.setDockSlot(index, pkg)
        render()
    }

    private inline fun addItem(label: String, bind: (ItemAppBinding) -> Unit) {
        val ib = ItemAppBinding.inflate(inflater, container, false)
        ib.label.text = label
        bind(ib)
        container.addView(ib.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
    }
}
