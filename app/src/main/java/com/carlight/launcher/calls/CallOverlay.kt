package com.carlight.launcher.calls

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.carlight.launcher.R
import com.carlight.launcher.overlay.DashboardController
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.ui.ThemeController

/**
 * Tarjeta de llamada estilo Android Auto, flotando arriba al centro, sobre el mapa.
 * Si la app BT de fábrica toma la pantalla, se devuelve el mapa al frente (ver DashboardController).
 */
object CallOverlay {

    private var view: View? = null
    private var timer: TextView? = null
    private var app: Context? = null

    private val tick = object : Runnable {
        override fun run() {
            val c = CallMonitor.current ?: return
            val t = timer ?: return
            if (c.state == CallMonitor.State.ACTIVE && c.activeSince > 0) {
                val s = (System.currentTimeMillis() - c.activeSince) / 1000
                t.text = "%d:%02d".format(s / 60, s % 60)
            }
            t.postDelayed(this, 1000)
        }
    }

    fun start(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        CallMonitor.start(ctx)
        CallMonitor.addListener { show(it) }
    }

    private fun show(call: CallMonitor.Call?) {
        val a = app ?: return
        val wm = a.getSystemService(WindowManager::class.java)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
        timer = null
        if (call == null || !Prefs(a).callsOverMap || !Settings.canDrawOverlays(a)) {
            DashboardController.onCallEnded()
            return
        }
        DashboardController.onCallStarted(a)
        val v = build(a, call)
        wm.addView(v, WindowManager.LayoutParams(
            (520 * a.resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (40 * a.resources.displayMetrics.density).toInt()
            title = "CarLightCall"
        })
        view = v
        timer?.post(tick)
    }

    private fun build(base: Context, call: CallMonitor.Call): View {
        val ctx = ContextThemeWrapper(
            base.createConfigurationContext(ThemeController.overrideFor(base, ThemeController.isNight(base))),
            R.style.Theme_CarLight,
        )
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(18), px(14), px(14), px(14))
            background = GradientDrawable().apply {
                cornerRadius = 28 * d
                setColor(ctx.getColor(R.color.call_bg))
                setStroke(px(1), ctx.getColor(R.color.glass_stroke))
            }
            elevation = 12 * d
        }
        root.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.glyph_phone)
            setColorFilter(0xFFFFFFFF.toInt())
            setPadding(px(12), px(12), px(12), px(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2563EB.toInt(), 0xFF60A5FA.toInt())).apply {
                shape = GradientDrawable.OVAL
            }
        }, LinearLayout.LayoutParams(px(52), px(52)))

        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(ctx).apply {
            text = call.name ?: call.number.ifBlank { ctx.getString(R.string.call_unknown) }
            textSize = 20f
            maxLines = 1
            setTextColor(ctx.getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        val sub = TextView(ctx).apply {
            text = ctx.getString(when (call.state) {
                CallMonitor.State.INCOMING -> R.string.call_incoming
                CallMonitor.State.DIALING -> R.string.call_dialing
                else -> R.string.call_active
            })
            textSize = 15f
            setTextColor(ctx.getColor(R.color.text_secondary))
        }
        texts.addView(sub)
        if (call.state == CallMonitor.State.ACTIVE) timer = sub
        root.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(14) })

        fun action(icon: Int, color: Int, onClick: () -> Unit) = ImageButton(ctx).apply {
            setImageResource(icon)
            setColorFilter(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            setOnClickListener { onClick() }
        }
        if (call.state == CallMonitor.State.INCOMING) {
            root.addView(action(R.drawable.glyph_phone, 0xFF16A34A.toInt()) { CallMonitor.accept() },
                LinearLayout.LayoutParams(px(60), px(60)).apply { marginEnd = px(12) })
        }
        root.addView(action(R.drawable.ic_call_end, 0xFFDC2626.toInt()) { CallMonitor.hangUp() },
            LinearLayout.LayoutParams(px(60), px(60)))
        return root
    }

    /** Para la marca de tiempo de la llamada. */
    @Suppress("unused") private fun now() = SystemClock.elapsedRealtime()
}
