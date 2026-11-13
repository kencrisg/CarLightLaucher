package com.carlight.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.widget.ImageView
import com.carlight.launcher.util.Bg

/**
 * Íconos escalados a su tamaño exacto en pantalla y guardados en una caché LRU de 3 MB.
 * Un ícono de 56 px en ARGB_8888 pesa ~12 KB, así que caben ~250.
 */
object IconCache {

    private val cache = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun bind(view: ImageView, component: ComponentName, sizePx: Int) {
        val key = component.flattenToShortString()
        view.tag = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        val appCtx = view.context.applicationContext
        Bg.io.execute {
            val bmp = load(appCtx, component, sizePx) ?: return@execute
            cache.put(key, bmp)
            Bg.main.post { if (view.tag == key) view.setImageBitmap(bmp) }
        }
    }

    fun clear() = cache.evictAll()

    private fun load(ctx: Context, component: ComponentName, size: Int): Bitmap? = try {
        val d = ctx.packageManager.getActivityIcon(component)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        bmp
    } catch (_: Exception) {
        null
    }
}
