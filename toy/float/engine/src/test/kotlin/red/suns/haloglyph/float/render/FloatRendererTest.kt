package red.suns.haloglyph.float.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.float.engine.Heading
import red.suns.haloglyph.float.engine.FloatEngine
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.engine.FloatRange
import red.suns.haloglyph.float.engine.FloatScale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Le dessin, et surtout le **miroir**.
 *
 * C'est le seul endroit du toy où une erreur ne se verrait pas en relisant le
 * code : la matrice se regarde par l'arrière, le hublot par l'avant, et les deux
 * doivent montrer la même scène vue des deux côtés. Un signe oublié donne un
 * niveau qui envoie la bulle du mauvais côté, ce que personne ne remarque tant
 * qu'on n'a pas les deux surfaces sous les yeux.
 *
 * Le reste tient les autres promesses : **aucun bord dégradé**, une bulle de
 * forme fixe qui a le droit de sortir du champ, une visée qui ne bouge jamais, un
 * cerne qui est tout ou rien, et une rose dont la hiérarchie est dans la taille.
 */
class FloatRendererTest {

    private val spec = MatrixSpec.Phone3

    /**
     * En deçà, rien de la rose ne peut se trouver.
     *
     * Les sondes de rose visent le **haut** du disque, où il n'y a que l'aiguille —
     * elle culmine à 8,06 du centre. Le repère le plus intérieur de la rose est
     * l'aileron d'un cardinal, à 9,0. La frontière est donc franche.
     */
    private val roseInner = 8.5f

    private fun reading(
        tilt: Float = 0f,
        dirX: Float = 0f,
        dirY: Float = 0f,
        heading: Float = 0f,
        hasHeading: Boolean = true,
        level: Boolean = false,
        vertical: Boolean = false,
        alongX: Boolean = true,
    ) = FloatEngine.Reading(
        tiltDegrees = tilt,
        dirX = dirX,
        dirY = dirY,
        hasTilt = true,
        vertical = vertical,
        alongX = alongX,
        level = level,
        moving = false,
        heading = heading,
        hasHeading = hasHeading,
        headingTrusted = true,
    )

    private fun draw(fromBack: Boolean, r: FloatEngine.Reading, mode: FloatMode): Frame {
        val frame = Frame(spec)
        FloatRenderer(spec, fromBack).render(frame, r, mode, FloatRange.NORMALE, 0.0)
        return frame
    }

    private fun idle(mode: FloatMode): Frame {
        val frame = Frame(spec)
        FloatRenderer(spec, fromBack = false).renderIdle(frame, mode)
        return frame
    }

    /**
     * Le centre de masse de la bulle.
     *
     * Un « pixel le plus clair » ne marcherait pas : la bulle est un plateau de
     * plusieurs cellules à pleine luminosité, et le premier de la liste est celui
     * du coin haut-gauche, pas celui du milieu.
     *
     * Ne vaut que sur une image **non verrouillée** : le cerne est à pleine lumière
     * lui aussi quand c'est droit, et il pèserait plus que la bulle.
     */
    private fun bubble(frame: Frame): Pair<Float, Float> {
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
        assertTrue("aucune bulle allumée", weight > 0f)
        return sx / weight to sy / weight
    }

    private fun count(frame: Frame, min: Float, minDistance: Float = 0f): Int =
        spec.leds.count { frame.values[it] > min && spec.distance[it] >= minDistance }

    /** Les cellules de la bulle. Même réserve que [bubble] : pas de verrou. */
    private fun bubbleCells(frame: Frame): Int = count(frame, 0.95f)

    // ----------------------------------------------------------------- niveau

    /**
     * Bord supérieur levé : la bulle monte, des deux côtés du téléphone. C'est
     * l'axe que le miroir ne touche pas, et il sert de témoin.
     */
    @Test
    fun `la bulle monte, vue de devant comme de derriere`() {
        val r = reading(tilt = 3f, dirY = 1f)
        assertTrue(bubble(draw(true, r, FloatMode.NIVEAU)).second < spec.centerY)
        assertTrue(bubble(draw(false, r, FloatMode.NIVEAU)).second < spec.centerY)
    }

    /**
     * Le flanc droit de l'**écran** levé : la bulle part à droite sur un hublot,
     * à gauche sur la matrice — qui est de l'autre côté du téléphone.
     */
    @Test
    fun `le miroir inverse l'abscisse de la bulle`() {
        val r = reading(tilt = 3f, dirX = 1f)
        assertTrue(
            "sur la matrice, la bulle devrait partir à gauche",
            bubble(draw(true, r, FloatMode.NIVEAU)).first < spec.centerX,
        )
        assertTrue(
            "sur un hublot, la bulle devrait partir à droite",
            bubble(draw(false, r, FloatMode.NIVEAU)).first > spec.centerX,
        )
    }

    @Test
    fun `a plat la bulle est centree`() {
        val (x, y) = bubble(draw(true, reading(), FloatMode.NIVEAU))
        assertEquals(spec.centerX.toFloat(), x, 0.01f)
        assertEquals(spec.centerY.toFloat(), y, 0.01f)
    }

    /**
     * À pleine échelle, la bulle est **coupée par le bord** — c'est la fin de
     * course, et une bulle qui s'arrêterait entière à deux cellules du cerne
     * mentirait sur l'endroit où l'échelle se termine.
     */
    @Test
    fun `a pleine echelle la bulle sort a moitie du champ`() {
        for (angle in 0 until 360 step 15) {
            val a = Math.toRadians(angle.toDouble())
            val frame = draw(
                true,
                reading(tilt = 90f, dirX = cos(a).toFloat(), dirY = sin(a).toFloat()),
                FloatMode.NIVEAU,
            )
            val lit = bubbleCells(frame)
            assertTrue("à $angle° la bulle a disparu ($lit cellules)", lit >= 5)
            assertTrue("à $angle° la bulle n'est pas entamée ($lit cellules)", lit < 21)

            val (x, y) = bubble(frame)
            val d = hypot(x - spec.centerX, y - spec.centerY)
            assertTrue("à $angle° la bulle n'atteint pas le bord ($d)", d > 9f)
        }
    }

    /**
     * Aucun bord dégradé : les luminosités présentes sont **des niveaux**, pas une
     * rampe. Une poignée de valeurs distinctes, et rien entre elles.
     */
    @Test
    fun `le niveau n'a aucun bord degrade`() {
        val frame = draw(true, reading(tilt = 3f, dirX = 0.6f, dirY = 0.8f), FloatMode.NIVEAU)
        val levels = spec.leds.map { frame.values[it] }.filter { it > 0f }.distinct()
        assertTrue("trop de nuances : $levels", levels.size <= 3)
    }

    @Test
    fun `la boussole n'a aucun bord degrade`() {
        val frame = draw(true, reading(heading = 37f), FloatMode.BOUSSOLE)
        val levels = spec.leds.map { frame.values[it] }.filter { it > 0f }.distinct()
        assertTrue("trop de nuances : $levels", levels.size <= 3)
    }

    /** Le rayon de la couronne de visée, tel que le renderer le calcule. */
    private val target = spec.maxDistance * FloatScale.TARGET_FRACTION

    private fun onRing(index: Int): Boolean =
        kotlin.math.abs(spec.distance[index] - target) <= 0.55f

    private fun onRim(index: Int): Boolean = spec.distance[index] >= spec.maxDistance - 0.8f

    /**
     * La visée ne dit rien de l'état de l'instrument : elle est la cible, elle est
     * toujours là, et toujours au même niveau. C'est le cerne qui porte l'état.
     */
    @Test
    fun `la visee ne change jamais`() {
        val loose = draw(true, reading(tilt = 3f, dirY = 1f), FloatMode.NIVEAU)
        val locked = draw(true, reading(tilt = 0.2f, dirY = 1f, level = true), FloatMode.NIVEAU)
        for (i in spec.leds) {
            if (!onRing(i) || onRim(i)) continue
            // Hors bulle, la couronne vaut la même chose dans les deux états.
            if (loose.values[i] > 0.9f || locked.values[i] > 0.9f) continue
            assertEquals("cellule $i", loose.values[i], locked.values[i], 1e-6f)
        }
        assertTrue("aucune couronne dessinée", spec.leds.any { onRing(it) && loose.values[it] > 0f })
    }

    /**
     * Le cerne est **tout ou rien** : éteint tant qu'on cherche, plein d'un coup
     * quand la bulle entre dans la tolérance. C'est le seul état de l'instrument.
     */
    @Test
    fun `le cerne s'allume quand c'est droit, et jamais autrement`() {
        val loose = draw(true, reading(tilt = 3f, dirY = 1f), FloatMode.NIVEAU)
        assertEquals("un cerne allumé alors que ça penche", 0, count(loose, 0f, spec.maxDistance - 0.8f))

        val locked = draw(true, reading(tilt = 0.2f, dirY = 1f, level = true), FloatMode.NIVEAU)
        val rim = spec.leds.count { onRim(it) }
        assertEquals("le cerne n'est pas plein", rim, count(locked, 0.95f, spec.maxDistance - 0.8f))
    }

    /**
     * La visée en retrait passe **sous** la bulle : la composition par maximum s'en
     * charge, et la bulle garde ses vingt et une cellules.
     */
    @Test
    fun `la visee n'entame pas la bulle`() {
        val frame = draw(true, reading(tilt = 0.6f, dirY = 1f), FloatMode.NIVEAU)
        assertEquals(21, bubbleCells(frame))
    }

    /**
     * La bulle a **toujours la même forme** : vingt et une cellules, où qu'elle
     * soit, tant qu'elle tient dans le disque. Le disque calculé qu'elle remplace
     * en changeait d'une position à l'autre, ce qui se lisait comme du bruit.
     */
    @Test
    fun `la bulle garde sa forme partout`() {
        for (angle in 0 until 360 step 30) {
            val a = Math.toRadians(angle.toDouble())
            val frame = draw(
                true,
                reading(tilt = 0.4f, dirX = cos(a).toFloat(), dirY = sin(a).toFloat()),
                FloatMode.NIVEAU,
            )
            assertEquals("à $angle°", 21, bubbleCells(frame))
        }
    }

    // --------------------------------------------------------------- le tunnel

    /** Les deux repères du tunnel, en cellules depuis le centre. */
    private val gate = 3

    private fun vertical(alongX: Boolean, tilt: Float, way: Float): Frame = draw(
        false,
        reading(
            tilt = tilt,
            dirX = if (alongX) way else 0f,
            dirY = if (alongX) 0f else way,
            vertical = true,
            alongX = alongX,
        ),
        FloatMode.NIVEAU,
    )

    /**
     * Debout, la course n'a qu'une dimension : la bulle reste sur la ligne du
     * tunnel, et le tunnel se tourne avec le téléphone.
     */
    @Test
    fun `le tunnel se tourne avec le telephone`() {
        val portrait = vertical(alongX = true, tilt = 2f, way = 1f)
        assertTrue("le bord haut du tunnel manque", portrait[spec.centerX, spec.centerY - 3] > 0f)
        assertTrue("le bord bas du tunnel manque", portrait[spec.centerX, spec.centerY + 3] > 0f)
        assertEquals(
            "un bord vertical dans un tunnel horizontal",
            0f,
            portrait[spec.centerX - 3, spec.centerY - 8],
            1e-6f,
        )
        assertEquals(
            "la bulle a quitté la ligne",
            spec.centerY.toFloat(),
            bubble(portrait).second,
            0.01f,
        )

        val landscape = vertical(alongX = false, tilt = 2f, way = 1f)
        assertTrue("le bord gauche du tunnel manque", landscape[spec.centerX - 3, spec.centerY] > 0f)
        assertEquals(
            "la bulle a quitté la ligne",
            spec.centerX.toFloat(),
            bubble(landscape).first,
            0.01f,
        )
    }

    /**
     * L'invariant du tunnel : les deux repères **encadrent la bulle centrée**, à
     * une cellule de jeu de chaque côté, et pas un pixel de plus. On voit la bulle
     * se caler entre les deux marques au moment exact où le cerne s'allume.
     */
    @Test
    fun `les reperes encadrent la bulle centree`() {
        val centered = vertical(alongX = true, tilt = 0f, way = 1f)
        val right = spec.leds.filter { centered.values[it] > 0.95f }.maxOf { spec.xOf(it) }
        assertEquals("la bulle ne tient pas entre les repères", spec.centerX + gate - 1, right)
        assertTrue("repère gauche absent", centered[spec.centerX - gate, spec.centerY] > 0f)
        assertTrue("repère droit absent", centered[spec.centerX + gate, spec.centerY] > 0f)
    }

    /** Et elle en sort dès qu'elle bouge d'une cellule. */
    @Test
    fun `la bulle franchit le repere des qu'elle quitte le centre`() {
        val off = vertical(alongX = true, tilt = 1f, way = 1f)
        val far = spec.leds.filter { off.values[it] > 0.95f }.maxOf { spec.xOf(it) }
        assertTrue("la bulle n'est pas sortie ($far)", far > spec.centerX + gate)
    }

    // --------------------------------------------------------------- boussole

    /** Le rayon auquel s'ancrent les lettres, tel que le renderer les place. */
    private val letterRadius = 10.4f

    /**
     * Ce qui est allumé dans la boîte de la lettre de [bearing], au cap [heading].
     *
     * On vise **la position calculée** plutôt qu'un secteur angulaire : à ce rayon,
     * un secteur assez large pour contenir une lettre entière en attrape déjà une
     * autre. Les décalages reprennent ceux du renderer, parce qu'un centrage
     * arithmétique ne rend pas la même chose sur une largeur paire et une impaire.
     */
    /**
     * Les quatre dessins, **recopiés du renderer**.
     *
     * Ce n'est pas une duplication gênante : ce sont eux, le contrat. Le jour où un
     * glyphe change ici sans changer là-bas, c'est le test qui doit tomber.
     */
    private class Letter(
        val bearing: Float,
        val glyph: Array<String>,
        val dx: Int,
        val dy: Int,
        val cells: Int,
    )

    private val NORTH = Letter(0f, arrayOf("110", "101", "101", "101"), -1, -2, 8)
    private val EAST =
        Letter(90f, arrayOf("0110", "1001", "1111", "1000", "0110"), -1, -2, 11)
    private val SOUTH = Letter(180f, arrayOf("011", "100", "111", "001", "110"), -1, -2, 9)
    private val WEST = Letter(270f, arrayOf("10001", "10101", "10101", "01010"), -2, -1, 10)
    private val LETTERS = listOf(NORTH, EAST, SOUTH, WEST)

    /**
     * Ce qui est allumé dans la boîte de [letter], à sa place calculée.
     *
     * On refait la descente du renderer : au rayon nominal si la lettre y tient
     * entière, sinon un dixième de cellule plus bas, jusqu'à ce qu'elle tienne.
     */
    private fun letterCells(
        frame: Frame,
        heading: Float,
        letter: Letter,
    ): Int {
        // Aucun miroir : la rose est la seule chose du toy qui ne se retourne pas
        // d'une surface à l'autre. Voir l'en-tête du renderer.
        val angle = Heading.delta(heading, letter.bearing) * (Math.PI / 180).toFloat()
        val ax = sin(angle.toDouble()).toFloat()
        val ay = -cos(angle.toDouble()).toFloat()

        var radius = letterRadius
        while (radius > letterRadius - 3f && !whole(letter, ax, ay, radius)) {
            radius -= 0.1f
        }
        val cx = Math.round(spec.centerX + ax * radius)
        val cy = Math.round(spec.centerY + ay * radius)
        var n = 0
        for (row in letter.glyph.indices) {
            for (col in letter.glyph[row].indices) {
                if (letter.glyph[row][col] != '1') continue
                if (frame[cx + letter.dx + col, cy + letter.dy + row] > 0.5f) n++
            }
        }
        return n
    }

    private fun whole(letter: Letter, ax: Float, ay: Float, radius: Float): Boolean {
        val cx = Math.round(spec.centerX + ax * radius)
        val cy = Math.round(spec.centerY + ay * radius)
        for (row in letter.glyph.indices) {
            for (col in letter.glyph[row].indices) {
                if (letter.glyph[row][col] != '1') continue
                if (!spec.isLed(cx + letter.dx + col, cy + letter.dy + row)) return false
            }
        }
        return true
    }

    private fun northCells(frame: Frame, heading: Float) = letterCells(frame, heading, NORTH)

    /**
     * Cap zéro : le `n` est en haut, des deux côtés — c'est le témoin, la pose où
     * le miroir ne change rien.
     *
     * Les quatre lettres **sont** la rose. Il n'y a plus de motif abstrait à
     * apprendre — croix pleine pour le nord, losange creux pour les autres — et
     * plus de cap chiffré au centre : un `n` au bord du disque dit où est le nord à
     * quelqu'un qui n'a jamais ouvert le toy.
     */
    @Test
    fun `cap au nord, le n est en haut du disque`() {
        for (fromBack in listOf(true, false)) {
            val frame = draw(fromBack, reading(heading = 0f), FloatMode.BOUSSOLE)
            assertEquals(
                "depuis ${if (fromBack) "l'arrière" else "l'avant"}",
                8,
                northCells(frame, 0f),
            )
            // Le dessin exact, au pixel : `110 / 101 / 101 / 101`.
            assertTrue(frame[11, 0] > 0f && frame[12, 0] > 0f)
            assertEquals(0f, frame[13, 0], 1e-6f)
            for (y in 1..3) {
                assertTrue("jambage gauche en $y", frame[11, y] > 0f)
                assertTrue("jambage droit en $y", frame[13, y] > 0f)
                assertEquals("le creux du n est bouché en $y", 0f, frame[12, y], 1e-6f)
            }
        }
    }

    /** Les trois autres à leur place, et chacune avec son dessin. */
    @Test
    fun `les quatre lettres tiennent les quatre directions`() {
        val frame = draw(false, reading(heading = 0f), FloatMode.BOUSSOLE)
        for (letter in LETTERS) {
            assertEquals(
                "lettre de ${letter.bearing}°",
                letter.cells,
                letterCells(frame, 0f, letter),
            )
        }
        // Et rien d'autre : quatre lettres et l'aiguille, qui fait onze cellules —
        // une de pointe, deux par ligne d'évidement, six de fût.
        assertEquals("il y a autre chose sur le disque", 38 + 11, count(frame, 0f))
    }

    /**
     * **La rose est la seule chose du toy qui ne se retourne pas.**
     *
     * Le niveau mesure une direction dans le repère de l'appareil, donc le miroir
     * s'applique. La rose montre une direction du monde, et pour regarder la
     * matrice il faut poser le téléphone écran en bas : ce retournement est un
     * second miroir, et les deux s'annulent. Les deux surfaces montrent donc la
     * **même image**, à la cellule près.
     */
    @Test
    fun `la rose est la meme des deux cotes`() {
        for (heading in listOf(0f, 37f, 90f, 214f)) {
            val r = reading(heading = heading)
            val back = draw(true, r, FloatMode.BOUSSOLE)
            val front = draw(false, r, FloatMode.BOUSSOLE)
            for (i in spec.leds) {
                assertEquals("cap $heading°, cellule $i", back.values[i], front.values[i], 0f)
            }
        }
    }

    /**
     * Cap au nord : `e` à droite, `w` à gauche. Les échanger est la seule faute
     * qu'on ne pardonne pas à une boussole, et c'est celle que le miroir faisait.
     *
     * La sonde ne compte pas les cellules — les deux lettres en ont dix et onze,
     * c'est trop proche — elle cherche la **barre du `e`** : quatre cellules
     * d'affilée sur une ligne, ce que le `w` n'a jamais.
     */
    @Test
    fun `l'est est a droite et l'ouest a gauche`() {
        for (fromBack in listOf(true, false)) {
            val side = if (fromBack) "la matrice" else "le hublot"
            val frame = draw(fromBack, reading(heading = 0f), FloatMode.BOUSSOLE)
            for (x in 21..24) {
                assertTrue("sur $side, la barre du e manque en $x", frame[x, 12] > 0.5f)
            }
            assertTrue("sur $side, le w manque à gauche", frame[0, 12] > 0.5f)
            assertEquals("sur $side, ce n'est pas un w à gauche", 0f, frame[1, 12], 1e-6f)
        }
    }

    /** Cap à l'est : le nord passe à gauche, des deux côtés. */
    @Test
    fun `cap a l'est, le nord passe a gauche`() {
        for (fromBack in listOf(true, false)) {
            val frame = draw(fromBack, reading(heading = 90f), FloatMode.BOUSSOLE)
            assertEquals("depuis ${if (fromBack) "l'arrière" else "l'avant"}", 8, northCells(frame, 90f))
        }
    }

    /**
     * **Une lettre ne change jamais de dessin.** C'est la promesse du motif fixe, et
     * elle se vérifie au compte, à tout cap : ce qui tourne est la position, pas le
     * glyphe. Une lettre inclinée n'existe pas sur une grille de vingt-cinq
     * cellules, et une lettre redessinée à chaque degré respirerait comme du bruit.
     */
    @Test
    fun `les lettres ne se deforment pas en tournant`() {
        for (heading in 0 until 360 step 3) {
            val h = heading.toFloat()
            val frame = draw(true, reading(heading = h), FloatMode.BOUSSOLE)
            for (letter in LETTERS) {
                assertEquals(
                    "lettre de ${letter.bearing}° au cap $heading°",
                    letter.cells,
                    letterCells(frame, h, letter),
                )
            }
        }
    }

    /**
     * Il n'y a **que** quatre directions : les intercardinaux sont partis.
     *
     * Huit repères sur un disque de vingt-cinq LEDs faisaient une couronne où le
     * nord — la seule chose qu'on cherche — se perdait au milieu des sept autres.
     */
    @Test
    fun `il n'y a plus d'intercardinaux`() {
        val frame = draw(true, reading(heading = 0f), FloatMode.BOUSSOLE)
        // Les diagonales, à mi-chemin entre deux lettres : rien.
        for ((x, y) in listOf(19 to 5, 5 to 5, 19 to 19, 5 to 19)) {
            assertEquals("un repère traîne en $x,$y", 0f, frame[x, y], 1e-6f)
        }
    }

    /** Plus d'anneau autour : seules les lettres occupent le cerne. */
    @Test
    fun `la rose n'a plus de cercle continu`() {
        val frame = draw(true, reading(heading = 20f), FloatMode.BOUSSOLE)
        val rim = spec.leds.count { spec.distance[it] >= spec.maxDistance - 1f }
        val lit = count(frame, 0f, spec.maxDistance - 1f)
        assertTrue("le cerne est rempli à $lit/$rim", lit < rim / 2)
    }

    /**
     * L'aiguille a une pointe **évidée** : deux traits qui s'écartent, et rien
     * entre eux.
     *
     * Pleine, elle faisait une tache de neuf cellules au milieu du disque — l'œil
     * s'y accrochait au lieu de chercher le `n`, et elle empâtait la zone que les
     * lettres traversent en tournant. Ouverte, elle dit la même direction en trois
     * cellules.
     */
    @Test
    fun `l'aiguille a la pointe evidee`() {
        val frame = draw(true, reading(heading = 120f), FloatMode.BOUSSOLE)
        val c = spec.centerX
        assertTrue("pas de pointe", frame[c, 8] > 0.5f)
        // Les deux traits s'écartent...
        assertTrue(frame[c - 1, 9] > 0.5f)
        assertTrue(frame[c + 1, 9] > 0.5f)
        assertTrue(frame[c - 2, 10] > 0.5f)
        assertTrue(frame[c + 2, 10] > 0.5f)
        // ...et rien ne les referme.
        assertEquals("la pointe est pleine", 0f, frame[c, 9], 1e-6f)
        for (x in c - 1..c + 1) {
            assertEquals("la pointe est pleine en $x", 0f, frame[x, 10], 1e-6f)
        }
        // Le fût, jusqu'en bas, et pas plus large qu'une cellule.
        for (y in 11..16) {
            assertTrue("fût interrompu en $y", frame[c, y] > 0.5f)
            assertEquals("le fût s'élargit en $y", 0f, frame[c - 1, y], 1e-6f)
        }
        assertEquals("le fût dépasse", 0f, frame[c, 17], 1e-6f)
    }

    /**
     * Sans magnétomètre, la boussole le dit en n'affichant **que** l'aiguille :
     * elle ne prétend rien, elle dit juste où pointe le téléphone.
     */
    @Test
    fun `sans cap, il n'y a pas de rose`() {
        val frame = draw(true, reading(hasHeading = false), FloatMode.BOUSSOLE)
        assertEquals("une lettre s'est allumée sans cap", 0, count(frame, 0f, roseInner))
        assertEquals("il reste autre chose que l'aiguille", 11, count(frame, 0f))
    }

    /** Aucun bord dégradé : la rose est en tout ou rien. */
    @Test
    fun `aucune lettre n'est en demi-teinte`() {
        val frame = draw(true, reading(heading = 0f), FloatMode.BOUSSOLE)
        val dim = spec.leds.count {
            spec.distance[it] >= roseInner && frame.values[it] > 0f && frame.values[it] < 0.95f
        }
        assertEquals("une lettre en demi-teinte", 0, dim)
    }

    // ------------------------------------------------------------------ repos

    /** Ce qui est allumé dans une bande de lignes, sans filtre de rayon. */
    private fun band(frame: Frame, rows: IntRange): Int =
        spec.leds.count { frame.values[it] > 0f && spec.yOf(it) in rows }

    /**
     * Le repos d'un hublot : la **marque du toy** au milieu, `TAP` en bas.
     *
     * Le mot seul ne suffisait plus — trois hublots du pack l'affichaient au pixel
     * près, et on ne savait pas lequel on allait réveiller. Rien de ce qui est
     * dessiné n'est une mesure : aucun capteur n'écoute entre deux rafales, donc
     * l'instrument n'apparaît pas du tout.
     */
    @Test
    fun `le repos porte la marque du toy et TAP`() {
        for (mode in FloatMode.entries) {
            val frame = idle(mode)
            // `TAP` en 3×4 lignes 5 à 8, la marque lignes 13 à 21 : le bloc est
            // centré, puis le mot monte d'une ligne et la marque descend de deux.
            assertEquals("pas de TAP en $mode", 21, band(frame, 5..8))
            assertEquals("quelque chose traîne au-dessus en $mode", 0, band(frame, 0..4))
            assertEquals("le mot colle à la marque en $mode", 0, band(frame, 9..12))
            assertEquals("quelque chose traîne en dessous en $mode", 0, band(frame, 22..24))
        }
        assertEquals("la fiole n'est pas entière", 45, band(idle(FloatMode.NIVEAU), 13..21))
        assertEquals("la rose n'est pas entière", 29, band(idle(FloatMode.BOUSSOLE), 13..21))
    }

    /**
     * La marque **suit le mode** : la fiole pour le niveau, la rose pour la
     * boussole.
     *
     * Les deux ont partagé la même image, et ça ne répondait pas à la question
     * qu'on se pose devant un hublot au repos : le tap change d'instrument, donc on
     * ne savait pas lequel on allait réveiller avant de l'avoir réveillé.
     *
     * La sonde vise la première ligne de la marque : la fiole y a le haut de son
     * cercle, cinq cellules d'affilée, là où la rose n'a que la pointe de sa flèche.
     */
    @Test
    fun `la marque dit quel instrument on reveille`() {
        val level = idle(FloatMode.NIVEAU)
        assertTrue("pas de fiole", level[10, 13] > 0f && level[14, 13] > 0f)
        assertTrue("pas de bulle dans la fiole", level[12, 17] > 0f)

        val compass = idle(FloatMode.BOUSSOLE)
        assertTrue("pas de rose", compass[12, 13] > 0f)
        assertEquals("la rose a le cercle de la fiole", 0f, compass[10, 13], 1e-6f)
        assertEquals("la rose a le cercle de la fiole", 0f, compass[14, 13], 1e-6f)
    }

    /**
     * Le mot est à la même place dans les deux modes : ce qui change est le dessin,
     * pas la mise en page. Un hublot qui déplacerait son `TAP` selon l'instrument
     * se lirait comme deux hublots différents.
     */
    @Test
    fun `seul le dessin change d'un instrument a l'autre`() {
        val level = idle(FloatMode.NIVEAU)
        val compass = idle(FloatMode.BOUSSOLE)
        for (i in spec.leds) {
            if (spec.yOf(i) in 13..21) continue
            assertEquals("cellule $i", level.values[i], compass.values[i], 0f)
        }
    }

    /** Rien ne sort du masque, quelle que soit la pose. */
    @Test
    fun `rien ne deborde du disque`() {
        val poses = listOf(
            reading(tilt = 45f, dirX = 0.7f, dirY = 0.7f),
            reading(tilt = 8f, dirX = 1f, vertical = true, alongX = true),
            reading(tilt = 8f, dirY = -1f, vertical = true, alongX = false),
        )
        for (mode in FloatMode.entries) {
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
