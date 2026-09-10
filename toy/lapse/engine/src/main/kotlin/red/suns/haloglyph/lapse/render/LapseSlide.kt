package red.suns.haloglyph.lapse.render

import red.suns.haloglyph.core.matrix.Frame
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Le glissement d'un lapse à l'autre : le courant sort par la gauche, le suivant
 * entre par la droite.
 *
 * Sorti du service et posé ici, en Kotlin pur, pour deux raisons. C'est du calcul
 * de frame, comme le reste de ce dossier — le service n'a pas à savoir composer
 * des images. Et surtout **c'est testable** : le défaut qui a motivé cette
 * extraction ne se voyait qu'à l'œil, sur une matrice, pendant 350 ms.
 *
 * ## L'invariant, et la façon de le rater
 *
 * Le glissement est une **translation de la frame de départ**, pas une
 * translation de la translation précédente. La frame sortante est figée à
 * l'instant de la bascule et resservie telle quelle à chaque image ; seul
 * [shiftAt] avance.
 *
 * Réinjecter le composite de l'image précédente comme sortante donne un résultat
 * qui *ressemble* à un glissement sur la première image et part en fumée
 * ensuite : les décalages s'additionnent, l'ancien lapse quitte l'écran en trois
 * images au lieu de dix, et le nouveau se fait traîner vers la gauche avec lui.
 * C'est exactement ce qui arrivait, et c'est ce que `LapseSlideTest` interdit
 * désormais.
 */
object LapseSlide {

    /** Durée du glissement (s). Assez pour se lire, trop court pour attendre. */
    const val DURATION = 0.35

    /**
     * Décalage en colonnes à [progress] ∈ [0, 1], sur une matrice de [size].
     *
     * Cubique sortante : la frame part vite et se pose, plutôt que de s'arracher
     * à vitesse constante. `0` au départ, `size` à l'arrivée — donc l'ancien
     * lapse est exactement hors champ à la fin, sans pixel résiduel.
     */
    fun shiftAt(progress: Double, size: Int): Int {
        val eased = 1 - (1 - progress.coerceIn(0.0, 1.0)).pow(3)
        return (eased * size).roundToInt()
    }

    /**
     * Compose l'image du glissement dans [target], déjà effacée.
     *
     * [outgoing] est la dernière frame affichée avant la bascule — **la même à
     * chaque appel** —, [incoming] le rendu du nouveau lapse à l'instant courant.
     */
    fun compose(target: Frame, outgoing: Frame, incoming: Frame, progress: Double) {
        val spec = target.spec
        val size = spec.size
        val dx = shiftAt(progress, size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val at = spec.index(x, y)
                val fromOld = x + dx
                if (fromOld in 0 until size) {
                    target.put(at, outgoing.values[spec.index(fromOld, y)])
                }
                // Le nouveau lapse écrase l'ancien là où il écrit : composition
                // autoritaire et non par maximum, sinon un pixel vif du lapse
                // sortant traverserait le suivant.
                val fromNew = x + dx - size
                if (fromNew in 0 until size) {
                    val b = incoming.values[spec.index(fromNew, y)]
                    if (b > 0f) target.put(at, b)
                }
            }
        }
    }
}
