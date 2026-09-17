package red.suns.haloglyph.sono.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'arithmétique de tranches, qui est la seule chose difficile de la classe.
 *
 * Elle l'est parce qu'elle mélange un temps flottant et un compte entier : une
 * échéance qu'on avance par additions successives dérive, et une division tombe
 * un ulp sous l'entier attendu juste à la bascule. Les deux bugs ont existé, et
 * ce sont eux que ces tests tiennent.
 */
class WaveformTest {

    private val columns = 25
    private val step = 0.06

    private fun waveform() = Waveform(columns, step)

    /** Pousse [n] tranches pleines, en donnant à chacune son propre niveau. */
    private fun fill(w: Waveform, n: Int, level: (Int) -> Float) {
        for (k in 0 until n) w.push(level(k), k * step)
        // La dernière tranche n'est commitée qu'à la bascule suivante.
        w.push(0f, n * step)
    }

    @Test
    fun `une tranche neuve n'est pas encore de l'histoire`() {
        val w = waveform()
        w.push(1f, 0.0)
        assertEquals(0, w.filled)
        assertEquals(Waveform.EMPTY, w.at(0), 0f)
    }

    @Test
    fun `la tranche la plus recente est en tete`() {
        val w = waveform()
        fill(w, 3) { 0.1f * (it + 1) }
        // age 0 = la dernière commitée, donc la troisième
        assertEquals(0.3f, w.at(0), 1e-6f)
        assertEquals(0.2f, w.at(1), 1e-6f)
        assertEquals(0.1f, w.at(2), 1e-6f)
    }

    /**
     * L'agrégation est un maximum : sur soixante millisecondes, une moyenne
     * diluerait une frappe dans le silence qui l'entoure.
     */
    @Test
    fun `une tranche garde le plus fort de ce qu'elle a recu`() {
        val w = waveform()
        w.push(0.2f, 0.00)
        w.push(0.9f, 0.02)
        w.push(0.3f, 0.04)
        w.push(0f, step)
        assertEquals(0.9f, w.at(0), 1e-6f)
    }

    /**
     * Le piège du flottant : cinq additions de 0,2 ne font pas 1,0, et
     * `0,6 / 0,2` vaut 2,999 999 999 999 999 6. Une seconde de temps doit faire
     * défiler le nombre exact de tranches, pas une de moins.
     */
    @Test
    fun `une seconde fait defiler le nombre exact de tranches`() {
        val w = Waveform(columns, 0.2)
        for (k in 0..5) w.push(0.5f, k * 0.2)
        // 5 bascules pour 6 poussées à 0,2 s d'intervalle
        assertEquals(5, w.filled)
    }

    @Test
    fun `une tranche pile sur l'echeance n'est pas perdue`() {
        val w = Waveform(columns, 0.2)
        w.push(0.5f, 0.0)
        w.push(0.5f, 0.2)
        w.push(0.5f, 0.4)
        w.push(0.5f, 0.6)
        assertEquals(3, w.filled)
    }

    /**
     * Une image sautée peut valoir plusieurs tranches — GC, veille, service
     * suspendu — et les colonnes manquantes doivent défiler quand même, sinon le
     * temps de l'image n'est plus celui du son.
     */
    @Test
    fun `un saut de plusieurs tranches les fait toutes defiler`() {
        val w = waveform()
        w.push(0.8f, 0.0)
        w.push(0.4f, step * 4)
        assertEquals(4, w.filled)
        // la tranche de 0,8 est retombée trois crans en arrière
        assertEquals(0.8f, w.at(3), 1e-6f)
    }

    /** Au-delà d'un écran entier, tout ce qui restait est périmé. */
    @Test
    fun `un saut de plus d'un ecran repart de zero`() {
        val w = waveform()
        fill(w, columns) { 1f }
        assertEquals(columns, w.filled)
        w.push(0.5f, step * (columns * 3))
        assertEquals(1, w.filled)
    }

    /**
     * Les aperçus de l'app comptent depuis l'ouverture de leur écran : leur
     * horloge repart de zéro sous une histoire déjà commencée. Sans recalage, la
     * tranche en cours attendrait de rattraper son ancienne échéance.
     */
    @Test
    fun `une horloge qui recule recale l'origine au lieu de figer`() {
        val w = waveform()
        // Histoire commencée tard dans la vie du processus…
        for (k in 0..5) w.push(0.5f, 100.0 + k * step)
        val before = w.filled
        assertTrue(before > 0)

        // …puis une horloge qui repart de zéro, comme celle d'un aperçu qu'on
        // rouvre. Sans recalage, la tranche courante attendrait de rattraper son
        // ancienne échéance et rien ne défilerait plus jamais.
        w.push(0.9f, 0.0)
        w.push(0.9f, step)
        w.push(0.9f, 2 * step)
        assertTrue("le visualiseur s'est figé", w.filled > before)
    }

    @Test
    fun `le tampon ne garde jamais plus que sa largeur`() {
        val w = waveform()
        fill(w, columns * 3) { 0.5f }
        assertEquals(columns, w.filled)
        assertEquals(Waveform.EMPTY, w.at(columns), 0f)
    }

    @Test
    fun `clear efface l'histoire et l'origine`() {
        val w = waveform()
        fill(w, 10) { 0.5f }
        w.clear()
        assertEquals(0, w.filled)
        assertEquals(Waveform.EMPTY, w.at(0), 0f)
    }
}
