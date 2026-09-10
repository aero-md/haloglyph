package red.suns.haloglyph.core.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.glyph.GlyphSettings.Target

/**
 * Le choix d'un chemin vers un écran Glyph, sans téléphone.
 *
 * Ce qu'on protège : l'ordre des candidats. Il porte tout le sens, et se lit mal
 * — rien dans une liste ne crie que son dernier élément ouvre un **autre**
 * écran. Ces tests le disent à voix haute.
 */
class GlyphSettingsTest {

    /** Le gestionnaire : exporté, sans filtre, donc joignable par composant seul. */
    private val manager = Target.Component(
        "com.nothing.thirdparty",
        "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
    )

    /** La vitrine Glyph Toys : un écran voisin, acceptable en dernier recours. */
    private val showcase = Target.Action("com.nothing.glyph.TOYS_MANAGER")

    @Test
    fun `le meilleur chemin est pris quand il repond`() {
        val chosen = GlyphSettings.choose(listOf(manager, showcase)) { true }
        assertEquals(manager, chosen)
    }

    /**
     * Le repli n'est pas un synonyme : il n'a le droit de servir que si le
     * gestionnaire est introuvable. S'il passait devant, le bouton « Tout gérer »
     * ouvrirait une vitrine sans rien à gérer.
     */
    @Test
    fun `la vitrine ne sert que si le gestionnaire manque`() {
        val chosen = GlyphSettings.choose(listOf(manager, showcase)) { it != manager }
        assertEquals(showcase, chosen)
    }

    /** Rien ne répond : pas de bouton, plutôt qu'un bouton qui ouvre autre chose. */
    @Test
    fun `aucun candidat resolvable donne null`() {
        assertNull(GlyphSettings.choose(listOf(manager, showcase)) { false })
    }

    @Test
    fun `un ecran manquant n'empeche pas l'autre`() {
        assertTrue(GlyphSettings.Screens(active = null, manager = manager).any)
        assertFalse(GlyphSettings.Screens(active = null, manager = null).any)
    }
}
