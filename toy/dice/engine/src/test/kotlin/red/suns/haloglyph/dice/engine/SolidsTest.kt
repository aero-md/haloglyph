package red.suns.haloglyph.dice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les solides sont **dérivés**, pas écrits : les normales, les centres et les
 * rayons inscrits sortent d'un maillage, et les valeurs d'un appariement des
 * faces opposées. C'est exactement le genre de code où une erreur ne se voit
 * qu'en jouant — un dé vu de l'intérieur, un 20 qui n'existe pas, une face qui
 * en vaut deux.
 */
class SolidsTest {

    @Test
    fun `chaque de a le nombre de faces de son nom`() {
        assertEquals(6, Dice.byKey("d6").faceCount)
        assertEquals(10, Dice.byKey("d10").faceCount)
        assertEquals(12, Dice.byKey("d12").faceCount)
        assertEquals(20, Dice.byKey("d20").faceCount)
        assertEquals(4, Dice.ALL.size)
    }

    @Test
    fun `les normales sont unitaires et sortantes`() {
        for (die in Dice.ALL) {
            for (face in die.faces) {
                assertEquals("${die.id} : normale non unitaire", 1.0, face.n.length, 1e-12)
                // La distance au plan est positive : le centre du dé est dedans.
                assertTrue("${die.id} : face vue de l'intérieur", face.d > 0)
                assertTrue("${die.id} : cercle inscrit vide", face.inr > 0)
            }
        }
    }

    @Test
    fun `chaque valeur est portee une fois et une seule`() {
        for (die in Dice.ALL) {
            val values = die.faces.map { it.value }.sorted()
            assertEquals("${die.id}", (1..die.faceCount).toList(), values)
        }
    }

    /**
     * La convention des vrais dés : deux faces opposées somment à `n + 1`. Les
     * quatre solides retenus ont tous leurs faces par paires parallèles, donc
     * l'appariement doit aboutir partout — y compris sur le d6, dont les valeurs
     * sont posées à la main et ne passent pas par l'appariement automatique.
     */
    @Test
    fun `les faces opposees somment au nombre de faces plus un`() {
        for (die in Dice.ALL) {
            for (face in die.faces) {
                val opposite = die.faces.first { (it.n dot face.n) < -0.999 }
                assertEquals(
                    "${die.id} : ${face.value} en face de ${opposite.value}",
                    die.faceCount + 1,
                    face.value + opposite.value,
                )
            }
        }
    }

    /**
     * Le point qui tient tout le toy : la face relue de l'orientation est bien
     * celle qu'on a demandé de poser. Si ce test tombe, le dé affiche un nombre
     * à côté de celui qu'il a tiré — et l'écart ne se verrait qu'une fois sur
     * douze, sur un seul solide.
     */
    @Test
    fun `la pose de repos montre la face demandee, quel que soit le cran`() {
        for (die in Dice.ALL) {
            for (value in 1..die.faceCount) {
                for (twist in 0 until die.spin) {
                    assertEquals(
                        "${die.id} : valeur $value, cran $twist",
                        value,
                        die.topFace(die.restQuat(value, twist)),
                    )
                }
            }
        }
    }

    /**
     * Le centre visuel d'une face est dans le plan de cette face, et la
     * distance du centre du dé à ce plan est bien [Face.d]. C'est l'invariant
     * qui lie les deux descriptions d'une face — celle du rendu, qui coupe des
     * plans, et celle des marques, qui se posent sur un centre.
     */
    @Test
    fun `le centre d'une face est dans son plan`() {
        for (die in Dice.ALL) {
            for (face in die.faces) {
                assertEquals("${die.id}", face.d, face.c dot face.n, 1e-12)
            }
        }
    }

    @Test
    fun `un identifiant inconnu retombe sur le d6`() {
        assertEquals(DieId.D6, Dice.byKey(null).id)
        assertEquals(DieId.D6, Dice.byKey("d100").id)
    }

    @Test
    fun `l'appui long fait le tour des quatre solides`() {
        var die = Dice.DEFAULT
        val seen = mutableListOf(die.id)
        repeat(3) {
            die = Dice.next(die)
            seen += die.id
        }
        assertEquals(listOf(DieId.D6, DieId.D10, DieId.D12, DieId.D20), seen)
        assertEquals(DieId.D6, Dice.next(die).id)
    }

    /**
     * Le d6 est le seul à porter des pips, et il en porte autant que sa valeur.
     * Les autres portent un nombre : pas de pips, un glyphe non vide.
     */
    @Test
    fun `le d6 porte des pips, les autres un nombre`() {
        val d6 = Dice.byKey("d6")
        for (face in d6.faces) {
            assertEquals("face ${face.value}", face.value, face.pips.size)
            assertEquals("", face.glyph)
        }
        for (die in Dice.ALL.filter { it.id != DieId.D6 }) {
            for (face in die.faces) {
                assertTrue("${die.id}", face.pips.isEmpty())
                assertEquals(face.value.toString(), face.glyph)
            }
        }
    }

    /** Les pips tiennent dans le cercle inscrit de leur face — sinon ils bavent. */
    @Test
    fun `les pips restent dans leur face`() {
        val d6 = Dice.byKey("d6")
        for (face in d6.faces) {
            for (pip in face.pips) {
                val offset = (pip - face.c).length
                assertTrue("face ${face.value} : pip à $offset > ${face.inr}", offset < face.inr)
                // Et sur le plan de la face, à un cheveu près.
                assertTrue(abs((pip dot face.n) - face.d) < 1e-9)
            }
        }
    }
}
