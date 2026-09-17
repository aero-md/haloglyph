package red.suns.haloglyph.float.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'échelle de la bulle — la raison d'être du toy.
 *
 * Ce qui est vérifié ici n'est pas que la formule est celle qu'on a écrite, mais
 * qu'elle **répare le défaut qu'on lui demande de réparer** : un niveau linéaire
 * sur vingt-cinq LEDs ne bouge pas d'un pixel pour un degré, et c'est le degré
 * qu'on cherche.
 */
class FloatScaleTest {

    /**
     * Le rayon utile d'un Phone (3) : celui du disque, sans retenue.
     *
     * La bulle a le droit de dépasser — son centre atteint le cerne, donc la moitié
     * du motif tombe hors du masque. On ne retranche donc plus son étendue ici.
     */
    private val maxRadius = 12.4f

    @Test
    fun `zero au centre, pleine echelle au bord`() {
        for (range in FloatRange.entries) {
            assertEquals(
                "${range.name} à plat",
                0f,
                FloatScale.radiusOf(0f, range.degrees, maxRadius),
                1e-6f,
            )
            assertEquals(
                "${range.name} à pleine échelle",
                maxRadius,
                FloatScale.radiusOf(range.degrees, range.degrees, maxRadius),
                1e-3f,
            )
        }
    }

    /** Au-delà, la bulle colle au bord au lieu de sortir du disque. */
    @Test
    fun `l'echelle sature au lieu de deborder`() {
        val r = FloatScale.radiusOf(180f, FloatRange.NORMALE.degrees, maxRadius)
        assertEquals(maxRadius, r, 1e-6f)
    }

    @Test
    fun `strictement croissante`() {
        var previous = -1f
        var degrees = 0f
        while (degrees <= 15f) {
            val r = FloatScale.radiusOf(degrees, FloatRange.NORMALE.degrees, maxRadius)
            assertTrue("$degrees° recule à $r", r > previous)
            previous = r
            degrees += 0.05f
        }
    }

    /**
     * Le cœur du sujet.
     *
     * Un degré d'inclinaison, en portée normale : l'échelle linéaire déplace la
     * bulle de moins d'une cellule — donc de rien du tout, puisqu'une cellule est
     * l'unité — là où la logarithmique en déplace plusieurs.
     */
    @Test
    fun `un degre se voit, ce qu'une echelle lineaire ne fait pas`() {
        val full = FloatRange.NORMALE.degrees
        val linear = maxRadius * 1f / full
        val log = FloatScale.radiusOf(1f, full, maxRadius)

        assertTrue("le linéaire déplace déjà $linear cellule(s)", linear < 1f)
        assertTrue("le logarithmique ne déplace que $log", log > 4.5f)
    }

    /**
     * Et le dixième de degré se voit maintenant partout — c'est ce que le genou
     * resserré a acheté : une cellule pleine là où il en fallait deux.
     */
    @Test
    fun `un dixieme de degre deplace la bulle d'au moins une cellule`() {
        for (range in FloatRange.entries) {
            val r = FloatScale.radiusOf(0.1f, range.degrees, maxRadius)
            assertTrue("${range.name} : 0,1° ne déplace que $r cellule(s)", r >= 0.9f)
        }
        assertTrue(FloatScale.radiusOf(0.1f, FloatRange.FINE.degrees, maxRadius) >= 1.4f)
    }

    /**
     * La propriété qui fait que le réglage s'appelle une portée et non une
     * sensibilité : changer de pleine échelle ne dégrade presque pas la finesse
     * au centre. En linéaire, le rapport serait de neuf.
     */
    @Test
    fun `la portee ne coute presque rien pres du centre`() {
        val fine = FloatScale.radiusOf(0.5f, FloatRange.FINE.degrees, maxRadius)
        val wide = FloatScale.radiusOf(0.5f, FloatRange.LARGE.degrees, maxRadius)
        assertTrue("rapport de ${fine / wide}", fine / wide < 2f)
    }

    @Test
    fun `tiltAt est l'inverse de radiusOf`() {
        for (range in FloatRange.entries) {
            for (degrees in listOf(0.1f, 0.5f, 1f, 3f, range.degrees)) {
                val r = FloatScale.radiusOf(degrees, range.degrees, maxRadius)
                assertEquals(
                    "${range.name} à $degrees°",
                    degrees,
                    FloatScale.tiltAt(r, range.degrees, maxRadius),
                    1e-2f,
                )
            }
        }
    }

    /**
     * La tolérance **est** la demi-cellule de course : au-delà, l'arrondi pose la
     * bulle sur la cellule d'à côté et elle n'est plus « au centre ».
     *
     * Elle s'exprime en part de rayon, donc elle ne dépend pas de la taille du
     * disque — ce qui laisse la porte ouverte à une matrice 13×13 sans rien à
     * retoucher. En toute rigueur une matrice plus grossière mériterait une
     * tolérance plus large, puisque sa demi-cellule vaut plus d'angle ; c'est un
     * problème du jour où une seconde géométrie existera.
     */
    @Test
    fun `la tolerance est celle du centre, quelle que soit la matrice`() {
        for (range in FloatRange.entries) {
            val expected = FloatScale.tolerance(range)
            for (radius in listOf(4f, 12.4f, 40f)) {
                assertEquals(
                    "${range.name} sur un rayon de $radius",
                    expected,
                    FloatScale.tiltAt(FloatScale.LOCK_FRACTION * radius, range.degrees, radius),
                    1e-4f,
                )
            }
        }
    }

    /**
     * Les ordres de grandeur annoncés dans la documentation du toy : deux à trois
     * **minutes d'arc**, soit moins d'un millimètre par mètre.
     */
    @Test
    fun `les tolerances annoncees`() {
        assertEquals(0.028f, FloatScale.tolerance(FloatRange.FINE), 0.004f)
        assertEquals(0.038f, FloatScale.tolerance(FloatRange.NORMALE), 0.004f)
        assertEquals(0.048f, FloatScale.tolerance(FloatRange.LARGE), 0.005f)
    }

    /**
     * Et le seuil est bien celui du **dessin** : à la tolérance exacte, la bulle
     * est encore posée sur la cellule centrale ; au double, elle est à côté.
     */
    @Test
    fun `au seuil, la bulle est encore au centre`() {
        for (range in FloatRange.entries) {
            val tolerance = FloatScale.tolerance(range)
            val inside = FloatScale.radiusOf(tolerance, range.degrees, maxRadius)
            assertTrue("${range.name} : $inside cellule(s)", Math.round(inside) == 0)
            val outside = FloatScale.radiusOf(tolerance * 2.5f, range.degrees, maxRadius)
            assertTrue("${range.name} : $outside cellule(s)", Math.round(outside) >= 1)
        }
    }
}
