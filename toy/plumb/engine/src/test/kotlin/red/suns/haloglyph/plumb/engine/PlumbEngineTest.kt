package red.suns.haloglyph.plumb.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le filtre, les deux régimes et le verrouillage — tout ce qui se passe entre le
 * capteur et le dessin.
 *
 * Les mesures sont versées comme le ferait `SENSOR_DELAY_GAME` : un échantillon
 * toutes les vingt millisecondes.
 */
class PlumbEngineTest {

    private val dt = 0.02
    private val g = 9.81f

    /** Verse [seconds] de gravité constante, téléphone posé face contre table. */
    private fun soak(engine: PlumbEngine, x: Float, y: Float, z: Float, seconds: Double = 1.0) {
        repeat((seconds / dt).toInt()) { engine.onAcceleration(x, y, z) }
    }

    /** La gravité que mesure un téléphone face contre table, penché de [degrees]
     *  vers son bord supérieur. */
    private fun tilted(degrees: Float): Triple<Float, Float, Float> {
        val a = Math.toRadians(degrees.toDouble())
        return Triple(0f, (g * sin(a)).toFloat(), (-g * cos(a)).toFloat())
    }

    /** Fait **tourner** le téléphone de [from] à [to], au lieu de l'y téléporter. */
    private fun sweep(engine: PlumbEngine, from: Float, to: Float, seconds: Double = 0.6) {
        val steps = (seconds / dt).toInt()
        repeat(steps) { k ->
            val (x, y, z) = tilted(from + (to - from) * (k + 1) / steps)
            engine.onAcceleration(x, y, z)
        }
    }

    @Test
    fun `pose a plat, la bulle est au centre et l'instrument se verrouille`() {
        val engine = PlumbEngine()
        soak(engine, 0f, 0f, -g)
        val r = engine.snapshot()
        assertEquals(0f, r.tiltDegrees, 1e-3f)
        assertTrue("devrait être de niveau", r.level)
        assertFalse(r.moving)
    }

    /**
     * L'horizontale est celle du monde, et rien ne s'y retranche : la mesure vaut
     * exactement l'angle de l'appareil. C'est tout le sujet d'un niveau à bulle,
     * et ça se vérifie en une ligne.
     */
    @Test
    fun `l'inclinaison mesuree est la bonne`() {
        for (degrees in listOf(0.5f, 2f, 7f, 30f)) {
            val engine = PlumbEngine()
            val (x, y, z) = tilted(degrees)
            soak(engine, x, y, z, 2.0)
            assertEquals("$degrees°", degrees, engine.snapshot().tiltDegrees, 0.05f)
        }
    }

    /**
     * Une bulle va vers le **haut**, comme dans une fiole en verre. Bord supérieur
     * levé, l'accéléromètre voit une composante Y positive, et la bulle part de ce
     * côté.
     */
    @Test
    fun `la bulle monte du cote leve`() {
        val engine = PlumbEngine()
        val (x, y, z) = tilted(6f)
        soak(engine, x, y, z, 2.0)
        val r = engine.snapshot()
        assertEquals(1f, r.dirY, 1e-2f)
        assertEquals(0f, r.dirX, 1e-2f)
    }

    /**
     * Le filtre est adaptatif, et ça doit se mesurer : près de l'horizontale il
     * est lent, franchement penché il est rapide. On compare la fraction du saut
     * franchie en un dixième de seconde dans les deux régimes.
     */
    @Test
    fun `le filtre est calme au centre et vif quand ca penche`() {
        val calm = PlumbEngine()
        soak(calm, 0f, 0f, -g, 0.5)
        val (cx, cy, cz) = tilted(0.4f)
        soak(calm, cx, cy, cz, 0.1)
        val calmProgress = calm.snapshot().tiltDegrees / 0.4f

        val quick = PlumbEngine()
        val (bx, by, bz) = tilted(20f)
        soak(quick, bx, by, bz, 2.0)
        val (qx, qy, qz) = tilted(26f)
        soak(quick, qx, qy, qz, 0.1)
        val quickProgress = (quick.snapshot().tiltDegrees - 20f) / 6f

        assertTrue("régime calme à $calmProgress", calmProgress < 0.4f)
        assertTrue("régime vif à $quickProgress", quickProgress > 0.7f)
    }

    /** Le téléphone qu'on promène n'est pas de niveau, même en passant par zéro. */
    @Test
    fun `un telephone en mouvement ne se verrouille pas`() {
        val engine = PlumbEngine()
        soak(engine, 0f, 0f, -g, 1.0)
        repeat(20) { k -> engine.onAcceleration(if (k % 2 == 0) 2.5f else -2.5f, 0f, -g) }
        assertTrue(engine.snapshot().moving)
        assertFalse(engine.snapshot().level)
    }

    @Test
    fun `le verrouillage n'est annonce qu'a l'entree`() {
        val engine = PlumbEngine()
        soak(engine, 0f, 0f, -g, 0.2)
        assertTrue("l'entrée dans la couronne n'est pas annoncée", engine.consumeLevel())
        soak(engine, 0f, 0f, -g, 0.5)
        assertFalse("le verrou se réannonce", engine.consumeLevel())
    }

    /** L'hystérésis : sortir un cheveu de la tolérance ne casse pas le verrou. */
    @Test
    fun `l'hysteresis tient le verrou`() {
        val engine = PlumbEngine()
        engine.range = PlumbRange.NORMALE
        soak(engine, 0f, 0f, -g, 0.5)
        assertTrue(engine.snapshot().level)

        val tolerance = PlumbScale.tolerance(PlumbRange.NORMALE)
        val (x, y, z) = tilted(tolerance * 1.2f)
        soak(engine, x, y, z, 2.0)
        assertTrue("le verrou a sauté juste au-dessus du seuil", engine.snapshot().level)

        val (fx, fy, fz) = tilted(tolerance * 2f)
        soak(engine, fx, fy, fz, 2.0)
        assertFalse("le verrou tient trop loin", engine.snapshot().level)
    }

    // ---------------------------------------------------- gravité fusionnée

    /** Pousse une image : la gravité fusionnée, puis l'accélération brute. */
    private fun fused(engine: PlumbEngine, ax: Float, ay: Float, az: Float) {
        engine.onGravity(0f, 0f, -g)
        engine.onAcceleration(ax, ay, az)
    }

    /**
     * Le défaut signalé : glisser le téléphone **à plat** sur la table envoyait la
     * bulle dans tous les sens. L'accéléromètre ne distingue pas une inclinaison
     * d'une translation ; le gyroscope, si. Avec la gravité fusionnée, la bulle ne
     * doit pas bouger d'un centième de degré.
     */
    @Test
    fun `la gravite fusionnee ignore une translation`() {
        val engine = PlumbEngine()
        repeat(30) { fused(engine, 0f, 0f, -g) }
        assertEquals(0f, engine.snapshot().tiltDegrees, 1e-3f)

        repeat(60) { k -> fused(engine, if (k % 2 == 0) 6f else -6f, 0f, -g) }

        assertEquals(
            "la bulle a bougé sous une translation",
            0f,
            engine.snapshot().tiltDegrees,
            1e-3f,
        )
        assertTrue("le mouvement devrait rester détecté", engine.snapshot().moving)
    }

    /**
     * Et le repli, lui, y est sensible — c'est de la physique, pas un défaut de
     * code. Le test est là pour que personne ne croie l'avoir réparé par un
     * filtrage : l'information n'est pas dans le signal.
     */
    @Test
    fun `sans gravite fusionnee, une translation deplace la bulle`() {
        val engine = PlumbEngine()
        soak(engine, 0f, 0f, -g, 0.6)
        repeat(60) { k -> engine.onAcceleration(if (k % 2 == 0) 6f else -6f, 0f, -g) }
        assertTrue(
            "le repli ne devrait pas être immunisé",
            engine.snapshot().tiltDegrees > 0.5f,
        )
    }

    // ------------------------------------------------------- régime vertical

    /**
     * Téléphone debout, tourné de [inPlane] degrés dans son propre plan.
     *
     * Zéro = portrait droit, 90 = couché d'un quart de tour. C'est la seule pose
     * qui intéresse le régime vertical : le plan de l'appareil y est vertical, donc
     * la verticale du monde tient entière dedans.
     */
    private fun upright(inPlane: Float): Triple<Float, Float, Float> {
        val a = Math.toRadians(inPlane.toDouble())
        return Triple((g * sin(a)).toFloat(), (g * cos(a)).toFloat(), 0f)
    }

    private fun stand(engine: PlumbEngine, inPlane: Float, seconds: Double = 2.0) {
        val (x, y, z) = upright(inPlane)
        soak(engine, x, y, z, seconds)
    }

    /**
     * Au-delà de 70°, ce n'est plus la planéité qu'on mesure mais **l'aplomb**, et
     * sur un seul axe : de combien le téléphone penche dans son propre plan. Un
     * portrait droit de travers de 4° affiche 4°.
     */
    @Test
    fun `debout, on mesure l'aplomb dans le plan de l'appareil`() {
        val engine = PlumbEngine()
        stand(engine, 4f)
        val r = engine.snapshot()
        assertTrue("devrait être passé en vertical", r.vertical)
        assertTrue("la bulle devrait courir sur X", r.alongX)
        assertEquals(4f, r.tiltDegrees, 0.1f)
        assertEquals("un seul axe", 0f, r.dirY, 0f)
        assertEquals(1f, r.dirX, 0f)
    }

    /**
     * Couché d'un quart de tour, les rôles s'échangent : c'est `uy` qui porte
     * l'aplomb, et la bulle glisse de haut en bas.
     */
    @Test
    fun `couche, la bulle change d'axe`() {
        val engine = PlumbEngine()
        stand(engine, 94f)
        val r = engine.snapshot()
        assertTrue(r.vertical)
        assertFalse("la bulle devrait courir sur Y", r.alongX)
        assertEquals(4f, r.tiltDegrees, 0.1f)
        assertEquals("un seul axe", 0f, r.dirX, 0f)
    }

    /**
     * Le décollement hors du plan ne compte pas : c'est la main qui ne plaque pas,
     * pas la surface qui penche. Ici le téléphone est droit dans son plan mais
     * bascule de 15° vers l'arrière — l'instrument doit annoncer zéro.
     */
    @Test
    fun `l'aplomb ignore le decollement hors du plan`() {
        val engine = PlumbEngine()
        val a = Math.toRadians(15.0)
        soak(engine, 0f, (g * cos(a)).toFloat(), (g * sin(a)).toFloat(), 2.0)
        val r = engine.snapshot()
        assertTrue("devrait être passé en vertical", r.vertical)
        assertEquals(0f, r.tiltDegrees, 0.05f)
    }

    @Test
    fun `en deca, on reste sur l'horizontale`() {
        val engine = PlumbEngine()
        val (x, y, z) = tilted(60f)
        soak(engine, x, y, z, 2.0)
        val r = engine.snapshot()
        assertFalse(r.vertical)
        assertEquals(60f, r.tiltDegrees, 0.1f)
    }

    /**
     * L'hystérésis de régime, et c'est elle qui le rend tenable à la main : sans
     * elle, un téléphone tenu autour du seuil changerait de référence plusieurs
     * fois par seconde.
     */
    @Test
    fun `la bascule a une hysteresis`() {
        val engine = PlumbEngine()
        val (ux, uy, uz) = tilted(80f)
        soak(engine, ux, uy, uz, 1.0)
        assertTrue(engine.snapshot().vertical)

        // On redescend à 67 : au-dessus du seuil de retour, donc toujours vertical.
        sweep(engine, 80f, 67f)
        assertTrue("le régime a lâché trop tôt", engine.snapshot().vertical)

        sweep(engine, 67f, 60f)
        assertFalse("le régime n'est pas revenu", engine.snapshot().vertical)
    }

    /**
     * Le choix du quart de tour a **sa propre** hystérésis, et elle est large :
     * soixante degrés, là où la géométrie en donnerait quarante-cinq. Un téléphone
     * tenu de travers garde donc l'axe sur lequel on a commencé à lire.
     */
    @Test
    fun `le quart de tour ne change qu'a soixante degres`() {
        val engine = PlumbEngine()
        stand(engine, 0f, 1.0)
        assertTrue(engine.snapshot().alongX)

        stand(engine, 55f, 1.0)
        assertTrue("l'axe a basculé trop tôt", engine.snapshot().alongX)

        stand(engine, 70f, 1.0)
        assertFalse("l'axe n'a pas basculé", engine.snapshot().alongX)
    }

    /** Debout aussi, la référence est celle du monde : aucun zéro à retrancher. */
    @Test
    fun `l'aplomb non plus n'a pas de reference`() {
        val engine = PlumbEngine()
        stand(engine, -8f)
        assertEquals(8f, engine.snapshot().tiltDegrees, 0.1f)
        assertEquals("la bulle devrait partir de l'autre côté", -1f, engine.snapshot().dirX, 0f)
    }

    // ----------------------------------------------------------------- le cap

    /**
     * Aucun glissement : le cap annoncé est celui qu'on vient de mesurer, dès la
     * première mesure. Et pas de tour complet au passage du nord — l'écart est
     * toujours lu en signé.
     */
    @Test
    fun `le cap est pris tel quel, sans glissement`() {
        val engine = PlumbEngine()
        engine.onHeading(355f, true)
        engine.onHeading(5f, true)
        assertEquals(5f, engine.snapshot().heading, 1e-3f)
    }

    /**
     * La zone morte : en deçà d'un degré, rien ne bouge. C'est la marche de
     * l'affichage, donc il n'y a rien sous elle à montrer — et c'est ce qui empêche
     * la rose de frémir sur un téléphone posé.
     */
    @Test
    fun `sous un degre, le cap ne bouge pas`() {
        val engine = PlumbEngine()
        engine.onHeading(120f, true)
        repeat(20) { k -> engine.onHeading(if (k % 2 == 0) 120.4f else 119.6f, true) }
        assertEquals(120f, engine.snapshot().heading, 1e-3f)

        engine.onHeading(121.5f, true)
        assertEquals(121.5f, engine.snapshot().heading, 1e-3f)
    }

    @Test
    fun `sans magnetometre, il n'y a pas de cap`() {
        val engine = PlumbEngine()
        engine.onHeading(120f, true)
        assertTrue(engine.snapshot().hasHeading)
        engine.onHeading(null, true)
        assertFalse(engine.snapshot().hasHeading)
    }

    @Test
    fun `un magnetometre non calibre se signale`() {
        val engine = PlumbEngine()
        engine.onHeading(120f, false)
        assertFalse(engine.snapshot().headingTrusted)
    }

    /** Un `cos` qui traîne : la pose penchée doit bien faire une norme de g. */
    @Test
    fun `le jeu de mesures du test est coherent`() {
        val (x, y, z) = tilted(30f)
        assertEquals(g, kotlin.math.sqrt(x * x + y * y + z * z), 1e-3f)
        assertEquals((g * cos(Math.toRadians(30.0))).toFloat(), -z, 1e-3f)
    }
}
