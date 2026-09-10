package red.suns.haloglyph.sono.dsp

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Banc de bandes logarithmiques pour l'affichage.
 *
 * 25 bandes de 40 Hz à 16 kHz, soit un rapport de 1,271 — le tiers d'octave, à
 * un cheveu près, et une bande par colonne de la matrice. Ce n'est pas un banc
 * IEC 61260 : les bandes viennent d'un regroupement de bins de FFT, dont les
 * flancs sont ceux de la fenêtre de Hann et non ceux d'un gabarit. Pour un
 * affichage c'est exactement ce qu'il faut ; pour une mesure de spectre
 * défendable il faudrait un banc IIR décimé.
 *
 * FFT de 4096 points : c'est la taille retenue par la théorie du projet
 * d'origine pour le **spectrogramme d'affichage**, là où la lisibilité prime sur
 * l'exactitude et où personne ne lit le niveau exact de la bande à 20 Hz.
 *
 * L'entrée est le signal **déjà pondéré A**. Le spectre brut d'une scène réelle
 * est écrasé par le grave : sans pondération les trois premières colonnes
 * saturent en permanence et les vingt-deux autres ne bougent jamais. Pondéré,
 * l'affichage montre ce qui contribue réellement au chiffre affiché.
 */
class BandAnalyzer(
    private val fs: Double,
    val bands: Int = 25,
    fLow: Double = 40.0,
    fHigh: Double = 16000.0,
    private val fftSize: Int = 4096,
) {
    private val fft = Fft(fftSize)
    private val window = Fft.hann(fftSize)
    private val ring = DoubleArray(fftSize)
    private var head = 0
    private var filled = 0

    private val block = DoubleArray(fftSize)
    private val spectrum = DoubleArray(fftSize / 2)

    /** Premier et dernier bin de chaque bande, bornes incluses. */
    private val binLo = IntArray(bands)
    private val binHi = IntArray(bands)

    init {
        val ratio = (fHigh / fLow).pow(1.0 / bands)
        val nyquistBin = fftSize / 2 - 1
        for (k in 0 until bands) {
            val f0 = fLow * ratio.pow(k.toDouble())
            val f1 = fLow * ratio.pow(k + 1.0)
            var lo = (f0 * fftSize / fs).roundToInt().coerceIn(1, nyquistBin)
            val hi = (f1 * fftSize / fs).roundToInt().coerceIn(1, nyquistBin)
            // au moins un bin par bande : sous ~90 Hz deux bornes voisines
            // tombent dans le même bin, et une bande vide afficherait un trou
            if (lo > hi) lo = hi
            binLo[k] = lo
            binHi[k] = hi
        }
    }

    fun reset() {
        ring.fill(0.0)
        head = 0
        filled = 0
    }

    fun push(x: Double) {
        ring[head] = x
        head = (head + 1) % fftSize
        if (filled < fftSize) filled++
    }

    val isReady: Boolean get() = filled >= fftSize

    /**
     * Écrit dans [out] le **carré moyen** de chaque bande. Appelé au rythme de
     * l'affichage (30 fps), pas à celui du signal : une FFT de 4096 points par
     * image reste sous le pour-cent d'un cœur.
     */
    fun analyze(out: DoubleArray) {
        require(out.size == bands)
        if (!isReady) {
            out.fill(0.0)
            return
        }
        for (i in 0 until fftSize) {
            block[i] = ring[(head + i) % fftSize] * window[i]
        }
        fft.powerSpectrum(block, spectrum)
        for (k in 0 until bands) {
            var s = 0.0
            for (b in binLo[k]..binHi[k]) s += spectrum[b]
            out[k] = s * POWER_SCALE
        }
    }

    companion object {
        /**
         * Repli du spectre bilatéral (×2) et gain cohérent de la fenêtre de Hann
         * (moyenne de w², soit 3/8). Le produit ramène la somme des bins au
         * carré moyen du signal.
         */
        const val POWER_SCALE = 2.0 / 0.375

        /** Carré moyen d'une bande → dB SPL, avec la même convention que le niveau. */
        fun bandDb(msq: Double, calibrationK: Double): Double =
            if (msq <= 1e-20) FLOOR_DBFS else 10 * log10(msq) + calibrationK
    }
}
