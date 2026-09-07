package red.suns.haloglyph.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'apparence émulée est une **table partagée**, pas une préférence de chaque
 * surface. Ces vérifications sont modestes mais elles tiennent la seule chose
 * qui compte : que l'aperçu des réglages et le widget ne puissent pas diverger
 * sans que quelqu'un le décide explicitement ici.
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
        // Ni aplat (1,0) ni semis de points perdus : un tiers de gouttière.
        assertTrue(MatrixLook.LED_RATIO in 0.5f..0.8f)
    }

    @Test
    fun `une LED eteinte reste visible, une LED faible reste au moins aussi visible`() {
        assertTrue("une matrice au repos ne doit pas être noire", MatrixLook.OFF_ALPHA > 0f)
        // Le seuil d'allumage doit rester sous le niveau de l'état éteint,
        // sinon une LED juste allumée serait plus sombre que ses voisines.
        assertTrue(MatrixLook.MIN_VISIBLE / 255f <= MatrixLook.OFF_ALPHA)
    }

    /**
     * La conversion qui fait qu'une surface émulée montre ce que la matrice
     * montre, et non la consigne qu'elle reçoit.
     */
    @Test
    fun `le percu part de zero, arrive a un, et ne redescend jamais`() {
        assertEquals(0f, MatrixLook.perceived(0), 0f)
        assertEquals(1f, MatrixLook.perceived(255), 1e-6f)
        // Saturé au-delà : une frame ne devrait pas dépasser, mais une surface
        // n'a pas à se fier à ça pour ne pas peindre plus blanc que blanc.
        assertEquals(1f, MatrixLook.perceived(300), 1e-6f)
        var previous = -1f
        for (v in 0..255) {
            val p = MatrixLook.perceived(v)
            assertTrue("perçu non monotone à $v", p >= previous)
            previous = p
        }
    }

    /**
     * Le point de tout l'exercice : **le perçu est au-dessus du rapport
     * cyclique**, partout entre les deux bouts. C'est ce qui rend visibles la
     * carcasse d'un dé et le sable d'un sablier, que l'alpha linéaire noyait.
     */
    @Test
    fun `le percu est plus clair que la consigne`() {
        for (v in 1..254) {
            assertTrue("$v", MatrixLook.perceived(v) > v / 255f)
        }
        // Les deux repères cités dans la table : une arête de dé et son lavis.
        assertEquals(0.46f, MatrixLook.perceived((0.18f * 255).toInt()), 0.01f)
        assertEquals(0.20f, MatrixLook.perceived((0.03f * 255).toInt()), 0.02f)
    }

    @Test
    fun `le blanc de la matrice n est pas le blanc pur, le champ n est pas le noir pur`() {
        assertTrue(MatrixLook.LIT_ARGB != 0xFFFFFFFF.toInt())
        assertTrue(MatrixLook.FIELD_ARGB != 0xFF000000.toInt())
    }
}
