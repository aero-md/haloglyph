package red.suns.haloglyph.core.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le test que l'économie de batterie aurait dû avoir dès le premier jour.
 *
 * Elle a été écrite en deux lignes — garder le tableau reçu, le comparer au
 * suivant — et ces deux lignes ont figé **tous** les hublots. `Frame.toBrightness`
 * réutilise son tableau, donc retenir la référence revient à comparer une image
 * avec elle-même : toujours égale, donc plus rien n'est jamais poussé.
 *
 * Le premier test ci-dessous rejoue exactement ça, et c'est le seul qui compte
 * vraiment.
 */
class FrameEchoTest {

    @Test
    fun `un tableau reutilise et modifie compte comme une nouvelle image`() {
        // Le cas réel : `Frame.toBrightness()` rend toujours le même tableau et
        // le réécrit à chaque image. Un écho qui garde la référence ne voit donc
        // jamais rien changer.
        val shared = IntArray(4)
        val echo = FrameEcho()

        shared[0] = 1
        assertTrue("première image", echo.accept(shared))

        shared[0] = 2
        assertTrue("le contenu a changé sous le même tableau", echo.accept(shared))

        shared[2] = 7
        assertTrue("encore", echo.accept(shared))
    }

    @Test
    fun `la premiere image part toujours`() {
        assertTrue(FrameEcho().accept(IntArray(4)))
    }

    @Test
    fun `une image identique ne repart pas`() {
        val echo = FrameEcho()
        assertTrue(echo.accept(intArrayOf(1, 2, 3)))
        assertFalse(echo.accept(intArrayOf(1, 2, 3)))
        assertFalse(echo.accept(intArrayOf(1, 2, 3)))
    }

    @Test
    fun `un aller-retour repart`() {
        val echo = FrameEcho()
        assertTrue(echo.accept(intArrayOf(1, 1)))
        assertFalse(echo.accept(intArrayOf(1, 1)))
        assertTrue(echo.accept(intArrayOf(2, 2)))
        assertTrue(echo.accept(intArrayOf(1, 1)))
    }

    @Test
    fun `un changement de taille repart`() {
        // Peut arriver quand un hublot change de matrice. Comparer des tableaux
        // de tailles différentes ne doit ni lever ni mentir.
        val echo = FrameEcho()
        assertTrue(echo.accept(intArrayOf(1, 2)))
        assertTrue(echo.accept(intArrayOf(1, 2, 3)))
        assertFalse(echo.accept(intArrayOf(1, 2, 3)))
    }
}
