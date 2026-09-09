package red.suns.haloglyph

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le sélecteur de langue nomme chaque langue **dans cette langue**.
 *
 * C'est la seule partie de [AppLanguage] qui soit testable hors appareil — le
 * reste parle au `LocaleManager` du système — et c'est aussi la seule qui puisse
 * silencieusement se dégrader : `getDisplayLanguage` rend « français » en
 * minuscule, et une majuscule perdue ne casse rien, elle enlaidit juste une
 * ligne que personne ne relit.
 */
class AppLanguageTest {

    @Test
    fun `chaque langue est nommee dans sa propre langue`() {
        assertEquals("Deutsch", AppLanguage.endonym("de"))
        assertEquals("English", AppLanguage.endonym("en"))
        assertEquals("Español", AppLanguage.endonym("es"))
        assertEquals("Français", AppLanguage.endonym("fr"))
        assertEquals("Italiano", AppLanguage.endonym("it"))
    }

    /**
     * Une langue que le JDK ne connaît pas garde son code plutôt que de rendre
     * une entrée cliquable sans étiquette. Le cas ne devrait pas se produire —
     * la liste vient des dossiers `values-xx` du projet — mais un sélecteur avec
     * une ligne vide serait pire qu'un code brut.
     */
    @Test
    fun `une langue inconnue du JDK reste lisible`() {
        assertEquals("Zz", AppLanguage.endonym("zz"))
    }
}
