package red.suns.haloglyph.sono.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Comportement du moteur — le temps est injecté, donc une minute de mesure se
 * rejoue en quelques millisecondes.
 */
class SonoEngineTest {

    private val fs = 48000.0

    private fun engine() = SonoEngine(fs).apply { status = SonoEngine.Status.OK }

    /** [seconds] de sinus 1 kHz d'amplitude [amp], poussé par blocs de 1024. */
    private fun feedSine(e: SonoEngine, amp: Double, seconds: Double, t0: Double = 0.0): Double {
        val block = FloatArray(1024)
        var n = 0
        var t = t0
        val total = (fs * seconds).toInt()
        while (n < total) {
            for (i in block.indices) {
                block[i] = (amp * sin(2 * PI * 1000 * (n + i) / fs)).toFloat()
            }
            n += block.size
            t = t0 + n / fs
            e.feed(block, block.size, t)
        }
        return t
    }

    @Test
    fun `un flux de zeros exacts est reconnu comme micro coupe, pas comme silence`() {
        val e = engine()
        val zeros = FloatArray(1024)
        var t = 0.0
        // moins d'une seconde : on ne conclut pas encore
        repeat(20) {
            t += 1024 / fs
            e.feed(zeros, zeros.size, t)
        }
        assertEquals(SonoEngine.Status.OK, e.status)

        // au-delà d'une seconde de zéros exacts, plus de doute
        repeat(40) {
            t += 1024 / fs
            e.feed(zeros, zeros.size, t)
        }
        assertEquals(SonoEngine.Status.MUTED, e.status)
    }

    @Test
    fun `un signal tres faible reste une mesure`() {
        val e = engine()
        // −100 dBFS : inaudible, mais pas nul — c'est un plancher de bruit
        feedSine(e, 1e-5, 1.5)
        assertEquals(SonoEngine.Status.OK, e.status)
    }

    @Test
    fun `la surcharge se latche puis retombe`() {
        val e = engine()
        val clip = FloatArray(1024) { if (it % 2 == 0) 1.0f else -1.0f }
        e.feed(clip, clip.size, 10.0)
        assertTrue(e.snapshot(10.1).overload)
        assertTrue(e.snapshot(11.4).overload)
        assertFalse(e.snapshot(11.6).overload)
    }

    @Test
    fun `le marqueur de crete tient puis redescend vers le niveau courant`() {
        val e = engine()
        var t = feedSine(e, 0.5, 2.0)
        val loud = e.snapshot(t)
        assertTrue("niveau attendu élevé", loud.peak > loud.laf - 0.5)

        // le signal s'effondre de 40 dB ; la crête doit tenir PEAK_HOLD secondes
        t = feedSine(e, 0.005, 1.0, t)
        val held = e.snapshot(t + 0.5)
        assertTrue("crête relâchée trop tôt", held.peak > held.laf + 20)

        // puis redescendre à PEAK_FALL dB/s
        var s = held
        var k = t + 0.5
        repeat(60) {
            k += 0.05
            s = e.snapshot(k)
        }
        assertTrue("crête bloquée en haut", s.peak < held.peak - 20)
    }

    @Test
    fun `le reset efface maximum, crete et integration`() {
        val e = engine()
        val t = feedSine(e, 0.5, 2.0)
        assertTrue(e.snapshot(t).lafmax > Calibration.MIN_DB + 10)

        e.reset()
        val after = e.snapshot(t + 0.01)
        assertEquals(Calibration.MIN_DB, after.laeq, 1e-9)
        // le maximum repart du courant, pas de l'ancien pic
        assertTrue(after.lafmax <= after.laf + 1e-6)
    }

    @Test
    fun `la position sur l'echelle est bornee`() {
        assertEquals(0.0, Calibration.position(0.0), 1e-9)
        assertEquals(0.0, Calibration.position(Calibration.MIN_DB), 1e-9)
        assertEquals(1.0, Calibration.position(Calibration.MAX_DB), 1e-9)
        assertEquals(1.0, Calibration.position(200.0), 1e-9)
        assertEquals(0.5, Calibration.position(70.0), 1e-9)
    }

    /**
     * Les bandes et le niveau large bande partagent la même scène sonore : c'est
     * ce que fait [Calibration.SPREAD], et c'est ce qui permet de passer d'un
     * mode à l'autre sans que « fort » change de sens.
     */
    @Test
    fun `l'echelle des bandes est decalee de SPREAD`() {
        assertEquals(0f, Calibration.bandPosition(Calibration.BAND_MIN), 1e-6f)
        assertEquals(1f, Calibration.bandPosition(Calibration.BAND_MAX), 1e-6f)
        assertEquals(
            Calibration.position(70.0).toFloat(),
            Calibration.bandPosition(70.0 - Calibration.SPREAD),
            1e-6f,
        )
    }
}
