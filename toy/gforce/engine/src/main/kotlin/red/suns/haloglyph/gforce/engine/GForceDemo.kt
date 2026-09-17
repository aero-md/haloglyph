package red.suns.haloglyph.gforce.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Un trajet **inventé**, fonction pure du temps.
 *
 * La vignette du hub est composée dans une liste qu'on fait défiler, et elle n'a
 * aucun endroit où arrêter ce qu'elle a démarré : un aperçu branché sur
 * l'accéléromètre laisserait un écouteur derrière lui à chaque passage. Même
 * arbitrage que Plumb et Sono — une surface qui ne peut pas fermer ne doit pas
 * ouvrir.
 *
 * ## Un tour, pas un métronome
 *
 * La première version alignait un lancer, un virage, un freinage : trois arches
 * de sinus, chacune sur un seul axe, jamais deux en même temps. Une voiture ne
 * roule pas comme ça — on freine **en tournant**, le point se déplace en diagonale
 * pendant qu'on relâche la pédale et qu'on charge l'extérieur du pneu, et un tour
 * de circuit enchaîne plusieurs virages de nature différente, pas un aller-retour
 * unique.
 *
 * Le trajet ci-dessous est donc écrit comme le fait un pilote : les deux axes,
 * [FRONT] (freinage positif, accélération négative) et [RIGHT], portent chacun
 * **leur propre liste** d'arches, posées à des instants qui se recouvrent. Un
 * freinage appuyé qui commence avant que le volant ne tourne, puis un virage qui
 * monte pendant que le frein se relâche : la bille trace alors un arc, pas une
 * ligne droite, exactement comme sur un vrai freinage dégressif en courbe
 * (« trail braking »).
 *
 * La chicane (± 0,49) est le seul endroit où la bille change franchement de côté
 * en moins d'une seconde — ce qu'un simple virage ne montre jamais, et que le
 * toy doit pourtant savoir dessiner.
 */
object GForceDemo {

    /** Le tour complet, en secondes. */
    const val PERIOD = 16.0

    /** `(début, fin, amplitude signée en g)` — voir [FRONT] et [RIGHT]. */
    private class Arc(val from: Double, val to: Double, val amp: Double)

    /**
     * Freinage (positif) et accélération (négatif), dans l'ordre du tour :
     * lancer, freinage dégressif du premier virage, remise de gaz, léger coup de
     * frein pour la chicane, longue accélération, gros freinage du dernier
     * virage serré, remise de gaz en sortie.
     */
    private val FRONT = arrayOf(
        Arc(0.00, 0.09, -0.35), // lancer
        Arc(0.09, 0.20, 1.10), // trail-brake, virage 1
        Arc(0.23, 0.33, -0.55), // sortie virage 1
        Arc(0.39, 0.44, 0.40), // entrée chicane
        Arc(0.48, 0.52, 0.25), // compression centrale
        Arc(0.56, 0.71, -0.75), // ligne droite
        Arc(0.73, 0.87, 1.30), // trail-brake, épingle
        Arc(0.89, 0.98, -0.50), // sortie d'épingle
    )

    /**
     * Latéral, dans l'ordre du tour : entrée du premier virage, léger
     * contre-braquage en sortie, chicane gauche-droite franche, un appui rapide
     * avant l'épingle, l'épingle elle-même — qui recouvre la fin du gros
     * freinage — puis la correction de sortie.
     */
    private val RIGHT = arrayOf(
        Arc(0.13, 0.29, 0.85), // virage 1
        Arc(0.29, 0.35, -0.15), // contre-braquage
        Arc(0.43, 0.49, -0.90), // chicane, premier appui
        Arc(0.49, 0.55, 0.90), // chicane, second appui
        Arc(0.65, 0.75, 0.55), // appui avant l'épingle
        Arc(0.75, 0.91, -1.05), // épingle
        Arc(0.91, 0.98, 0.20), // sortie
    )

    fun at(seconds: Double): GForceEngine.Reading {
        val phase = (seconds / PERIOD) % 1.0
        val front = sumOf(FRONT, phase)
        val right = sumOf(RIGHT, phase)

        return GForceEngine.Reading(
            towardFront = front.toFloat(),
            towardRight = right.toFloat(),
            hasFix = true,
            front = peakOf(FRONT) { it > 0 }.toFloat(),
            rear = peakOf(FRONT) { it < 0 }.toFloat(),
            left = peakOf(RIGHT) { it < 0 }.toFloat(),
            right = peakOf(RIGHT) { it > 0 }.toFloat(),
        )
    }

    private fun sumOf(arcs: Array<Arc>, phase: Double): Double {
        var v = 0.0
        for (a in arcs) v += a.amp * bump(phase, a.from, a.to)
        return v
    }

    /** La plus grande amplitude des arches qui vérifient [side] — sans lui, `0`. */
    private inline fun peakOf(arcs: Array<Arc>, side: (Double) -> Boolean): Double =
        arcs.filter { side(it.amp) }.maxOfOrNull { abs(it.amp) } ?: 0.0

    /**
     * Une demi-arche de sinus entre [from] et [to], nulle ailleurs.
     *
     * Elle part et revient à zéro **avec une pente nulle**, ce qui compte plus
     * qu'il n'y paraît : une rampe linéaire ferait démarrer et arrêter la bille
     * d'un coup, et la vignette aurait l'air de sauter plutôt que de rouler.
     */
    private fun bump(phase: Double, from: Double, to: Double): Double {
        if (phase < from || phase > to) return 0.0
        return sin(PI * (phase - from) / (to - from))
    }
}
