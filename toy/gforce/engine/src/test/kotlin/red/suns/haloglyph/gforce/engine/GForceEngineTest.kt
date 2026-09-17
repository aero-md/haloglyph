package red.suns.haloglyph.gforce.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Un accéléromètre de bord, testé **sans voiture**.
 *
 * C'est tout l'intérêt d'un moteur en Kotlin pur : on verse des vecteurs, on lit
 * des g. Ce qui est vérifié ici n'est pas que la trigonométrie est celle qu'on a
 * écrite, mais que le toy tient sa seule promesse difficile — **un support penché
 * ne doit pas fausser la mesure**. Six degrés d'inclinaison valent 0,10 g de
 * gravité qui fuit dans les axes horizontaux, soit cinq fois l'erreur du capteur.
 */
class GForceEngineTest {

    private val g = 9.80665f

    /**
     * Verse [count] images : la gravité, puis l'accélération propre.
     *
     * Assez d'images pour que le passe-bas ait convergé — à 0,35 de coefficient, le
     * résidu tombe sous le dix-millième en vingt échantillons.
     */
    private fun feed(
        engine: GForceEngine,
        gravity: Triple<Float, Float, Float>,
        linear: Triple<Float, Float, Float>,
        count: Int = 40,
    ) {
        repeat(count) {
            engine.onGravity(gravity.first, gravity.second, gravity.third)
            engine.onLinear(linear.first, linear.second, linear.third)
        }
    }

    /**
     * Un téléphone **debout dans un support**, penché de [lean] degrés vers
     * l'arrière — vers le conducteur.
     *
     * Repère de l'appareil : X à droite de l'écran, Y vers le haut, Z sortant de
     * l'écran, donc vers l'arrière du véhicule. La gravité mesurée pointe vers le
     * **haut**, convention d'Android.
     */
    private fun cradle(lean: Float): Triple<Float, Float, Float> {
        val a = Math.toRadians(lean.toDouble())
        return Triple(0f, (g * cos(a)).toFloat(), (g * sin(a)).toFloat())
    }

    /** L'accélération propre d'un freinage de [force] g : le véhicule part vers l'arrière. */
    private fun braking(force: Float, lean: Float = 0f): Triple<Float, Float, Float> {
        // Horizontale dans le monde, donc inclinée dans le repère de l'appareil :
        // c'est exactement ce que le support penché fait au signal.
        val a = Math.toRadians(lean.toDouble())
        val m = force * g
        return Triple(0f, (-m * sin(a)).toFloat(), (m * cos(a)).toFloat())
    }

    // ------------------------------------------------------------ la mesure

    @Test
    fun `sans mesure, le cadran n'a rien a dire`() {
        val engine = GForceEngine()
        assertFalse(engine.snapshot().hasFix)
        engine.onGravity(0f, g, 0f)
        assertFalse("la gravité seule ne suffit pas", engine.snapshot().hasFix)
        engine.onLinear(0f, 0f, 0f)
        assertTrue(engine.snapshot().hasFix)
    }

    /** On freine : le corps part **vers l'avant**, et c'est ce que le toy annonce. */
    @Test
    fun `un freinage jette le corps vers l'avant`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), braking(0.8f))
        val r = engine.snapshot()
        assertEquals(0.8f, r.towardFront, 0.01f)
        assertEquals(0f, r.towardRight, 0.01f)
    }

    /** Et une accélération fait l'inverse, au signe près. */
    @Test
    fun `une acceleration jette le corps vers l'arriere`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), braking(-0.4f))
        assertEquals(-0.4f, engine.snapshot().towardFront, 0.01f)
    }

    /**
     * Virage à gauche : l'accélération centripète pointe à gauche, donc le corps
     * part à droite. C'est le sens que tout conducteur connaît, et celui de la
     * bille.
     */
    @Test
    fun `un virage a gauche jette le corps a droite`() {
        val engine = GForceEngine()
        // Gauche du véhicule = −X de l'appareil, puisque la droite est +X.
        feed(engine, cradle(0f), Triple(-0.9f * g, 0f, 0f))
        val r = engine.snapshot()
        assertEquals(0.9f, r.towardRight, 0.01f)
        assertEquals(0f, r.towardFront, 0.01f)
    }

    /**
     * **Le test qui compte.** Un support n'est jamais d'aplomb, et la mesure ne doit
     * pas s'en apercevoir : à 20° de pente comme à zéro, un freinage de 0,8 g reste
     * un freinage de 0,8 g.
     *
     * Sans la projection sur le plan horizontal, la gravité fuirait dans l'axe
     * longitudinal et ajouterait `sin(20°)` — soit 0,34 g d'erreur, quarante fois le
     * défaut du capteur.
     */
    @Test
    fun `un support penche ne fausse pas la mesure`() {
        for (lean in listOf(-20f, -6f, 0f, 6f, 20f)) {
            val engine = GForceEngine()
            feed(engine, cradle(lean), braking(0.8f, lean))
            assertEquals(
                "support à $lean°",
                0.8f,
                engine.snapshot().towardFront,
                0.01f,
            )
        }
    }

    /** Au repos, penché ou non, le cadran affiche zéro. */
    @Test
    fun `a l'arret le cadran est au centre`() {
        val engine = GForceEngine()
        feed(engine, cradle(15f), Triple(0f, 0f, 0f))
        val r = engine.snapshot()
        assertEquals(0f, r.towardFront, 1e-3f)
        assertEquals(0f, r.towardRight, 1e-3f)
    }

    // ------------------------------------------------------- l'avant du véhicule

    /**
     * Téléphone **posé à plat** sur la planche de bord, haut vers le pare-brise.
     * L'avant ne sort plus par le dos mais par le haut de l'appareil, et le toy doit
     * le voir tout seul.
     */
    @Test
    fun `a plat, l'avant passe sur l'axe du haut`() {
        val engine = GForceEngine()
        // À plat écran en l'air : la verticale est +Z. Freinage = le véhicule part
        // vers l'arrière, donc vers −Y.
        feed(engine, Triple(0f, 0f, g), Triple(0f, -0.8f * g, 0f))
        assertEquals(0.8f, engine.snapshot().towardFront, 0.01f)
    }

    /**
     * L'hystérésis du choix d'axe : un téléphone couché de 30° dans son support
     * garde l'avant sur son dos, et ne bascule qu'une fois vraiment à plat.
     *
     * Sans elle, un support mal réglé verrait le cadran faire un quart de tour à
     * chaque cahot.
     */
    @Test
    fun `l'axe d'avant ne bascule pas au premier cahot`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), braking(0.8f))
        assertEquals(0.8f, engine.snapshot().towardFront, 0.01f)

        // Couché de 30° : l'axe du dos est encore franchement horizontal.
        feed(engine, cradle(30f), braking(0.8f, 30f))
        assertEquals("l'axe a basculé trop tôt", 0.8f, engine.snapshot().towardFront, 0.02f)
    }

    // ------------------------------------------------------------- les pics

    @Test
    fun `les pics retiennent le maximum de chaque direction`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), braking(0.9f))
        feed(engine, cradle(0f), braking(-0.5f))
        feed(engine, cradle(0f), Triple(-0.7f * g, 0f, 0f))
        feed(engine, cradle(0f), Triple(0.3f * g, 0f, 0f))
        feed(engine, cradle(0f), Triple(0f, 0f, 0f))

        val r = engine.snapshot()
        assertEquals(0.9f, r.front, 0.02f)
        assertEquals(0.5f, r.rear, 0.02f)
        assertEquals(0.7f, r.right, 0.02f)
        assertEquals(0.3f, r.left, 0.02f)
        assertEquals("la mesure vive devrait être retombée", 0f, r.towardFront, 0.01f)
    }

    @Test
    fun `effacer les pics remet le trajet a zero`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), braking(0.9f))
        assertTrue(engine.snapshot().front > 0.5f)
        engine.clearPeaks()
        assertEquals(0f, engine.snapshot().front, 0f)
    }

    /**
     * Le passe-bas, mesuré plutôt qu'annoncé.
     *
     * Une secousse isolée de 2 g — un nid-de-poule — ne passe qu'au coefficient du
     * filtre, soit 0,7 g enregistré au lieu de 2. Le filtre **divise**, il n'efface
     * pas : une secousse qui dure trois échantillons passe aux trois quarts. C'est
     * le compromis assumé — couper plus bas abîmerait la montée d'un vrai freinage,
     * qui s'établit en trois cents millisecondes.
     */
    @Test
    fun `une secousse isolee ne passe qu'au tiers`() {
        val engine = GForceEngine()
        feed(engine, cradle(0f), Triple(0f, 0f, 0f))

        engine.onGravity(0f, g, 0f)
        engine.onLinear(0f, 0f, 2f * g)

        val front = engine.snapshot().front
        assertTrue("le pic a gobé la secousse entière : $front", front < 0.8f)
        assertTrue("le filtre a tout mangé : $front", front > 0.6f)
    }

    // ------------------------------------------------------------- le repli

    /**
     * Sans capteur d'accélération linéaire, on retranche la gravité à la main. Le
     * résultat doit être le même — c'est la même soustraction, faite un étage plus
     * bas.
     */
    @Test
    fun `le repli sur l'accelerometre brut donne la meme chose`() {
        val engine = GForceEngine()
        val (gx, gy, gz) = cradle(0f)
        val (ax, ay, az) = braking(0.8f)
        repeat(40) {
            engine.onGravity(gx, gy, gz)
            engine.onAcceleration(ax + gx, ay + gy, az + gz)
        }
        assertEquals(0.8f, engine.snapshot().towardFront, 0.01f)
    }

    /** Un `sin` qui traîne : la pose penchée doit bien faire une norme de g. */
    @Test
    fun `le jeu de mesures du test est coherent`() {
        val (x, y, z) = cradle(20f)
        assertEquals(g, kotlin.math.sqrt(x * x + y * y + z * z), 1e-3f)
        val (bx, by, bz) = braking(0.8f, 20f)
        assertEquals(0.8f * g, kotlin.math.sqrt(bx * bx + by * by + bz * bz), 1e-3f)
    }
}
