package red.suns.haloglyph.core.glyph

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.FrameSink
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * La boucle de rendu de la matrice, sans le service autour.
 *
 * Composition et non héritage : elle est appelée par [MatrixToyService], qui a
 * déjà [GlyphMatrixService] pour parent. Une classe de base de plus n'aurait pas
 * pu servir.
 *
 * ## Ce qu'elle garantit
 *
 * - **Cadence relue à chaque tour.** [intervalMs] est une fonction, pas une
 *   constante : un toy peut respirer — 33 ms quand quelque chose bouge, une
 *   seconde au repos — sans redémarrer sa boucle.
 * - **Base de temps monotone.** `elapsedRealtime`, insensible aux changements
 *   d'heure, remise à zéro à chaque [start].
 * - **Rien ne tourne après [stop].** Le `Runnable` en attente est retiré, et le
 *   drapeau coupe celui qui serait déjà parti.
 */
class MatrixLoop(
    spec: MatrixSpec,
    private val intervalMs: () -> Long,
    private val render: (Frame, Double, Boolean) -> Unit,
) {

    /** Tampon de rendu réutilisé d'une image à l'autre. */
    val frame: Frame = Frame(spec)

    private val handler = Handler(Looper.getMainLooper())
    private var sink: FrameSink? = null
    private var startedAt = 0L

    var running = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            push(animated = true)
            handler.postDelayed(this, intervalMs())
        }
    }

    /** La matrice est là : c'est maintenant qu'une image peut partir. */
    fun attach(target: FrameSink) {
        sink = target
    }

    /** Elle s'en va. La boucle s'arrête d'abord, sinon elle pousserait dans le vide. */
    fun detach() {
        stop()
        sink = null
    }

    fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    /** Recale la cadence sur maintenant : le tour en attente est jeté, un autre part. */
    fun restart() {
        stop()
        start()
    }

    /**
     * Force une image hors boucle — après un changement de réglage, par exemple
     * — **et réaligne la cadence sur maintenant**.
     *
     * Le réalignement n'est pas un détail. [intervalMs] est relu à la fin de
     * chaque tour : un toy au repos programme son prochain tour jusqu'à une
     * seconde plus tard. Si un réglage démarre une animation entre-temps, rendre
     * une image sans toucher au tour en attente donne exactement ce qu'on a
     * observé sur Lapse — la première image de la transition, puis un gel jusqu'à
     * la seconde suivante, puis l'état final. L'animation n'a jamais joué.
     *
     * `startedAt` ne bouge pas : la base de temps d'un toy ne doit pas sauter
     * parce qu'un réglage a changé.
     */
    fun renderNow() {
        if (!running) {
            push(animated = false)
            return
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Une image statique, hors boucle : l'Always-On n'anime pas. */
    fun renderStatic() {
        push(animated = false)
    }

    private fun push(animated: Boolean) {
        val target = sink ?: return
        val t = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
        frame.clear()
        render(frame, t, animated)
        frame.pushTo(target)
    }
}
