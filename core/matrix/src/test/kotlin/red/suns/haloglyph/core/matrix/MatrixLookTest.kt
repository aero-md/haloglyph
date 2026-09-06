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

    @Test
    fun `le blanc de la matrice n est pas le blanc pur, le champ n est pas le noir pur`() {
        assertTrue(MatrixLook.LIT_ARGB != 0xFFFFFFFF.toInt())
        assertTrue(MatrixLook.FIELD_ARGB != 0xFF000000.toInt())
    }
}
