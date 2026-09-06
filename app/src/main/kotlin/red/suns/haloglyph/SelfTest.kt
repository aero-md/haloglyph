package red.suns.haloglyph

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.drawCentered
import kotlin.math.PI
import kotlin.math.sin

/**
 * Le rendu de vérification de la plomberie.
 *
 * Tant qu'aucun toy n'est migré, c'est lui qui prouve que la chaîne complète
 * tient : `Frame` → renderer → sink, sur les trois surfaces. Il est écrit avec
 * `core:matrix` seul — aucune dépendance Android — donc le *même* objet
 * alimente la préview Compose, un widget et, le jour où on le branche, la
 * matrice physique.
 *
 * Ce qu'il montre, et pourquoi :
 *
 * - **le nombre de LEDs**, écrit avec la police 3×5 : les polices et le tracé
 *   centré fonctionnent ;
 * - **une comète sur l'anneau de bord** : le contour est bien trié dans le sens
 *   horaire depuis midi, ce dont dépendra l'anneau des secondes de Lapse ;
 * - **une respiration du disque entier** : le masque tient — si les coins
 *   s'allument, la surface a oublié d'appliquer la géométrie.
 */
object SelfTest {

    /** Tour complet de la comète, en secondes. */
    private const val SWEEP_PERIOD = 3.0

    /** Longueur de la traîne, en LEDs. */
    private const val TRAIL = 12

    fun render(frame: Frame, elapsedSeconds: Double) {
        val spec = frame.spec

        // Respiration de fond : discrète, juste de quoi voir la silhouette.
        val breath = (0.05f + 0.03f * sin(2 * PI * elapsedSeconds / 4).toFloat())
        frame.fill(breath)

        // Comète sur le contour.
        val ring = spec.edgeRing
        val head = ((elapsedSeconds / SWEEP_PERIOD) % 1.0 * ring.size).toInt()
        for (k in 0 until TRAIL) {
            val index = ring[(head - k + ring.size * 2) % ring.size]
            frame.setAt(index, 1f - k / TRAIL.toFloat())
        }

        frame.drawCentered(Fonts.F3, spec.ledCount.toString(), spec.centerY, 0.85f)
    }
}
