package red.suns.haloglyph.debug

import android.content.Context
import red.suns.haloglyph.SelfTest
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.widget.BaseMatrixWidgetProvider

/**
 * Widget de vérification, variante debug uniquement.
 *
 * Il fait tourner [SelfTest] — le même objet que la préview du hub — sur la
 * surface widget. Si les deux ne se ressemblent pas, c'est la promesse produit
 * qui est fausse, et on veut le savoir avant d'avoir migré quatre toys dessus.
 *
 * Il exerce aussi la politique des deux régimes : au repos une frame figée, au
 * tap une rafale bornée.
 */
class SelfTestWidget : BaseMatrixWidgetProvider() {

    override fun renderIdle(context: Context, frame: Frame) {
        // Instant figé, choisi pour que la comète soit visible.
        SelfTest.render(frame, 0.0)
    }

    override fun renderBurst(context: Context, frame: Frame, elapsedSeconds: Double): Boolean {
        SelfTest.render(frame, elapsedSeconds)
        return true // la borne des 5 secondes suffit à arrêter la rafale
    }
}
