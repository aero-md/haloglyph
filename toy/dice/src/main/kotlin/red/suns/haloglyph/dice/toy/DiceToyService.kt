package red.suns.haloglyph.dice.toy

import android.content.Context
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame

/**
 * Glyph Toy « dé ».
 *
 * **Secouer pour jeter**, appui long pour changer de solide. Il n'y a rien
 * d'autre, et c'est le toy : pas de menu, pas de bouton. Un dé se prend en main
 * et se lance.
 *
 * Ce service n'est que la plomberie du carrousel : Glyph Interface le lie quand
 * l'utilisateur arrive sur le dé, le délie quand il en part, et lui envoie les
 * appuis du Glyph Button. Le dé lui-même — le capteur, le jet, la vibration, le
 * rendu — est dans [DiceMatrix].
 *
 * Deux régimes de cadence. Un dé posé est une image fixe : au repos on tient la
 * seconde, ce qui laisse le processeur tranquille pendant que le capteur, lui,
 * continue d'écouter. Un jet passe à 30 fps — et la boucle est **relancée** au
 * lieu d'attendre son prochain tour, sinon la seconde de repos s'intercalerait
 * entre le geste et le départ du dé.
 */
class DiceToyService : MatrixToyService(TAG) {

    private val dice by lazy { DiceMatrix(applicationContext, spec, ::restart) }

    override val frameIntervalMs: Long get() = dice.frameIntervalMs

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        dice.start()
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        dice.stop()
        super.onGlyphDisconnected(context)
    }

    /** Appui long = solide suivant. */
    override fun onTouchPointLongPress() {
        dice.nextDie()
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        dice.render(frame, animated)
    }

    /** Recale la boucle sur la nouvelle cadence, sans attendre le tour en cours. */
    private fun restart() {
        stopLoop()
        startLoop()
    }

    private companion object {
        const val TAG = "DiceToy"
    }
}
