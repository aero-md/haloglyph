package dev.aero.glyphlapse.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LapseEngineTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun engine() = LapseEngine(zone)

    @Test
    fun `arrivee - un seul event au passage jusqu'a vers depuis`() {
        val e = engine()
        e.setRef(10_000L)
        e.update(nowMillis = 5_000L, now = 0.0)   // UNTIL
        assertTrue(e.drainEvents().isEmpty())

        val snap = e.update(nowMillis = 10_500L, now = 1.0) // SINCE → arrivée
        assertEquals(listOf(LapseEngine.Event.Arrived), e.drainEvents())
        assertNotNull(snap.arrivalT)

        e.update(nowMillis = 11_000L, now = 1.5)
        assertTrue(e.drainEvents().isEmpty()) // pas de re-déclenchement
    }

    @Test
    fun `arrivee - animation bornee a ARRIVAL_DUR`() {
        val e = engine()
        e.setRef(10_000L)
        e.update(5_000L, 0.0)
        e.update(10_500L, 1.0)
        assertNotNull(e.update(12_000L, 1.0 + LapseEngine.ARRIVAL_DUR - 0.1).arrivalT)
        assertNull(e.update(20_000L, 1.0 + LapseEngine.ARRIVAL_DUR + 0.1).arrivalT)
    }

    @Test
    fun `pas d'arrivee si on demarre deja en depuis`() {
        val e = engine()
        e.setRef(0L)
        e.update(5_000L, 0.0)
        e.update(6_000L, 1.0)
        assertTrue(e.drainEvents().isEmpty())
    }

    @Test
    fun `setRef annule l'arrivee en cours`() {
        val e = engine()
        e.setRef(10_000L)
        e.update(5_000L, 0.0)
        e.update(10_500L, 1.0)
        e.drainEvents()
        e.setRef(50_000L)
        assertNull(e.update(11_000L, 1.2).arrivalT)
    }

    @Test
    fun `cycle de format - ordre et event`() {
        val e = engine()
        assertEquals(LapseEngine.Format.DETAIL2, e.format)
        e.cycleFormat(0.0)
        assertEquals(LapseEngine.Format.COMPACT, e.format)
        e.cycleFormat(0.0)
        assertEquals(LapseEngine.Format.CYCLE, e.format)
        e.cycleFormat(0.0)
        assertEquals(LapseEngine.Format.DAYS, e.format)
        e.cycleFormat(0.0)
        assertEquals(LapseEngine.Format.DETAIL2, e.format) // boucle
        assertEquals(4, e.drainEvents().count { it == LapseEngine.Event.FormatChanged })
    }

    @Test
    fun `slide - fenetre de transition puis retour au repos`() {
        val e = engine()
        e.cycleFormat(10.0)
        val during = e.update(0L, 10.1)
        assertNotNull(during.slideT)
        assertEquals(LapseEngine.Format.DETAIL2, during.prevFormat)
        assertTrue(during.animating)

        val after = e.update(0L, 10.0 + LapseEngine.SLIDE + 0.05)
        assertNull(after.slideT)
        assertNull(after.prevFormat)
        assertFalse(after.animating)
    }

    @Test
    fun `setFormatQuiet - ni slide ni event`() {
        val e = engine()
        e.setFormatQuiet(LapseEngine.Format.DAYS)
        assertEquals(LapseEngine.Format.DAYS, e.format)
        assertTrue(e.drainEvents().isEmpty())
        assertNull(e.update(0L, 1.0).slideT)
    }

    @Test
    fun `format cycle - toujours animating`() {
        val e = engine()
        e.setFormatQuiet(LapseEngine.Format.CYCLE)
        assertTrue(e.update(0L, 100.0).animating)
    }
}
