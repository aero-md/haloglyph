package red.suns.haloglyph.float.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.MatrixWidgetRefresh
import red.suns.haloglyph.float.FloatConfig
import red.suns.haloglyph.float.engine.FloatEngine
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.render.FloatRenderer

/**
 * Glyph Toy « Float » : un niveau à bulle, et une boussole.
 *
 * **Appui long pour changer d'instrument**, et c'est le seul geste. Il n'y a rien
 * à régler, rien à caler : on pose le téléphone sur ce qu'on veut mettre droit,
 * et on lit.
 *
 * ## Pourquoi il vaut mieux que les niveaux qu'on connaît
 *
 * Parce que son échelle n'est pas proportionnelle — voir `FloatScale`. Un niveau
 * ordinaire sur vingt-cinq LEDs ne bouge pas d'un pixel pour un degré, ce qui en
 * fait un instrument qui ne sait pas dire ce qu'on lui demande. Ici la bulle est
 * vive au centre, où on la regarde, et saturée au bord, où elle n'a plus rien à
 * apprendre à personne.
 *
 * ## Un seul geste, et c'est voulu
 *
 * Il y en a eu deux : l'appui long, et une secousse qui remettait l'horizontale à
 * zéro sur la pose courante. La seconde est partie avec ce qu'elle servait — un
 * niveau à bulle n'a pas de réglage de zéro, c'est ce qui en fait un instrument.
 * Voir `FloatEngine`. Ce qui reste est la convention du pack, et rien de plus :
 * appui long, élément suivant.
 *
 * ## Cadence
 *
 * Quarante images par seconde, tout le temps, là où le reste du pack en fait
 * trente. Contrairement à Lapse et à Dice, il n'y a pas d'état de repos : tant que
 * le toy est affiché, la main bouge. Une cadence adaptative n'aurait rien à quoi
 * s'adapter — et un niveau qui répond avec un temps de retard est un niveau qu'on
 * n'utilise pas deux fois.
 *
 * C'est le seul toy du pack à dépasser trente, et il ne le fait pas pour le
 * confort : c'est le seul dont chaque image soit une **mesure**. Ailleurs, une
 * image de plus interpole une animation déjà écrite ; ici elle est la seule chose
 * qui distingue une bulle qui suit la main d'une bulle qui la rattrape.
 */
class FloatToyService : MatrixToyService(TAG) {

    private val engine = FloatEngine()
    private val renderer by lazy { FloatRenderer(spec, fromBack = true) }

    private lateinit var prefs: SharedPreferences
    private var sensors: FloatSensors? = null
    private var mode = FloatMode.DEFAULT

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    override val frameIntervalMs: Long get() = FRAME_MS

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        prefs = FloatConfig.prefs(context)
        mode = FloatConfig.mode(prefs)
        engine.range = FloatConfig.range(prefs)
        sensors = FloatSensors(context, engine).also {
            it.compass = mode == FloatMode.BOUSSOLE
            it.start()
        }
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        sensors?.stop()
        sensors = null
        // Le filtre repart de zéro à la prochaine ouverture : il n'a aucune raison
        // de croire que le téléphone est resté dans la même pose entre-temps.
        engine.reset()
        super.onGlyphDisconnected(context)
    }

    /**
     * Appui long = instrument suivant.
     *
     * Le choix est écrit dans les préférences du toy, donc il vaut **partout** :
     * les hublots le relisent, et leur tap fait le même geste dans l'autre sens.
     * On réveille les receivers, qui n'existent qu'entre deux diffusions et
     * n'écoutent donc rien.
     */
    override fun onTouchPointLongPress() {
        mode = mode.next
        FloatConfig.setMode(prefs, mode)
        sensors?.compass = mode == FloatMode.BOUSSOLE
        tick(VibrationEffect.EFFECT_TICK)
        MatrixWidgetRefresh.requestUpdateAll(this)
        renderNow()
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        // Relu à chaque image, comme Lapse et Dice : la portée se change depuis
        // l'écran de réglages pendant que la matrice affiche autre chose, et
        // s'inventer un canal de notification pour deux lectures de préférences
        // déjà en mémoire ne se défendrait pas.
        engine.range = FloatConfig.range(prefs)
        val next = FloatConfig.mode(prefs)
        if (next != mode) {
            mode = next
            sensors?.compass = mode == FloatMode.BOUSSOLE
        }

        // L'information du toy, et la seule qu'on ne puisse pas lire sans
        // retourner le téléphone. C'est pour elle que la permission VIBRATE est
        // déclarée, et c'est la seule chose que ce toy vibre.
        if (engine.consumeLevel()) tick(VibrationEffect.EFFECT_CLICK)

        // `animated` est ignoré : ce toy ne déclare pas d'Always-On, donc le
        // système ne demande jamais d'image statique. Voir le manifeste.
        renderer.render(frame, engine.snapshot(), mode, engine.range, elapsedSeconds)
    }

    private fun tick(effect: Int) {
        runCatching { vibrator.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    private companion object {
        const val TAG = "FloatToy"

        /** ~40 fps. Une pose ne se repose pas, la boucle non plus. */
        const val FRAME_MS = 25L
    }
}
