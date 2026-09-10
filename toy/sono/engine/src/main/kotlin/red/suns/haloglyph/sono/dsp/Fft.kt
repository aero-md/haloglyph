package red.suns.haloglyph.sono.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT radix-2 en place, tables précalculées.
 *
 * Une instance = une taille, et elle réutilise ses tampons : la boucle de rendu
 * tourne à 30 fps et n'a pas à allouer 4096 doubles par image. Pas thread-safe,
 * ce qui va bien — un seul thread l'appelle.
 *
 * Écrite à la main plutôt qu'empruntée : la chaîne doit rester défendable étage
 * par étage, et une FFT radix-2 est de la taille d'une page.
 */
class Fft(val n: Int) {
    init {
        require(n > 0 && n and (n - 1) == 0) { "taille FFT non puissance de deux : $n" }
    }

    private val cosT = DoubleArray(n / 2) { cos(-2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(-2 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            r[i] = j
        }
    }

    private val re = DoubleArray(n)
    private val im = DoubleArray(n)

    /**
     * Spectre de puissance de [x] (fenêtré par l'appelant), écrit dans [out] sur
     * `n/2` bins. `out[k]` = |X_k|² normalisé par n², donc indépendant de la
     * taille de transformée.
     */
    fun powerSpectrum(x: DoubleArray, out: DoubleArray) {
        for (i in 0 until n) {
            re[rev[i]] = x[i]
            im[rev[i]] = 0.0
        }
        var len = 2
        while (len <= n) {
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until len / 2) {
                    val t = k * step
                    val wr = cosT[t]
                    val wi = sinT[t]
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
        val norm = 1.0 / (n.toDouble() * n.toDouble())
        for (k in out.indices) out[k] = (re[k] * re[k] + im[k] * im[k]) * norm
    }

    companion object {
        /** Fenêtre de Hann. */
        fun hann(n: Int) = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
    }
}
