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
import red.suns.haloglyph.lapse.render.LapseSlide
import red.suns.haloglyph.lapse.render.MatrixLabels
import java.time.ZoneId

/**
 * Glyph Toy « compteur temporel ».
 *
 * Repos : un tick par seconde, aligné sur la frontière de seconde pour que
 * l'anneau avance pile au tic. 30 fps pendant les animations — slide de format,
 * format Cycle, arrivée, sablier, changement de lapse.
 *
 * Appui long = lapse suivant, avec un glissement horizontal : le lapse courant
 * sort par la gauche, le suivant entre par la droite ([LapseSlide]).
 *
 * **Une transition démarre par [renderNow], jamais par un simple rendu.** Un
 * lapse en mode anneau laisse la boucle dormir jusqu'à la frontière de seconde ;
 * partir de là sans réarmer donnait une première image, un gel, puis l'état
 * final — l'animation ne jouait pas du tout.
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

    /** Dernière frame réellement affichée, composite de transition compris. */
    private val lastShown by lazy { Frame(spec) }

    /** Copie figée de [lastShown] à l'instant de la bascule : ce qui sort. */
    private val slideFrom by lazy { Frame(spec) }

    /** Rendu du nouveau lapse à l'instant courant : ce qui entre. */
    private val incoming by lazy { Frame(spec) }

    private var lapseSlideStart: Double? = null
    private var hasShown = false

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
        hasShown = false
        super.onGlyphDisconnected(context)
    }

    /**
     * Bascule, **puis** persiste — dans cet ordre.
     *
     * L'écriture réveille [PrefsWatcher] sur le fil principal, donc `onPrefsChanged`
     * s'exécute avant que cette méthode ne rende la main. Persister d'abord
     * faisait basculer le watcher, puis basculer une seconde fois ici : deux
     * vibrations et une transition relancée quelques millisecondes après son
     * départ. En basculant d'abord, `activeIndex` est déjà à jour quand le
     * watcher regarde, et il se contente de relire la config.
     */
    override fun onTouchPointLongPress() {
        val next = nextIndex()
        if (next == activeIndex) return
        beginLapseSwitch(next)
        // Persiste : c'est ce qui synchronise l'app et le widget avec la matrice.
        LapseConfig.setActiveIndex(prefs, next)
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
        val progress = start?.let { (now() - it) / LapseSlide.DURATION }
        val sliding = progress != null && progress < 1.0

        if (sliding) {
            incoming.clear()
            renderer.render(incoming, snap)
            // `slideFrom` reste **figée** pour toute la durée : le glissement
            // translate la frame de départ, pas la translation précédente. La
            // réécrire ferait s'additionner les décalages — voir `LapseSlide`.
            LapseSlide.compose(frame, slideFrom, incoming, progress)
        } else {
            if (start != null) lapseSlideStart = null
            renderer.render(frame, snap)
        }

        // Ce qui part vraiment à l'écran, transition comprise : c'est de là que
        // partira la prochaine bascule, même déclenchée en plein glissement.
        lastShown.copyFrom(frame)
        hasShown = true
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
        MatrixWidgetRefresh.requestUpdateAll(this)
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

    /** Reconfigure l'engine sur [newIndex] et démarre le glissement. */
    private fun beginLapseSwitch(newIndex: Int) {
        activeIndex = newIndex
        val cfg = LapseConfig.readLapse(prefs, newIndex, zone)
        engine.setRef(cfg.ref)
        engine.setFormatQuiet(cfg.format)
        engine.secondsMode = cfg.seconds
        if (hasShown) {
            // Le glissement part de ce qui est à l'écran, pas d'un rendu refait
            // après coup — et la copie le fige pour les 350 ms qui viennent.
            slideFrom.copyFrom(lastShown)
            lapseSlideStart = now()
        }
        tick(VibrationEffect.EFFECT_TICK)
        // Rend **et réarme la boucle** : au repos elle dort jusqu'à la prochaine
        // frontière de seconde, et sans ce réveil la transition ne jouerait pas.
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

    private companion object {
        const val TAG = "LapseToy"

        /** ~30 fps pendant les animations. */
        const val ANIMATED_FRAME_MS = 33L

        /** Vibration d'arrivée : trois coups qui s'allongent. */
        val ARRIVAL_PATTERN = longArrayOf(0, 90, 60, 90, 60, 220, 80, 350)
    }
}
