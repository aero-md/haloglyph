package red.suns.haloglyph.core.glyph

import android.content.Context
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Socle d'un toy animé : la boucle de rendu, une fois pour toutes.
 *
 * Un toy concret n'écrit plus que [renderFrame]. Le reste — démarrer quand la
 * matrice se connecte, s'arrêter quand elle s'en va, ne pas laisser un
 * `Handler` tourner après `onUnbind` — est ici, parce que c'est exactement le
 * genre de code qu'on recopie mal la quatrième fois.
 *
 * La boucle elle-même vit dans [MatrixLoop], par composition : voir là-bas.
 *
 * Cadence : [frameIntervalMs] est une propriété *lue à chaque frame*, pas une
 * constante de construction. Un toy peut donc respirer — 33 ms quand quelque
 * chose bouge, une seconde au repos — sans redémarrer sa boucle.
 */
abstract class MatrixToyService(tag: String) : GlyphMatrixService(tag) {

    /** V1 : Phone (3). Le paramètre existe pour que le 13×13 ne soit pas un chantier. */
    protected open val spec: MatrixSpec = MatrixSpec.Phone3

    /** Intervalle entre deux frames, relu à chaque tour de boucle. */
    protected open val frameIntervalMs: Long get() = DEFAULT_FRAME_MS

    /**
     * `by lazy` et non un champ : [spec] et [frameIntervalMs] sont redéfinis par
     * la sous-classe, et lire `spec` dans un initialiseur de champ le lirait
     * avant que la sous-classe soit construite — c'est-à-dire avant que sa
     * redéfinition existe.
     */
    private val loop: MatrixLoop by lazy {
        MatrixLoop(spec, { frameIntervalMs }, ::renderFrame)
    }

    /** Tampon de rendu réutilisé d'une frame à l'autre. */
    protected val frame: Frame get() = loop.frame

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
        loop.attach(GlyphSink(manager, spec))
        startLoop()
    }

    override fun onGlyphDisconnected(context: Context) {
        loop.detach()
    }

    override fun onAodUpdate() {
        // L'AOD n'anime pas : une frame, et on rend la main.
        loop.renderStatic()
    }

    protected fun startLoop() {
        loop.start()
    }

    protected fun stopLoop() {
        loop.stop()
    }

    /** Voir [MatrixLoop.renderNow] : une image maintenant, et la cadence recalée. */
    protected fun renderNow() {
        loop.renderNow()
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
