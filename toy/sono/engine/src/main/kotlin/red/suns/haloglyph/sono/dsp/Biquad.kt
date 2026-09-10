package red.suns.haloglyph.sono.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Section du second ordre, forme directe II transposée.
 *
 * Coefficients déjà normalisés (a0 = 1). L'état est interne et mutable : une
 * instance appartient à une seule chaîne de traitement, on ne la partage pas
 * entre deux pondérations.
 */
class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    /**
     * |H(e^{jω})| à [f] Hz. Sert à normaliser le gain de la cascade à 1 kHz et
     * aux tests de conformité contre les gabarits IEC — donc jamais appelé dans
     * la boucle temps réel.
     */
    fun magnitude(f: Double, fs: Double): Double {
        val w = 2 * PI * f / fs
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val nRe = b0 + b1 * c1 + b2 * c2
        val nIm = -(b1 * s1 + b2 * s2)
        val dRe = 1.0 + a1 * c1 + a2 * c2
        val dIm = -(a1 * s1 + a2 * s2)
        return hypot(nRe, nIm) / hypot(dRe, dIm)
    }

    /** Même section, numérateur multiplié par [k] — sert à porter le gain global. */
    fun scaled(k: Double) = Biquad(b0 * k, b1 * k, b2 * k, a1, a2)

    companion object {
        /**
         * Transformée bilinéaire d'une section analogique.
         *
         * [n] et [d] sont les coefficients en **s décroissant** : `[s², s¹, s⁰]`.
         * Pas de pré-warping — c'est le design classique des pondérations
         * normalisées, dont l'écart au gabarit est mesuré plutôt que corrigé
         * (voir `WeightingTest`).
         */
        fun bilinear(n: DoubleArray, d: DoubleArray, fs: Double): Biquad {
            val c = 2.0 * fs
            val cc = c * c
            val (n2, n1, n0) = n
            val (d2, d1, d0) = d
            val b = doubleArrayOf(
                n2 * cc + n1 * c + n0,
                -2 * n2 * cc + 2 * n0,
                n2 * cc - n1 * c + n0,
            )
            val a = doubleArrayOf(
                d2 * cc + d1 * c + d0,
                -2 * d2 * cc + 2 * d0,
                d2 * cc - d1 * c + d0,
            )
            return Biquad(b[0] / a[0], b[1] / a[0], b[2] / a[0], a[1] / a[0], a[2] / a[0])
        }
    }
}
