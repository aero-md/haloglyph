package red.suns.haloglyph.sono.dsp

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pondérations fréquentielles IEC 61672-1.
 *
 * La fonction analytique de la pondération A est
 *
 *     H_A(s) = K · s⁴ / ((s+ω₁)² (s+ω₂) (s+ω₃) (s+ω₄)²)
 *
 * factorisée ici en trois sections du second ordre, chacune passée en numérique
 * par transformée bilinéaire, puis normalisée pour que |H| = 1 à 1 kHz.
 *
 * Les quatre fréquences de pôle sont celles de la norme ; elles ne sont pas
 * approchées, seule la transformée l'est. Le prix de l'absence de pré-warping
 * est un écart qui croît avec la fréquence : négligeable jusqu'à 4 kHz, de
 * l'ordre du demi-dB à 8 kHz à 48 kHz d'échantillonnage. C'est mesuré par les
 * tests, pas supposé.
 */
object Weighting {
    const val F1 = 20.598997
    const val F2 = 107.65265
    const val F3 = 737.86223
    const val F4 = 12194.217

    /** Fréquence de référence : la pondération y vaut 0 dB par définition. */
    const val F_REF = 1000.0

    /** Coupure du passe-haut de protection (dérive DC des MEMS, infrasons). */
    const val F_HP = 10.0

    private fun w(f: Double) = 2 * PI * f

    /**
     * Cascade A complète, gain normalisé à 1 kHz.
     *
     * [withHighPass] n'existe que pour les tests : c'est la cascade **seule**
     * qui se compare au gabarit de la norme, le passe-haut étant une protection
     * maison qui creuse volontairement l'infrason (−3 dB à 10 Hz par-dessus la
     * pondération). En production il est toujours là.
     */
    fun aWeighting(fs: Double, withHighPass: Boolean = true): Filter {
        val w1 = w(F1)
        val w2 = w(F2)
        val w3 = w(F3)
        val w4 = w(F4)
        val sections = listOf(
            // s² / (s+ω₁)² — double pôle réel, remonte les basses de 12 dB/oct
            Biquad.bilinear(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(1.0, 2 * w1, w1 * w1),
                fs,
            ),
            // s² / ((s+ω₂)(s+ω₃)) — les deux pôles du médium
            Biquad.bilinear(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(1.0, w2 + w3, w2 * w3),
                fs,
            ),
            // 1 / (s+ω₄)² — redescend l'aigu
            Biquad.bilinear(
                doubleArrayOf(0.0, 0.0, 1.0),
                doubleArrayOf(1.0, 2 * w4, w4 * w4),
                fs,
            ),
        )
        return normalized(sections, fs, withHighPass)
    }

    /**
     * Pondération Z : plate, mais pas « rien ». Le passe-haut de 10 Hz reste,
     * sans quoi la dérive continue du MEMS s'ajoute au niveau mesuré et un
     * silence se lit à 50 dB.
     */
    fun zWeighting(fs: Double): Filter = Filter(listOf(highPass(fs)))

    /** Butterworth ordre 2 à [F_HP], commun à toutes les pondérations. */
    fun highPass(fs: Double): Biquad {
        val wh = w(F_HP)
        return Biquad.bilinear(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(1.0, sqrt(2.0) * wh, wh * wh),
            fs,
        )
    }

    /** Applique au premier étage le gain qui met la cascade à 0 dB à 1 kHz. */
    private fun normalized(sections: List<Biquad>, fs: Double, withHighPass: Boolean): Filter {
        var g = 1.0
        for (s in sections) g *= s.magnitude(F_REF, fs)
        val scaled = sections.toMutableList()
        scaled[0] = scaled[0].scaled(1.0 / g)
        // le passe-haut vient en tête : il protège les sections suivantes du DC
        return Filter(if (withHighPass) listOf(highPass(fs)) + scaled else scaled)
    }
}

/** Cascade de sections, appliquée dans l'ordre. */
class Filter(private val sections: List<Biquad>) {

    fun reset() = sections.forEach { it.reset() }

    fun process(x: Double): Double {
        var y = x
        for (s in sections) y = s.process(y)
        return y
    }

    /** Réponse en amplitude, en dB, pour les tests de conformité. */
    fun responseDb(f: Double, fs: Double): Double {
        var m = 1.0
        for (s in sections) m *= s.magnitude(f, fs)
        return 20 * log10(m)
    }
}
