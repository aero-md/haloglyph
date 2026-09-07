package red.suns.haloglyph

import android.content.Context
import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.ui.ToyEntry
import red.suns.haloglyph.core.ui.ToyPreviewRenderer
import red.suns.haloglyph.dice.DiceConfig
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Quat
import red.suns.haloglyph.dice.engine.Roll
import red.suns.haloglyph.dice.engine.T_END
import red.suns.haloglyph.dice.engine.drawValue
import red.suns.haloglyph.dice.render.DiceRenderer
import red.suns.haloglyph.dice.toy.DiceToyService
import red.suns.haloglyph.dice.widget.DiceWidget
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import red.suns.haloglyph.lapse.settings.LapseSettingsActivity
import red.suns.haloglyph.lapse.toy.LapseToyService
import red.suns.haloglyph.lapse.widget.LapseWidget
import java.time.ZoneId
import kotlin.random.Random

/**
 * Les toys embarqués dans *cette* app.
 *
 * Assemblé ici et nulle part ailleurs : `app` est le seul module qui sache ce
 * qu'il embarque. Chaque toy apporte son propre module ; le catalogue ne fait
 * que les nommer.
 *
 * Les entrées `upcoming` sont des toys **annoncés, pas écrits**. Elles ne
 * mentent pas : la ligne est atténuée, sans chevron, marquée « à venir », et son
 * aperçu n'est qu'une initiale posée sur la matrice — pas une capture d'un
 * rendu qui n'existe pas.
 *
 * Slot n'y figure pas et n'y figurera pas : décision du 06.09.2026, il sort du
 * périmètre de l'application (PRODUIT §2).
 */
object ToyCatalog {

    fun of(context: Context): List<ToyEntry> = listOf(
        ToyEntry(
            id = LapseConfig.TOY_ID,
            nameRes = red.suns.haloglyph.lapse.R.string.toy_lapse_name,
            summaryRes = red.suns.haloglyph.lapse.R.string.toy_lapse_summary,
            glyphService = LapseToyService::class.java,
            widgetProvider = LapseWidget::class.java,
            preview = LapsePreview(context),
            settingsActivity = LapseSettingsActivity::class.java,
        ),
        ToyEntry(
            id = DiceConfig.TOY_ID,
            nameRes = red.suns.haloglyph.dice.R.string.toy_dice_name,
            summaryRes = red.suns.haloglyph.dice.R.string.toy_dice_summary,
            glyphService = DiceToyService::class.java,
            widgetProvider = DiceWidget::class.java,
            preview = DicePreview(context),
            // Pas de `settingsActivity` : ce toy n'a rien à régler. La ligne du
            // hub n'est donc pas cliquable, et n'affiche pas de chevron — ce qui
            // est exact, il n'y a nulle part où aller.
        ),
        ToyEntry(
            id = "sono-spectre",
            nameRes = R.string.toy_spectre_name,
            summaryRes = R.string.toy_spectre_summary,
            preview = InitialPreview("S"),
            upcoming = true,
        ),
        ToyEntry(
            id = "sono-needle",
            nameRes = R.string.toy_needle_name,
            summaryRes = R.string.toy_needle_summary,
            preview = InitialPreview("A"),
            upcoming = true,
        ),
    )
}

/**
 * L'aperçu de Lapse dans le hub : le vrai moteur, le vrai renderer, la
 * configuration réellement enregistrée.
 *
 * C'est tout l'intérêt d'avoir sorti le moteur en Kotlin pur — la vignette du
 * hub, l'aperçu de l'écran de réglages, le widget et la matrice affichent la
 * même chose parce qu'ils exécutent le même code, pas parce que quelqu'un a
 * pensé à les tenir synchronisés.
 */
private class LapsePreview(context: Context) : ToyPreviewRenderer {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val engine = LapseEngine(zone)
    private val renderer = LapseRenderer()
    private val prefs = LapseConfig.prefs(context.applicationContext)

    override fun render(frame: Frame, elapsedSeconds: Double) {
        renderer.labels = MatrixLabels.current()
        // Idempotent : `applyActive` ne touche au moteur que si la préférence a
        // changé, donc relire à chaque frame ne relance aucune animation.
        LapseConfig.applyActive(prefs, engine, zone)
        engine.drainEvents()
        renderer.render(frame, engine.update(System.currentTimeMillis(), System.nanoTime() / 1e9))
    }
}

/**
 * L'aperçu du dé dans le hub : il **joue**, parce qu'un dé posé ne dit rien de
 * ce que fait le toy.
 *
 * La vignette rejoue un jet toutes les [DICE_PERIOD] secondes, dé courant
 * compris — celui que l'appui long a laissé sur la matrice. Chaque cycle tire sa
 * graine de son propre numéro : le jet est donc une fonction pure du temps, il
 * ne dépend pas de la cadence d'affichage, et deux vignettes de la même liste
 * montrent la même chose au même instant. C'est le même parti pris que le
 * moteur, appliqué à sa vitrine.
 */
private class DicePreview(context: Context) : ToyPreviewRenderer {

    private val prefs = DiceConfig.prefs(context.applicationContext)
    private val renderer = DiceRenderer()

    private var cycle = Long.MIN_VALUE
    private var die: Die = DiceConfig.die(prefs)
    private var roll: Roll = newRoll(die.restQuat(1, 0), 0L)

    override fun render(frame: Frame, elapsedSeconds: Double) {
        val current = (elapsedSeconds / DICE_PERIOD).toLong()
        if (current != cycle) {
            cycle = current
            // Relu à chaque cycle : le solide peut avoir changé sur la matrice
            // pendant que le hub était ouvert.
            die = DiceConfig.die(prefs)
            roll = newRoll(roll.qEnd, current)
        }
        renderer.render(frame, die, roll.viewAt(elapsedSeconds - cycle * DICE_PERIOD))
    }

    private fun newRoll(from: Quat, seed: Long): Roll {
        val rnd = Random(seed)
        return Roll.make(die, from, drawValue(die, rnd), rnd)
    }
}

/** Un jet, puis le temps de le lire avant le suivant. */
private const val DICE_PERIOD = T_END + 1.4

/** Aperçu d'un toy pas encore écrit : son initiale, dans la police de la matrice. */
private class InitialPreview(private val letter: String) : ToyPreviewRenderer {
    override fun render(frame: Frame, elapsedSeconds: Double) {
        frame.drawCentered(Fonts.F5, letter, frame.spec.centerY, 0.7f)
    }
}
