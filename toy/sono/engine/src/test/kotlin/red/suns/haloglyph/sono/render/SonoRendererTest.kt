package red.suns.haloglyph.sono.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.sono.engine.Calibration
import red.suns.haloglyph.sono.engine.SonoDemo
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.engine.SonoMode

/**
 * Les trois modes, vérifiés sur la frame plutôt qu'à l'œil sur une matrice.
 */
class SonoRendererTest {

    private val spec = MatrixSpec.Phone3
    private val demo = SonoDemo()

    private fun frameOf(
        mode: SonoMode,
        renderer: SonoRenderer = SonoRenderer(spec),
        t: Double = 3.0,
    ): Frame = Frame(spec).also { renderer.render(it, demo.snapshotAt(t), mode) }

    private fun lit(frame: Frame) = frame.values.count { it > 0f }

    @Test
    fun `les trois modes montrent trois choses differentes`() {
        val renderer = SonoRenderer(spec)
        // L'histoire est remplie une fois pour toutes : les trois modes voient
        // le même instant, seule la façon de le montrer change.
        var t = 0.0
        repeat(60) {
            renderer.render(Frame(spec), demo.snapshotAt(t), SonoMode.SPECTRE)
            t += 0.1
        }

        val images = SonoMode.entries.map { mode ->
            Frame(spec).also { renderer.render(it, demo.snapshotAt(t), mode, feedHistory = false) }
                .values.copyOf()
        }
        for (i in images.indices) {
            assertTrue("mode ${SonoMode.entries[i]} vide", images[i].any { it > 0f })
            for (j in i + 1 until images.size) {
                assertNotEquals(
                    "${SonoMode.entries[i]} et ${SonoMode.entries[j]} rendent la même image",
                    images[i].toList(),
                    images[j].toList(),
                )
            }
        }
    }

    /**
     * Le spectre est en tout ou rien : sur 25 LEDs de côté, une nuance ne se lit
     * pas comme une nuance mais comme une LED qui hésite.
     */
    @Test
    fun `le spectre n'emploie aucune demi-teinte`() {
        val frame = frameOf(SonoMode.SPECTRE)
        val greys = frame.values.filter { it > 0f && it < 1f }
        assertTrue("demi-teintes trouvées : $greys", greys.isEmpty())
    }

    /**
     * L'exception assumée : dans un spectrogramme les deux axes sont pris par la
     * fréquence et le temps, l'intensité **est** la donnée. Mais par paliers —
     * une rampe continue se lirait comme du bruit.
     */
    @Test
    fun `le spectrogramme code le niveau en paliers`() {
        val renderer = SonoRenderer(spec)
        var t = 0.0
        repeat(80) {
            renderer.render(Frame(spec), demo.snapshotAt(t), SonoMode.SPECTROGRAMME)
            t += 0.1
        }
        val frame = Frame(spec)
        renderer.render(frame, demo.snapshotAt(t), SonoMode.SPECTROGRAMME, feedHistory = false)

        val levels = frame.values.filter { it > 0f }.distinct()
        assertTrue("spectrogramme vide", levels.isNotEmpty())
        assertTrue("plus de trois paliers allumés : $levels", levels.size <= 3)
    }

    /** L'histoire se remplit du haut vers le bas : au début, le bas est noir. */
    @Test
    fun `le spectrogramme se remplit du haut`() {
        val renderer = SonoRenderer(spec)
        renderer.render(Frame(spec), demo.snapshotAt(0.0), SonoMode.SPECTROGRAMME)
        renderer.render(Frame(spec), demo.snapshotAt(0.25), SonoMode.SPECTROGRAMME)

        val frame = Frame(spec)
        renderer.render(frame, demo.snapshotAt(0.3), SonoMode.SPECTROGRAMME, feedHistory = false)

        val bottom = (spec.size / 2 until spec.size).sumOf { y ->
            (0 until spec.size).count { x -> frame[x, y] > 0f }
        }
        assertEquals("le bas devrait encore être vide", 0, bottom)
    }

    /** Sans micro, aucun mode ne meurt noir. */
    @Test
    fun `sans mesure chaque mode montre quelque chose`() {
        val idle = SonoEngine.Snapshot(
            laf = Calibration.MIN_DB,
            las = Calibration.MIN_DB,
            laeq = Calibration.MIN_DB,
            lafmax = Calibration.MIN_DB,
            peak = Calibration.MIN_DB,
            bands = DoubleArray(25) { Calibration.BAND_MIN },
            overload = false,
            status = SonoEngine.Status.NO_MIC,
            elapsed = 0.0,
            t = 1.0,
        )
        for (mode in SonoMode.entries) {
            val frame = Frame(spec)
            SonoRenderer(spec).render(frame, idle, mode)
            assertTrue("$mode est noir sans micro", lit(frame) > 10)
        }
    }

    /** La surcharge doit rester lisible par-dessus n'importe quel mode. */
    @Test
    fun `la surcharge allume l'anneau dans tous les modes`() {
        val geometry = DiscGeometry(spec)
        for (mode in SonoMode.entries) {
            val frame = Frame(spec)
            SonoRenderer(spec).render(
                frame,
                demo.snapshotAt(2.0).copy(overload = true),
                mode,
            )
            assertTrue(
                "$mode : anneau incomplet",
                geometry.ring.all { frame.values[it] >= 1f },
            )
        }
    }

    /** Le tour du mode suivant boucle, et ne saute personne. */
    @Test
    fun `la rotation des modes fait le tour`() {
        var mode = SonoMode.DEFAULT
        val seen = mutableListOf(mode)
        repeat(SonoMode.entries.size - 1) {
            mode = mode.next
            seen += mode
        }
        assertEquals(SonoMode.entries.toSet(), seen.toSet())
        assertEquals(SonoMode.DEFAULT, mode.next)
        assertEquals(SonoMode.DEFAULT, SonoMode.byKey("n'importe quoi"))
        assertEquals(SonoMode.AIGUILLE, SonoMode.byKey("AIGUILLE"))
    }
}
