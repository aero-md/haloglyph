package red.suns.haloglyph.plumb.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Une pose **inventée**, fonction pure du temps.
 *
 * C'est la seule chose du toy qui ne soit pas une mesure, et elle existe pour une
 * raison précise : la vignette du hub est composée dans une liste qu'on fait
 * défiler, et elle n'a aucun endroit où arrêter ce qu'elle a démarré. Un aperçu
 * branché sur l'accéléromètre laisserait donc un écouteur derrière lui à chaque
 * passage. Même arbitrage que l'aperçu de Sono, pour un coût plus faible mais un
 * défaut identique : une surface qui ne peut pas fermer ne doit pas ouvrir.
 *
 * L'écran de réglages, lui, **mesure vraiment** — il a un cycle de vie, donc il
 * peut rendre ce qu'il a pris.
 *
 * Ce que la vignette raconte en sept secondes est exactement ce que fait le toy :
 * un téléphone qu'on redresse, une bulle qui se pose dans la couronne, et qui
 * repart. Le cycle ne dépend que du temps écoulé, donc deux vignettes affichées
 * ensemble montrent la même chose au même instant.
 */
object PlumbDemo {

    /** Un aller-retour complet : on penche, on cale, on repart. */
    const val PERIOD = 7.0

    fun at(seconds: Double, range: PlumbRange): PlumbEngine.Reading {
        val phase = (seconds / PERIOD) % 1.0

        // Au carré : la bulle **traîne** près du centre au lieu de le traverser.
        // C'est là qu'on regarde un niveau, donc c'est là que l'aperçu doit
        // passer son temps.
        val approach = (0.5 + 0.5 * cos(2 * PI * phase))
        val wobble = 1.0 + WOBBLE * sin(2 * PI * WOBBLE_TURNS * phase)
        val tilt = (MAX_TILT * approach * approach * wobble).coerceAtLeast(0.0).toFloat()

        val direction = 2 * PI * SWIVEL * phase + START_ANGLE

        return PlumbEngine.Reading(
            tiltDegrees = tilt,
            dirX = cos(direction).toFloat(),
            dirY = sin(direction).toFloat(),
            hasTilt = true,
            // La vignette reste à plat : la bascule verticale est un régime de
            // main, pas une chose qu'un aperçu de 72 dp puisse raconter. `alongX`
            // est donc sans objet ici.
            vertical = false,
            alongX = true,
            level = tilt <= PlumbScale.tolerance(range),
            moving = false,
            heading = Heading.normalize((seconds * TURN_RATE).toFloat()),
            hasHeading = true,
            headingTrusted = true,
        )
    }

    /** L'inclinaison de départ, en degrés : penché, mais pas hors d'usage. */
    private const val MAX_TILT = 6.0

    /** Amplitude et cadence du frémissement — une main, pas un moteur pas à pas. */
    private const val WOBBLE = 0.12
    private const val WOBBLE_TURNS = 3.0

    /** Tours de la direction de pente sur un cycle. */
    private const val SWIVEL = 0.7

    /** Pour que la bulle ne parte pas systématiquement vers la droite. */
    private const val START_ANGLE = 0.8

    /** Degrés par seconde de la rose : un tour en quarante secondes. */
    private const val TURN_RATE = 9.0
}
