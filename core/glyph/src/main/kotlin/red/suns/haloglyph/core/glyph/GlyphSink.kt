package red.suns.haloglyph.core.glyph

import com.nothing.ketchum.GlyphMatrixManager
import red.suns.haloglyph.core.matrix.FrameSink
import red.suns.haloglyph.core.matrix.MatrixSpec

/**
 * Le sink de la matrice physique.
 *
 * Deux échelles se rencontrent ici, et c'est tout ce que fait cette classe :
 * les renderers sortent du **0..255**, `setMatrixFrame` attend du **0..4095**.
 * Le facteur ×16 est celui de `GlyphMatrixUtils` dans le SDK.
 *
 * Le tampon est alloué une fois : on pousse 30 frames par seconde, une
 * allocation de 625 entiers par frame serait 18 000 objets par minute offerts
 * au GC pour rien.
 *
 * Les valeurs poussées sur une cellule sans LED sont ignorées par le matériel —
 * c'est pourquoi le masque n'est pas critique *ici*, alors qu'il l'est pour les
 * surfaces émulées.
 */
class GlyphSink(
    private val manager: GlyphMatrixManager,
    spec: MatrixSpec = MatrixSpec.Phone3,
) : FrameSink {

    private val buffer = IntArray(spec.cellCount)

    override fun push(brightness: IntArray) {
        require(brightness.size == buffer.size) {
            "frame de ${brightness.size} valeurs, la matrice en attend ${buffer.size}"
        }
        for (i in buffer.indices) buffer[i] = brightness[i] * BRIGHTNESS_MULTIPLIER
        manager.setMatrixFrame(buffer)
    }

    private companion object {
        /** 0..255 (renderer) → 0..4095 (matériel). */
        const val BRIGHTNESS_MULTIPLIER = 16
    }
}
