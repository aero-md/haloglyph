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
 * Ce qu'on protège : l'action passe **avant** le composant. Le nom de classe du
 * gestionnaire a déjà changé une fois — l'app tierce qui l'ouvre en dur vise un
 * `ToysManagerActivity` absent du firmware d'un Phone (3) — alors que l'action
 * a survécu au déménagement.
 */
class GlyphSettingsTest {

    private val action = Target.Action("com.nothing.glyph.TOYS_MANAGER")
    private val component = Target.Component(
        "com.nothing.thirdparty",
        "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
    )

    @Test
    fun `l'action passe avant le composant`() {
        val chosen = GlyphSettings.choose(listOf(action, component)) { true }
        assertEquals(action, chosen)
    }

    /** Firmware sans l'action : le composant observé ailleurs sauve la mise. */
    @Test
    fun `le composant sert de repli`() {
        val chosen = GlyphSettings.choose(listOf(action, component)) { it is Target.Component }
        assertEquals(component, chosen)
    }

    /** Rien ne répond : pas de bouton, plutôt qu'un bouton qui ouvre autre chose. */
    @Test
    fun `aucun candidat resolvable donne null`() {
        assertNull(GlyphSettings.choose(listOf(action, component)) { false })
    }

    @Test
    fun `un ecran manquant n'empeche pas l'autre`() {
        val screens = GlyphSettings.Screens(active = null, manager = action)
        assertTrue(screens.any)

        assertFalse(GlyphSettings.Screens(active = null, manager = null).any)
    }
}
