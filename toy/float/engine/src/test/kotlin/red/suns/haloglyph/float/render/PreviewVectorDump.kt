package red.suns.haloglyph.float.render

import org.junit.Test
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixLook
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.float.engine.FloatEngine
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.engine.FloatRange
import java.io.File
import java.util.Locale

/**
 * Générateur de `float_toy_preview.xml` — l'icône du toy dans Glyph Interface.
 *
 * Elle est **produite par le renderer**, comme celles de Lapse, Dice et Sono :
 * une icône dessinée à la main finirait par ne plus ressembler au toy. Ce
 * fichier n'est pas un test, c'est un outil qu'on relance quand le dessin change.
 */
class PreviewVectorDump {

    @Test
    fun dump() {
        val spec = MatrixSpec.Phone3
        val frame = Frame(spec)
        FloatRenderer(spec, fromBack = true).render(
            frame,
            FloatEngine.Reading(
                tiltDegrees = 1.6f,
                dirX = -0.42f,
                dirY = 0.91f,
                hasTilt = true,
                vertical = false,
                alongX = true,
                level = false,
                moving = false,
                heading = 0f,
                hasHeading = false,
                headingTrusted = true,
            ),
            FloatMode.NIVEAU,
            FloatRange.NORMALE,
            0.0,
        )

        val out = File("build/float_toy_preview.xml")
        out.parentFile.mkdirs()
        out.writeText(vector(spec, frame))
        println("écrit : ${out.absolutePath}")
    }

    /** Les paliers retenus. Quatre, comme les icônes existantes. */
    private val ladder = floatArrayOf(0.10f, 0.24f, 0.55f, 1f)

    private fun vector(spec: MatrixSpec, frame: Frame): String {
        val sb = StringBuilder()
        sb.append(HEADER)
        sb.append(path(cells(spec, spec.leds.toList()), "#F8F8F4", 0.12f))

        for (level in ladder) {
            val cells = spec.leds.filter { snap(frame.values[it]) == level }
            if (cells.isEmpty()) continue
            val alpha = MatrixLook.alphaOf((level * 255).toInt())
            sb.append(path(cells(spec, cells), "#F8F8F4", alpha))
        }
        sb.append("</vector>\n")
        return sb.toString()
    }

    /** Palier le plus proche, ou `0` si la cellule est éteinte. */
    private fun snap(value: Float): Float {
        if (value < 0.04f) return 0f
        return ladder.minBy { kotlin.math.abs(it - value) }
    }

    private fun cells(spec: MatrixSpec, indices: List<Int>): String = buildString {
        for (i in indices) {
            val x = ORIGIN + spec.xOf(i) * PITCH + INSET
            val y = ORIGIN + spec.yOf(i) * PITCH + INSET
            append(String.format(Locale.ROOT, "M%.1f,%.1fh13v13h-13Z", x, y))
        }
    }

    private fun path(data: String, color: String, alpha: Float): String = buildString {
        append("    <path\n")
        append("        android:pathData=\"$data\"\n")
        append("        android:fillColor=\"$color\"\n")
        append(String.format(Locale.ROOT, "        android:fillAlpha=\"%.2f\" />%n", alpha).replace("\r", ""))
        append("\n")
    }

    private companion object {
        /** Le disque émulé occupe 448 des 512 unités, comme les icônes existantes. */
        const val ORIGIN = 32.0
        const val PITCH = 448.0 / 25
        val INSET = PITCH * (1 - MatrixLook.DUTY) / 2

        val HEADER = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Apercu du toy pour Glyph Interface et pour le selecteur de widgets : le
                 niveau, bulle legerement hors de la couronne, rendu par FloatRenderer a la
                 geometrie du Phone (3). Genere par PreviewVectorDump, donc pas edite a la
                 main — c'est la vraie sortie du renderer, pas un dessin qui lui ressemble.

                 Les niveaux passent par MatrixLook.alphaOf, comme les deux surfaces
                 emulees : la frame porte des rapports cycliques, et une icone qui montrerait
                 la consigne au lieu du resultat serait fausse. Le calque a 0,12 est celui
                 des LEDs eteintes, meme convention que les icones des autres toys. -->
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="192dp"
                android:height="192dp"
                android:viewportWidth="512"
                android:viewportHeight="512">
                <path
                    android:pathData="M256,24A232,232 0 1,0 256,488A232,232 0 1,0 256,24Z"
                    android:fillColor="#000000" />

        """.trimIndent() + "\n"
    }
}
