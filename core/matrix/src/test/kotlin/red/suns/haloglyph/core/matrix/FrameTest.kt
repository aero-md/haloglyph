package red.suns.haloglyph.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTest {

    private val spec = MatrixSpec.Phone3

    @Test
    fun `ecrire hors du disque est un no-op, pas une exception`() {
        val frame = Frame(spec)
        frame.set(0, 0, 1f)      // coin : cellule sans LED
        frame.set(-5, 12, 1f)    // hors grille
        frame.set(12, 40, 1f)
        assertTrue(frame.toBrightness().all { it == 0 })
    }

    @Test
    fun `la composition se fait par maximum`() {
        val frame = Frame(spec)
        frame.set(12, 12, 0.8f)
        frame.set(12, 12, 0.3f)
        assertEquals(0.8f, frame[12, 12], 1e-6f)

        // `put` est autoritaire, lui.
        frame.put(spec.index(12, 12), 0.1f)
        assertEquals(0.1f, frame[12, 12], 1e-6f)
    }

    @Test
    fun `la sortie est bornee entre 0 et 255, et masquee`() {
        val frame = Frame(spec)
        frame.fill(2f) // au-delà de 1 : doit saturer, pas déborder
        val out = frame.toBrightness()

        assertEquals(625, out.size)
        assertEquals(489, out.count { it == 255 })
        assertEquals(136, out.count { it == 0 })
        assertTrue(out.all { it in 0..255 })
        // Les coins restent éteints quoi qu'on fasse.
        assertEquals(0, out[spec.index(0, 0)])
    }

    @Test
    fun `dim n eclaire jamais et clear remet tout a zero`() {
        val frame = Frame(spec)
        frame.fill(1f)
        frame.dim(0.25f)
        assertEquals(0.25f, frame[12, 12], 1e-6f)
        assertEquals(63, frame.toBrightness()[spec.index(12, 12)])

        frame.clear()
        assertTrue(frame.toBrightness().all { it == 0 })
    }

    @Test
    fun `le tampon de sortie est reutilise, un sink qui garde doit copier`() {
        val frame = Frame(spec)
        val first = frame.toBrightness()
        frame.set(12, 12, 1f)
        val second = frame.toBrightness()
        // Même instance : c'est le contrat documenté de FrameSink.push.
        assertEquals(255, first[spec.index(12, 12)])

        val recording = RecordingSink(spec.cellCount)
        frame.pushTo(recording)
        val kept = recording.last!!
        frame.clear()
        frame.pushTo(recording)
        assertNotSame(kept, recording.last)
        assertEquals(255, kept[spec.index(12, 12)])
    }

    @Test
    fun `FanOutSink donne exactement la meme frame a tout le monde`() {
        val a = RecordingSink(spec.cellCount)
        val b = RecordingSink(spec.cellCount)
        val frame = Frame(spec)
        frame.set(12, 12, 1f)
        frame.pushTo(FanOutSink(a, b))

        assertEquals(1, a.frameCount)
        assertEquals(1, b.frameCount)
        assertTrue(a.last!!.contentEquals(b.last!!))
    }
}
