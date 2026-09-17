package red.suns.haloglyph.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le budget binder, tenu par un test plutôt que par la mémoire de qui a écrit le
 * plafond.
 *
 * Au-delà d'environ 500 Ko par `RemoteViews`, `TransactionTooLargeException` — et
 * ce n'est pas le widget qui tombe, c'est le launcher. Le passage du RGB_565 à
 * l'ARGB_8888, qu'exige un widget rond, a doublé le poids du pixel : c'est
 * exactement le genre de changement qui fait sauter un plafond réglé pour l'autre
 * format.
 */
class MatrixBitmapTest {

    @Test
    fun `la plus grande bitmap possible tient dans le budget binder`() {
        assertTrue(
            "${MatrixBitmap.byteSize(MatrixBitmap.MAX_SIDE_PX)} octets",
            MatrixBitmap.byteSize(MatrixBitmap.MAX_SIDE_PX) < BUDGET_BYTES,
        )
    }

    @Test
    fun `une taille demandee est ramenee dans les bornes`() {
        assertEquals(MatrixBitmap.MAX_SIDE_PX, MatrixBitmap.clampSide(4000))
        assertEquals(MatrixBitmap.MIN_SIDE_PX, MatrixBitmap.clampSide(0))
        assertEquals(MatrixBitmap.MIN_SIDE_PX, MatrixBitmap.clampSide(-1))
        assertEquals(200, MatrixBitmap.clampSide(200))
    }

    /**
     * Le plafond ne sert plus seulement à ne pas faire tomber le launcher : la
     * rafale pousse **cette** bitmap-là, une quinzaine de fois par seconde, parce
     * qu'elle rend à la définition du repos. Il lui faut donc la marge d'un
     * régime soutenu, pas seulement celle d'un envoi isolé.
     */
    @Test
    fun `la plus grande bitmap reste tenable en rafale`() {
        val perFrame = MatrixBitmap.byteSize(MatrixBitmap.MAX_SIDE_PX)
        assertTrue("$perFrame octets par image", perFrame <= BUDGET_BYTES / 1.5)
    }

    private companion object {
        /** Ce que le système tolère par transaction, marge de l'emballage comprise. */
        const val BUDGET_BYTES = 460_000
    }
}
