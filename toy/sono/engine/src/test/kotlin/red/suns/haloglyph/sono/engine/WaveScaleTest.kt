package red.suns.haloglyph.sono.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'échelle de la forme d'onde — celle qui décide que le silence est plat.
 */
class WaveScaleTest {

    private val dt = 1.0 / 30

    /** Pousse [seconds] de niveau constant. */
    private fun soak(s: WaveScale, db: Double, seconds: Double) {
        repeat((seconds / dt).toInt()) {
            s.decay(dt)
            s.update(db, dt)
        }
    }

    /**
     * Le cœur du sujet : une échelle d'amplitude écrase ce qui est loin sous le
     * plafond, là où une échelle en décibels le remonte à mi-hauteur.
     */
    @Test
    fun `l'echelle est une amplitude, pas des decibels`() {
        val s = WaveScale()
        soak(s, 40.0, 3.0)
        // Plafond porté à 40 + 40 = 80 par une salve, puis on lit des niveaux
        // plus bas dans la même image.
        s.decay(dt)
        s.update(80.0, dt)

        assertEquals("le plafond est à pleine hauteur", 1f, s.amplitude(80.0), 1e-3f)
        // −20 dB : un dixième en amplitude brute, moins encore une fois mis en
        // forme. Sur une échelle en décibels, ce niveau occuperait le tiers de la
        // hauteur — c'est exactement la tache qu'on a passé trois tours à chasser.
        assertTrue("−20 dB occupe ${s.amplitude(60.0)}", s.amplitude(60.0) <= 0.1f)
        assertTrue("−25 dB occupe ${s.amplitude(55.0)}", s.amplitude(55.0) <= 0.06f)
    }

    /**
     * Et la mise en forme creuse le haut : le plafond se posant toujours sur la
     * plus forte tranche récente, ce qui la suit de peu doit rester nettement en
     * dessous, sinon une onde de parole colle au bord du disque en permanence.
     */
    @Test
    fun `ce qui suit le sommet de peu reste nettement en dessous`() {
        val s = WaveScale()
        soak(s, 40.0, 3.0)
        s.decay(dt)
        s.update(80.0, dt)

        val justUnder = s.amplitude(74.0) // −6 dB
        assertTrue("−6 dB occupe encore $justUnder", justUnder < 0.42f)
        assertTrue("−6 dB écrasé à $justUnder", justUnder > 0.2f)
        // Et l'ordre reste celui du son : plus fort, plus haut.
        assertTrue(s.amplitude(77.0) > justUnder)
    }

    /**
     * Le défaut que tout ça corrige : un bruit de fond stationnaire ne doit pas
     * dépasser une rangée sur douze, même quand il fluctue.
     */
    @Test
    fun `un fond stationnaire reste au ras de l'axe`() {
        val s = WaveScale()
        soak(s, 45.0, 4.0)
        assertEquals("un fond parfaitement stable n'allume rien", 0f, s.amplitude(45.0), 1e-6f)

        // Et sa fluctuation, non plus : dix décibels au-dessus de son propre
        // minimum, il plafonne à un dixième — une rangée sur douze.
        assertTrue("fluctuation trop visible", s.amplitude(55.0) <= 0.12f)
    }

    /** Ce qu'on est venu voir, lui, occupe l'écran. */
    @Test
    fun `une voix sur le meme fond remplit l'ecran`() {
        val s = WaveScale()
        soak(s, 45.0, 4.0)
        s.decay(dt)
        s.update(73.0, dt)
        assertTrue(
            "une voix à 28 dB du fond n'occupe que ${s.amplitude(73.0)}",
            s.amplitude(73.0) > 0.6f,
        )
    }

    /**
     * La butée du gain automatique. Sans elle, une pièce vide finit par
     * normaliser son propre bruit et le remonte à pleine hauteur — l'erreur
     * classique d'un gain qu'on laisse sans plancher.
     */
    @Test
    fun `une piece vide ne normalise pas son propre bruit`() {
        val s = WaveScale()
        // Dix secondes de fond seul : le plafond retombe jusqu'au bruit lui-même.
        soak(s, 45.0, 10.0)
        assertTrue(
            "le plafond devrait s'être posé sur le bruit, il est à ${s.ceilingDb()}",
            s.ceilingDb() <= s.floorDb() + 1.0,
        )
        // Et pourtant rien ne sature : c'est la butée qui tient l'échelle, pas le
        // plafond. Sans elle, ce même bruit occuperait toute la hauteur.
        assertTrue("le bruit remonte à ${s.amplitude(55.0)}", s.amplitude(55.0) <= 0.12f)
    }

    /** Un son qui dure ne fait pas pomper l'échelle : il se re-désigne plafond. */
    @Test
    fun `un son soutenu garde une hauteur stable`() {
        val s = WaveScale()
        soak(s, 40.0, 3.0)
        soak(s, 85.0, 2.0)
        assertEquals(1f, s.amplitude(85.0), 1e-6f)
    }

    /** Après une salve, l'échelle redescend pour laisser voir ce qui suit. */
    @Test
    fun `le plafond redescend apres une salve`() {
        val s = WaveScale()
        soak(s, 40.0, 3.0)
        soak(s, 100.0, 0.5)
        val loud = s.ceilingDb()
        soak(s, 40.0, 3.0)
        assertTrue("plafond resté à ${s.ceilingDb()} (salve à $loud)", s.ceilingDb() < loud - 20.0)
    }

    /** Ce qui n'est pas une mesure ne fait ni plafond ni hauteur. */
    @Test
    fun `une absence de mesure n'allume rien et ne leve pas le plafond`() {
        val s = WaveScale()
        soak(s, 45.0, 3.0)
        val before = s.ceilingDb()
        s.update(Double.NEGATIVE_INFINITY, dt)
        s.update(-80.0, dt)
        assertEquals(before, s.ceilingDb(), 1e-9)
        assertEquals(0f, s.amplitude(-80.0), 1e-6f)
        assertEquals(0f, s.amplitude(Double.NaN), 1e-6f)
    }

    @Test
    fun `clear remet l'echelle a l'amorce`() {
        val s = WaveScale()
        soak(s, 45.0, 3.0)
        s.clear()
        assertTrue(s.floorDb().isNaN())
        assertTrue(s.ceilingDb().isNaN())
        assertEquals(0f, s.amplitude(90.0), 1e-6f)
    }
}
