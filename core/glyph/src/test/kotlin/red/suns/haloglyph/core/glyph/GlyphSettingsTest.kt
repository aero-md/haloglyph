package red.suns.haloglyph.core.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tri des écrans Glyph, sans téléphone.
 *
 * Ce qu'on protège : ne **jamais** confondre le gestionnaire avec la liste des
 * toys actifs, et ne rien proposer plutôt que d'ouvrir un écran au hasard.
 */
class GlyphSettingsTest {

    private val manager = "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"

    @Test
    fun `les deux ecrans sont distingues`() {
        val screens = GlyphSettings.pick(
            listOf(
                "com.nothing.thirdparty.matrix.toys.ToysActivity",
                manager,
            )
        )
        assertEquals("com.nothing.thirdparty.matrix.toys.ToysActivity", screens.active)
        assertEquals(manager, screens.manager)
        assertTrue(screens.any)
    }

    /** L'entrée principale n'est pas celle qui est enfouie le plus profond. */
    @Test
    fun `l'ecran le moins enfoui gagne`() {
        val screens = GlyphSettings.pick(
            listOf(
                "com.nothing.thirdparty.matrix.toys.detail.ToysDetailActivity",
                "com.nothing.thirdparty.matrix.toys.ToysActivity",
                manager,
            )
        )
        assertEquals("com.nothing.thirdparty.matrix.toys.ToysActivity", screens.active)
    }

    /** Firmware renommé : le mot « Manager » suffit encore à trancher. */
    @Test
    fun `le gestionnaire est reconnu meme renomme`() {
        val renamed = "com.nothing.thirdparty.matrix.toys.ToysManagerV2Activity"
        val screens = GlyphSettings.pick(listOf(renamed))
        assertEquals(renamed, screens.manager)
        // Un seul écran exposé : c'est le gestionnaire, pas la liste. Pas de
        // second bouton qui rouvrirait le même écran sous un autre nom.
        assertNull(screens.active)
    }

    /** Téléphone sans Glyph, ou paquet invisible : aucun bouton. */
    @Test
    fun `rien d'exporte donne aucun ecran`() {
        val screens = GlyphSettings.pick(emptyList())
        assertNull(screens.active)
        assertNull(screens.manager)
        assertFalse(screens.any)
    }

    /** Le reste du paquet n'est pas un écran de toys. */
    @Test
    fun `les activites hors du sous-paquet toys sont ignorees`() {
        val screens = GlyphSettings.pick(
            listOf(
                "com.nothing.thirdparty.MainActivity",
                "com.nothing.thirdparty.matrix.wallpaper.WallpaperActivity",
            )
        )
        assertFalse(screens.any)
    }
}
