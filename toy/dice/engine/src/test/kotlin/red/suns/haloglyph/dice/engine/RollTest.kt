package red.suns.haloglyph.dice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Le jet est une **fonction du temps**, et c'est ce qui se teste ici : le
 * résultat est connu au départ, l'animation doit y conduire, et elle doit le
 * faire sans que la trajectoire se voie reprise en main.
 */
class RollTest {

    private fun rolls(count: Int = 200): Sequence<Roll> = sequence {
        val rnd = Random(20260907)
        repeat(count) {
            val die = Dice.ALL[rnd.nextInt(Dice.ALL.size)]
            val value = drawValue(die, rnd)
            yield(Roll.make(die, die.restQuat(1, 0), value, rnd))
        }
    }

    /**
     * La promesse du toy : la face annoncée au tirage est celle qu'on lit une
     * fois le dé posé. Elle est relue de l'orientation, pas du tirage — c'est
     * donc bien la géométrie qui répond.
     */
    @Test
    fun `le de se pose sur la face tiree`() {
        for (roll in rolls()) {
            assertEquals(
                "${roll.die.id} : ${roll.value}",
                roll.value,
                roll.die.topFace(roll.orientationAt(T_END)),
            )
        }
    }

    /**
     * La mise en place ne doit pas se voir : la culbute libre et la correction
     * vers la pose se mélangent avec un poids qui part de zéro, donc
     * l'orientation reste continue en [T_BRAKE] comme en [T_LAND]. Une rupture
     * ici, c'est un dé qui « saute » sur sa face.
     */
    @Test
    fun `l'orientation ne saute pas aux raccords`() {
        for (roll in rolls(60)) {
            for (t in doubleArrayOf(T_BRAKE, T_LAND)) {
                val before = roll.orientationAt(t - 1e-4)
                val after = roll.orientationAt(t + 1e-4)
                assertTrue(
                    "${roll.die.id} : saut d'orientation à $t",
                    angleBetween(before, after) < 0.02,
                )
            }
        }
    }

    /** Le dé part du centre, y revient, et ne sort jamais du champ large. */
    @Test
    fun `le de reste dans le hublot`() {
        for (roll in rolls(60)) {
            var t = 0.0
            while (t <= T_END) {
                val p = roll.posAt(t)
                assertTrue("dérive de ${p.length} à $t", p.length < 1.0)
                t += 0.01
            }
            assertEquals(0.0, roll.posAt(0.0).length, 1e-12)
            assertEquals(0.0, roll.posAt(T_LAND).length, 1e-12)
        }
    }

    /**
     * La caméra : gros plan au repos, reculée pendant tout le vol, revenue une
     * fois posée. C'est cette courbe-là qui commande la révélation du nombre.
     */
    @Test
    fun `la camera recule au jet et revient a la pose`() {
        assertEquals(1.0, zoomAt(0.0), 1e-12)
        assertTrue("recul trop mou", zoomAt(0.1) < 0.25)
        assertEquals(0.0, zoomAt(T_TOSS), 1e-12)
        assertEquals(0.0, zoomAt(T_LAND - 0.01), 1e-12)
        assertEquals(0.0, zoomAt(T_LAND), 1e-12)
        assertEquals(1.0, zoomAt(T_END), 1e-12)
        assertEquals(1.0, zoomAt(T_END + 10), 1e-12)
    }

    /**
     * Une marque ne s'imprime qu'au gros plan. Le seuil est franchi vite au
     * départ — le recul de la caméra est à la puissance cinq — et la marque est
     * pleine avant la fin du zoom, de sorte qu'elle ne bouge plus une fois
     * lisible.
     */
    @Test
    fun `la marque ne s'allume qu'au gros plan`() {
        // Éteinte en cent millisecondes : la marque ne s'attarde pas sur un dé
        // qui part, c'est le recul de la caméra qui la coupe.
        assertEquals(0.0, revealAt(zoomAt(0.1)), 1e-12)
        assertEquals(0.0, revealAt(zoomAt(1.0)), 1e-12)
        assertEquals(0.0, revealAt(zoomAt(T_LAND)), 1e-12)
        assertEquals(1.0, revealAt(zoomAt(T_END)), 1e-12)
        // Pleine avant la fin du zoom : il reste du gros plan à lire.
        assertTrue(revealAt(zoomAt(T_LAND + 0.4)) > 0.99)
    }

    /**
     * Les fenêtres mortes. Elles ne sont pas décoratives : sans la première, le
     * poignet qui revient relance le dé ; sans la seconde, on peut secouer sans
     * fin sans jamais rien lire.
     */
    @Test
    fun `le jet n'ecoute pas tout le temps`() {
        assertEquals(Verdict.OK, verdict(null))
        assertEquals(Verdict.TOO_EARLY, verdict(0.1))
        assertEquals(Verdict.OK, verdict(1.0))
        assertEquals(Verdict.TOO_LATE, verdict(T_LAND - 0.2))
        assertEquals(Verdict.READING, verdict(T_LAND + 0.2))
        assertEquals(Verdict.OK, verdict(T_END))
    }

    @Test
    fun `les etapes se suivent dans l'ordre`() {
        assertEquals(Stage.REST, stageAt(null))
        assertEquals(Stage.TOSS, stageAt(0.1))
        assertEquals(Stage.TUMBLE, stageAt(1.0))
        assertEquals(Stage.BRAKE, stageAt(2.0))
        assertEquals(Stage.READ, stageAt(T_LAND + 0.1))
        assertEquals(Stage.REST, stageAt(T_END))
    }

    /** Le tressaut d'impact est une cellule entière, ou rien. */
    @Test
    fun `le tressaut est en cellules entieres`() {
        val roll = rolls(1).first()
        assertNotNull(roll.joltAt(T_LAND + 0.01))
        assertEquals(-1, roll.joltAt(T_LAND + 0.01)!!.dy)
        assertEquals(1, roll.joltAt(T_LAND + 0.07)!!.dy)
        assertEquals(null, roll.joltAt(T_LAND + 0.3))
    }

    /**
     * Le même jet rejoué donne la même chose : c'est ce qui permet à la
     * vignette du hub, au widget et à la matrice de montrer le même dé.
     */
    @Test
    fun `un jet est reproductible a graine egale`() {
        val die = Dice.byKey("d20")
        val a = Roll.make(die, die.restQuat(1, 0), 17, Random(42))
        val b = Roll.make(die, die.restQuat(1, 0), 17, Random(42))
        var t = 0.0
        while (t <= T_END) {
            assertEquals(a.orientationAt(t), b.orientationAt(t))
            t += 0.05
        }
        assertEquals(a.twist, b.twist)
    }

    /** Angle entre deux orientations, radians — le signe du quaternion mis à part. */
    private fun angleBetween(a: Quat, b: Quat): Double {
        val d = kotlin.math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w)
        return 2 * kotlin.math.acos(d.coerceAtMost(1.0))
    }
}
