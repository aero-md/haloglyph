package red.suns.haloglyph

import android.content.Context
import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.drawCentered
import red.suns.haloglyph.core.ui.ToyEntry
import red.suns.haloglyph.core.ui.ToyPreviewRenderer
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import red.suns.haloglyph.lapse.settings.LapseSettingsActivity
import red.suns.haloglyph.lapse.toy.LapseToyService
import red.suns.haloglyph.lapse.widget.LapseWidget
import java.time.ZoneId

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
            id = "dice",
            nameRes = R.string.toy_dice_name,
            summaryRes = R.string.toy_dice_summary,
            preview = InitialPreview("D"),
            upcoming = true,
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

/** Aperçu d'un toy pas encore écrit : son initiale, dans la police de la matrice. */
private class InitialPreview(private val letter: String) : ToyPreviewRenderer {
    override fun render(frame: Frame, elapsedSeconds: Double) {
        frame.drawCentered(Fonts.F5, letter, frame.spec.centerY, 0.7f)
    }
}
