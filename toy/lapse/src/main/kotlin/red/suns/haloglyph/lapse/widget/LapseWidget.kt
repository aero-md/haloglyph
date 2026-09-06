package red.suns.haloglyph.lapse.widget

import android.content.Context
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.BaseMatrixWidgetProvider
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import java.time.ZoneId

/**
 * Lapse en widget d'écran d'accueil : la matrice, sur un téléphone qui n'en a pas.
 *
 * Le widget **n'a aucun lien** avec [red.suns.haloglyph.lapse.toy.LapseToyService].
 * Il instancie le moteur et le renderer en direct, dans son propre processus, et
 * lit les mêmes préférences. C'est la contrepartie du fait que le moteur soit du
 * Kotlin pur — et la raison pour laquelle il ne faut jamais binder le service
 * depuis ici : ce bind déclencherait une connexion au service Glyph, inexistant
 * sur un téléphone non-Nothing.
 *
 * Au repos, le rendu est celui de l'Always-On — les unités, sans l'anneau des
 * secondes : un widget rafraîchi au quart d'heure qui afficherait une aiguille de
 * secondes figée mentirait sur ce qu'il montre. Au tap, la rafale rend l'anneau
 * et l'animation, cinq secondes durant.
 */
class LapseWidget : BaseMatrixWidgetProvider() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val renderer by lazy { LapseRenderer(spec) }
    private val engine by lazy { LapseEngine(zone) }

    override fun renderIdle(context: Context, frame: Frame) {
        renderer.render(frame, snapshot(context, 0.0), includeSeconds = false)
    }

    override fun renderBurst(context: Context, frame: Frame, elapsedSeconds: Double): Boolean {
        renderer.render(frame, snapshot(context, elapsedSeconds), includeSeconds = true)
        return true
    }

    private fun snapshot(context: Context, elapsedSeconds: Double): LapseEngine.Snapshot {
        renderer.labels = MatrixLabels.current()
        LapseConfig.applyActive(LapseConfig.prefs(context), engine, zone)
        return engine.update(System.currentTimeMillis(), elapsedSeconds)
    }
}
