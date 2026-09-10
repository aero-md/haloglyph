package red.suns.haloglyph.lapse.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import org.junit.Test

/**
 * Ce qu'un glissement de lapse doit être : **une translation**, et rien d'autre.
 *
 * Le défaut d'origine ne se voyait qu'à l'œil, sur la matrice, pendant 350 ms —
 * « moins fluide que dans l'app ». Il tenait à une frame sortante réécrite à
 * chaque image avec le composite de l'image précédente : les décalages
 * s'additionnaient. Ces tests le rendent visible en JVM, en une milliseconde.
 */
class LapseSlideTest {

    private val spec = MatrixSpec.Phone3

    /** Une colonne allumée, repérable : c'est elle qu'on suit d'image en image. */
    private fun column(x: Int): Frame = Frame(spec).also { frame ->
        for (y in 0 until spec.size) frame.set(x, y, 1f)
    }

    /** Colonnes allumées d'une frame, dans l'ordre. */
    private fun litColumns(frame: Frame): List<Int> =
        (0 until spec.size).filter { x ->
            (0 until spec.size).any { y -> frame[x, y] > 0.5f }
        }

    @Test
    fun `le decalage va de zero a la largeur`() {
        assertEquals(0, LapseSlide.shiftAt(0.0, spec.size))
        assertEquals(spec.size, LapseSlide.shiftAt(1.0, spec.size))
        // Borné : une image en retard donne un progrès > 1, pas un débordement.
        assertEquals(spec.size, LapseSlide.shiftAt(1.4, spec.size))
        assertEquals(0, LapseSlide.shiftAt(-0.2, spec.size))
    }

    /** Sortante devant, entrante derrière : le décalage ne recule jamais. */
    @Test
    fun `le decalage est monotone`() {
        var previous = -1
        var p = 0.0
        while (p <= 1.0) {
            val dx = LapseSlide.shiftAt(p, spec.size)
            assertTrue("recul à p=$p : $dx après $previous", dx >= previous)
            previous = dx
            p += 0.01
        }
    }

    @Test
    fun `au depart on voit le lapse sortant, a l'arrivee le lapse entrant`() {
        val outgoing = column(6)
        val incoming = column(18)

        val start = Frame(spec)
        LapseSlide.compose(start, outgoing, incoming, 0.0)
        assertEquals(listOf(6), litColumns(start))

        val end = Frame(spec)
        LapseSlide.compose(end, outgoing, incoming, 1.0)
        assertEquals(listOf(18), litColumns(end))
    }

    /**
     * Le cœur de l'affaire : à chaque instant, la marque du lapse sortant est à
     * sa colonne d'origine **moins le décalage du moment** — jamais moins la
     * somme des décalages déjà servis.
     *
     * Le test rejoue la séquence d'images telle que le service la produit, en
     * réutilisant la même sortante à chaque tour. Un service qui réinjecterait
     * son propre composite verrait la colonne filer vers la gauche et
     * disparaître au bout de trois images.
     */
    @Test
    fun `la frame sortante est translatee de sa position d'origine`() {
        val origin = 18
        val outgoing = column(origin)
        val incoming = Frame(spec)
        val target = Frame(spec)

        var seen = 0
        // Une image toutes les 33 ms, la cadence animée du service.
        val steps = (LapseSlide.DURATION / 0.033).toInt()
        for (step in 0..steps) {
            val progress = (step * 0.033) / LapseSlide.DURATION
            val dx = LapseSlide.shiftAt(progress, spec.size)
            target.clear()
            LapseSlide.compose(target, outgoing, incoming, progress)

            val expected = origin - dx
            val label = "à p=%.2f".format(progress)
            if (expected >= 0) {
                assertEquals(label, listOf(expected), litColumns(target))
                seen++
            } else {
                assertEquals("$label : rien ne doit rester", emptyList<Int>(), litColumns(target))
            }
        }
        assertTrue("séquence trop courte pour conclure", seen > 3)
    }

    /**
     * L'entrant arrive par la droite et se pose au bon endroit : à l'arrivée, la
     * frame composée est celle du nouveau lapse, à la colonne près.
     */
    @Test
    fun `le lapse entrant arrive par la droite`() {
        val outgoing = Frame(spec)
        val incoming = column(4)
        val target = Frame(spec)

        var previous = spec.size
        for (step in 0..20) {
            val p = step / 20.0
            target.clear()
            LapseSlide.compose(target, outgoing, incoming, p)
            val columns = litColumns(target)
            if (columns.isNotEmpty()) {
                val x = columns.single()
                assertTrue("l'entrant recule à p=$p : $x après $previous", x <= previous)
                previous = x
            }
        }
        assertEquals(4, previous)
    }
}
