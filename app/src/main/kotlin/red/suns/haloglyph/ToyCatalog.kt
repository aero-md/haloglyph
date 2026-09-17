package red.suns.haloglyph

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
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
import red.suns.haloglyph.gforce.GForceConfig
import red.suns.haloglyph.gforce.engine.GForceDemo
import red.suns.haloglyph.gforce.engine.GForceMode
import red.suns.haloglyph.gforce.render.GForceRenderer
import red.suns.haloglyph.gforce.toy.GForceToyService
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import red.suns.haloglyph.lapse.settings.LapseSettingsActivity
import red.suns.haloglyph.lapse.toy.LapseToyService
import red.suns.haloglyph.float.FloatConfig
import red.suns.haloglyph.float.engine.FloatDemo
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.render.FloatRenderer
import red.suns.haloglyph.float.toy.FloatToyService
import red.suns.haloglyph.sono.MicPermission
import red.suns.haloglyph.sono.SonoConfig
import red.suns.haloglyph.sono.engine.SonoDemo
import red.suns.haloglyph.sono.engine.SonoMode
import red.suns.haloglyph.sono.mic.SonoMicTile
import red.suns.haloglyph.sono.render.SonoRenderer
import red.suns.haloglyph.sono.settings.SonoSettingsActivity
import red.suns.haloglyph.sono.toy.SonoToyService
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
            widgetProviders = HALO_WIDGETS,
            preview = LapsePreview(context),
            settingsActivity = LapseSettingsActivity::class.java,
        ),
        ToyEntry(
            id = DiceConfig.TOY_ID,
            nameRes = red.suns.haloglyph.dice.R.string.toy_dice_name,
            summaryRes = red.suns.haloglyph.dice.R.string.toy_dice_summary,
            glyphService = DiceToyService::class.java,
            widgetProviders = HALO_WIDGETS,
            preview = DicePreview(context),
            // Pas d'écran de réglages : supprimé le 2026-09-16. Le solide se
            // change à l'appui long sur la matrice, et par hublot dans les
            // réglages du hublot lui-même (`WidgetConfig`) — voir
            // `DiceWidgetToy`. L'écran ne portait rien d'autre.
        ),
        // Sonoglyph exposait « Spectre » et « Aiguille » comme deux toys ; ils
        // n'en font plus qu'un, à trois modes, cyclés à l'appui long. Une seule
        // ligne ici, donc, et une seule entrée dans Glyph Interface — pour un
        // micro qui ne s'ouvre qu'une fois.
        ToyEntry(
            id = SonoConfig.TOY_ID,
            nameRes = red.suns.haloglyph.sono.R.string.toy_sono_name,
            summaryRes = red.suns.haloglyph.sono.R.string.toy_sono_summary,
            glyphService = SonoToyService::class.java,
            // Sono a fini par avoir un widget, après l'avoir refusé longtemps :
            // le hublot n'ouvre le micro que pendant les cinq secondes qui
            // suivent un tap, pastille Android allumée, et montre `---` le reste
            // du temps. Voir `SonoWidgetToy`.
            widgetProviders = HALO_WIDGETS,
            preview = SonoPreview(),
            settingsActivity = SonoSettingsActivity::class.java,
            requiredPermission = MicPermission.NAME,
        ),
        // Le seul toy du pack qui ait deux instruments **et** un hublot : sur la
        // matrice, l'appui long passe du niveau à la boussole ; dans un hublot,
        // le choix descend dans les réglages du hublot, ce qui permet d'en poser
        // un de chaque côté. Voir `FloatWidgetToy`.
        //
        // Pas d'écran de réglages : supprimé le 2026-09-16, avec la portée
        // qu'il portait seul. Reste fixe à `FloatRange.DEFAULT`.
        ToyEntry(
            id = FloatConfig.TOY_ID,
            nameRes = red.suns.haloglyph.float.R.string.toy_float_name,
            summaryRes = red.suns.haloglyph.float.R.string.toy_float_summary,
            glyphService = FloatToyService::class.java,
            widgetProviders = HALO_WIDGETS,
            preview = FloatPreview(context),
        ),
        // Le premier toy du pack dont la **matrice n'est pas la surface
        // principale** : dans un support de voiture, le téléphone regarde le
        // conducteur, donc la matrice regarde la route. C'est le hublot qu'on lit
        // en roulant, et de préférence en boucle continue — une rafale de trente
        // secondes ne couvre pas un trajet.
        ToyEntry(
            id = GForceConfig.TOY_ID,
            nameRes = red.suns.haloglyph.gforce.R.string.toy_gforce_name,
            summaryRes = red.suns.haloglyph.gforce.R.string.toy_gforce_summary,
            glyphService = GForceToyService::class.java,
            widgetProviders = HALO_WIDGETS,
            preview = GForcePreview(),
            // Pas d'écran de réglages : supprimé le 2026-09-16. La face se
            // change au geste, et le rappel de support n'était qu'un texte.
        ),
    )

    /**
     * Les tuiles de réglages rapides du pack.
     *
     * Elles ne vivent que devant une Glyph Matrix — armer le micro avant de
     * retourner le téléphone. Sur un téléphone qui n'en a pas, elles
     * proposeraient d'allumer ce qui n'existe pas, et le hub les retire dès que
     * la sonde a répondu. Voir `GlyphTiles`.
     *
     * Elles ne sont pas dans [ToyEntry] : une tuile n'est pas une surface de toy
     * au même titre que les autres — elle porte le nom de l'application, elle
     * vit dans le volet, et un seul toy en a une.
     */
    val TILES: List<Class<*>> = listOf(
        SonoMicTile::class.java,
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

/**
 * L'aperçu de Sono dans le hub : le vrai renderer, sur une scène **inventée**.
 *
 * L'une des deux vignettes du pack qui n'exécutent pas le toy de bout en bout,
 * et c'est délibéré : **ouvrir le hub ne doit pas allumer le micro**.
 *
 * L'histoire de l'onde se remplit toute seule, à partir de la scène simulée : le
 * renderer la nourrit à chaque image, quel que soit le mode affiché.
 *
 * **Le mode alterne**, depuis le 2026-09-16, plutôt que de montrer celui
 * réellement réglé : la vignette est l'endroit où l'on voit ce qu'un toy sait
 * faire, pas un miroir d'un réglage qu'on ne choisit plus jamais deux fois. Un
 * palier par mode, dans l'ordre de la rotation — spectre, aiguille, onde.
 */
private class SonoPreview : ToyPreviewRenderer {

    private val demo = SonoDemo()
    private val renderer = SonoRenderer()

    override fun render(frame: Frame, elapsedSeconds: Double) {
        val modes = SonoMode.entries
        val mode = modes[((elapsedSeconds / MODE_PERIOD).toLong() % modes.size).toInt()]
        renderer.render(frame, demo.snapshotAt(elapsedSeconds), mode)
    }

    private companion object {
        /** Le temps de lire un mode avant de passer au suivant. */
        const val MODE_PERIOD = 6.0
    }
}

/**
 * L'aperçu de Float dans le hub : le vrai renderer, sur une pose **inventée**.
 *
 * L'autre vignette simulée, et pour une raison de même nature que celle de Sono :
 * elle vit dans une liste qu'on fait défiler, sans aucun endroit où rendre ce
 * qu'elle aurait pris. Un aperçu branché sur l'accéléromètre laisserait un
 * écouteur derrière lui à chaque passage.
 *
 * Ce qui est simulé est la **pose du téléphone**, rien d'autre : l'échelle, la
 * couronne, la tolérance et le miroir sont ceux du toy — la portée est celle du
 * réglage, `FloatRange.DEFAULT` depuis que rien ne la change plus.
 *
 * **L'instrument alterne**, depuis le 2026-09-16 : niveau, puis boussole, calé
 * sur le cycle de [FloatDemo] pour changer entre deux poses plutôt qu'en pleine
 * inclinaison.
 *
 * Le miroir est celui d'un hublot et non celui de la matrice : une vignette se
 * regarde sur un écran, donc de face. Voir `FloatRenderer`.
 */
private class FloatPreview(context: Context) : ToyPreviewRenderer {

    private val prefs = FloatConfig.prefs(context.applicationContext)
    private val renderer = FloatRenderer(fromBack = false)

    override fun render(frame: Frame, elapsedSeconds: Double) {
        val range = FloatConfig.range(prefs)
        val cycle = (elapsedSeconds / FloatDemo.PERIOD).toLong()
        val mode = if (cycle % 2 == 0L) FloatMode.NIVEAU else FloatMode.BOUSSOLE
        renderer.render(
            frame,
            FloatDemo.at(elapsedSeconds, range),
            mode,
            range,
            elapsedSeconds,
        )
    }
}

/**
 * L'aperçu de G-Forces dans le hub : le vrai renderer, sur un trajet **inventé**.
 *
 * La troisième vignette simulée, et celle dont la raison est la plus simple : la
 * mesure n'existe qu'en voiture. Une vignette branchée sur l'accéléromètre
 * montrerait un cadran plat pendant qu'on fait défiler une liste, ce qui est à la
 * fois vrai et parfaitement inutile — on ne saurait pas à quoi ressemble le toy.
 *
 * Elle rejoue donc un tour abrégé, quinze secondes, voir `GForceDemo`. Ce qui est
 * simulé est la **conduite**, rien d'autre : l'échelle, les graduations et le
 * miroir sont ceux du toy.
 *
 * **La face alterne**, depuis le 2026-09-16 : la bille, puis les pics, un tour
 * de [GForceDemo] chacune — la vignette montre le cadran en roulant, puis ce
 * qu'on y lirait à l'arrêt.
 */
private class GForcePreview : ToyPreviewRenderer {

    private val renderer = GForceRenderer(fromBack = false)

    override fun render(frame: Frame, elapsedSeconds: Double) {
        val cycle = (elapsedSeconds / GForceDemo.PERIOD).toLong()
        val mode = if (cycle % 2 == 0L) GForceMode.VIF else GForceMode.PICS
        renderer.render(frame, GForceDemo.at(elapsedSeconds), mode)
    }
}
