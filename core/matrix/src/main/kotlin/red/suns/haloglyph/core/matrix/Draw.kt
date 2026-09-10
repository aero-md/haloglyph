package red.suns.haloglyph.core.matrix

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Segment : un cœur net d'un pixel, plus une épaule en demi-teinte du côté où le
 * trait passe entre deux cellules.
 *
 * Un anticrénelage classique — répartir chaque échantillon sur ses quatre
 * voisins — donne ici un trait large de deux cellules, gris des deux côtés. Sur
 * 25 LEDs, une aiguille de deux cellules de large n'est plus une aiguille.
 * L'épaule ne s'allume donc qu'au-delà d'un quart de cellule d'écart, et
 * proportionnellement : le trait reste plein et droit, et le gris ne sert qu'à
 * casser l'escalier des obliques.
 *
 * C'est le seul endroit du socle où la nuance est produite par le dessin et non
 * par une valeur : ailleurs, un pixel gris code quelque chose. Ici il rattrape
 * la grille, et rien de plus.
 */
fun Frame.line(x0: Float, y0: Float, x1: Float, y1: Float, brightness: Float = 1f) {
    val dx = x1 - x0
    val dy = y1 - y0
    val n = max(1, (max(abs(dx), abs(dy)) * 2).roundToInt())
    val flat = abs(dx) >= abs(dy)
    for (k in 0..n) {
        val t = k.toFloat() / n
        val x = x0 + dx * t
        val y = y0 + dy * t
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        set(xi, yi, brightness)
        val f = if (flat) y - yi else x - xi
        val shoulder = 2 * abs(f) - SHOULDER
        if (shoulder > 0f) {
            val step = if (f > 0) 1 else -1
            if (flat) set(xi, yi + step, brightness * shoulder)
            else set(xi + step, yi, brightness * shoulder)
        }
    }
}

/** Écart minimal, en cellules, avant qu'un trait allume son épaule. */
private const val SHOULDER = 0.5f
