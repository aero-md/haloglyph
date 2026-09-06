package dev.aero.glyphlapse.render

import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.engine.TimeBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

class LapseRendererTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val renderer = LapseRenderer()

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private fun diff(ref: Long, now: Long) = TimeBreakdown.breakdown(ref, now, zone)

    /** Diff riche : 5 unités pertinentes (années → minutes). */
    private val fullDiff = diff(ms(2000, 1, 1), ms(2026, 7, 23, 15, 30, 45))

    /** Diff courte : jours en tête. */
    private val daysDiff = diff(ms(2026, 7, 18, 10, 0, 0), ms(2026, 7, 23, 15, 30, 45))

    private fun snap(
        d: TimeBreakdown.Diff,
        format: LapseEngine.Format = LapseEngine.Format.DETAIL2,
        secondsMode: LapseEngine.SecondsMode = LapseEngine.SecondsMode.RING,
    ) = LapseEngine.Snapshot(
        diff = d, format = format, prevFormat = null, slideT = null,
        secondsMode = secondsMode, arrivalT = null, t = 12.34,
    )

    @Test
    fun `unites - zeros de tete masques, granularite complete ensuite`() {
        val u = renderer.units(daysDiff)
        assertEquals(listOf("J", "H", "'"), u.map { it.inline })
        assertEquals(5, renderer.units(fullDiff).size)
    }

    @Test
    fun `unites - heures en tete`() {
        val d = diff(ms(2026, 7, 23, 10, 0, 0), ms(2026, 7, 23, 15, 12, 0))
        assertEquals(listOf("H", "'"), renderer.units(d).map { it.inline })
    }

    @Test
    fun `rendu - sortie bornee 0-255 et rien hors disque`() {
        for (format in LapseEngine.Format.entries) {
            val frame = renderer.render(snap(fullDiff, format))
            assertEquals(625, frame.size)
            for (y in 0 until 25) for (x in 0 until 25) {
                val v = frame[y * 25 + x]
                assertTrue(v in 0..255)
                val dx = x - 12.0
                val dy = y - 12.0
                if (dx * dx + dy * dy >= 12.5 * 12.5) {
                    assertEquals("hors disque en ($x,$y)", 0, v)
                }
            }
        }
    }

    @Test
    fun `rendu - chaque format allume du contenu`() {
        for (format in LapseEngine.Format.entries) {
            val lit = renderer.render(snap(fullDiff, format)).count { it > 128 }
            assertTrue("format $format vide", lit > 10)
        }
    }

    @Test
    fun `anneau - nombre de cellules proportionnel aux secondes`() {
        val g = FloatArray(625)
        renderer.renderRing(g, fullDiff) // 45 s
        val expected = (fullDiff.seconds / 60.0 * Disc.ring.size).roundToInt()
        assertEquals(expected, g.count { it > 0f })
    }

    @Test
    fun `sablier - quantite de sable fidele et croissante`() {
        fun sandCount(seconds: Int): Int {
            val d = diff(ms(2026, 7, 23, 12, 0, 0), ms(2026, 7, 23, 12, 3, seconds))
            val g = FloatArray(625)
            renderer.renderHourglass(g, d, t = 5.0)
            // le filet central (x=12) est exclu ; le sable de fond assombri
            // descend jusqu'à ~0.055, seuil abaissé en conséquence
            return g.withIndex().count { (i, b) -> b >= 0.05f && i % 25 != 12 }
        }
        val c10 = sandCount(10)
        val c30 = sandCount(30)
        val c50 = sandCount(50)
        assertTrue("progression attendue : $c10 < $c30 < $c50", c10 < c30 && c30 < c50)
        // dichotomie : au plus une ligne d'écart avec la cible (hors colonne du filet)
        val target30 = (30 / 60.0 * Disc.inside.size).roundToInt()
        assertTrue("écart cible: $c30 vs $target30", Math.abs(c30 - target30) <= 26)
    }

    @Test
    fun `texte - jamais clippe pour les formats détail sur diff complete`() {
        // Chaque ligne des formats Détail/Détail 2 doit trouver un placement sans clipping
        val u = renderer.units(fullDiff)
        fun assertFits(f: Font, s: String, y: Int) {
            val g = FloatArray(625)
            renderer.layoutLine(g, f, s, y, 1f)
            // tous les pixels des glyphes doivent atterrir dans le disque
            // (le nudge et le resserrage adaptatif ne changent pas leur nombre)
            var expected = 0
            for (c in s) {
                if (c == ' ') continue
                val gl = f.glyphs[c] ?: continue
                expected += gl.sumOf { row -> row.count { it == '1' } }
            }
            assertEquals("clipping pour '$s' à y=$y", expected, g.count { it > 0f })
        }
        // Détail 5 lignes en 3×4 : bandes extrêmes
        assertFits(Fonts.F4, "${u[0].value}${u[0].inline}", 1)
        assertFits(Fonts.F4, "${u[4].value}${u[4].inline}", 21)
        // Détail 2 : lignes appariées
        assertFits(Fonts.F3, "${u[1].value}${u[1].inline} ${u[2].value}${u[2].inline}", 10)
        assertFits(Fonts.F3, "${u[3].value}${u[3].inline} ${u[4].value}${u[4].inline}", 16)
    }

    @Test
    fun `moins d'une minute - secondes au centre`() {
        val d = diff(ms(2026, 7, 23, 12, 0, 0), ms(2026, 7, 23, 12, 0, 42))
        val lit = renderer.render(snap(d)).count { it > 128 }
        assertTrue(lit > 10)
    }

    @Test
    fun `arrivee - flash initial plein disque`() {
        val s = LapseEngine.Snapshot(
            diff = fullDiff, format = LapseEngine.Format.DETAIL2, prevFormat = null,
            slideT = null, secondsMode = LapseEngine.SecondsMode.RING,
            arrivalT = 0.01, t = 1.0,
        )
        val frame = renderer.render(s)
        val litRatio = frame.count { it > 100 } / Disc.inside.size.toDouble()
        assertTrue("flash attendu, ratio=$litRatio", litRatio > 0.9)
    }

    @Test
    fun `slide - transition melange ancien et nouveau format`() {
        val s = LapseEngine.Snapshot(
            diff = fullDiff, format = LapseEngine.Format.DAYS,
            prevFormat = LapseEngine.Format.DETAIL2,
            slideT = LapseEngine.SLIDE / 2, secondsMode = LapseEngine.SecondsMode.RING,
            arrivalT = null, t = 1.0,
        )
        val frame = renderer.render(s)
        assertTrue(frame.count { it > 128 } > 10)
    }
}
