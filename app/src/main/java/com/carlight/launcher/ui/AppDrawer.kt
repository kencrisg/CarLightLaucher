package com.carlight.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.carlight.launcher.R
import com.carlight.launcher.apps.AppEntry
import com.carlight.launcher.apps.AppRepository
import com.carlight.launcher.apps.IconCache
import com.carlight.launcher.databinding.ActivityHomeBinding
import com.carlight.launcher.databinding.ItemAppBinding

/** Cajón de apps superpuesto. Modo normal (abre la app) o selector (para el dock). */
class AppDrawer(
    private val b: ActivityHomeBinding,
    private val repo: AppRepository,
    private val onLaunch: (AppEntry) -> Unit,
    private val onVisibilityChanged: (Boolean) -> Unit,
) {
    private var pickCallback: ((AppEntry) -> Unit)? = null
    private val iconPx = b.root.resources.getDimensionPixelSize(R.dimen.app_icon)
    private val adapter = Adapter()

    val isOpen: Boolean get() = b.drawer.visibility == View.VISIBLE

    init {
        b.drawerGrid.adapter = adapter
        b.drawerGrid.setOnItemClickListener { _, _, pos, _ ->
            val entry = adapter.getItem(pos)
            val cb = pickCallback
            hide()
            if (cb != null) cb(entry) else onLaunch(entry)
        }
        b.drawerClose.setOnClickListener { hide() }
    }

    fun showAll() = show(R.string.drawer_title, null)

    fun showPicker(cb: (AppEntry) -> Unit) = show(R.string.drawer_pick_title, cb)

    fun hide() {
        pickCallback = null
        b.drawer.visibility = View.GONE
        onVisibilityChanged(false)
    }

    fun refresh() = adapter.notifyDataSetChanged()

    private fun show(title: Int, cb: ((AppEntry) -> Unit)?) {
        pickCallback = cb
        b.drawerTitle.setText(title)
        b.drawerGrid.setSelection(0)
        b.drawer.alpha = 0f
        b.drawer.visibility = View.VISIBLE
        onVisibilityChanged(true)
        b.drawer.animate().alpha(1f).setDuration(140).start()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = repo.apps.size
        override fun getItem(position: Int) = repo.apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ib = convertView?.tag as? ItemAppBinding
                ?: ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    .also { it.root.tag = it }
            val entry = getItem(position)
            ib.label.text = entry.label
            IconCache.bind(ib.icon, entry.component, iconPx)
            return ib.root
        }
    }
}
