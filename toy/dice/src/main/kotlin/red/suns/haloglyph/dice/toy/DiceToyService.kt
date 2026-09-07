package red.suns.haloglyph.dice.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.glyph.MatrixToyService
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.MatrixWidgetRefresh
import red.suns.haloglyph.dice.DiceConfig
import red.suns.haloglyph.dice.engine.Dice
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Quat
import red.suns.haloglyph.dice.engine.Roll
import red.suns.haloglyph.dice.engine.Stage
import red.suns.haloglyph.dice.engine.T_END
import red.suns.haloglyph.dice.engine.Verdict
import red.suns.haloglyph.dice.engine.drawValue
import red.suns.haloglyph.dice.engine.stageAt
import red.suns.haloglyph.dice.engine.verdict
import red.suns.haloglyph.dice.render.DiceRenderer
import red.suns.haloglyph.dice.widget.DiceWidget

/**
 * Glyph Toy « dé ».
 *
 * **Secouer pour jeter**, appui long pour changer de solide. Il n'y a rien
 * d'autre, et c'est le toy : pas de menu, pas de bouton, pas d'écran de
 * réglages. Un dé se prend en main et se lance.
 *
 * Deux régimes de cadence. Un dé posé est une image fixe : au repos on tient la
 * seconde, ce qui laisse le processeur tranquille pendant que le capteur, lui,
 * continue d'écouter. Un jet passe à 30 fps — et la boucle est **relancée** au
 * lieu d'attendre son prochain tour, sinon la seconde de repos s'intercalerait
 * entre le geste et le départ du dé, ce qui est exactement le retard qu'un dé ne
 * pardonne pas.
 *
 * L'état ne vit pas dans les préférences : le jet en cours est une fonction du
 * temps, gardée ici. Ce qui est persisté, c'est le résultat une fois posé — pour
 * le widget et pour la vignette du hub, qui doivent montrer le même dé.
 */
class DiceToyService : MatrixToyService(TAG) {

    private val renderer by lazy { DiceRenderer(spec) }

    private lateinit var prefs: SharedPreferences
    private var shaker: Shaker? = null

    private var die: Die = Dice.DEFAULT

    /** L'orientation du dé posé. Point de départ et point d'arrivée de tout jet. */
    private var resting: Quat = die.restQuat(1, 0)

    /** Le jet en cours, `null` si le dé est posé. */
    private var roll: Roll? = null
    private var rollStart = 0L

    /** Dernière étape franchie, pour ne vibrer qu'aux transitions. */
    private var stage = Stage.REST

    private val vibrator: Vibrator by lazy {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    override val frameIntervalMs: Long
        get() = if (roll != null) ROLLING_FRAME_MS else IDLE_FRAME_MS

    override fun onGlyphConnected(context: Context, manager: GlyphMatrixManager) {
        prefs = DiceConfig.prefs(context)
        die = DiceConfig.die(prefs)
        resting = DiceConfig.restQuat(prefs, die)
        shaker = Shaker(context) { onShake() }.also { it.start() }
        super.onGlyphConnected(context, manager)
    }

    override fun onGlyphDisconnected(context: Context) {
        shaker?.stop()
        shaker = null
        roll = null
        super.onGlyphDisconnected(context)
    }

    /** Appui long = solide suivant. Le dé change, la face gardée est rebornée. */
    override fun onTouchPointLongPress() {
        die = Dice.next(die)
        DiceConfig.setDie(prefs, die)
        roll = null
        stage = Stage.REST
        resting = DiceConfig.restQuat(prefs, die)
        tick()
        // Le widget lit les mêmes préférences mais ne les écoute pas : c'est un
        // receiver, il n'existe qu'entre deux diffusions. On le réveille.
        MatrixWidgetRefresh.requestUpdate(this, DiceWidget::class.java)
        restart()
    }

    override fun renderFrame(frame: Frame, elapsedSeconds: Double, animated: Boolean) {
        val current = roll
        // L'Always-On ne rend qu'une image à la minute : un jet ne s'y joue pas,
        // et un dé posé est justement ce qu'il y a de mieux à y montrer.
        if (!animated || current == null) {
            renderer.render(frame, die, Roll.resting(die, resting))
            return
        }

        val t = secondsSinceToss()
        if (t >= T_END) {
            land(current)
            renderer.render(frame, die, Roll.resting(die, resting))
            return
        }

        val next = stageAt(t)
        if (next != stage) {
            stage = next
            // Le seul retour haptique du vol : l'arrivée. Vibrer à chaque rebond
            // ferait du toy une brosse à dents — le dé rebondit six fois.
            if (next == Stage.READ) {
                runCatching { vibrator.vibrate(VibrationEffect.createWaveform(LAND_PATTERN, -1)) }
            }
        }
        renderer.render(frame, die, current.viewAt(t))
    }

    /**
     * Le geste.
     *
     * [verdict] décide seul s'il compte : la fenêtre morte du départ écarte le
     * poignet qui revient, celle de l'arrivée protège un résultat qui n'a pas
     * encore été montré. Entre les deux, une seconde secousse renvoie le dé en
     * l'air — c'est précisément ce qu'une main fait quand le jet lui déplaît.
     */
    private fun onShake() {
        val t = roll?.let { secondsSinceToss() }
        if (verdict(t) != Verdict.OK) return
        toss()
    }

    private fun toss() {
        roll = Roll.make(die, currentOrientation(), drawValue(die))
        rollStart = SystemClock.elapsedRealtime()
        stage = Stage.TOSS
        tick()
        restart()
    }

    /** Le dé est posé : sa pose devient le repos, son résultat est mémorisé. */
    private fun land(finished: Roll) {
        resting = finished.qEnd
        roll = null
        stage = Stage.REST
        DiceConfig.setResult(prefs, finished.value, finished.twist)
        MatrixWidgetRefresh.requestUpdate(this, DiceWidget::class.java)
    }

    /**
     * Base de temps monotone à nous, et non l'`elapsedSeconds` de la boucle.
     *
     * Redémarrer la boucle remet son horloge à zéro ; le jet, lui, ne doit pas
     * repartir du début parce qu'on a changé de cadence.
     */
    private fun secondsSinceToss(): Double =
        (SystemClock.elapsedRealtime() - rollStart) / 1000.0

    private fun currentOrientation(): Quat =
        roll?.orientationAt(secondsSinceToss()) ?: resting

    /** Recale la boucle sur la nouvelle cadence, sans attendre le tour en cours. */
    private fun restart() {
        stopLoop()
        startLoop()
    }

    private fun tick() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private companion object {
        const val TAG = "DiceToy"

        /** ~30 fps pendant le jet. */
        const val ROLLING_FRAME_MS = 33L

        /** Dé posé : l'image ne change pas, la boucle n'a rien à dire. */
        const val IDLE_FRAME_MS = 1000L

        /** Arrivée : deux coups secs, le dé qui touche et qui se cale. */
        val LAND_PATTERN = longArrayOf(0, 22, 45, 70)
    }
}
