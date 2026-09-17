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

    /**
     * La crête instantanée est bien la crête du **signal** et non un niveau
     * intégré : sur un sinus, elle doit tomber exactement un facteur de crête
     * au-dessus du RMS, soit 3,01 dB. C'est ce qui distingue une forme d'onde
     * d'une colline.
     */
    @Test
    fun `la crete instantanee est au facteur de crete du sinus`() {
        val e = engine()
        val t = feedSine(e, 0.5, 2.0)
        val s = e.snapshot(t)
        assertEquals(3.01, s.lpeak - s.laf, 0.3)
    }

    /**
     * Lire la crête la consomme : sa fenêtre est celle entre deux instantanés,
     * sans quoi une colonne de forme d'onde garderait celle de la précédente.
     */
    @Test
    fun `la crete instantanee est consommee a la lecture`() {
        val e = engine()
        val t = feedSine(e, 0.5, 2.0)
        assertTrue(e.snapshot(t).lpeak > Calibration.MAX_DB - 10)
        assertTrue(
            "la crête aurait dû repartir de zéro",
            e.snapshot(t + 0.033).lpeak < Calibration.MIN_DB,
        )
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
     * Les bandes et le niveau large bande décrivent la même scène sonore, à
     * [Calibration.SPREAD] près : l'énergie d'un signal répartie sur 25 bandes
     * en laisse ~14 dB de moins à chacune.
     */
    @Test
    fun `l'echelle des bandes est decalee de SPREAD`() {
        assertEquals(Calibration.MIN_DB - Calibration.SPREAD, Calibration.BAND_MIN, 1e-9)
        assertEquals(Calibration.MAX_DB - Calibration.SPREAD, Calibration.BAND_MAX, 1e-9)
        assertEquals(
            Calibration.MAX_DB - Calibration.MIN_DB,
            Calibration.BAND_MAX - Calibration.BAND_MIN,
            1e-9,
        )
    }

    /**
     * Un banc de 25 bandes alimenté par un sinus pur ne met qu'**une** bande en
     * évidence : c'est ce que le spectre normalisé doit pouvoir montrer, et ce
     * qu'une échelle absolue noyait sous le fond de la pièce.
     */
    @Test
    fun `un sinus pur ne charge qu'une poignee de bandes`() {
        val e = engine()
        val t = feedSine(e, 0.3, 2.0)
        val bands = e.snapshot(t).bands
        val loudest = bands.indices.maxBy { bands[it] }
        val others = bands.indices.filter { it != loudest }
        assertTrue(
            "la bande dominante ne se détache pas",
            others.all { bands[it] < bands[loudest] - 10.0 },
        )
    }
}
