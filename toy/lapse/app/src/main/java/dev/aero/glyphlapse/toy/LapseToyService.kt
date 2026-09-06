package dev.aero.glyphlapse.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import dev.aero.glyphlapse.Config
import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.render.LapseRenderer
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Glyph Toy « compteur temporel » — seul module dépendant du GlyphMatrixSDK.
 * Repos : 1 tick/s aligné sur la seconde. 30 fps pendant les animations
 * (slide de format, format Cycle, arrivée, changement de lapse).
 * Appui long = lapse actif suivant (parmi les lapse activés), avec un slide
 * horizontal : le lapse courant sort vers la gauche, le suivant entre par la droite.
 */
class LapseToyService : GlyphMatrixService("GlyphLapse") {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val engine = LapseEngine(zone)
    private val renderer = LapseRenderer()
    private val frameHandler = Handler(Looper.getMainLooper())
    private var running = false
    private lateinit var prefs: SharedPreferences

    private var activeIndex = 0
    private var lapseSlideStart: Double? = null
    private var outgoingFrame: IntArray? = null
    private var lastFrame: IntArray? = null

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
            val newActive = Config.activeIndex(p)
            if (newActive != activeIndex) {
                beginLapseSwitch(newActive)
            } else {
                Config.applyActive(p, engine, zone)
                if (running) renderFrame()
            }
        }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val animating = renderFrame()
            // aligné sur la frontière de seconde au repos : l'anneau avance pile au tic
            val delay = if (animating) FRAME_MS
            else (1000L - System.currentTimeMillis() % 1000L).coerceAtLeast(FRAME_MS)
            frameHandler.postDelayed(this, delay)
        }
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager,
    ) {
        prefs = Config.prefs(context)
        activeIndex = Config.activeIndex(prefs)
        Config.applyActive(prefs, engine, zone)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        running = true
        frameHandler.post(tick)
    }

    override fun performOnServiceDisconnected(context: Context) {
        running = false
        frameHandler.removeCallbacksAndMessages(null)
        lapseSlideStart = null
        outgoingFrame = null
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onTouchPointLongPress() {
        val next = nextEnabledIndex()
        if (next == activeIndex) return
        Config.setActiveIndex(prefs, next) // persiste pour synchroniser l'app
        beginLapseSwitch(next)
    }

    override fun onAodUpdate() {
        // AOD : rendu statique sans anneau/sablier, le système cadence les updates
        val snap = engine.update(System.currentTimeMillis(), now())
        push(renderer.render(snap, includeSeconds = false))
    }

    private fun now(): Double = System.nanoTime() / 1e9

    private fun isEnabled(i: Int): Boolean =
        i == 0 || Config.readLapse(prefs, i, zone).enabled

    /** Prochain lapse activé après l'actif (rotation), ou l'actif si seul activé. */
    private fun nextEnabledIndex(): Int {
        for (k in 1 until Config.LAPSE_COUNT) {
            val cand = (activeIndex + k) % Config.LAPSE_COUNT
            if (isEnabled(cand)) return cand
        }
        return activeIndex
    }

    /** Reconfigure l'engine sur [newIndex] et démarre le slide de transition. */
    private fun beginLapseSwitch(newIndex: Int) {
        val old = lastFrame
        activeIndex = newIndex
        val cfg = Config.readLapse(prefs, newIndex, zone)
        engine.setRef(cfg.ref)
        engine.setFormatQuiet(cfg.format)
        engine.secondsMode = cfg.seconds
        if (old != null) {
            outgoingFrame = old
            lapseSlideStart = now()
        }
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        if (running) renderFrame()
    }

    /** Rend une frame et renvoie true si une animation est en cours (→ 30 fps). */
    private fun renderFrame(): Boolean {
        val snap = engine.update(System.currentTimeMillis(), now())
        engine.drainEvents().forEach { event ->
            when (event) {
                LapseEngine.Event.FormatChanged ->
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

                LapseEngine.Event.Arrived ->
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 90, 60, 90, 60, 220, 80, 350), -1
                        )
                    )
            }
        }
        val bright = renderer.render(snap)

        val start = lapseSlideStart
        val sliding = start != null && (now() - start) < LAPSE_SLIDE
        val out = if (sliding && outgoingFrame != null) {
            slideFrames(outgoingFrame!!, bright, (now() - start!!) / LAPSE_SLIDE)
        } else {
            if (start != null) { lapseSlideStart = null; outgoingFrame = null }
            bright
        }
        push(out)
        lastFrame = out
        return snap.animating || sliding
    }

    /** Composite : [old] sort vers la gauche, [new] entre par la droite. */
    private fun slideFrames(old: IntArray, new: IntArray, p: Double): IntArray {
        val e = 1 - (1 - p.coerceIn(0.0, 1.0)).pow(3)
        val dx = (e * SIZE).roundToInt()
        return IntArray(SIZE * SIZE) { i ->
            val x = i % SIZE
            val y = i / SIZE
            var b = 0
            val sxOld = x + dx
            if (sxOld in 0 until SIZE) b = old[y * SIZE + sxOld]
            val sxNew = x + dx - SIZE
            if (sxNew in 0 until SIZE) {
                val nb = new[y * SIZE + sxNew]
                if (nb > 0) b = nb
            }
            b
        }
    }

    private fun push(brightness: IntArray) {
        // setMatrixFrame attend des luminosités 0..4095 : le renderer sort du 0..255,
        // on applique le même ×16 que GlyphMatrixUtils (BRIGHTNESS_MULTIPLIER)
        for (i in frameBuf.indices) frameBuf[i] = brightness[i] * BRIGHTNESS_MULTIPLIER
        matrix?.setMatrixFrame(frameBuf)
    }

    private val frameBuf = IntArray(25 * 25)

    private companion object {
        const val FRAME_MS = 33L // ~30 fps pendant les animations
        const val BRIGHTNESS_MULTIPLIER = 16
        const val SIZE = 25
        const val LAPSE_SLIDE = 0.35 // durée du slide de changement de lapse (s)
    }
}
