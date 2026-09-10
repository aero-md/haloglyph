package red.suns.haloglyph.sono.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * Conformité de la chaîne DSP.
 *
 * Les tolérances ne sont pas rondes : ce sont les écarts **mesurés** de cette
 * implémentation, arrondis au cran supérieur. Un gabarit choisi large ne dit
 * rien ; celui-ci casse dès que la factorisation ou la transformée bouge.
 */
class WeightingTest {

    private val fs = 48000.0

    /** Valeurs nominales de la pondération A, IEC 61672-1. */
    private val iecA = listOf(
        10.0 to -70.4, 12.5 to -63.4, 16.0 to -56.7, 20.0 to -50.5, 25.0 to -44.7,
        31.5 to -39.4, 40.0 to -34.6, 50.0 to -30.2, 63.0 to -26.2, 80.0 to -22.5,
        100.0 to -19.1, 125.0 to -16.1, 160.0 to -13.4, 200.0 to -10.9, 250.0 to -8.6,
        315.0 to -6.6, 400.0 to -4.8, 500.0 to -3.2, 630.0 to -1.9, 800.0 to -0.8,
        1000.0 to 0.0, 1250.0 to 0.6, 1600.0 to 1.0, 2000.0 to 1.2, 2500.0 to 1.3,
        3150.0 to 1.2, 4000.0 to 1.0, 5000.0 to 0.5, 6300.0 to -0.1, 8000.0 to -1.1,
    )

    @Test
    fun `ponderation A dans le gabarit entre 20 Hz et 4 kHz`() {
        val a = Weighting.aWeighting(fs, withHighPass = false)
        var worst = 0.0
        for ((f, ref) in iecA) {
            if (f < 20.0 || f > 4000.0) continue
            val err = a.responseDb(f, fs) - ref
            if (abs(err) > abs(worst)) worst = err
        }
        assertTrue("écart max $worst dB", abs(worst) <= 0.30)
    }

    /**
     * Au-delà de 4 kHz la bilinéaire sans pré-warping serre la réponse vers
     * Nyquist. C'est assumé et borné, pas ignoré : −0,59 dB à 8 kHz, ce qui
     * reste dans la tolérance classe 1 (±1,1 dB à cette fréquence).
     */
    @Test
    fun `derive haute frequence bornee a 0,6 dB jusqu'a 8 kHz`() {
        val a = Weighting.aWeighting(fs, withHighPass = false)
        var worst = 0.0
        for ((f, ref) in iecA) {
            val err = a.responseDb(f, fs) - ref
            if (abs(err) > abs(worst)) worst = err
        }
        assertTrue("écart max $worst dB", abs(worst) <= 0.60)
    }

    @Test
    fun `gain exactement unitaire a 1 kHz`() {
        val a = Weighting.aWeighting(fs, withHighPass = false)
        assertEquals(0.0, a.responseDb(Weighting.F_REF, fs), 1e-9)
    }

    @Test
    fun `le passe-haut retire 3 dB a 10 Hz et rien a 1 kHz`() {
        val nu = Weighting.aWeighting(fs, withHighPass = false)
        val full = Weighting.aWeighting(fs, withHighPass = true)
        assertEquals(-3.01, full.responseDb(10.0, fs) - nu.responseDb(10.0, fs), 0.05)
        assertEquals(0.0, full.responseDb(1000.0, fs) - nu.responseDb(1000.0, fs), 0.01)
    }

    // ---------- niveau ----------

    private class Chain(fs: Double) {
        val filter = Weighting.aWeighting(fs)
        val fast = Detector(fs, Detector.TAU_FAST)
        val slow = Detector(fs, Detector.TAU_SLOW)
        val leq = Integrator()

        fun push(x: Double) {
            val y = filter.process(x)
            fast.process(y)
            slow.process(y)
            leq.process(y)
        }
    }

    private fun sine(amp: Double, f: Double, seconds: Double): Chain {
        val c = Chain(fs)
        val n = (fs * seconds).toInt()
        for (i in 0 until n) c.push(amp * sin(2 * PI * f * i / fs))
        return c
    }

    @Test
    fun `un sinus pleine echelle vaut moins 3,01 dBFS`() {
        val c = sine(1.0, 1000.0, 3.0)
        assertEquals(-3.0103, msqToDbfs(c.fast.meanSquare), 0.02)
        assertEquals(-3.0103, msqToDbfs(c.leq.meanSquare), 0.05)
    }

    @Test
    fun `la chaine est lineaire en niveau`() {
        val loud = msqToDbfs(sine(1.0, 1000.0, 3.0).fast.meanSquare)
        val quiet = msqToDbfs(sine(0.1, 1000.0, 3.0).fast.meanSquare)
        assertEquals(-20.0, quiet - loud, 0.02)
    }

    @Test
    fun `la ponderation se retrouve sur le niveau a 100 Hz`() {
        val ref = msqToDbfs(sine(1.0, 1000.0, 4.0).slow.meanSquare)
        val low = msqToDbfs(sine(1.0, 100.0, 4.0).slow.meanSquare)
        assertEquals(-19.1, low - ref, 0.3)
    }

    // ---------- détecteurs ----------

    /** Temps mis pour franchir [targetDb] sur un échelon d'énergie unité. */
    private fun riseTime(tau: Double, targetDb: Double): Double {
        val det = Detector(fs, tau)
        for (i in 0 until (fs * 5).toInt()) {
            det.process(1.0)
            if (msqToDbfs(det.meanSquare) >= targetDb) return i / fs
        }
        return Double.MAX_VALUE
    }

    @Test
    fun `les constantes de temps sont celles de la norme`() {
        // après une constante de temps le carré moyen vaut 1 − 1/e
        val target = 10 * log10(1 - 1 / E)
        assertEquals(Detector.TAU_FAST, riseTime(Detector.TAU_FAST, target), 0.002)
        assertEquals(Detector.TAU_SLOW, riseTime(Detector.TAU_SLOW, target), 0.01)
    }

    // ---------- banc de bandes ----------

    @Test
    fun `le banc conserve l'energie et place le sinus dans la bonne bande`() {
        val ba = BandAnalyzer(fs, 25)
        for (i in 0 until 8192) ba.push(sin(2 * PI * 1000 * i / fs))
        val out = DoubleArray(25)
        ba.analyze(out)

        // un sinus d'amplitude 1 a un carré moyen de 0,5
        assertEquals(0.5, out.sum(), 0.02)

        // 25·ln(1000/40)/ln(400) = 13,4 → la bande 13 contient 1 kHz
        val peak = out.indices.maxByOrNull { out[it] }
        assertEquals(13, peak)
    }
}
