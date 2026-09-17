package red.suns.haloglyph.sono.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.sono.dsp.FLOOR_DBFS
import red.suns.haloglyph.sono.engine.Calibration
import red.suns.haloglyph.sono.engine.SonoDemo
import red.suns.haloglyph.sono.engine.SonoEngine
import red.suns.haloglyph.sono.engine.SonoMode

/**
 * Les trois modes, vérifiés sur la frame plutôt qu'à l'œil sur une matrice.
 */
class SonoRendererTest {

    private val spec = MatrixSpec.Phone3
    private val demo = SonoDemo()

    /**
     * Le pas des scénarios : une **colonne** d'onde.
     *
     * Les instantanés fabriqués ici ne portent qu'une crête, donc une colonne —
     * c'est le repli documenté de `pushWave`. Avancer d'une colonne par appel
     * garde les scénarios lisibles ; le vrai moteur, lui, en verse deux ou trois
     * par image.
     */
    private val step = SonoRenderer.COLUMN_SECONDS

    private fun lit(frame: Frame) = frame.values.count { it > 0f }

    /** Un instantané de niveaux imposés, pour piloter l'affichage au lieu de le subir. */
    private fun snapshot(
        t: Double,
        lpeak: Double = Calibration.MIN_DB,
        bandDb: Double = Calibration.BAND_MIN,
        status: SonoEngine.Status = SonoEngine.Status.OK,
    ) = SonoEngine.Snapshot(
        laf = Calibration.MIN_DB,
        las = Calibration.MIN_DB,
        laeq = Calibration.MIN_DB,
        lafmax = Calibration.MIN_DB,
        peak = Calibration.MIN_DB,
        lpeak = lpeak,
        bands = DoubleArray(25) { bandDb },
        overload = false,
        status = status,
        elapsed = t,
        t = t,
    )

    /** Fait tourner [seconds] de scène, et rend l'horodatage atteint. */
    private fun soak(
        renderer: SonoRenderer,
        mode: SonoMode,
        seconds: Double,
        from: Double = 0.0,
        snap: (Double) -> SonoEngine.Snapshot,
    ): Double {
        var t = from
        repeat((seconds / step).toInt()) {
            renderer.render(Frame(spec), snap(t), mode)
            t += step
        }
        return t
    }

    /** Ce que le renderer dessinerait maintenant, sans rien verser dans son état. */
    private fun peek(renderer: SonoRenderer, mode: SonoMode, snap: SonoEngine.Snapshot): Frame =
        Frame(spec).also { renderer.render(it, snap, mode, feedHistory = false) }

    // ---------- le fond de scène ----------

    /**
     * Le défaut qui a fait exister [red.suns.haloglyph.sono.engine.NoiseFloor] :
     * une pièce ordinaire n'est pas silencieuse, et son bruit permanent occupait
     * un tiers du disque en permanence. Un fond **stationnaire** ne doit plus
     * rien allumer du tout.
     */
    @Test
    fun `un fond stationnaire finit par ne montrer que l'axe`() {
        for (mode in listOf(SonoMode.SPECTRE, SonoMode.ONDE)) {
            val renderer = SonoRenderer(spec)
            val steady = { t: Double -> snapshot(t, lpeak = 52.0, bandDb = 44.0) }
            val t = soak(renderer, mode, seconds = 6.0, snap = steady)

            val frame = peek(renderer, mode, steady(t))
            val cy = spec.centerY
            val offAxis = frame.values.indices.count {
                frame.values[it] > 0f && spec.yOf(it) != cy
            }
            assertEquals("$mode : le fond allume encore $offAxis LEDs", 0, offAxis)
        }
    }

    /**
     * Le démarrage réel, et le défaut qu'il produisait : le banc de bandes rend
     * des zéros exacts tant que sa FFT n'est pas pleine, et la crête large bande
     * en fait autant avant le premier bloc du micro. Les deux sortent au repli
     * « pas de signal », le plancher s'y amorçait, et **les deux modes restaient
     * blancs** le temps qu'il remonte — soit des minutes.
     */
    @Test
    fun `le demarrage sans signal ne blanchit pas l'ecran`() {
        val absent = FLOOR_DBFS + Calibration.K
        for (mode in listOf(SonoMode.SPECTRE, SonoMode.ONDE)) {
            val renderer = SonoRenderer(spec)
            // Une demi-seconde d'avant-mesure, puis une pièce ordinaire.
            var t = soak(renderer, mode, seconds = 0.5) {
                snapshot(it, lpeak = absent, bandDb = absent)
            }
            val room = { u: Double -> snapshot(u, lpeak = 52.0, bandDb = 44.0) }
            t = soak(renderer, mode, seconds = 6.0, from = t, snap = room)

            val frame = peek(renderer, mode, room(t))
            val offAxis = frame.values.indices.count {
                frame.values[it] > 0f && spec.yOf(it) != spec.centerY
            }
            assertEquals("$mode : $offAxis LEDs allumées par une pièce calme", 0, offAxis)
        }
    }

    /** Et ce qui sort du fond, lui, se voit tout de suite. */
    @Test
    fun `ce qui depasse le fond remplit le disque`() {
        for (mode in listOf(SonoMode.SPECTRE, SonoMode.ONDE)) {
            val renderer = SonoRenderer(spec)
            var t = soak(renderer, mode, seconds = 6.0) { snapshot(it, lpeak = 52.0, bandDb = 44.0) }

            // Trente décibels au-dessus du fond, le temps de remplir l'écran.
            t = soak(renderer, mode, seconds = 1.5, from = t) {
                snapshot(it, lpeak = 82.0, bandDb = 74.0)
            }

            val frame = peek(renderer, mode, snapshot(t, lpeak = 82.0, bandDb = 74.0))
            assertTrue("$mode : ${lit(frame)} LEDs allumées", lit(frame) > 200)
        }
    }

    // ---------- l'onde ----------

    /**
     * Le cœur du visualiseur : une crête entrée à droite doit se retrouver une
     * colonne plus à gauche à chaque tranche.
     */
    @Test
    fun `l'onde defile de droite a gauche`() {
        val renderer = SonoRenderer(spec)
        // Le fond d'abord : sans plancher établi, une crête isolée amorce le
        // plancher sur elle-même et ne s'affiche pas.
        var t = soak(renderer, SonoMode.ONDE, seconds = 3.0) { snapshot(it, lpeak = 40.0) }

        // Une seule tranche forte, puis le retour au fond.
        renderer.render(Frame(spec), snapshot(t, lpeak = 100.0), SonoMode.ONDE)
        t += step
        renderer.render(Frame(spec), snapshot(t, lpeak = 40.0), SonoMode.ONDE)

        fun tallest(): Int {
            val frame = peek(renderer, SonoMode.ONDE, snapshot(t, lpeak = 40.0))
            return (0 until spec.size).maxBy { x ->
                (0 until spec.size).count { y -> frame[x, y] > 0f }
            }
        }

        assertEquals("la crête devrait entrer par la droite", spec.size - 1, tallest())

        repeat(3) {
            val before = tallest()
            t += step
            renderer.render(Frame(spec), snapshot(t, lpeak = 40.0), SonoMode.ONDE)
            assertEquals("la crête n'a pas glissé d'une colonne", before - 1, tallest())
        }
    }

    /**
     * À une colonne par image, la gigue de la boucle de rendu fait régulièrement
     * passer deux tranches d'un coup. La colonne sautée reprend la précédente :
     * sans ça, une colonne sur deux s'éteint et l'onde devient un peigne.
     */
    @Test
    fun `une tranche sautee ne laisse pas de trou`() {
        val renderer = SonoRenderer(spec)
        var t = soak(renderer, SonoMode.ONDE, seconds = 3.0) { snapshot(it, lpeak = 40.0) }
        t = soak(renderer, SonoMode.ONDE, seconds = 1.0, from = t) { snapshot(it, lpeak = 85.0) }

        // Un bond de deux tranches, comme un GC au mauvais moment.
        t += 2 * step
        renderer.render(Frame(spec), snapshot(t, lpeak = 85.0), SonoMode.ONDE)

        val frame = peek(renderer, SonoMode.ONDE, snapshot(t, lpeak = 85.0))
        val heights = (0 until spec.size)
            .filter { x -> spec.isLed(x, spec.centerY + 1) }
            .map { x -> (0 until spec.size).count { y -> frame[x, y] > 0f } }
        assertTrue("colonne éteinte au milieu de l'onde : $heights", heights.all { it > 1 })
    }

    /**
     * L'axe médian traverse le disque en permanence, y compris là où l'histoire
     * n'est pas encore arrivée : une onde qui commence à mi-écran se lirait
     * comme un signal coupé.
     */
    @Test
    fun `l'axe median traverse le disque des la premiere image`() {
        val frame = Frame(spec)
        SonoRenderer(spec).render(frame, snapshot(0.0), SonoMode.ONDE)
        val cy = spec.centerY
        val across = (0 until spec.size).count { x -> spec.isLed(x, cy) }
        assertEquals(across, (0 until spec.size).count { x -> frame[x, cy] > 0f })
    }

    // ---------- les trois modes ----------

    @Test
    fun `les trois modes montrent trois choses differentes`() {
        val renderer = SonoRenderer(spec)
        // L'histoire est remplie une fois pour toutes : les trois modes voient
        // le même instant, seule la façon de le montrer change.
        val t = soak(renderer, SonoMode.SPECTRE, seconds = 10.0) { demo.snapshotAt(it) }

        val images = SonoMode.entries.map { peek(renderer, it, demo.snapshotAt(t)).values.copyOf() }
        for (i in images.indices) {
            assertTrue("mode ${SonoMode.entries[i]} vide", images[i].any { it > 0f })
            for (j in i + 1 until images.size) {
                assertNotEquals(
                    "${SonoMode.entries[i]} et ${SonoMode.entries[j]} rendent la même image",
                    images[i].toList(),
                    images[j].toList(),
                )
            }
        }
    }

    /**
     * Sur 25 LEDs de côté, une nuance ne se lit pas comme une nuance mais comme
     * une LED qui hésite. Les deux modes à barres sont donc en tout ou rien —
     * c'est la règle que le spectrogramme abandonné ne pouvait pas tenir.
     */
    @Test
    fun `les modes a barres n'emploient aucune demi-teinte`() {
        val renderer = SonoRenderer(spec)
        val t = soak(renderer, SonoMode.ONDE, seconds = 6.0) { demo.snapshotAt(it) }
        for (mode in listOf(SonoMode.SPECTRE, SonoMode.ONDE)) {
            val frame = peek(renderer, mode, demo.snapshotAt(t))
            val greys = frame.values.filter { it > 0f && it < 1f }
            assertTrue("$mode : demi-teintes trouvées $greys", greys.isEmpty())
        }
    }

    /** Sans micro, aucun mode ne meurt noir. */
    @Test
    fun `sans mesure chaque mode montre quelque chose`() {
        for (mode in SonoMode.entries) {
            val frame = Frame(spec)
            SonoRenderer(spec).render(
                frame,
                snapshot(1.0, status = SonoEngine.Status.NO_MIC),
                mode,
            )
            assertTrue("$mode est noir sans micro", lit(frame) > 10)
        }
    }

    /** La surcharge doit rester lisible par-dessus n'importe quel mode. */
    @Test
    fun `la surcharge allume l'anneau dans tous les modes`() {
        val geometry = DiscGeometry(spec)
        for (mode in SonoMode.entries) {
            val frame = Frame(spec)
            SonoRenderer(spec).render(
                frame,
                demo.snapshotAt(2.0).copy(overload = true),
                mode,
            )
            assertTrue(
                "$mode : anneau incomplet",
                geometry.ring.all { frame.values[it] >= 1f },
            )
        }
    }

    /** Le tour du mode suivant boucle, et ne saute personne. */
    @Test
    fun `la rotation des modes fait le tour`() {
        var mode = SonoMode.DEFAULT
        val seen = mutableListOf(mode)
        repeat(SonoMode.entries.size - 1) {
            mode = mode.next
            seen += mode
        }
        assertEquals(SonoMode.entries.toSet(), seen.toSet())
        assertEquals(SonoMode.DEFAULT, mode.next)
        assertEquals(SonoMode.DEFAULT, SonoMode.byKey("n'importe quoi"))
        assertEquals(SonoMode.AIGUILLE, SonoMode.byKey("AIGUILLE"))
    }

    @Test
    fun `le renderer n'est pas construit avec le mode spectrogramme`() {
        // Sentinelle : le mode a existé, ses chaînes et sa préférence aussi. Une
        // clé oubliée quelque part doit retomber sur le défaut, pas ressusciter.
        assertEquals(SonoMode.DEFAULT, SonoMode.byKey("SPECTROGRAMME"))
        assertEquals(3, SonoMode.entries.size)
    }
}
