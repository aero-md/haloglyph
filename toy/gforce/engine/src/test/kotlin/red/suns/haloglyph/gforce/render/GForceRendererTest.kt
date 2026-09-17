package red.suns.haloglyph.gforce.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.gforce.engine.GForceEngine
import red.suns.haloglyph.gforce.engine.GForceMode
import red.suns.haloglyph.gforce.engine.GForceScale
import kotlin.math.hypot

/**
 * Le cadran, et surtout le **miroir**.
 *
 * C'est le seul endroit du toy où une erreur ne se verrait pas en relisant le
 * code : la matrice se regarde par l'arrière, le hublot par l'avant, et les deux
 * doivent montrer la même scène vue des deux côtés. Ici le miroir porte sur deux
 * choses et non une — la course de la bille, **et** les deux pics latéraux, qui
 * échangent de place. Un signe oublié sur l'un des deux donne un cadran qui se
 * contredit d'un mode à l'autre, ce que personne ne remarque sans les comparer.
 */
class GForceRendererTest {

    private val spec = MatrixSpec.Phone3

    private fun reading(
        front: Float = 0f,
        right: Float = 0f,
        hasFix: Boolean = true,
        peakFront: Float = 0f,
        peakRear: Float = 0f,
        peakLeft: Float = 0f,
        peakRight: Float = 0f,
    ) = GForceEngine.Reading(
        towardFront = front,
        towardRight = right,
        hasFix = hasFix,
        front = peakFront,
        rear = peakRear,
        left = peakLeft,
        right = peakRight,
    )

    private fun draw(fromBack: Boolean, r: GForceEngine.Reading, mode: GForceMode): Frame {
        val frame = Frame(spec)
        GForceRenderer(spec, fromBack).render(frame, r, mode)
        return frame
    }

    /** Le centre de masse de la bille : le seul plein feu du mode vif. */
    private fun ball(frame: Frame): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        var weight = 0f
        for (i in spec.leds) {
            val v = frame.values[i]
            if (v < 0.95f) continue
            sx += spec.xOf(i) * v
            sy += spec.yOf(i) * v
            weight += v
        }
        assertTrue("aucune bille allumée", weight > 0f)
        return sx / weight to sy / weight
    }

    private fun count(frame: Frame, min: Float): Int = spec.leds.count { frame.values[it] > min }

    // -------------------------------------------------------------- la bille

    /**
     * On freine : la bille part **vers le haut**, des deux côtés du téléphone.
     * C'est l'axe que le miroir ne touche pas, et il sert de témoin.
     */
    @Test
    fun `un freinage envoie la bille vers le haut`() {
        val r = reading(front = 0.8f)
        assertTrue(ball(draw(true, r, GForceMode.VIF)).second < spec.centerY)
        assertTrue(ball(draw(false, r, GForceMode.VIF)).second < spec.centerY)
    }

    /**
     * Virage à gauche, donc corps jeté à droite : la bille part à droite sur un
     * hublot, à gauche sur la matrice — qui est de l'autre côté du téléphone.
     */
    @Test
    fun `le miroir inverse l'abscisse de la bille`() {
        val r = reading(right = 0.8f)
        assertTrue(
            "sur la matrice, la bille devrait partir à gauche",
            ball(draw(true, r, GForceMode.VIF)).first < spec.centerX,
        )
        assertTrue(
            "sur un hublot, la bille devrait partir à droite",
            ball(draw(false, r, GForceMode.VIF)).first > spec.centerX,
        )
    }

    @Test
    fun `a l'arret la bille est au centre`() {
        val (x, y) = ball(draw(true, reading(), GForceMode.VIF))
        assertEquals(spec.centerX.toFloat(), x, 0.01f)
        assertEquals(spec.centerY.toFloat(), y, 0.01f)
    }

    /**
     * À pleine échelle, la bille est **coupée par le bord** : la fin de course est
     * le bord, pas deux cellules avant.
     */
    @Test
    fun `a pleine echelle la bille sort a moitie du champ`() {
        val frame = draw(true, reading(front = GForceScale.FULL_SCALE), GForceMode.VIF)
        val lit = count(frame, 0.95f)
        assertTrue("la bille a disparu ($lit cellules)", lit >= 5)
        assertTrue("la bille n'est pas entamée ($lit cellules)", lit < 21)
        val (_, y) = ball(frame)
        assertTrue("la bille n'atteint pas le bord ($y)", y < 3f)
    }

    /**
     * La course est bornée **en norme** : une poussée pleine en diagonale ne doit
     * pas envoyer la bille dans un coin, c'est-à-dire hors du disque.
     */
    @Test
    fun `la course est bornee en norme, pas axe par axe`() {
        val full = GForceScale.FULL_SCALE
        val frame = draw(true, reading(front = full, right = full), GForceMode.VIF)
        val (x, y) = ball(frame)
        val d = hypot(x - spec.centerX, y - spec.centerY)
        assertTrue("la bille est partie à $d du centre", d <= spec.maxDistance + 0.6f)
    }

    // --------------------------------------------------------- les graduations

    /** Deux graduations par côté, et rien d'autre autour de la bille. */
    @Test
    fun `le mode vif porte huit graduations`() {
        val frame = draw(true, reading(hasFix = false), GForceMode.VIF)
        // Quatre côtés, deux repères, cinq cellules chacun.
        assertEquals(4 * GForceScale.TICKS * 5, count(frame, 0f))
    }

    /** Pas de cerne, pas de couronne : un accéléromètre n'a pas de cible. */
    @Test
    fun `il n'y a ni cerne ni couronne`() {
        val frame = draw(true, reading(front = 0.3f), GForceMode.VIF)
        val rim = spec.leds.count { spec.distance[it] >= spec.maxDistance - 0.8f }
        val lit = spec.leds.count {
            frame.values[it] > 0f && spec.distance[it] >= spec.maxDistance - 0.8f
        }
        assertTrue("le cerne est allumé à $lit/$rim", lit == 0)
    }

    /** Sans mesure, pas de bille : la règle reste, ce qu'elle mesurerait n'y est pas. */
    @Test
    fun `sans mesure, la regle est seule`() {
        val frame = draw(true, reading(hasFix = false), GForceMode.VIF)
        assertEquals("une bille sans capteur", 0, count(frame, 0.5f))
    }

    // ---------------------------------------------------------------- les pics

    /** Les graduations et la bille s'effacent : la place est aux chiffres. */
    @Test
    fun `les pics remplacent la bille et la regle`() {
        val live = draw(true, reading(front = 0.4f), GForceMode.VIF)
        val peaks = draw(true, reading(peakFront = 1.2f, peakRear = 0.5f), GForceMode.PICS)
        assertTrue("la bille est restée", count(peaks, 0.95f) < count(live, 0.95f))
        // Les deux graduations de l'axe vertical tombent entre les blocs de
        // chiffres : si elles étaient encore dessinées, elles se verraient là.
        val tick = (spec.maxDistance / 3f).toInt()
        assertTrue("la règle est restée", live[spec.centerX, spec.centerY - tick] > 0f)
        assertEquals(0f, peaks[spec.centerX, spec.centerY - tick], 1e-6f)
        assertEquals(0f, peaks[spec.centerX, spec.centerY + tick], 1e-6f)
    }

    /** Ce qui est allumé dans une bande de lignes. */
    private fun rows(frame: Frame, range: IntRange): Int =
        spec.leds.count { frame.values[it] > 0f && spec.yOf(it) in range }

    /**
     * Les quatre nombres sont **là où la bille était** : le freinage en haut,
     * l'accélération en bas, les virages sur les côtés.
     */
    @Test
    fun `chaque pic s'ecrit du cote ou la bille partait`() {
        val frame = draw(
            false,
            reading(peakFront = 1.2f, peakRear = 0.5f, peakLeft = 0.9f, peakRight = 0.8f),
            GForceMode.PICS,
        )
        assertTrue("rien en haut", rows(frame, 2..7) > 8)
        assertTrue("rien en bas", rows(frame, 17..22) > 8)
        assertTrue("rien au milieu", rows(frame, 10..15) > 20)
    }

    /**
     * Le miroir échange les deux latéraux, exactement comme il échange la course de
     * la bille. On compare les deux surfaces sur un cadran où un seul côté porte un
     * chiffre non nul.
     */
    @Test
    fun `le miroir echange les deux pics lateraux`() {
        // Un seul côté porte un chiffre non nul, et `11` n'allume pas le même
        // nombre de cellules que `00` : les deux moitiés sont donc distinguables
        // au comptage, ce qu'un cadran symétrique ne permettrait pas.
        val r = reading(peakRight = 1.1f)
        val front = draw(false, r, GForceMode.PICS)
        val back = draw(true, r, GForceMode.PICS)

        val left = 0..10
        val right = 14..24
        val band = 10..15
        assertTrue(
            "les deux côtés sont identiques, le test ne prouve rien",
            lit(front, left, band) != lit(front, right, band),
        )
        assertEquals(
            "la matrice ne renvoie pas à gauche ce que le hublot met à droite",
            lit(front, right, band),
            lit(back, left, band),
        )
        assertEquals(
            lit(front, left, band),
            lit(back, right, band),
        )
    }

    private fun lit(frame: Frame, xs: IntRange, ys: IntRange): Int = spec.leds.count {
        frame.values[it] > 0f && spec.xOf(it) in xs && spec.yOf(it) in ys
    }

    /**
     * La virgule : un pixel, une ligne **sous** la ligne de base, dans la gouttière
     * qui sépare déjà les deux chiffres. C'est ce qui permet à quatre valeurs de
     * tenir sur un disque de vingt-cinq LEDs.
     */
    @Test
    fun `la virgule est un pixel sous la gouttiere`() {
        val frame = draw(false, reading(peakFront = 1.2f), GForceMode.PICS)
        // Valeur du haut : chiffres en lignes 2..6, virgule en ligne 7, colonne 12.
        assertTrue("pas de virgule", frame[12, 7] > 0f)
        assertEquals("la gouttière n'est pas vide", 0f, frame[12, 4], 1e-6f)
    }

    /** Un cadran vierge affiche quatre zéros, et c'est la vérité. */
    @Test
    fun `un trajet neuf affiche des zeros`() {
        assertEquals("00", GForceScale.format(0f))
        assertEquals("12", GForceScale.format(1.16f))
        assertEquals("99", GForceScale.format(42f))
    }

    // ------------------------------------------------------------------ repos

    /**
     * Le repos porte **deux** choses : la marque du toy au milieu, `TAP` en bas.
     *
     * Le mot seul ne suffisait plus : trois hublots du pack l'affichaient au pixel
     * près, et on ne savait pas lequel on allait réveiller.
     */
    @Test
    fun `le repos porte la marque du toy et TAP`() {
        val frame = Frame(spec)
        GForceRenderer(spec, fromBack = false).renderIdle(frame)
        // `TAP` en 3×4 lignes 5 à 8, la marque lignes 12 à 22 : le bloc est centré,
        // mot compris, puis la marque seule descend de deux lignes.
        assertEquals("pas de TAP", 21, rows(frame, 5..8))
        assertEquals("la marque n'est pas entière", 29, rows(frame, 12..22))
        assertEquals("quelque chose traîne au-dessus", 0, rows(frame, 0..4))
        assertEquals("le mot colle à la marque", 0, rows(frame, 9..11))
        assertEquals("quelque chose traîne en dessous", 0, rows(frame, 23..24))
    }

    /**
     * La marque est le cadran vif en miniature : la bille au centre, et **deux
     * graduations de chaque côté** aux mêmes places relatives que sur le vrai
     * cadran — à trois et à cinq cellules du centre, sur les quatre axes.
     */
    @Test
    fun `la marque porte deux graduations par cote`() {
        val frame = Frame(spec)
        GForceRenderer(spec, fromBack = false).renderIdle(frame)
        // La marque est centrée sur **elle-même**, pas sur le disque : le bloc
        // entier est centré puis la marque descend de deux lignes, donc son milieu
        // tombe cinq lignes sous celui de la matrice.
        val c = spec.centerX
        val m = 17
        for (d in listOf(3, 5)) {
            assertTrue("graduation haute à $d", frame[c, m - d] > 0f)
            assertTrue("graduation basse à $d", frame[c, m + d] > 0f)
            assertTrue("graduation gauche à $d", frame[c - d, m] > 0f)
            assertTrue("graduation droite à $d", frame[c + d, m] > 0f)
        }
        assertTrue("pas de bille au centre", frame[c, m] > 0f)
        // Entre la bille et la première graduation, du vide.
        assertEquals(0f, frame[c + 2, m], 1e-6f)
        assertEquals(0f, frame[c, m - 2], 1e-6f)
    }

    // ----------------------------------------------------------- les invariants

    /**
     * Aucun bord dégradé : les luminosités présentes sont **des niveaux**, pas une
     * rampe.
     */
    @Test
    fun `le cadran n'a aucun bord degrade`() {
        for (mode in GForceMode.entries) {
            val frame = draw(
                true,
                reading(front = 0.4f, right = -0.3f, peakFront = 1.2f, peakLeft = 0.9f),
                mode,
            )
            val levels = spec.leds.map { frame.values[it] }.filter { it > 0f }.distinct()
            assertTrue("$mode : trop de nuances : $levels", levels.size <= 3)
        }
    }

    /** Rien ne sort du masque, quelle que soit la poussée. */
    @Test
    fun `rien ne deborde du disque`() {
        val poses = listOf(
            reading(front = 3f, right = 3f),
            reading(front = -3f, right = -3f),
            reading(peakFront = 9.9f, peakRear = 9.9f, peakLeft = 9.9f, peakRight = 9.9f),
        )
        for (mode in GForceMode.entries) {
            for (pose in poses) {
                val frame = draw(true, pose, mode)
                for (i in 0 until spec.cellCount) {
                    if (!spec.isLed(i)) {
                        assertEquals("cellule $i hors disque allumée", 0f, frame.values[i], 0f)
                    }
                }
            }
        }
    }
}
