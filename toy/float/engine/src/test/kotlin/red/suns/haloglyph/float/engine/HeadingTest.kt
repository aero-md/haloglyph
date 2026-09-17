package red.suns.haloglyph.float.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * L'azimut, recopié d'Android et donc à vérifier contre des poses connues.
 *
 * C'est le prix du Kotlin pur : le SDK fait ce calcul, on ne l'appelle pas, et il
 * faut donc pouvoir montrer qu'on tombe sur la même réponse. Les quatre premiers
 * cas sont des poses qu'on peut refaire à la main avec un téléphone et une
 * boussole.
 */
class HeadingTest {

    /** Champ terrestre de l'hémisphère nord : vers le nord, et plongeant. */
    private val fieldNorth = 20f
    private val fieldDown = 40f

    /** Quaternion d'une rotation d'angle [degrees] autour de l'axe Z. */
    private fun aboutZ(degrees: Float): FloatArray {
        val half = Math.toRadians(degrees / 2.0)
        return floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
    }

    @Test
    fun `pose de reference, bord superieur au nord`() {
        val q = aboutZ(0f)
        assertEquals(0f, Heading.fromRotationVector(q[0], q[1], q[2], q[3]), 1e-3f)
    }

    /**
     * Tourner l'appareil de +90° autour de son axe Z, c'est le faire pivoter dans
     * le sens trigonométrique vu de dessus : le bord supérieur quitte le nord pour
     * l'ouest, donc 270.
     */
    @Test
    fun `une rotation directe emmene le cap vers l'ouest`() {
        val q = aboutZ(90f)
        assertEquals(270f, Heading.fromRotationVector(q[0], q[1], q[2], q[3]), 1e-3f)
    }

    @Test
    fun `le quaternion se complete quand w manque`() {
        val q = aboutZ(90f)
        // Ce que rendaient les appareils antérieurs à Android 4.3 : trois valeurs.
        assertEquals(270f, Heading.fromRotationVector(q[0], q[1], q[2], null), 1e-3f)
    }

    /**
     * Le piège qui a coûté ce test : au-delà d'un demi-tour, `cos(θ/2)` est
     * négatif. Un repli qui prendrait le signe pour une absence renverrait le cap
     * symétrique — 146 au lieu de 214 — et seulement dans la moitié des poses.
     */
    @Test
    fun `un w negatif est une vraie valeur, pas une absence`() {
        val q = aboutZ(214f)
        assertEquals(146f, Heading.fromRotationVector(q[0], q[1], q[2], q[3]), 1e-2f)
    }

    @Test
    fun `a plat face en l'air, bord superieur au nord`() {
        val a = Heading.fromVectors(
            gx = 0f, gy = 0f, gz = 9.81f,
            mx = 0f, my = fieldNorth, mz = -fieldDown,
        )
        assertEquals(0f, a!!, 1e-3f)
    }

    /** Bord supérieur à l'est : le nord passe sur le flanc gauche de l'appareil. */
    @Test
    fun `a plat face en l'air, bord superieur a l'est`() {
        val a = Heading.fromVectors(
            gx = 0f, gy = 0f, gz = 9.81f,
            mx = -fieldNorth, my = 0f, mz = -fieldDown,
        )
        assertEquals(90f, a!!, 1e-3f)
    }

    /**
     * Les deux chemins doivent tomber sur la même réponse : c'est ce qui autorise
     * le repli quand un appareil ne déclare pas de vecteur de rotation.
     */
    @Test
    fun `les deux chemins concordent`() {
        for (degrees in listOf(0f, 37f, 90f, 214f, 359f)) {
            // L'appareil tourné de `degrees` dans le sens trigonométrique : son
            // cap vaut donc 360 − degrees.
            val rad = Math.toRadians(degrees.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            // Le nord, exprimé dans le repère tourné de l'appareil : c'est la
            // rotation inverse appliquée à l'axe nord du monde.
            val mx = s * fieldNorth
            val my = c * fieldNorth
            val fromVectors = Heading.fromVectors(0f, 0f, 9.81f, mx, my, -fieldDown)!!
            val q = aboutZ(degrees)
            val fromQuaternion = Heading.fromRotationVector(q[0], q[1], q[2], q[3])
            assertEquals("à $degrees°", fromQuaternion, fromVectors, 1e-2f)
        }
    }

    /** Deux vecteurs colinéaires ne donnent pas de cap, et on le dit. */
    @Test
    fun `pas de cap quand le champ est colineaire a la gravite`() {
        assertNull(Heading.fromVectors(0f, 0f, 9.81f, 0f, 0f, -40f))
    }

    @Test
    fun `normalize ramene dans le tour`() {
        assertEquals(10f, Heading.normalize(370f), 1e-4f)
        assertEquals(350f, Heading.normalize(-10f), 1e-4f)
        assertEquals(0f, Heading.normalize(720f), 1e-4f)
    }

    /** Le passage du nord ne doit pas faire faire un tour complet à un lissage. */
    @Test
    fun `delta prend le chemin le plus court`() {
        assertEquals(20f, Heading.delta(350f, 10f), 1e-4f)
        assertEquals(-20f, Heading.delta(10f, 350f), 1e-4f)
        assertEquals(180f, Heading.delta(0f, 180f), 1e-4f)
        assertEquals(0f, Heading.delta(90f, 90f), 1e-4f)
    }
}
