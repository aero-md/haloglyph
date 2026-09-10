package red.suns.haloglyph.lapse.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.config.PrefsWatcher
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.MatrixWidgetRefresh
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import red.suns.haloglyph.lapse.widget.LapseWidget
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Glyph Toy « compteur temporel ».
 *
 * Repos : un tick par seconde, aligné sur la frontière de seconde pour que
 * l'anneau avance pile au tic. 30 fps pendant les animations — slide de format,
 * format Cycle, arrivée, changement de lapse.
 *
 * Appui long = lapse activé suivant, avec un slide horizontal : le lapse courant
 * sort par la gauche, le suivant entre par la droite.
 *
 * Ce qui a disparu à la migration : le bind du SDK, la boucle `Handler`, le
 * tampon ×16 et le masque du disque. Tout ça vit dans `core:glyph` et
 * `core:matrix`, partagé avec les autres toys. Il ne reste ici que ce qui est
 * propre à Lapse.
 */
class LapseToyService : MatrixToyService(TAG) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val engine = LapseEngine(zone)
    private val renderer by lazy { LapseRenderer(spec) }

    private lateinit var prefs: SharedPreferences
    private var watcher: PrefsWatcher? = null

    private var activeIndex = 0
    private var animating = false

    /** Frame sortante du lapse précédent, gardée le temps du slide. */
    private val outgoing by lazy { Frame(spec) }
    private val incoming by lazy { Frame(spec) }
    private var lapseSlideStart: Double? = null
    private var hasOutgoing = false

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    /**
     * Au repos, on ne vise pas « une seconde plus tard » mais « la prochaine
     * frontière de seconde » : sinon la dérive du `Handler` fait avancer
     * l'anneau à contretemps, ce qui se voit à l'œil sur un compteur.
     */
    override val frameIntervalMs: Long
        get() = if (animating) {
            ANIMATED_FRAME_MS
        } else {
            (1000L - System.currentTimeMillis() % 1000L).coerceAtLeast(ANIMATED_FRAME_MS)
        }

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        prefs = LapseConfig.prefs(context)
        activeIndex = LapseConfig.activeIndex(prefs)
        LapseConfig.applyActive(prefs, engine, zone)
        watcher = PrefsWatcher(prefs) { onPrefsChanged() }.also { it.start() }
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        watcher?.stop()
        watcher = null
        lapseSlideStart = null
        hasOutgoing = false
        super.onGlyphDisconnected(context)
    }

    override fun onTouchPointLongPress() {
        val next = nextIndex()
        if (next == activeIndex) return
        // Persiste : c'est ce qui synchronise l'app et le widget avec la matrice.
        LapseConfig.setActiveIndex(prefs, next)
        beginLapseSwitch(next)
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        renderer.labels = currentLabels()
        val snap = engine.update(System.currentTimeMillis(), now())
        drainHaptics()

        if (!animated) {
            // AOD : rendu statique, sans anneau ni sablier. Le système cadence.
            renderer.render(frame, snap, includeSeconds = false)
            return
        }

        val start = lapseSlideStart
        val progress = start?.let { (now() - it) / LAPSE_SLIDE }
        val sliding = hasOutgoing && progress != null && progress < 1.0

        if (sliding) {
            incoming.clear()
            renderer.render(incoming, snap)
            slide(frame, outgoing, incoming, progress)
        } else {
            if (start != null) lapseSlideStart = null
            renderer.render(frame, snap)
        }

        // La frame affichée devient la sortante du prochain changement de lapse.
        outgoing.copyFrom(frame)
        hasOutgoing = true
        animating = snap.animating || sliding
    }

    /**
     * Les étiquettes de la matrice suivent la langue du **processus**.
     *
     * Relues à chaque frame et non posées une fois à la connexion : le système
     * applique une langue par app sans tuer le processus, et une matrice restée
     * dans l'ancienne langue jusqu'au prochain redémarrage se remarquerait tout
     * de suite. À côté des 625 valeurs d'une frame, c'est gratuit.
     */
    private fun currentLabels(): MatrixLabels = MatrixLabels.current()

    private fun now(): Double = System.nanoTime() / 1e9

    private fun onPrefsChanged() {
        val next = LapseConfig.activeIndex(prefs)
        if (next != activeIndex) {
            beginLapseSwitch(next)
        } else {
            LapseConfig.applyActive(prefs, engine, zone)
            renderNow()
        }
        // Le widget lit les mêmes préférences mais ne les écoute pas : c'est un
        // receiver, il n'existe qu'entre deux diffusions. On le réveille.
        MatrixWidgetRefresh.requestUpdate(this, LapseWidget::class.java)
    }

    /**
     * Prochain lapse dans la rotation (appui long).
     *
     * Tous les lapse configurés y sont éligibles : il n'y a plus de lapse
     * « défini mais désactivé » depuis que la liste de gestion retire un lapse
     * plutôt que de l'éteindre — [LapseConfig.lapseCount] dit à lui seul
     * combien il y en a.
     */
    private fun nextIndex(): Int {
        val count = LapseConfig.lapseCount(prefs)
        return (activeIndex + 1) % count
    }

    /** Reconfigure l'engine sur [newIndex] et démarre le slide de transition. */
    private fun beginLapseSwitch(newIndex: Int) {
        activeIndex = newIndex
        val cfg = LapseConfig.readLapse(prefs, newIndex, zone)
        engine.setRef(cfg.ref)
        engine.setFormatQuiet(cfg.format)
        engine.secondsMode = cfg.seconds
        // `outgoing` porte déjà la dernière frame affichée : le slide démarre
        // sur ce qui est à l'écran, pas sur un rendu refait après coup.
        lapseSlideStart = now()
        tick(VibrationEffect.EFFECT_TICK)
        renderNow()
    }

    private fun drainHaptics() {
        engine.drainEvents().forEach { event ->
            when (event) {
                LapseEngine.Event.FormatChanged -> tick(VibrationEffect.EFFECT_TICK)
                LapseEngine.Event.Arrived -> vibrator.vibrate(
                    VibrationEffect.createWaveform(ARRIVAL_PATTERN, -1),
                )
            }
        }
    }

    private fun tick(effect: Int) {
        runCatching { vibrator.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    /** Composite : [old] sort vers la gauche, [new] entre par la droite. */
    private fun slide(target: Frame, old: Frame, new: Frame, progress: Double) {
        val e = 1 - (1 - progress.coerceIn(0.0, 1.0)).pow(3)
        val dx = (e * spec.size).roundToInt()
        for (y in 0 until spec.size) {
            for (x in 0 until spec.size) {
                val at = spec.index(x, y)
                val fromOld = x + dx
                if (fromOld in 0 until spec.size) {
                    target.put(at, old.values[spec.index(fromOld, y)])
                }
                // Le nouveau lapse écrase l'ancien là où il écrit : composition
                // autoritaire et non par maximum, sinon un pixel vif du lapse
                // sortant traverserait le suivant.
                val fromNew = x + dx - spec.size
                if (fromNew in 0 until spec.size) {
                    val b = new.values[spec.index(fromNew, y)]
                    if (b > 0f) target.put(at, b)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LapseToy"

        /** ~30 fps pendant les animations. */
        const val ANIMATED_FRAME_MS = 33L

        /** Durée du slide de changement de lapse (s). */
        const val LAPSE_SLIDE = 0.35

        /** Vibration d'arrivée : trois coups qui s'allongent. */
        val ARRIVAL_PATTERN = longArrayOf(0, 90, 60, 90, 60, 220, 80, 350)
    }
}
