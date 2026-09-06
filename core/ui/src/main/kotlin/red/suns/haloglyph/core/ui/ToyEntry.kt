package red.suns.haloglyph.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import red.suns.haloglyph.core.matrix.Frame

/**
 * Ce qu'un toy déclare au hub.
 *
 * Un toy porte ses trois surfaces dans son propre module ; le hub n'a besoin de
 * savoir que ceci : comment le nommer, comment le dessiner en petit, et quel
 * écran de réglages ouvrir. Les classes de service et de widget sont passées en
 * `Class<*>` — le hub s'en sert pour compter les widgets posés et pour dire si
 * le toy est installé dans Glyph Interface, jamais pour les instancier.
 *
 * Aucun registre global : c'est le module `app` qui assemble la liste, parce
 * qu'il est le seul à connaître les toys embarqués dans *cette* app. Slot, par
 * exemple, vivra dans une autre. Un registre statique ferait croire le contraire.
 */
data class ToyEntry(
    /** Identifiant stable, aussi utilisé comme nom de fichier de préférences. */
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val summaryRes: Int,
    @DrawableRes val iconRes: Int,
    /** Le service exposé à Glyph Interface, `null` si le toy est widget seulement. */
    val glyphService: Class<*>? = null,
    /** Le fournisseur de widget, `null` si le toy n'en propose pas. */
    val widgetProvider: Class<*>? = null,
    /** Aperçu animé pour la fiche du hub : la même signature que le rendu du toy. */
    val preview: ToyPreviewRenderer? = null,
    /** Écran de réglages du toy. */
    val settings: (@Composable () -> Unit)? = null,
)

/** Rend l'aperçu d'un toy — `(frame vierge, secondes écoulées)`. */
fun interface ToyPreviewRenderer {
    fun render(frame: Frame, elapsedSeconds: Double)
}
