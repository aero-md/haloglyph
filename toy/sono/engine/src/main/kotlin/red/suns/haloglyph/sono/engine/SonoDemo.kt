package red.suns.haloglyph.sono.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Une scène sonore inventée, fonction du temps seul.
 *
 * ## Pourquoi l'app ne montre jamais le vrai micro
 *
 * Les aperçus de Haloglyph exécutent le vrai moteur du toy — c'est la règle du
 * projet, et c'est ce qui garantit que la vignette et la matrice montrent la
 * même chose. Sono est la seule exception, et elle est délibérée : **ouvrir le
 * hub ne doit pas allumer le micro.**
 *
 * Un aperçu branché sur la capture allumerait l'indicateur micro d'Android à
 * chaque passage dans la liste des toys, pour une vignette de 72 dp. C'est
 * exactement le genre de chose qui fait désinstaller une app, et ce serait mérité.
 * Le micro ne s'ouvre donc que là où il sert : sur la matrice, quand le toy est
 * réellement affiché.
 *
 * Ce qui est vrai malgré tout : les données passent par le **même renderer**,
 * dans les mêmes unités et sur la même échelle. Seule la source diffère.
 *
 * ## Ce que ça imite
 *
 * Trois formants qui dérivent, une enveloppe lente, et une pente qui donne plus
 * d'énergie au grave — ce que fait à peu près n'importe quelle scène réelle une
 * fois pondérée A. Le résultat est plausible et ne se répète pas à l'œil : les
 * trois périodes sont incommensurables.
 */
class SonoDemo(private val bands: Int = 25) {

    /** Réutilisé d'un instantané à l'autre, comme celui du moteur. */
    private val values = DoubleArray(bands)

    fun snapshotAt(t: Double): SonoEngine.Snapshot {
        var sum = 0.0
        for (k in 0 until bands) {
            val p = k.toDouble() / (bands - 1)
            var a = 0.0
            for (f in FORMANTS) {
                val center = f.center + f.drift * sin(t * f.rate + f.phase)
                val d = (p - center) / f.width
                a += f.gain * exp(-d * d) * (0.55 + 0.45 * sin(t * f.rate * 2.3 + f.phase))
            }
            // Pente : le grave porte plus d'énergie, l'aigu moins.
            a *= 1.0 - 0.45 * p
            // Un plancher qui bouge un peu, sinon les bords sont morts.
            a += 0.06 + 0.03 * abs(sin(t * 0.7 + p * 3.0))

            val level = a.coerceIn(0.0, 1.0)
            values[k] = Calibration.BAND_MIN + level * (Calibration.BAND_MAX - Calibration.BAND_MIN)
            sum += level
        }

        // Le niveau large bande suit l'énergie affichée : l'aiguille et le
        // spectre racontent la même chose, comme sur une vraie mesure.
        val mean = sum / bands
        val laf = Calibration.MIN_DB + (0.18 + 0.72 * mean) * (Calibration.MAX_DB - Calibration.MIN_DB)

        return SonoEngine.Snapshot(
            laf = laf,
            las = laf,
            laeq = laf,
            lafmax = laf,
            peak = laf,
            bands = values,
            overload = false,
            status = SonoEngine.Status.OK,
            elapsed = t,
            t = t,
        )
    }

    private class Formant(
        val center: Double,
        val width: Double,
        val gain: Double,
        val rate: Double,
        val drift: Double,
        val phase: Double,
    )

    private companion object {
        val FORMANTS = arrayOf(
            Formant(center = 0.16, width = 0.13, gain = 0.85, rate = 0.83, drift = 0.06, phase = 0.0),
            Formant(center = 0.44, width = 0.10, gain = 0.62, rate = 1.31, drift = 0.10, phase = 1.7),
            Formant(center = 0.74, width = 0.16, gain = 0.44, rate = 0.47, drift = 0.13, phase = 3.4),
        )
    }
}
