package red.suns.haloglyph.core.matrix

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MatrixSpecTest {

    private val spec = MatrixSpec.Phone3

    @Test
    fun `le Phone 3 porte 489 LEDs dans une grille de 625 cellules`() {
        assertEquals(25, spec.size)
        assertEquals(625, spec.cellCount)
        assertEquals(489, spec.ledCount)
    }

    @Test
    fun `la repartition par ligne est celle de la table du GDK`() {
        val expected = intArrayOf(
            7, 11, 15, 17, 19, 21, 21, 23, 23,
            25, 25, 25, 25, 25, 25, 25,
            23, 23, 21, 21, 19, 17, 15, 11, 7,
        )
        assertArrayEquals(expected, spec.ledsPerRow())
        assertEquals(489, expected.sum())
    }

    /**
     * Les deux définitions de la matrice — la table officielle et le disque
     * géométrique — doivent coïncider. Si Nothing publie une table révisée, ce
     * test dit *où* elle diverge du cercle plutôt que de laisser un décalage
     * d'une LED se propager dans les renderers.
     */
    @Test
    fun `la table coincide exactement avec le disque de rayon 12,5`() {
        for (y in 0 until 25) {
            for (x in 0 until 25) {
                val inDisc = hypot((x - 12).toFloat(), (y - 12).toFloat()) < 12.5f
                assertEquals("cellule ($x, $y)", inDisc, spec.isLed(x, y))
            }
        }
    }

    @Test
    fun `hors grille n est jamais une LED et ne leve rien`() {
        assertFalse(spec.isLed(-1, 12))
        assertFalse(spec.isLed(25, 12))
        assertFalse(spec.isLed(12, -1))
        assertFalse(spec.isLed(-1))
        assertFalse(spec.isLed(625))
        // Les coins existent dans la grille mais pas dans le disque.
        assertFalse(spec.isLed(0, 0))
        assertTrue(spec.isLed(12, 12))
    }

    @Test
    fun `l anneau de bord est un contour ferme, trie depuis midi`() {
        val ring = spec.edgeRing
        // Un contour, pas un disque : assez de LEDs pour faire le tour, très
        // loin des 489.
        assertTrue("anneau de ${ring.size} LEDs", ring.size in 60..120)
        assertEquals("aucun doublon", ring.size, ring.distinct().size)
        assertTrue("toutes physiques", ring.all { spec.isLed(it) })

        // Toutes sur le bord extérieur : aucune LED du contour n'est à plus
        // d'un rayon de LED du bord du disque.
        assertTrue(ring.all { spec.distance[it] > 10f })

        // Premier élément : le sommet, à midi.
        val first = ring.first()
        assertEquals(12, spec.xOf(first))
        assertEquals(0, spec.yOf(first))

        // Sens horaire : le quart suivant est à droite, pas à gauche.
        val quarter = ring[ring.size / 4]
        assertTrue("x=${spec.xOf(quarter)}", spec.xOf(quarter) > 20)
    }

    @Test
    fun `chaque LED du contour a un voisin direct absent`() {
        val onEdge = spec.edgeRing.toSet()
        for (i in spec.leds) {
            val x = spec.xOf(i)
            val y = spec.yOf(i)
            val exposed = !spec.isLed(x - 1, y) || !spec.isLed(x + 1, y) ||
                !spec.isLed(x, y - 1) || !spec.isLed(x, y + 1)
            assertEquals("LED ($x, $y)", exposed, i in onEdge)
        }
    }
}
