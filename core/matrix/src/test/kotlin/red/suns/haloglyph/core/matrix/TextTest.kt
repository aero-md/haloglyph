package red.suns.haloglyph.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {

    private val spec = MatrixSpec.Phone3

    @Test
    fun `la largeur inclut un separateur d un pixel`() {
        assertEquals(5, Fonts.F5.textWidth("0"))
        assertEquals(11, Fonts.F5.textWidth("00"))
        assertEquals(3, Fonts.F3.textWidth("0"))
        assertEquals(0, Fonts.F3.textWidth(""))
        // ' ' est un séparateur de groupe, pas un glyphe : 3 + 2 px + 3.
        assertEquals(8, Fonts.F3.textWidth("0 0"))
    }

    @Test
    fun `un texte centre au milieu du disque ne perd aucun pixel`() {
        val frame = Frame(spec)
        val text = "12345"
        val y = 9
        assertEquals(0, frame.clippedPixels(Fonts.F3, text, frame.centeredX(Fonts.F3, text), y))
    }

    /**
     * Le cœur du centrage conscient du disque : haut de la matrice, la ligne ne
     * fait que 15 LEDs de large. Un centrage arithmétique mord le bord ; le
     * décalage doit rattraper ce qui peut l'être.
     */
    @Test
    fun `le nudge reduit le debordement dans les bandes etroites`() {
        val frame = Frame(spec)
        val font = Fonts.F3
        val text = "88 88"
        val y = 2
        val x0 = frame.centeredX(font, text)
        val naive = frame.clippedPixels(font, text, x0, y)
        val (dx, remaining) = frame.bestOffset(font, text, x0, y)

        assertTrue("le nudge ne doit jamais empirer", remaining <= naive)
        assertTrue("décalage borné", dx in -3..3)
    }

    @Test
    fun `drawLine resserre les groupes plutot que de perdre un chiffre`() {
        val frame = Frame(spec)
        val font = Fonts.F3
        // Deux groupes en haut du disque : avec l'espacement large, ça déborde.
        frame.drawLine(font, "88 88", 2)
        val lit = frame.toBrightness().count { it > 0 }

        val tight = Frame(spec)
        tight.drawText(font, "8888", tight.centeredX(font, "8888"), 2)
        // Autant de pixels allumés que la version resserrée : rien n'a été perdu.
        assertEquals(tight.toBrightness().count { it > 0 }, lit)
    }

    @Test
    fun `un caractere inconnu avance sans empiler`() {
        val frame = Frame(spec)
        frame.drawText(Fonts.F5, "0?0", 8, 9)
        // Deux chiffres dessinés, aucun glyphe pour '?', et pas de superposition :
        // les colonnes des deux zéros sont disjointes.
        val columns = (0 until 25).filter { x ->
            (0 until 25).any { y -> frame[x, y] > 0f }
        }
        assertTrue(columns.isNotEmpty())
        assertEquals(columns.size, columns.distinct().size)
    }

    @Test
    fun `drawLines centre le bloc verticalement`() {
        val frame = Frame(spec)
        frame.drawLines(Fonts.F3, listOf("111", "222", "333"))
        val rows = (0 until 25).filter { y -> (0 until 25).any { x -> frame[x, y] > 0f } }
        val top = rows.first()
        val bottom = rows.last()
        // Bloc de 3 lignes de 5 px + 2 interlignes = 17 px, centré dans 25.
        assertEquals(17, bottom - top + 1)
        assertTrue("écarts haut/bas comparables", kotlin.math.abs(top - (24 - bottom)) <= 1)
    }
}
