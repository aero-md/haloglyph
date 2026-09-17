package red.suns.haloglyph.dice.toy

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
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

/**
 * Le dé sur la matrice : le capteur, le jet en cours, la vibration, le rendu.
 *
 * Tout ce qui était [DiceToyService] sauf le service : le service ne garde que sa
 * plomberie de carrousel, et le dé lui-même vit ici.
 *
 * L'état ne vit pas dans les préférences : le jet en cours est une fonction du
 * temps, gardée ici. Ce qui est persisté, c'est le résultat une fois posé — pour
 * le widget et pour la vignette du hub, qui doivent montrer le même dé.
 *
 * @param onPace le régime vient de changer — dé posé ↔ dé en l'air — et la
 * boucle doit se recaler **maintenant**. Sans ça, la seconde de repos
 * s'intercalerait entre le geste et le départ du dé, ce qui est exactement le
 * retard qu'un dé ne pardonne pas.
 */
internal class DiceMatrix(
    private val context: Context,
    spec: MatrixSpec,
    private val onPace: () -> Unit,
) {

    private val prefs: SharedPreferences = DiceConfig.prefs(context)
    private val renderer by lazy { DiceRenderer(spec) }

    private var die: Die = Dice.DEFAULT

    /** L'orientation du dé posé. Point de départ et point d'arrivée de tout jet. */
    private var resting: Quat = die.restQuat(1, 0)

    /** Le jet en cours, `null` si le dé est posé. */
    private var roll: Roll? = null
    private var rollStart = 0L

    /** Dernière étape franchie, pour ne vibrer qu'aux transitions. */
    private var stage = Stage.REST

    private var shaker: Shaker? = null

    private val vibrator: Vibrator by lazy {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    }

    /** Un dé posé est une image fixe ; un jet, trente images par seconde. */
    val frameIntervalMs: Long
        get() = if (roll != null) ROLLING_FRAME_MS else IDLE_FRAME_MS

    /** Relit le dé en main et met le capteur à l'écoute. */
    fun start() {
        die = DiceConfig.die(prefs)
        resting = DiceConfig.restQuat(prefs, die)
        shaker = Shaker(context) { onShake() }.also { it.start() }
    }

    fun stop() {
        shaker?.stop()
        shaker = null
        roll = null
        stage = Stage.REST
    }

    /**
     * Relit le solide, et ne fait rien s'il n'a pas changé.
     *
     * Appelé à chaque image — un `getString` sur des préférences déjà en mémoire,
     * à côté des 625 valeurs d'une frame c'est gratuit. Ce qui ne l'est pas,
     * c'est de rater le changement : le solide se choisit aussi depuis l'écran de
     * réglages et depuis les réglages d'un hublot, pendant que la matrice affiche
     * autre chose. Même parti pris que Lapse, qui relit sa configuration à
     * chaque frame plutôt que de s'inventer un canal.
     *
     * Un jet en cours est **abandonné** : un 17 tiré sur un d20 n'a aucune face
     * où se poser sur un d6, et une culbute qui changerait de solide en vol ne
     * voudrait rien dire.
     */
    private fun adopt(next: Die) {
        if (next == die) return
        die = next
        roll = null
        stage = Stage.REST
        resting = DiceConfig.restQuat(prefs, die)
    }

    /** Solide suivant. Le dé change, la face gardée est rebornée. */
    fun nextDie() {
        DiceConfig.setDie(prefs, Dice.next(die))
        adopt(DiceConfig.die(prefs))
        tick()
        // Le widget lit les mêmes préférences mais ne les écoute pas : c'est un
        // receiver, il n'existe qu'entre deux diffusions. On le réveille.
        MatrixWidgetRefresh.requestUpdateAll(context)
        onPace()
    }

    fun render(frame: Frame, animated: Boolean) {
        adopt(DiceConfig.die(prefs))
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
        onPace()
    }

    /** Le dé est posé : sa pose devient le repos, son résultat est mémorisé. */
    private fun land(finished: Roll) {
        resting = finished.qEnd
        roll = null
        stage = Stage.REST
        DiceConfig.setResult(prefs, finished.value, finished.twist)
        MatrixWidgetRefresh.requestUpdateAll(context)
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

    private fun tick() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private companion object {
        /** ~30 fps pendant le jet. */
        const val ROLLING_FRAME_MS = 33L

        /** Dé posé : l'image ne change pas, la boucle n'a rien à dire. */
        const val IDLE_FRAME_MS = 1000L

        /** Arrivée : deux coups secs, le dé qui touche et qui se cale. */
        val LAND_PATTERN = longArrayOf(0, 22, 45, 70)
    }
}
