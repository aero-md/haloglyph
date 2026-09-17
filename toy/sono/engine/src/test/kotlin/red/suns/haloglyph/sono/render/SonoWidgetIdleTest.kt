package red.suns.haloglyph.sono.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.centeredX
import red.suns.haloglyph.core.matrix.clippedPixels

/**
 * Le repos de Sono dans un hublot : le mot, la marque, et rien qui déborde.
 *
 * Ce rendu tient dans vingt-cinq lignes au pixel près, et le disque rogne les
 * bords. Un test plutôt qu'un coup d'œil, parce qu'un mot rogné d'une colonne ne
 * se voit pas sur une vignette et se voit très bien sur un écran d'accueil.
 */
class SonoWidgetIdleTest {

    private val spec = MatrixSpec.Phone3

    private fun frame(): Frame = Frame(spec).also { SonoWidgetIdle.render(it) }

    /** Les lignes allumées, de haut en bas. */
    private fun litRows(f: Frame): List<Int> {
        val b = f.toBrightness()
        return (0 until spec.size).filter { y ->
            (0 until spec.size).any { x -> spec.isLed(x, y) && b[spec.index(x, y)] > 0 }
        }
    }

    /**
     * Deux étages, dans l'ordre du pack : le **mot** en haut, la **marque** en
     * dessous.
     *
     * Il y en a eu trois — `MIC`, l'onde, `TAP` — et le premier ne disait rien que
     * le toy ne dise déjà. Ce qui reste est la grammaire commune à tous les
     * hublots : on écrit le geste, on dessine l'identité.
     */
    @Test
    fun `le mot est en haut, la marque en dessous`() {
        val rows = litRows(frame())
        assertEquals("le mot n'est pas à sa ligne", 4, rows.first())
        assertEquals("la marque ne descend pas jusqu'en bas", 18, rows.last())
        // La gouttière : une ligne vide entre le bas du mot et le haut de l'onde,
        // sur les colonnes du mot. L'onde, elle, la traverse sur ses bords.
        val b = frame().toBrightness()
        for (x in 7..17) {
            assertEquals("la gouttière est bouchée en $x", 0, b[spec.index(x, 8)])
        }
    }

    @Test
    fun `le mot n'est pas rogne par le disque`() {
        val f = frame()
        assertEquals(0, f.clippedPixels(Fonts.F4, "TAP", f.centeredX(Fonts.F4, "TAP"), 4))
    }

    /**
     * L'axe est **plein sur toute la largeur** du disque : c'est lui qui fait
     * l'onde. Sans lui il ne resterait que des barres posées côte à côte.
     */
    @Test
    fun `l'axe traverse tout le disque`() {
        val b = frame().toBrightness()
        for (x in 0 until spec.size) {
            assertTrue("l'axe est coupé en $x", b[spec.index(x, 12)] > 0)
        }
    }

    /** Rien ne sort du masque, et rien ne traîne sous la marque. */
    @Test
    fun `rien ne deborde`() {
        val f = frame()
        for (i in 0 until spec.cellCount) {
            if (!spec.isLed(i)) assertEquals("cellule $i hors disque", 0f, f.values[i], 0f)
        }
        for (y in 19..24) {
            val lit = (0 until spec.size).count { x -> f[x, y] > 0f }
            assertEquals("ligne $y devrait être vide", 0, lit)
        }
    }

    @Test
    fun `le rendu ne depend pas de l'heure`() {
        // Contrairement au repos de la matrice, qui fait tourner une comète. Ici
        // c'est une figure fixe : deux appels de suite doivent donner la même
        // image, sinon un hublot au repos pousserait une bitmap pour rien à
        // chaque battement — voir l'économie de `WidgetBurstService.draw`.
        assertEquals(frame().toBrightness().toList(), frame().toBrightness().toList())
    }
}
