package red.suns.haloglyph.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La rotation d'un hublot, testée là où elle se laisse tester.
 *
 * Ces règles décident de ce qu'on voit après un double tap, et deux d'entre
 * elles n'ont aucune chance d'être exercées à la main : la rotation vide, et
 * l'identifiant enregistré pour un toy qui n'existe plus. Ce sont pourtant les
 * deux seules qui laisseraient un widget noir.
 */
class WidgetRotationTest {

    private val all = listOf("lapse", "dice", "sono")

    // ---------- decode ----------

    @Test
    fun `rien d'enregistre vaut tous les toys`() {
        assertEquals(all, WidgetRotation.decode(null, all))
    }

    @Test
    fun `l'ordre est celui du catalogue, pas celui de l'enregistrement`() {
        assertEquals(listOf("lapse", "sono"), WidgetRotation.decode("sono,lapse", all))
    }

    @Test
    fun `un toy retire de l'app disparait sans rien casser`() {
        assertEquals(listOf("dice"), WidgetRotation.decode("dice,slot", all))
    }

    @Test
    fun `une rotation qui ne designerait plus rien retombe sur tout`() {
        // Le seul chemin qui y mène est un fichier écrit par une version qui
        // n'est plus là. Un hublot vide n'est pas un état affichable.
        assertEquals(all, WidgetRotation.decode("slot", all))
    }

    @Test
    fun `un aller-retour par l'enregistrement ne perd rien`() {
        val kept = listOf("lapse", "sono")
        assertEquals(kept, WidgetRotation.decode(WidgetRotation.encode(kept), all))
    }

    // ---------- resolve ----------

    @Test
    fun `le toy affiche est celui qui est enregistre`() {
        assertEquals("dice", WidgetRotation.resolve("dice", all))
    }

    @Test
    fun `un toy sorti de la rotation ne reste pas a l'ecran`() {
        assertEquals("lapse", WidgetRotation.resolve("dice", listOf("lapse", "sono")))
    }

    @Test
    fun `sans rien d'enregistre, c'est le premier`() {
        assertEquals("lapse", WidgetRotation.resolve(null, all))
    }

    @Test
    fun `une rotation vide n'affiche rien`() {
        assertNull(WidgetRotation.resolve("dice", emptyList()))
    }

    // ---------- next ----------

    @Test
    fun `le double tap avance d'un cran`() {
        assertEquals("dice", WidgetRotation.next("lapse", all))
        assertEquals("sono", WidgetRotation.next("dice", all))
    }

    @Test
    fun `le double tap boucle`() {
        assertEquals("lapse", WidgetRotation.next("sono", all))
    }

    @Test
    fun `un seul toy dans la rotation, le double tap ne fait rien`() {
        assertEquals("dice", WidgetRotation.next("dice", listOf("dice")))
    }

    @Test
    fun `avancer depuis un toy sorti de la rotation repart du premier`() {
        // On résout d'abord — « dice » n'est plus là, donc on est en réalité sur
        // « lapse » — puis on avance. Sauter à « sono » ferait disparaître un toy
        // de la suite sans que personne l'ait demandé.
        assertEquals("sono", WidgetRotation.next("dice", listOf("lapse", "sono")))
    }

    // ---------- variant ----------

    private val dice = listOf("d6", "d10", "d12", "d20")

    @Test
    fun `la variante enregistree fait foi`() {
        assertEquals("d20", WidgetRotation.variant("d20", dice, "d6"))
    }

    @Test
    fun `sans rien d'enregistre, c'est le repli du toy`() {
        // Le dé en main sur la matrice : un hublot neuf montre ce que montre le
        // téléphone, et ne s'en détache qu'au premier réglage.
        assertEquals("d12", WidgetRotation.variant(null, dice, "d12"))
    }

    @Test
    fun `une variante disparue retombe sur le repli`() {
        assertEquals("d6", WidgetRotation.variant("d8", dice, "d6"))
    }

    @Test
    fun `un repli disparu retombe sur la premiere`() {
        assertEquals("d6", WidgetRotation.variant("d8", dice, "d4"))
    }

    @Test
    fun `un toy sans variante n'en a aucune`() {
        assertNull(WidgetRotation.variant("d6", emptyList(), "d6"))
    }
}
