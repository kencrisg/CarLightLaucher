package com.carlight.launcher.tools

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.carlight.launcher.R
import com.carlight.launcher.prefs.Prefs
import com.carlight.launcher.ui.Accent
import com.carlight.launcher.ui.ThemeController
import com.carlight.launcher.ui.glass.GlassBackground
import com.carlight.launcher.ui.glass.WallpaperHoleDrawable
import com.carlight.launcher.ui.glass.WallpaperRenderer
import com.carlight.launcher.util.Bg
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Prueba del micrófono de la radio: medidor de nivel en vivo, elección de la fuente
 * (frontal, trasero, modo llamada, modo voz), grabar 5 s y reproducir con ganancia por software.
 * La ganancia aplica a la prueba; la de las llamadas la maneja el chip, fuera de Android.
 */
class MicTestActivity : Activity() {

    private class Source(val label: String, val id: Int)

    private val sources = listOf(
        Source("Frontal", MediaRecorder.AudioSource.MIC),
        Source("Trasero", MediaRecorder.AudioSource.CAMCORDER),
        Source("Llamada", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
        Source("Voz", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    )
    private val gains = listOf(1, 2, 4, 8, 16)

    /** Se recuerdan entre aperturas. */
    private val sp by lazy { getSharedPreferences("mic_test", Context.MODE_PRIVATE) }
    private var source: Int
        get() = sp.getInt("source", 0).coerceIn(0, sources.size - 1)
        set(v) = sp.edit().putInt("source", v).apply()
    private var gain: Int
        get() = sp.getInt("gain", 2).takeIf { it in gains } ?: 2
        set(v) = sp.edit().putInt("gain", v).apply()
    private var filters: Boolean
        get() = sp.getBoolean("filters", false)
        set(v) = sp.edit().putBoolean("filters", v).apply()
    private var night = false

    private lateinit var prefs: Prefs
    private lateinit var accent: Accent
    private lateinit var content: LinearLayout
    private lateinit var meter: LevelMeter
    private lateinit var levelText: TextView
    private lateinit var status: TextView
    private lateinit var recordBtn: TextView
    private lateinit var playBtn: TextView
    private var glass: GlassBackground? = null
    private val dp by lazy { resources.displayMetrics.density }

    @Volatile private var running = false
    @Volatile private var recording = false
    private var thread: Thread? = null
    private var recorded: ShortArray? = null
    private var maxSeen = 0f

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        night = ThemeController.isNight(newBase)
        applyOverrideConfiguration(ThemeController.overrideFor(newBase, night))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Accent.current(prefs)
        val palette = WallpaperRenderer.style(prefs.background).palette(night)
        val wallpaper = WallpaperHoleDrawable(resources, palette.gradient)
        val root = android.widget.FrameLayout(this).apply { background = wallpaper; fitsSystemWindows = true }
        val card = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(28), px(20), px(28), px(24))
        }
        card.addView(content)
        root.addView(card, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(px(10), px(10), px(10), px(10))
        })
        setContentView(root)
        glass = GlassBackground(this, night, prefs.skyline, prefs.background, prefs.glassLevel, null) { wallpaper.setBitmap(it) }
            .also { it.attach(card, 24f) }
        root.viewTreeObserver.addOnGlobalLayoutListener { glass?.onLayout(root) }
        build()
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMonitor()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startMonitor()
        else status.setText(R.string.mic_no_permission)
    }

    override fun onStop() {
        stopMonitor()
        super.onStop()
    }

    override fun onDestroy() {
        glass?.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun build() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            setText(R.string.mic_title)
            textSize = 26f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })

        // ---- Prueba ----
        section(R.string.mic_test)
        hint(getString(R.string.mic_hint))

        label(R.string.mic_source)
        segmented(sources.map { it.label }, source) { source = it; restartMonitor() }

        label(R.string.mic_level)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        meter = LevelMeter(this).also { it.colors = intArrayOf(accent.start, accent.end, 0xFFEF4444.toInt()) }
        row.addView(meter, LinearLayout.LayoutParams(0, px(34), 1f))
        levelText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(levelText, LinearLayout.LayoutParams(px(150), ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        status = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.text_secondary)) }
        content.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8) })

        label(R.string.mic_gain)
        segmented(gains.map { "${it}×" }, gains.indexOf(gain)) { gain = gains[it]; build(); restartMonitor() }

        label(R.string.mic_filters)
        segmented(listOf(getString(R.string.set_off), getString(R.string.set_on)), if (filters) 1 else 0) { filters = it == 1; build(); restartMonitor() }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recordBtn = button(getString(R.string.mic_record), accent = true) { record() }
        playBtn = button(getString(R.string.mic_play), accent = false) { play() }
        playBtn.alpha = if (recorded == null) 0.5f else 1f
        buttons.addView(recordBtn, LinearLayout.LayoutParams(0, px(56), 1f).apply { marginEnd = px(10) })
        buttons.addView(playBtn, LinearLayout.LayoutParams(0, px(56), 1f))
        content.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(22) })
        content.addView(button(getString(R.string.settings_done), accent = false) { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)).apply { topMargin = px(10) })
    }

    private fun section(text: Int) = content.addView(TextView(this).apply {
        setText(text)
        textSize = 20f
        setTextColor(getColor(R.color.text_primary))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(22) })

    private fun label(text: Int) = content.addView(TextView(this).apply {
        setText(text)
        textSize = 13f
        isAllCaps = true
        letterSpacing = 0.1f
        setTextColor(getColor(R.color.text_secondary))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(18); bottomMargin = px(8) })

    private fun hint(text: String) = content.addView(TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(getColor(R.color.text_secondary))
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(4) })

    private fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = getDrawable(R.drawable.bg_segment)
            setPadding(px(4), px(4), px(4), px(4))
        }
        options.forEachIndexed { i, t ->
            group.addView(TextView(this).apply {
                text = t
                gravity = Gravity.CENTER
                textSize = 16f
                if (i == selected) {
                    background = accent.pill(13 * dp)
                    setTextColor(0xFFFFFFFF.toInt())
                } else {
                    background = getDrawable(R.drawable.bg_nav_item)
                    setTextColor(getColor(R.color.text_primary))
                }
                setOnClickListener { if (i != selected) { onSelect(i); if (options === sources.map { it.label }) Unit } }
            }, LinearLayout.LayoutParams(0, px(50), 1f))
        }
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Selección visual inmediata para la fuente (no reconstruye toda la pantalla)
        if (options.size == sources.size && options.first() == sources.first().label) {
            for (i in 0 until group.childCount) group.getChildAt(i).setOnClickListener {
                if (i != source) { source = i; build(); restartMonitor() }
            }
        }
    }

    private fun button(text: String, accent: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 17f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (accent) {
            background = this@MicTestActivity.accent.pill(16 * dp)
            setTextColor(0xFFFFFFFF.toInt())
        } else {
            background = getDrawable(R.drawable.bg_segment)
            setTextColor(getColor(R.color.text_primary))
        }
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- audio

    private val rate = 16000

    private fun newRecord(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val r = runCatching {
            AudioRecord(sources[source].id, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, rate / 5 * 2))
        }.getOrNull() ?: return null
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return null }
        if (filters) {
            if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(r.audioSessionId)?.enabled = true }
            if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(r.audioSessionId)?.enabled = true }
        }
        return r
    }

    private fun startMonitor() {
        if (running || !::meter.isInitialized) return
        running = true
        maxSeen = 0f
        thread = Thread({
            val rec = newRecord()
            if (rec == null) {
                Bg.main.post { status.setText(R.string.mic_unavailable) }
                running = false
                return@Thread
            }
            val buf = ShortArray(rate / 20)   // 50 ms
            val capture = ArrayList<Short>()
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                var peak = 0
                for (i in 0 until n) {
                    val v = (buf[i] * gain).coerceIn(-32768, 32767)
                    if (recording) capture += v.toShort()
                    sum += v.toDouble() * v
                    peak = max(peak, abs(v))
                }
                val rms = sqrt(sum / n)
                val db = if (rms < 1) -90.0 else 20 * log10(rms / 32768.0)
                val level = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)
                if (recording && capture.size >= rate * 5) {
                    recorded = capture.toShortArray()
                    capture.clear()
                    recording = false
                    Bg.main.post { onRecorded() }
                }
                Bg.main.post { showLevel(level, db, peak >= 32700) }
            }
            rec.stop()
            rec.release()
        }, "carlight-mic").also { it.start() }
    }

    private fun stopMonitor() {
        running = false
        recording = false
        thread?.join(500)
        thread = null
    }

    private fun restartMonitor() {
        stopMonitor()
        startMonitor()
    }

    private fun showLevel(level: Float, db: Double, clip: Boolean) {
        meter.level = level
        maxSeen = max(maxSeen, level)
        meter.peak = maxSeen
        levelText.text = if (db <= -89) "— dB" else "%.0f dB".format(db)
        if (recording) return
        status.setText(when {
            clip -> R.string.mic_clip
            maxSeen < 0.15f -> R.string.mic_silent
            maxSeen < 0.45f -> R.string.mic_low
            else -> R.string.mic_ok
        })
    }

    private fun record() {
        if (!running) return
        recording = true
        status.setText(R.string.mic_recording)
        recordBtn.alpha = 0.5f
    }

    private fun onRecorded() {
        recordBtn.alpha = 1f
        playBtn.alpha = 1f
        status.setText(R.string.mic_recorded)
    }

    private fun play() {
        val data = recorded ?: return
        Thread {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            Thread.sleep(data.size * 1000L / rate + 200)
            track.release()
        }.start()
        getSystemService(AudioManager::class.java)  // reproducción por la salida multimedia (parlantes)
    }

    private fun px(v: Int) = (v * dp).toInt()

    /** Barra de nivel con degradado y marca del pico. */
    class LevelMeter @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
        var colors = intArrayOf(0xFF22D3EE.toInt(), 0xFF8B5CF6.toInt(), 0xFFEF4444.toInt())
        var level = 0f
            set(v) { field = v; invalidate() }
        var peak = 0f
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.gauge_track) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.text_primary) }
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            val rad = h / 2
            r.set(0f, 0f, width.toFloat(), h)
            c.drawRoundRect(r, rad, rad, track)
            fill.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, android.graphics.Shader.TileMode.CLAMP)
            r.set(0f, 0f, max(h, width * level), h)
            if (level > 0.01f) c.drawRoundRect(r, rad, rad, fill)
            val px = width * peak
            if (peak > 0.02f) c.drawRect(px - 2, 3f, px + 2, h - 3, mark)
        }
    }

    @Suppress("unused") private fun pill() = GradientDrawable()
}
