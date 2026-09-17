package red.suns.haloglyph.core.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Le placement du hublot, vérifié sur toute la plage de tailles réellement
 * utilisée — d'une vignette de 22 dp à un aperçu de 252 dp, en densité 1 à 4.
 *
 * Ce sont des invariants, pas des valeurs : la cellule bouge d'une taille à
 * l'autre, les règles non.
 */
class MatrixGeometryTest {

    private val spec = MatrixSpec.Phone3

    /** Toutes les tailles plausibles, du plus petit repère au grand aperçu. */
    private val boxes = (40..1024 step 7).toList()

    @Test
    fun `le hublot ne deborde jamais de sa boite`() {
        for (box in boxes) {
            val g = MatrixGeometry(spec, box)
            assertTrue("$box px : disque de ${g.disc}", g.disc <= box)
            assertTrue("$box px : origine négative", g.discOrigin >= 0)
        }
    }

    @Test
    fun `il reste toujours un cerne entre la derniere LED et la decoupe`() {
        for (box in boxes) {
            val g = MatrixGeometry(spec, box)
            assertTrue("$box px : cerne nul", g.ring >= 1)
        }
    }

    /** Deux LEDs jointives ne se distinguent plus : il faut de la gouttière. */
    @Test
    fun `une LED tient dans sa cellule`() {
        for (box in boxes) {
            val g = MatrixGeometry(spec, box)
            assertTrue("$box px : LED de ${g.led} pour ${g.cell}", g.led <= g.cell)
            assertTrue("$box px : LED nulle", g.led >= 1)
            assertTrue("$box px : marge négative", g.pad >= 0)
            assertTrue("$box px : LED hors cellule", g.pad + g.led <= g.cell)
        }
    }

    /** La grille reste dans le disque, coins compris. */
    @Test
    fun `le champ est centre dans le disque`() {
        for (box in boxes) {
            val g = MatrixGeometry(spec, box)
            assertEquals(g.disc, g.field + 2 * g.ring)
            assertTrue(g.topOf(0) >= 0)
            assertTrue(g.leftOf(spec.size - 1) + g.led <= g.disc)
        }
    }

    /** Plus de place ne donne jamais un hublot plus petit. */
    @Test
    fun `le hublot grandit avec sa boite`() {
        var previous = 0
        for (box in boxes) {
            val disc = MatrixGeometry(spec, box).disc
            assertTrue("$box px : le disque a rétréci", disc >= previous)
            previous = disc
        }
    }

    /**
     * Le hublot est aussi grand que la boîte le permet : un pas d'un pixel de
     * plus ne rentrerait pas avec son cerne.
     *
     * C'est l'invariant qui compte, et non une part de la boîte à occuper : la
     * cellule est entière, donc il reste toujours du mou, et ce mou vaut jusqu'à
     * un pas complet sur les toutes petites vignettes. Le mou reste dehors, ce
     * qui garde la boîte à la taille qu'on lui a demandée.
     */
    @Test
    fun `le hublot est aussi grand que la boite le permet`() {
        for (box in boxes) {
            val g = MatrixGeometry(spec, box)
            if (g.cell <= MatrixGeometry.MIN_CELL) continue
            assertTrue(
                "$box px : un pas de ${g.cell + 1} tenait encore",
                MatrixGeometry.discFor(spec, g.cell + 1) > box,
            )
        }
    }

    /**
     * À la taille du widget — deux cellules d'écran, donc 250 à 330 pixels — le
     * mou tombe à quelques pixels. C'est la seule taille où il compte vraiment :
     * le widget **est** le hublot, et le voir flotter dans son emplacement se
     * verrait.
     */
    @Test
    fun `a la taille d'un widget, le hublot remplit son emplacement`() {
        for (box in 240..360) {
            val g = MatrixGeometry(spec, box)
            assertTrue("$box px : disque de ${g.disc}", g.disc >= box * 0.93)
        }
    }
}
