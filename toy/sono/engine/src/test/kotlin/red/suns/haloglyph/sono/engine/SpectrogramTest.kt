package red.suns.haloglyph.sono.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'histoire du spectre : ce qui doit rester vrai quand le temps passe, saute,
 * ou revient en arrière.
 */
class SpectrogramTest {

    private val bands = 4
    private val rows = 5
    private val row = 0.2

    private fun history() = Spectrogram(bands, rows, row)

    private fun flat(v: Double) = DoubleArray(bands) { v }

    @Test
    fun `rien pousse, rien a lire`() {
        val h = history()
        assertEquals(0, h.filled)
        assertEquals(Spectrogram.EMPTY, h.at(0, 0), 1e-9)
    }

    /** Une tranche ne bascule qu'une fois sa durée écoulée. */
    @Test
    fun `une ligne par tranche de temps`() {
        val h = history()
        h.push(flat(50.0), 0.0)
        h.push(flat(50.0), 0.1)
        assertEquals(0, h.filled)

        h.push(flat(50.0), 0.2)
        assertEquals(1, h.filled)
        assertEquals(50.0, h.at(0, 0), 1e-9)
    }

    /**
     * Agrégation par maximum : sur 200 ms, une moyenne diluerait un transitoire
     * dans le silence qui l'entoure. C'est la frappe qu'on vient voir.
     */
    @Test
    fun `la tranche garde le maximum, pas la moyenne`() {
        val h = history()
        h.push(flat(40.0), 0.0)
        h.push(flat(95.0), 0.05)
        h.push(flat(42.0), 0.1)
        h.push(flat(40.0), 0.2)
        assertEquals(95.0, h.at(0, 0), 1e-9)
    }

    /** Le plus récent est en tête, et l'histoire recule d'un cran par tranche. */
    @Test
    fun `l'age zero est la ligne la plus recente`() {
        val h = history()
        var t = 0.0
        for (v in listOf(50.0, 60.0, 70.0)) {
            h.push(flat(v), t)
            t += row
            h.push(flat(v), t)
        }
        assertEquals(70.0, h.at(0, 0), 1e-9)
        assertEquals(60.0, h.at(1, 0), 1e-9)
        assertEquals(50.0, h.at(2, 0), 1e-9)
    }

    /**
     * Une image sautée peut valoir plusieurs tranches — GC, veille, service
     * suspendu. Les lignes manquantes défilent quand même, sinon le temps de
     * l'image ne serait plus celui du son.
     *
     * C'est aussi le test qui garde l'arithmétique honnête : avancer une
     * échéance de 0,2 en 0,2 ne retombe pas sur 0,6 en flottant, et un saut de
     * trois tranches n'en faisait défiler que deux.
     */
    @Test
    fun `un trou dans le temps fait defiler toutes les lignes sautees`() {
        val h = history()
        h.push(flat(50.0), 0.0)
        h.push(flat(80.0), 0.6)
        assertEquals("3 tranches en une fois", 3, h.filled)
        // La valeur agrégée part avec la première ligne commise ; les suivantes
        // sont vides, parce que rien n'a été mesuré pendant le trou.
        assertEquals(80.0, h.at(2, 0), 1e-9)
        assertEquals(Spectrogram.EMPTY, h.at(0, 0), 1e-9)
    }

    /**
     * Au-delà d'un écran, il n'y a plus rien à faire défiler : tout ce qui
     * restait est périmé. On efface au lieu d'itérer — un trou d'une heure vaut
     * dix-huit mille tranches, et le résultat serait le même écran vide.
     */
    @Test
    fun `un trou plus long que l'ecran efface l'histoire`() {
        val h = history()
        var t = 0.0
        repeat(rows) {
            h.push(flat(50.0), t)
            t += row
        }
        assertTrue(h.filled > 1)

        h.push(flat(80.0), t + rows * row * 10)
        assertEquals(1, h.filled)
        assertEquals(80.0, h.at(0, 0), 1e-9)
        assertEquals(Spectrogram.EMPTY, h.at(1, 0), 1e-9)
    }

    /** Le tampon est circulaire : au-delà de sa hauteur, on oublie le plus vieux. */
    @Test
    fun `l'histoire ne depasse jamais la hauteur de la matrice`() {
        val h = history()
        var t = 0.0
        repeat(rows * 3) {
            h.push(flat(50.0 + it), t)
            t += row
            h.push(flat(50.0 + it), t)
        }
        assertEquals(rows, h.filled)
        assertEquals(Spectrogram.EMPTY, h.at(rows, 0), 1e-9)
    }

    /**
     * Les aperçus de l'app comptent depuis l'ouverture de leur écran : leur
     * horloge repart de zéro. Sans recalage, la tranche en cours attendrait de
     * rattraper son ancienne échéance et le spectrogramme se figerait.
     */
    @Test
    fun `une horloge qui recule ne fige pas le defilement`() {
        val h = history()
        h.push(flat(50.0), 100.0)
        h.push(flat(50.0), 100.2)
        val before = h.filled

        h.push(flat(60.0), 0.0)
        h.push(flat(60.0), 0.2)
        assertTrue("le défilement s'est arrêté", h.filled > before)
        assertEquals(60.0, h.at(0, 0), 1e-9)
    }

    @Test
    fun `clear efface tout`() {
        val h = history()
        h.push(flat(50.0), 0.0)
        h.push(flat(50.0), 0.4)
        h.clear()
        assertEquals(0, h.filled)
        assertEquals(Spectrogram.EMPTY, h.at(0, 0), 1e-9)
    }
}
