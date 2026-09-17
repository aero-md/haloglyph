package red.suns.haloglyph.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'apparence émulée est une **table partagée**, pas une préférence de chaque
 * surface. Ces vérifications sont modestes mais elles tiennent la seule chose
 * qui compte : que l'aperçu de l'app et le widget ne puissent pas diverger sans
 * que quelqu'un le décide explicitement ici.
 */
class MatrixLookTest {

    @Test
    fun `une LED et sa gouttiere remplissent exactement une cellule`() {
        val pitch = 12f
        val total = MatrixLook.ledSize(pitch) + 2 * MatrixLook.inset(pitch)
        assertEquals(pitch, total, 1e-4f)
    }

    @Test
    fun `la LED est carree et laisse voir la grille`() {
        // Ni aplat (1,0) ni semis de points perdus : un peu plus d'un quart de
        // gouttière, la cote relevée par le portail sur un Phone (3).
        assertTrue(MatrixLook.DUTY in 0.5f..0.85f)
    }

    /**
     * Une LED éteinte est du plastique gris, pas du noir : sans elle la
     * silhouette de la matrice n'existe qu'une fois la première LED allumée.
     */
    @Test
    fun `une LED eteinte reste visible`() {
        assertTrue(MatrixLook.OFF_ARGB != MatrixLook.FIELD_ARGB)
        val grey = MatrixLook.OFF_ARGB and 0xFF
        val field = MatrixLook.FIELD_ARGB and 0xFF
        assertTrue("une matrice au repos ne doit pas être noire", grey > field)
    }

    /**
     * La rampe d'opacité : rien sous le seuil, puis **jamais moins que le
     * plancher**. C'est ce qui rend visibles la carcasse d'un dé et le sable
     * d'un sablier, que l'alpha linéaire noyait — le halo se charge du reste.
     */
    @Test
    fun `la rampe part du plancher, arrive a un, et ne redescend jamais`() {
        assertEquals(0f, MatrixLook.alphaOf(0), 0f)
        assertEquals(0f, MatrixLook.alphaOf(MatrixLook.MIN_VISIBLE), 0f)
        assertEquals(1f, MatrixLook.alphaOf(255), 1e-6f)
        // Saturé au-delà : une frame ne devrait pas dépasser, mais une surface
        // n'a pas à se fier à ça pour ne pas peindre plus blanc que blanc.
        assertEquals(1f, MatrixLook.alphaOf(300), 1e-6f)

        var previous = -1f
        for (v in 0..255) {
            val a = MatrixLook.alphaOf(v)
            assertTrue("rampe non monotone à $v", a >= previous)
            assertTrue("sous le plancher à $v", a == 0f || a >= MatrixLook.FLOOR)
            previous = a
        }
    }

    /**
     * Chaque style tient la même promesse sur sa rampe : rien sous le seuil,
     * jamais moins que **son** plancher, jamais de redescente, et le blanc plein
     * au bout. Ce sont les deux seules courbes que les surfaces appliquent, et
     * elles ne sont vérifiées nulle part ailleurs.
     */
    @Test
    fun `chaque style a une rampe qui part de son plancher et monte jusqu a un`() {
        for (style in MatrixLook.LedStyle.entries) {
            assertEquals(style.name, 0f, style.alphaOf(MatrixLook.MIN_VISIBLE), 0f)
            assertEquals(style.name, 1f, style.alphaOf(255), 1e-6f)
            var previous = -1f
            for (v in 0..255) {
                val a = style.alphaOf(v)
                assertTrue("${style.name} : rampe non monotone à $v", a >= previous)
                assertTrue("${style.name} : sous le plancher à $v", a == 0f || a >= style.floor)
                previous = a
            }
        }
    }

    /**
     * Ce qui distingue vraiment les deux styles, et qu'un réglage mal recopié
     * ferait taire : sans halo, c'est le **creux sombre** des LEDs éteintes qui
     * donne sa trame au disque, alors que sharp les pose en gris sur du presque
     * noir. Le contraste s'inverse, et c'est le principe.
     */
    @Test
    fun `soft n a pas de halo et inverse le contraste de la trame`() {
        val sharp = MatrixLook.LedStyle.SHARP
        val soft = MatrixLook.LedStyle.SOFT

        assertTrue("sharp doit avoir un halo", sharp.halo > 0f)
        assertEquals("soft ne doit pas en avoir", 0f, soft.halo, 0f)

        assertTrue("sharp : LED éteinte plus claire que le champ", grey(sharp.offArgb) > grey(sharp.fieldArgb))
        assertTrue("soft : LED éteinte plus sombre que le champ", grey(soft.offArgb) < grey(soft.fieldArgb))
    }

    /** Le plancher de sharp est haut parce que le halo prend le relais. */
    @Test
    fun `le plancher suit la presence du halo`() {
        assertTrue(MatrixLook.LedStyle.SOFT.floor < MatrixLook.LedStyle.SHARP.floor)
    }

    private fun grey(argb: Int): Int = argb and 0xFF

    @Test
    fun `le blanc de la matrice n est pas le blanc pur, le champ n est pas le noir pur`() {
        assertTrue(MatrixLook.LIT_ARGB != 0xFFFFFFFF.toInt())
        assertTrue(MatrixLook.FIELD_ARGB != 0xFF000000.toInt())
    }
}
