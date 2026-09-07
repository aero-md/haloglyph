package red.suns.haloglyph.dice.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.dice.engine.Dice
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Roll
import red.suns.haloglyph.dice.engine.T_END
import red.suns.haloglyph.dice.engine.T_LAND
import red.suns.haloglyph.dice.engine.revealAt
import red.suns.haloglyph.dice.engine.zoomAt
import kotlin.random.Random

/**
 * Ce qu'un dé posé doit montrer : des points **alignés**, et qui ne bougent
 * plus.
 *
 * Les deux défauts corrigés ici se voyaient tout de suite et se décrivaient
 * mal — « les puces sautent », « le dé a l'air de travers ». Ils avaient la même
 * origine : une position sub-cellulaire arrondie image par image, et pip par
 * pip. Ces tests fixent le résultat en cellules entières, là où l'œil le lit.
 */
class PipLayoutTest {

    private val spec = MatrixSpec.Phone3
    private val renderer = DiceRenderer(spec)

    /** Centres des blocs de marque d'une frame, en cellules. */
    private fun marks(die: Die, view: red.suns.haloglyph.dice.engine.View, lit: Float): Set<Pair<Int, Int>> {
        val frame = Frame(spec)
        renderer.render(frame, die, view)
        val cells = spec.leds.filter { frame.values[it] >= lit - 1e-4f }
            .map { spec.xOf(it) to spec.yOf(it) }
            .toSet()
        return cells.filter { (x, y) -> (x - 1 to y) !in cells && (x to y - 1) !in cells }
            .map { it.first + 1 to it.second + 1 }
            .toSet()
    }

    private fun resting(die: Die, value: Int, twist: Int) =
        marks(die, Roll.resting(die, die.restQuat(value, twist)), 1f)

    /**
     * Le défaut tel qu'il se voyait : un 4 en trapèze, la puce médiane du 6 qui
     * change de colonne d'une rangée à l'autre.
     *
     * Le motif est symétrique par rapport au centre de la face ; une fois posé
     * sur la trame, il doit le rester. C'était faux dès que le cran de rotation
     * n'était pas nul, parce que chaque pip était projeté puis arrondi dans son
     * coin — une demi-cellule suffisait à faire basculer l'un sans l'autre.
     */
    @Test
    fun `les pips du d6 sont symetriques par rapport au centre du hublot`() {
        val d6 = Dice.byKey("d6")
        val cx = spec.centerX
        val cy = spec.centerY
        for (value in 1..6) {
            for (twist in 0 until d6.spin) {
                val pips = resting(d6, value, twist)
                assertEquals("valeur $value cran $twist", value, pips.size)
                for ((x, y) in pips) {
                    assertTrue(
                        "valeur $value cran $twist : (${x}, $y) sans opposé",
                        (2 * cx - x to 2 * cy - y) in pips,
                    )
                }
            }
        }
    }

    /**
     * Les pips se rangent en colonnes et en lignes, régulièrement espacées :
     * trois valeurs au plus sur chaque axe, et l'écart du haut égal à celui du
     * bas. C'est ce qui fait qu'un 6 se lit comme deux colonnes de trois plutôt
     * que comme six taches.
     */
    @Test
    fun `les pips du d6 tombent sur une grille reguliere`() {
        val d6 = Dice.byKey("d6")
        for (value in 1..6) {
            for (twist in 0 until d6.spin) {
                val pips = resting(d6, value, twist)
                val label = "valeur $value cran $twist"
                for (axis in listOf(pips.map { it.first }, pips.map { it.second })) {
                    val lines = axis.distinct().sorted()
                    assertTrue("$label : ${lines.size} lignes", lines.size <= 3)
                    if (lines.size == 3) {
                        assertEquals(
                            "$label : lignes irrégulières $lines",
                            lines[1] - lines[0],
                            lines[2] - lines[1],
                        )
                    }
                }
            }
        }
    }

    /**
     * Le cran de rotation ne change pas ce qu'on lit : les marques sont
     * tamponnées droites, et les six motifs du d6 sont symétriques au quart de
     * tour. Une face posée doit donc donner **exactement** la même trame quel
     * que soit son cran.
     */
    @Test
    fun `le cran de rotation ne deplace pas les pips`() {
        val d6 = Dice.byKey("d6")
        for (value in 1..6) {
            val reference = resting(d6, value, 0)
            for (twist in 1 until d6.spin) {
                assertEquals("valeur $value cran $twist", reference, resting(d6, value, twist))
            }
        }
    }

    /**
     * L'invariant de la révélation : **dès qu'une marque est visible, elle est à
     * sa place définitive.** La caméra a fini sa course avant que quoi que ce
     * soit s'allume, il ne reste donc aucune cellule à parcourir.
     */
    @Test
    fun `une marque visible ne bouge plus d'une seule cellule`() {
        for (die in Dice.ALL) {
            for (value in intArrayOf(1, die.faceCount)) {
                val roll = Roll.make(die, die.restQuat(1, 0), value, Random(value.toLong()))
                val settled = marks(die, roll.viewAt(T_END), 1f)
                assertTrue("${die.id}/$value : aucune marque à l'arrivée", settled.isNotEmpty())

                var t = T_LAND
                var seen = 0
                while (t <= T_END) {
                    val lit = revealAt(zoomAt(t)).toFloat()
                    // Au-dessus de l'arête : en dessous, la marque n'écrase pas
                    // encore la carcasse et le relevé serait partiel.
                    if (lit > 0.25f) {
                        assertEquals(
                            "${die.id}/$value à t=%.3f".format(t - T_LAND),
                            settled,
                            marks(die, roll.viewAt(t), lit),
                        )
                        seen++
                    }
                    t += 0.01
                }
                assertTrue("${die.id}/$value : rien d'échantillonné", seen > 10)
            }
        }
    }
}
