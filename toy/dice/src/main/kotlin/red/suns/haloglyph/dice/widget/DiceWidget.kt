package red.suns.haloglyph.dice.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.BaseMatrixWidgetProvider
import red.suns.haloglyph.dice.DiceConfig
import red.suns.haloglyph.dice.engine.Roll
import red.suns.haloglyph.dice.engine.T_END
import red.suns.haloglyph.dice.engine.drawValue
import red.suns.haloglyph.dice.render.DiceRenderer

/**
 * Le dé en widget d'écran d'accueil : la matrice, sur un téléphone qui n'en a
 * pas — et le jet à portée de pouce sur un téléphone qui en a une.
 *
 * Au repos, la dernière face obtenue, posée. Au tap, un jet complet : le tirage
 * est fait tout de suite et mémorisé, l'animation n'est que la fonction du temps
 * qui y conduit. C'est ce qui permet à la rafale de s'interrompre sans mentir —
 * le résultat est déjà écrit, la frame de repos qui suit montre la même face.
 *
 * Le widget **n'a aucun lien** avec le service Glyph : il instancie le renderer
 * en direct, dans son propre processus, et lit les mêmes préférences. Binder le
 * service déclencherait une connexion à `com.nothing.thirdparty`, inexistant sur
 * un téléphone non-Nothing.
 */
class DiceWidget : BaseMatrixWidgetProvider() {

    private val renderer by lazy { DiceRenderer(spec) }

    /**
     * Le jet en cours de rafale.
     *
     * Posé par [onTap] et lu par [renderBurst], qui tourne sur le fil de la
     * rafale : `@Volatile` parce que ce n'est pas le même fil, même si le
     * démarrage du fil suffirait en théorie à publier l'écriture.
     */
    @Volatile
    private var roll: Roll? = null

    override fun renderIdle(context: Context, frame: Frame) {
        val prefs = DiceConfig.prefs(context)
        val die = DiceConfig.die(prefs)
        renderer.render(frame, die, Roll.resting(die, DiceConfig.restQuat(prefs, die)))
    }

    override fun onTap(context: Context) {
        val prefs = DiceConfig.prefs(context)
        val die = DiceConfig.die(prefs)
        val throwing = Roll.make(die, DiceConfig.restQuat(prefs, die), drawValue(die))
        roll = throwing
        DiceConfig.setResult(prefs, throwing.value, throwing.twist)
    }

    override fun renderBurst(context: Context, frame: Frame, elapsedSeconds: Double): Boolean {
        val current = roll ?: return false
        if (elapsedSeconds >= T_END) return false
        renderer.render(frame, current.die, current.viewAt(elapsedSeconds))
        return true
    }
}
