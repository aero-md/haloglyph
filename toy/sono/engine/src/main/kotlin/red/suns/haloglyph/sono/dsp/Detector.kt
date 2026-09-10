package red.suns.haloglyph.sono.dsp

import kotlin.math.exp
import kotlin.math.log10

/**
 * Détecteur RMS exponentiel — la pondération temporelle de l'IEC 61672-1.
 *
 * `α = 1 − exp(−1/(fs·τ))` et non `1/(fs·τ)` : l'approximation au premier ordre
 * dérive de plusieurs pour cent sur les constantes courtes.
 *
 * L'état est le **carré moyen**, jamais la racine : c'est ce qui rend le
 * détecteur linéaire en énergie, donc additif, donc juste sur des salves.
 */
class Detector(fs: Double, tau: Double) {
    private val alpha = 1.0 - exp(-1.0 / (fs * tau))
    private var msq = 0.0

    fun reset() {
        msq = 0.0
    }

    fun process(x: Double): Double {
        msq += alpha * (x * x - msq)
        return msq
    }

    val meanSquare: Double get() = msq

    companion object {
        const val TAU_FAST = 0.125
        const val TAU_SLOW = 1.0
    }
}

/**
 * Intégrateur linéaire — `Leq` sur toute la durée de la session.
 *
 * Somme des carrés et compte d'échantillons, pas de moyenne glissante : le Leq
 * est défini comme une énergie moyenne sur une durée, et la garder exacte veut
 * dire ne jamais oublier un échantillon.
 */
class Integrator {
    private var sum = 0.0
    private var n = 0L

    fun reset() {
        sum = 0.0
        n = 0L
    }

    fun process(x: Double) {
        sum += x * x
        n++
    }

    val meanSquare: Double get() = if (n == 0L) 0.0 else sum / n

    /** Nombre d'échantillons intégrés — l'appelant en tire une durée. */
    val samples: Double get() = n.toDouble()
    val isEmpty: Boolean get() = n == 0L
}

/**
 * Carré moyen → dBFS. Convention du projet : 0 dBFS = RMS unité, donc un sinus
 * pleine échelle vaut −3,01 dBFS. Un carré moyen nul renvoie [FLOOR_DBFS]
 * plutôt que −∞, pour que rien en aval n'ait à tester un infini.
 */
fun msqToDbfs(msq: Double): Double =
    if (msq <= 1e-20) FLOOR_DBFS else 10 * log10(msq)

const val FLOOR_DBFS = -200.0
