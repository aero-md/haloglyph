package red.suns.haloglyph.sono.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.sono.dsp.FLOOR_DBFS

/**
 * Le suivi du fond de scène — la pièce qui décide de ce qui mérite d'être
 * allumé.
 */
class NoiseFloorTest {

    private val dt = 1.0 / 30

    /** Pousse [seconds] de niveau constant dans le canal 0. */
    private fun soak(f: NoiseFloor, db: Double, seconds: Double) {
        repeat((seconds / dt).toInt()) { f.update(0, db, dt) }
    }

    @Test
    fun `le premier niveau amorce le plancher`() {
        val f = NoiseFloor(1)
        f.update(0, 42.0, dt)
        assertEquals(42.0, f.floorOf(0), 1e-9)
        assertEquals(0f, f.position(0, 42.0), 1e-6f)
    }

    /**
     * Le cas qui justifie toute la classe : un bruit de fond stationnaire finit
     * par ne plus rien afficher du tout.
     */
    @Test
    fun `un bruit stationnaire finit par ne plus rien allumer`() {
        val f = NoiseFloor(1)
        // Amorcé haut, comme après un claquement de porte à l'ouverture.
        f.update(0, 80.0, dt)
        soak(f, 45.0, 5.0)
        assertEquals(0f, f.position(0, 45.0), 1e-6f)
    }

    /** Et un son qui sort du fond, lui, s'affiche. */
    @Test
    fun `ce qui depasse le fond s'affiche`() {
        val f = NoiseFloor(1)
        soak(f, 45.0, 5.0)
        assertTrue("un son 20 dB au-dessus du fond doit se voir", f.position(0, 65.0) > 0.3f)
        assertEquals("butée à SPAN au-dessus du fond", 1f, f.position(0, 120.0), 1e-6f)
    }

    /** La marge existe pour que la respiration du fond n'allume rien. */
    @Test
    fun `la fluctuation du fond reste sous la marge`() {
        val f = NoiseFloor(1)
        soak(f, 45.0, 5.0)
        assertEquals(0f, f.position(0, 45.0 + NoiseFloor.GATE_DB - 0.5), 1e-6f)
    }

    /**
     * Descente rapide : une pause dans la parole doit suffire à retrouver le
     * fond réel, sinon le plancher reste perché après chaque phrase.
     */
    @Test
    fun `le plancher redescend vite vers un fond plus bas`() {
        val f = NoiseFloor(1)
        soak(f, 70.0, 2.0)
        soak(f, 40.0, 1.0)
        assertTrue("plancher resté à ${f.floorOf(0)}", f.floorOf(0) < 43.0)
    }

    /**
     * Montée lente : une phrase entière ne doit pas déplacer la référence, sinon
     * l'écran s'éteint au milieu de ce qu'on regarde.
     */
    @Test
    fun `le plancher monte lentement sous un son qui dure`() {
        val f = NoiseFloor(1)
        soak(f, 40.0, 1.0)
        val before = f.floorOf(0)
        soak(f, 80.0, 4.0)
        val climbed = f.floorOf(0) - before
        assertTrue("monté de $climbed dB en 4 s", climbed < 4.0)
        assertTrue("le son est encore visible", f.position(0, 80.0) > 0.5f)
    }

    /** Il ne dépasse jamais le niveau courant : sinon l'écran s'éteindrait. */
    @Test
    fun `le plancher ne depasse jamais le niveau courant`() {
        val f = NoiseFloor(1)
        f.update(0, 50.0, dt)
        repeat(600) { f.update(0, 50.0, dt) }
        assertTrue(f.floorOf(0) <= 50.0 + 1e-9)
    }

    /**
     * Chaque bande suit le sien : un fond écrasé dans le grave ne doit pas
     * décider de ce que montrent les aigus.
     */
    @Test
    fun `les canaux sont independants`() {
        val f = NoiseFloor(3)
        repeat(300) {
            f.update(0, 70.0, dt)
            f.update(1, 40.0, dt)
            f.update(2, 20.0, dt)
        }
        assertEquals(0f, f.position(0, 70.0), 1e-6f)
        assertEquals(0f, f.position(1, 40.0), 1e-6f)
        assertEquals(0f, f.position(2, 20.0), 1e-6f)
        // Le même niveau absolu ne dit pas la même chose selon la bande.
        assertTrue(f.position(2, 50.0) > f.position(0, 50.0))
    }

    // ---------- ce qui n'est pas une mesure ----------

    /**
     * Le défaut qui rendait le spectre et l'onde **entièrement blancs** sur la
     * matrice : le banc de bandes sort des zéros exacts tant que sa FFT n'est pas
     * pleine, donc le repli à −200 dBFS. Le plancher s'y amorçait, et comme il ne
     * remonte que de [NoiseFloor.RISE_DB_PER_S], il lui fallait plusieurs minutes
     * pour revenir — pendant lesquelles tout était en butée haute.
     */
    @Test
    fun `le repli d'un signal absent n'amorce pas le plancher`() {
        val f = NoiseFloor(1)
        val absent = FLOOR_DBFS + Calibration.K
        repeat(10) { f.update(0, absent, dt) }
        assertTrue("plancher amorcé sur une absence de mesure", f.floorOf(0).isNaN())

        // Et la première vraie mesure amorce, elle.
        soak(f, 45.0, 3.0)
        assertEquals(0f, f.position(0, 45.0), 1e-6f)
        assertTrue("le son réel doit se voir tout de suite", f.position(0, 70.0) > 0.5f)
    }

    /** Une absence de mesure ne s'affiche pas non plus, plancher établi ou non. */
    @Test
    fun `une absence de mesure n'allume rien`() {
        val f = NoiseFloor(1)
        soak(f, 45.0, 3.0)
        assertEquals(0f, f.position(0, -80.0), 1e-6f)
        assertEquals(0f, f.position(0, Double.NEGATIVE_INFINITY), 1e-6f)
        assertEquals(0f, f.position(0, Double.NaN), 1e-6f)
    }

    /** Et une interruption du flux en pleine mesure ne perd pas la référence. */
    @Test
    fun `une coupure du flux ne deplace pas le plancher`() {
        val f = NoiseFloor(1)
        soak(f, 45.0, 3.0)
        val before = f.floorOf(0)
        repeat(60) { f.update(0, FLOOR_DBFS + Calibration.K, dt) }
        assertEquals(before, f.floorOf(0), 1e-9)
    }

    @Test
    fun `clear remet les canaux a l'amorce`() {
        val f = NoiseFloor(1)
        soak(f, 45.0, 2.0)
        f.clear()
        assertEquals(0f, f.position(0, 90.0), 1e-6f)
        f.update(0, 90.0, dt)
        assertEquals(90.0, f.floorOf(0), 1e-9)
    }
}
