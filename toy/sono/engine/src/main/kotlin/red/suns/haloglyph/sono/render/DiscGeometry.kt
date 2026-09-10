package red.suns.haloglyph.sono.render

import red.suns.haloglyph.core.matrix.MatrixSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Les mesures du hublot dont le cadran a besoin, calculées une fois par
 * géométrie.
 *
 * `MatrixSpec` porte déjà le masque, les distances et l'anneau trié ; ce qui
 * manque ici est angulaire, et n'intéresse que le dessin d'un instrument rond.
 * Plutôt que d'alourdir le socle avec de la trigonométrie qu'un seul toy
 * utilise, on la garde là où elle sert.
 */
class DiscGeometry(val spec: MatrixSpec) {

    /** Contour du disque : une LED présente ayant au moins un voisin absent. */
    private val edge: IntArray = spec.edgeRing

    /** Angle de chaque cellule du contour, en radians, 0 = midi, sens horaire. */
    private val edgeAngle = DoubleArray(edge.size) { angleOf(edge[it]) }

    /** Anneau large : la bande extérieure, plus lisible qu'un contour d'une LED. */
    val ring: IntArray = spec.ringBand(spec.radius - 1.2f)

    /** Angle d'une cellule vue du centre, en radians, 0 = midi, sens horaire. */
    fun angleOf(index: Int): Double = atan2(
        (spec.xOf(index) - spec.centerX).toDouble(),
        -(spec.yOf(index) - spec.centerY).toDouble(),
    )

    /**
     * Distance au centre de la cellule du contour la plus proche de la direction
     * [a] — le rayon disponible pour un cadran à cet angle.
     *
     * Un balayage radial donnerait un autre résultat, et un moins bon : dans les
     * diagonales le rayon tombe entre deux cellules du contour, et l'arrondi le
     * renvoie une cellule plus bas. Le cadran décrochait du bord juste là où on
     * le regarde. En passant par le contour, l'arc est celui du disque, par
     * construction.
     */
    fun edgeDistance(a: Double): Float {
        var best = 0f
        var bestGap = Double.MAX_VALUE
        for (k in edge.indices) {
            var gap = abs(edgeAngle[k] - a)
            if (gap > PI) gap = 2 * PI - gap
            if (gap < bestGap) {
                bestGap = gap
                best = spec.distance[edge[k]]
            }
        }
        return best
    }

    /** Demi-hauteur du disque à la colonne (ou la ligne) [n], en cellules. */
    fun halfSpan(n: Int): Float {
        val d = (n - spec.centerX).toFloat()
        val r2 = spec.radius * spec.radius - d * d
        return if (r2 <= 0f) 0f else sqrt(r2)
    }

    /** Les cellules du contour dont l'angle tombe dans ±[sweep]. */
    fun arc(sweep: Double): IntArray =
        edge.filterIndexed { k, _ -> abs(edgeAngle[k]) <= sweep }.toIntArray()
}
