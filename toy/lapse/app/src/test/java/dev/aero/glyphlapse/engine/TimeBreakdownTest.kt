package dev.aero.glyphlapse.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeBreakdownTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `depuis simple - annees mois jours heures minutes secondes`() {
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2020, 3, 10, 8, 0, 0),
            nowMillis = ms(2026, 7, 23, 15, 30, 45),
            zone = zone,
        )
        assertEquals(TimeBreakdown.Direction.SINCE, d.direction)
        assertEquals(6, d.years)
        assertEquals(4, d.months)
        assertEquals(13, d.days)
        assertEquals(7, d.hours)
        assertEquals(30, d.minutes)
        assertEquals(45, d.seconds)
    }

    @Test
    fun `jusqu'a - direction et memes valeurs en miroir`() {
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2027, 1, 1),
            nowMillis = ms(2026, 12, 25, 12, 0, 0),
            zone = zone,
        )
        assertEquals(TimeBreakdown.Direction.UNTIL, d.direction)
        assertEquals(0, d.years)
        assertEquals(0, d.months)
        assertEquals(6, d.days)
        assertEquals(12, d.hours)
    }

    @Test
    fun `fin de mois clampee - 31 janvier plus 1 mois`() {
        // 31 janv. → 1ᵉʳ mars : 1 mois (janv.31 + 1 mois = 28 févr.) + 1 jour
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2026, 1, 31),
            nowMillis = ms(2026, 3, 1),
            zone = zone,
        )
        assertEquals(0, d.years)
        assertEquals(1, d.months)
        assertEquals(1, d.days)
        assertEquals(0, d.hours)
    }

    @Test
    fun `bissextile - 29 fevrier plus un an`() {
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2024, 2, 29),
            nowMillis = ms(2025, 3, 1),
            zone = zone,
        )
        assertEquals(1, d.years)
        assertEquals(0, d.months)
        assertEquals(1, d.days)
    }

    @Test
    fun `dst - le jour du changement d'heure fait 23 heures reelles`() {
        // Passage à l'heure d'été le 29 mars 2026 à 02:00 (Europe/Paris)
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2026, 3, 28, 12, 0, 0),
            nowMillis = ms(2026, 3, 29, 12, 0, 0),
            zone = zone,
        )
        assertEquals(0, d.days)
        assertEquals(23, d.hours)
        assertEquals(0, d.minutes)
    }

    @Test
    fun `diff nulle`() {
        val t = ms(2026, 7, 23, 12, 0, 0)
        val d = TimeBreakdown.breakdown(t, t, zone)
        assertEquals(TimeBreakdown.Direction.SINCE, d.direction)
        assertEquals(0, d.years + d.months + d.days + d.hours + d.minutes + d.seconds)
        assertEquals(0L, d.totalDays)
    }

    @Test
    fun `moins d'une minute - que des secondes`() {
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2026, 7, 23, 12, 0, 0),
            nowMillis = ms(2026, 7, 23, 12, 0, 42),
            zone = zone,
        )
        assertEquals(0, d.minutes)
        assertEquals(42, d.seconds)
    }

    @Test
    fun `totalDays - periodes de 24h pleines`() {
        val d = TimeBreakdown.breakdown(
            refMillis = ms(2026, 1, 1, 0, 0, 0),
            nowMillis = ms(2026, 1, 8, 23, 59, 0),
            zone = zone,
        )
        assertEquals(7L, d.totalDays)
    }
}
