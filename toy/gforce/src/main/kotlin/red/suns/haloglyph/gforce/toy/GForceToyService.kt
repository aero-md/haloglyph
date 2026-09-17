package red.suns.haloglyph.gforce.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.MatrixWidgetRefresh
import red.suns.haloglyph.gforce.GForceConfig
import red.suns.haloglyph.gforce.engine.GForceEngine
import red.suns.haloglyph.gforce.render.GForceRenderer

/**
 * Glyph Toy « G-Forces » : un accéléromètre de bord.
 *
 * Téléphone debout dans un support, dos vers la route. La bille dit ce qu'on subit
 * maintenant, l'appui long bascule sur les quatre pics du trajet.
 *
 * ## Ce que le toy mesure vraiment, et ce qu'il ne mesure pas
 *
 * À ±0,03 g près **si le téléphone est fixe**. Le capteur n'est pas le facteur
 * limitant — voir `GForceEngine` — c'est le support : un téléphone qui bouge dans
 * son berceau ajoute ses propres accélérations à celles de la voiture, et rien ne
 * permet de les distinguer. Un téléphone qui roule dans un vide-poches n'affiche
 * pas une mesure dégradée, il affiche du bruit.
 *
 * ## La matrice n'est pas la surface principale, et c'est assumé
 *
 * Dans un support, le téléphone regarde le conducteur : la matrice regarde donc le
 * pare-brise. C'est le **hublot d'écran d'accueil** qui se lit en roulant, et cette
 * surface-ci sert à qui se penche par-dessus, ou à qui pose le téléphone à plat
 * sur la planche de bord — pose que le moteur reconnaît, voir `GForceEngine`.
 *
 * ## Cadence
 *
 * Quarante images par seconde, comme Float et pour la même raison : chaque image
 * est une **mesure**, pas l'interpolation d'une animation déjà écrite. C'est la
 * seule chose qui distingue une bille qui suit la voiture d'une bille qui la
 * rattrape.
 */
class GForceToyService : MatrixToyService(TAG) {

    private val engine = GForceEngine()
    private val renderer by lazy { GForceRenderer(spec, fromBack = true) }

    private lateinit var prefs: SharedPreferences
    private var sensors: GForceSensors? = null
    private var mode = red.suns.haloglyph.gforce.engine.GForceMode.DEFAULT

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    override val frameIntervalMs: Long get() = FRAME_MS

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        prefs = GForceConfig.prefs(context)
        mode = GForceConfig.mode(prefs)
        // Ouvrir le toy, c'est commencer un trajet. Les pics de la session
        // précédente n'ont rien à dire de celui-ci.
        engine.reset()
        sensors = GForceSensors(context, engine).also { it.start() }
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        sensors?.stop()
        sensors = null
        engine.reset()
        super.onGlyphDisconnected(context)
    }

    /**
     * Appui long = l'autre face du cadran.
     *
     * Le choix est écrit dans les préférences du toy, donc il vaut **partout** :
     * les hublots le relisent, et leur tap fait le même geste. On les réveille, un
     * receiver n'existant qu'entre deux diffusions.
     */
    override fun onTouchPointLongPress() {
        mode = mode.next
        GForceConfig.setMode(prefs, mode)
        tick()
        MatrixWidgetRefresh.requestUpdateAll(this)
        renderNow()
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        // Relu à chaque image, comme Lapse, Dice et Float : la face se change
        // depuis un hublot pendant que la matrice affiche autre chose.
        mode = GForceConfig.mode(prefs)
        // `animated` est ignoré : ce toy ne déclare pas d'Always-On, donc le
        // système ne demande jamais d'image statique. Voir le manifeste.
        renderer.render(frame, engine.snapshot(), mode)
    }

    /**
     * Le seul retour haptique du toy : le changement de face.
     *
     * Il n'y en a pas sur les pics, et c'est délibéré — un accéléromètre de bord
     * bat un record toutes les dix secondes en conduite normale. Vibrer à chaque
     * fois ferait du toy une chose qui demande l'attention au pire moment.
     */
    private fun tick() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private companion object {
        const val TAG = "GForceToy"

        /** ~40 fps. Chaque image est une mesure. */
        const val FRAME_MS = 25L
    }
}
