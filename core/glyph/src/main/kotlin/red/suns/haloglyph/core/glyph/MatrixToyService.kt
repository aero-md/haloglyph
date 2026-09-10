package red.suns.haloglyph.core.glyph

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.FrameSink
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Socle d'un toy animé : la boucle de rendu, une fois pour toutes.
 *
 * Un toy concret n'écrit plus que [renderFrame]. Le reste — démarrer quand la
 * matrice se connecte, s'arrêter quand elle s'en va, ne pas laisser un
 * `Handler` tourner après `onUnbind` — est ici, parce que c'est exactement le
 * genre de code qu'on recopie mal la quatrième fois.
 *
 * Cadence : [frameIntervalMs] est une propriété *lue à chaque frame*, pas une
 * constante de construction. Un toy peut donc respirer — 33 ms quand quelque
 * chose bouge, une seconde au repos — sans redémarrer sa boucle. C'est la même
 * idée que la cadence adaptative validée par le projet de référence externe.
 */
abstract class MatrixToyService(tag: String) : GlyphMatrixService(tag) {

    /** V1 : Phone (3). Le paramètre existe pour que le 13×13 ne soit pas un chantier. */
    protected open val spec: MatrixSpec = MatrixSpec.Phone3

    /** Tampon de rendu réutilisé d'une frame à l'autre. */
    protected val frame: Frame by lazy { Frame(spec) }

    /** Intervalle entre deux frames, relu à chaque tour de boucle. */
    protected open val frameIntervalMs: Long get() = DEFAULT_FRAME_MS

    private val handler = Handler(Looper.getMainLooper())
    private var sink: FrameSink? = null
    private var startedAt = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            renderAndPush(animated = true)
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    /**
     * Dessine l'état courant.
     *
     * @param frame tampon **déjà effacé**.
     * @param elapsedSeconds temps depuis le démarrage de la boucle. Base de
     * temps monotone : `elapsedRealtime`, insensible aux changements d'heure.
     * @param animated `false` en Always-On — rendu statique, pas d'anneau des
     * secondes ni d'animation : le système ne redemande une frame qu'à la minute.
     */
    protected abstract fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean)

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        sink = GlyphSink(manager, spec)
        startLoop()
    }

    override fun onGlyphDisconnected(context: Context) {
        stopLoop()
        sink = null
    }

    override fun onAodUpdate() {
        // L'AOD n'anime pas : une frame, et on rend la main.
        renderAndPush(animated = false)
    }

    protected fun startLoop() {
        if (running) return
        running = true
        startedAt = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    protected fun stopLoop() {
        running = false
        handler.removeCallbacks(tick)
    }

    /**
     * Force une frame hors boucle — après un changement de réglage, par exemple
     * — **et réaligne la cadence sur maintenant**.
     *
     * Le réalignement n'est pas un détail. [frameIntervalMs] est relu à la fin de
     * chaque tour : un toy au repos programme son prochain tour jusqu'à une
     * seconde plus tard. Si un réglage démarre une animation entre-temps, rendre
     * une image sans toucher au tour en attente donne exactement ce qu'on a
     * observé sur Lapse — la première image de la transition, puis un gel jusqu'à
     * la seconde suivante, puis l'état final. L'animation n'a jamais joué.
     *
     * On retire donc le tour en attente et on en poste un immédiatement : la
     * frame part maintenant, et le suivant sera programmé avec l'intervalle que
     * l'animation vient d'imposer. `startedAt` ne bouge pas — la base de temps
     * d'un toy ne doit pas sauter parce qu'un réglage a changé.
     */
    protected fun renderNow() {
        if (!running) {
            renderAndPush(animated = false)
            return
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun renderAndPush(animated: Boolean) {
        val target = sink ?: return
        val t = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
        frame.clear()
        renderFrame(frame, t, animated)
        frame.pushTo(target)
    }

    override fun onDestroy() {
        stopLoop()
        super.onDestroy()
    }

    private companion object {
        /** ~30 fps, la cadence des toys existants. */
        const val DEFAULT_FRAME_MS = 33L
    }
}
