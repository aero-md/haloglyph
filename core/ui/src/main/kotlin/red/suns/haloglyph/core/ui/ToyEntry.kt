package red.suns.haloglyph.core.ui

import androidx.annotation.StringRes
import red.suns.haloglyph.core.matrix.Frame

/**
 * Ce qu'un toy déclare au hub.
 *
 * Un toy porte ses trois surfaces dans son propre module ; le hub n'a besoin de
 * savoir que ceci : comment le nommer, comment le décrire en une ligne, comment
 * le dessiner sur la matrice en petit, et quel écran de réglages ouvrir. Les
 * classes de service et de widget sont passées en `Class<*>` — le hub s'en sert
 * pour compter les widgets posés et pour dire si le toy est déclaré à Glyph
 * Interface, jamais pour les instancier.
 *
 * Aucun registre global : c'est le module `app` qui assemble la liste, parce
 * qu'il est le seul à connaître les toys embarqués dans *cette* app. Un registre
 * statique laisserait croire qu'il existe un catalogue commun à plusieurs apps.
 */
data class ToyEntry(
    /** Identifiant stable, aussi utilisé comme nom de fichier de préférences. */
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val summaryRes: Int,
    /** Le service exposé à Glyph Interface, `null` si le toy est widget seulement. */
    val glyphService: Class<*>? = null,
    /** Le fournisseur de widget, `null` si le toy n'en propose pas. */
    val widgetProvider: Class<*>? = null,
    /** Aperçu animé pour la fiche du hub : la même signature que le rendu du toy. */
    val preview: ToyPreviewRenderer? = null,
    /**
     * L'écran de réglages du toy, si le toy en a un.
     *
     * Une `Activity` et non un composable : le retour, la restauration d'état et
     * la tâche système sont alors ceux d'Android, sans bibliothèque de
     * navigation à embarquer pour deux écrans. `null` = ligne non cliquable,
     * ce qui est le cas d'un toy à venir.
     */
    val settingsActivity: Class<*>? = null,

    /** Toy annoncé mais pas encore là : ligne visible, atténuée, sans chevron. */
    val upcoming: Boolean = false,

    /**
     * La permission d'exécution sans laquelle ce toy ne peut rien afficher.
     *
     * Déclarée plutôt que testée par le hub au cas par cas : le hub n'a pas à
     * savoir que Sono écoute, seulement qu'un toy peut être empêché. Une chaîne
     * et non un `Boolean` calculé, parce que l'état change pendant que l'écran
     * est ouvert — on revient des réglages du système avec la réponse — et
     * qu'une valeur figée à la construction du catalogue mentirait.
     *
     * `null` = rien à demander, ce qui est le cas de tous les autres toys.
     */
    val requiredPermission: String? = null,
)

/** Rend l'aperçu d'un toy — `(frame vierge, secondes écoulées)`. */
fun interface ToyPreviewRenderer {
    fun render(frame: Frame, elapsedSeconds: Double)
}
