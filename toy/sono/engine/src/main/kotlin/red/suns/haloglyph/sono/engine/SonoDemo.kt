package red.suns.haloglyph.sono.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
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
 *
 * S'y ajoute une **crête hachée**, qui n'a rien à voir avec les formants : c'est
 * ce que lit le visualiseur, et c'est la seule grandeur de la scène qui doive
 * bouger d'une image à l'autre plutôt que d'une seconde à l'autre.
 */
class SonoDemo(private val bands: Int = 25) {

    /** Réutilisés d'un instantané à l'autre, comme ceux du moteur. */
    private val values = DoubleArray(bands)
    private val peaks = DoubleArray(SLICES)

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
        val range = Calibration.MAX_DB - Calibration.MIN_DB
        val laf = Calibration.MIN_DB + (0.18 + 0.72 * mean) * range

        // La crête, elle, ne suit plus le niveau large bande : un signal calé sur
        // `mean` reste dans une fenêtre étroite — la moyenne de vingt-cinq bandes
        // dont la plupart ne portent que le plancher — et l'onde qui en sortait
        // ne quittait jamais le haut du disque. [waveEnvelope] est écrite à la
        // main pour l'inverse : de vrais creux de silence entre des coups francs
        // de formes différentes, comme une syllabe qui attaque puis retombe.
        //
        // Découpée en tranches comme celle du moteur, sinon l'aperçu ne
        // montrerait pas la même onde que la matrice : il défilerait à la même
        // vitesse, mais par paliers de trois colonnes identiques.
        var lpeak = Double.NEGATIVE_INFINITY
        for (j in 0 until SLICES) {
            val at = t - (SLICES - 1 - j) * SonoEngine.PEAK_SLICE_SECONDS
            val db = Calibration.MIN_DB + (0.05 + 0.88 * waveEnvelope(at)) * range
            peaks[j] = db
            if (db > lpeak) lpeak = db
        }

        return SonoEngine.Snapshot(
            laf = laf,
            las = laf,
            laeq = laf,
            lafmax = laf,
            peak = laf,
            lpeak = lpeak,
            lpeaks = peaks,
            lpeakCount = SLICES,
            bands = values,
            overload = false,
            status = SonoEngine.Status.OK,
            elapsed = t,
            t = t,
        )
    }

    /**
     * L'enveloppe de la forme d'onde, à l'instant [t] — **indépendante du
     * spectre**, et c'est le point.
     *
     * `mean` moyenne vingt-cinq bandes dont la plupart ne portent que le
     * plancher : le signal qui en résultait ne descendait jamais assez bas ni ne
     * montait assez haut, et une vague qui reste au milieu du disque se lit comme
     * plate. Ici chaque [Hit] est écrit à la main — un instant, une amplitude, une
     * attaque et une chute — et l'enveloppe est le **maximum** de tous les coups
     * actifs, comme une syllabe qui attaque puis retombe avant la suivante. Les
     * coups ne se recouvrent qu'à peine : entre deux, l'onde redescend vraiment
     * vers le silence plutôt que de rebondir sur un plancher qui ne la lâche pas.
     *
     * Attaque et chute varient d'un coup à l'autre — de 15 ms à 80 ms — pour que
     * le tracé ne montre pas toujours la même forme : certains sont des piques
     * franches, d'autres des gonflements plus lents.
     */
    private fun waveEnvelope(t: Double): Double {
        val cycle = ((t % WAVE_CYCLE) + WAVE_CYCLE) % WAVE_CYCLE
        var v = FLOOR_NOISE + FLOOR_JAG * abs(sin(t * 5.3))
        for (h in HITS) {
            val tau = cycle - h.at
            if (tau < 0) continue
            val shape = if (tau < h.attack) tau / h.attack else exp(-(tau - h.attack) / h.decay)
            v = max(v, h.amp * shape)
        }
        return v.coerceIn(0.0, 1.0)
    }

    private class Formant(
        val center: Double,
        val width: Double,
        val gain: Double,
        val rate: Double,
        val drift: Double,
        val phase: Double,
    )

    /** Un coup de l'onde : démarre à [at] (dans `0 until WAVE_CYCLE`), monte en [attack] secondes, retombe en [decay]. */
    private class Hit(val at: Double, val amp: Double, val attack: Double, val decay: Double)

    private companion object {
        /**
         * Tranches de crête par instantané : de quoi couvrir une image de 33 ms.
         *
         * Le compte n'a pas à être juste au près. L'onde se cale sur les
         * horodatages et non sur le nombre de tranches : deux qui tombent dans la
         * même colonne s'y fondent au maximum, et une colonne qui n'en reçoit
         * aucune reprend la précédente.
         */
        const val SLICES = 2

        val FORMANTS = arrayOf(
            Formant(center = 0.16, width = 0.13, gain = 0.85, rate = 0.83, drift = 0.06, phase = 0.0),
            Formant(center = 0.44, width = 0.10, gain = 0.62, rate = 1.31, drift = 0.10, phase = 1.7),
            Formant(center = 0.74, width = 0.16, gain = 0.44, rate = 0.47, drift = 0.13, phase = 3.4),
        )

        /** Longueur du motif de l'onde, en secondes, avant qu'il ne se répète. */
        const val WAVE_CYCLE = 3.6

        /** Ce qui reste entre deux coups : un fond qui bouge un peu, pas un zéro mort. */
        const val FLOOR_NOISE = 0.03
        const val FLOOR_JAG = 0.025

        /**
         * Les coups, à la main : instant, amplitude, montée, chute. Trois
         * groupes séparés par un vrai creux — pas une syllabe continue — pour
         * que l'onde montre aussi bien un silence qu'une attaque.
         */
        val HITS = arrayOf(
            Hit(at = 0.05, amp = 0.55, attack = 0.02, decay = 0.10),
            Hit(at = 0.22, amp = 0.85, attack = 0.015, decay = 0.14),
            Hit(at = 0.34, amp = 0.35, attack = 0.02, decay = 0.08),
            // creux de silence jusqu'à 0.55
            Hit(at = 0.55, amp = 0.95, attack = 0.03, decay = 0.22),
            Hit(at = 0.95, amp = 0.28, attack = 0.06, decay = 0.18),
            // creux de silence jusqu'à 1.35
            Hit(at = 1.35, amp = 0.70, attack = 0.02, decay = 0.12),
            Hit(at = 1.50, amp = 0.50, attack = 0.015, decay = 0.08),
            Hit(at = 1.62, amp = 0.60, attack = 0.02, decay = 0.10),
            Hit(at = 1.90, amp = 0.22, attack = 0.08, decay = 0.30),
            // creux de silence jusqu'à 2.40
            Hit(at = 2.40, amp = 1.00, attack = 0.02, decay = 0.25),
            Hit(at = 2.75, amp = 0.45, attack = 0.03, decay = 0.12),
            Hit(at = 3.10, amp = 0.65, attack = 0.02, decay = 0.16),
            Hit(at = 3.30, amp = 0.30, attack = 0.04, decay = 0.10),
            // creux de silence jusqu'à la fin du cycle (3.6)
        )
    }
}
