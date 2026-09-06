package red.suns.haloglyph.lapse.render

import red.suns.haloglyph.core.matrix.Font
import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.matrix.drawLine
import red.suns.haloglyph.lapse.engine.TimeBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ce que les étiquettes de la matrice promettent aux langues qu'on servira
 * ensuite : un glyphe pour chaque lettre, la largeur du français, et rien qui
 * se lise comme un chiffre.
 *
 * L'app de réglages, elle, n'a pas besoin de tests : `lint` signale une clé
 * manquante dans un dossier `values-xx`, et le compilateur refuse un `R.string` qui
 * n'existe pas. C'est la matrice qui est fragile, parce que ses contraintes
 * — trois colonnes, un disque de 25 px — ne sont écrites nulle part ailleurs.
 */
class MatrixLabelsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    /** Diff riche : les cinq unités sont pertinentes, donc toutes écrites. */
    private val fullDiff = TimeBreakdown.breakdown(
        ms(2000, 1, 1), ms(2026, 7, 23, 15, 30, 45), zone,
    )

    /** Les étiquettes collées à une valeur — celles que toutes les polices écrivent. */
    private fun inlineLabels(l: MatrixLabels) =
        listOf(l.years, l.months, l.days, l.hours, l.minutes)

    @Test
    fun `les cinq langues du portail sont servies`() {
        assertEquals(listOf("de", "en", "es", "fr", "it"), MatrixLabels.ALL.keys.sorted())
    }

    @Test
    fun `langue inconnue - repli sur l'anglais`() {
        assertSame(MatrixLabels.EN, MatrixLabels.of("ja"))
        assertSame(MatrixLabels.EN, MatrixLabels.of(""))
        // une balise régionale n'est pas une langue : `of` attend le code seul,
        // et ce qu'il ne reconnaît pas retombe proprement plutôt que de crasher
        assertSame(MatrixLabels.EN, MatrixLabels.of("pt-BR"))
        assertSame(MatrixLabels.FR, MatrixLabels.of("FR"))
    }

    @Test
    fun `chaque etiquette a un glyphe dans les polices qui l'ecrivent`() {
        fun check(font: Font, fontName: String, code: String, label: String) {
            for (c in label) {
                assertNotNull(
                    "$code : le glyphe '$c' manque à la police $fontName",
                    font.glyphs[c],
                )
            }
        }
        for ((code, l) in MatrixLabels.ALL) {
            for (label in inlineLabels(l)) {
                // Détail à 3-4 lignes / repli du format Jours
                check(Fonts.F3, "3×5", code, label)
                // Détail à 5 lignes
                check(Fonts.F4, "3×4", code, label)
                // Compact, Détail à ≤ 2 lignes, format Jours
                check(Fonts.F5, "5×7", code, label)
            }
            // La ligne du bas du format Cycle n'est écrite qu'en 3×5…
            check(Fonts.F3, "3×5", code, l.minutesCycle)
            // …et le décompte des secondes qu'en 5×7.
            check(Fonts.F5, "5×7", code, l.seconds)
        }
    }

    /**
     * L'invariant qui dispense de rejouer la mise en page à chaque langue.
     *
     * Le placement dans le disque — centrage, nudge, resserrage du groupe — ne
     * dépend que des largeurs. Tant qu'elles sont celles du français, ce que
     * `LapseRendererTest` vérifie sur le français vaut pour les cinq langues.
     * Une langue ajoutée avec une lettre plus large casse ici, et pas en
     * production sur un compteur tronqué.
     */
    @Test
    fun `chaque langue occupe exactement la largeur du francais`() {
        for ((fontName, font) in listOf("3×5" to Fonts.F3, "3×4" to Fonts.F4, "5×7" to Fonts.F5)) {
            val reference = inlineLabels(MatrixLabels.FR).map { font.textWidth(it) }
            for ((code, l) in MatrixLabels.ALL) {
                assertEquals(
                    "$code : largeurs différentes du français en $fontName",
                    reference,
                    inlineLabels(l).map { font.textWidth(it) },
                )
            }
        }
    }

    /**
     * Une étiquette est toujours collée à son nombre — « 12H », « 5D ». Les
     * capitales qui ont un sosie chiffré s'y lisent donc comme un chiffre de
     * plus : c'est ce qui a écarté le `O` de l'italien (*ore*), où « 12O » se
     * lit « 120 ». Les cinq langues disent les heures `H`.
     */
    @Test
    fun `aucune etiquette ne se confond avec un chiffre`() {
        val sosies = mapOf('O' to '0', 'I' to '1', 'Z' to '2', 'B' to '8')
        for ((code, l) in MatrixLabels.ALL) {
            for (label in inlineLabels(l)) {
                for (c in label) {
                    assertTrue(
                        "$code : '$c' se lit comme un ${sosies[c]} collé à un nombre",
                        c !in sosies,
                    )
                }
            }
        }
    }

    /**
     * Le même contrôle de clipping que `LapseRendererTest`, joué sur les cinq
     * langues : tous les pixels des glyphes doivent atterrir dans le disque.
     * Redondant avec l'invariant de largeur ci-dessus — et c'est voulu, l'un
     * dit *pourquoi* ça tient, l'autre *que* ça tient.
     */
    @Test
    fun `aucune langue ne clippe hors du disque`() {
        val renderer = LapseRenderer()
        fun assertFits(font: Font, s: String, y: Int, code: String) {
            val frame = Frame(MatrixSpec.Phone3)
            frame.drawLine(font, s, y, 1f)
            var expected = 0
            for (c in s) {
                if (c == ' ') continue
                val gl = font.glyphs[c] ?: continue
                expected += gl.sumOf { row -> row.count { it == '1' } }
            }
            assertEquals(
                "$code : clipping pour « $s » à y=$y",
                expected,
                frame.values.count { it > 0f },
            )
        }
        for ((code, l) in MatrixLabels.ALL) {
            renderer.labels = l
            val u = renderer.units(fullDiff)
            assertEquals(5, u.size)
            fun cell(i: Int) = "${u[i].value}${u[i].inline}"
            // Détail 5 lignes en 3×4 : les bandes extrêmes, les plus étroites
            assertFits(Fonts.F4, cell(0), 1, code)
            assertFits(Fonts.F4, cell(4), 21, code)
            // Détail 2 : les lignes appariées, les plus larges
            assertFits(Fonts.F3, "${cell(1)} ${cell(2)}", 10, code)
            assertFits(Fonts.F3, "${cell(3)} ${cell(4)}", 16, code)
        }
    }

    /**
     * La marque du compte à rebours, langue par langue — « J-42 » a une
     * traduction dans chacune, et ce n'est pas toujours l'initiale de « jour »
     * de la table d'à côté par hasard : le jour J, D-Day, Tag X, día D et
     * giorno G nomment la même chose.
     */
    @Test
    fun `marque du compte a rebours - une initiale par langue`() {
        assertEquals("J", MatrixLabels.FR.days)
        assertEquals("D", MatrixLabels.EN.days)
        assertEquals("T", MatrixLabels.DE.days)
        assertEquals("D", MatrixLabels.ES.days)
        assertEquals("G", MatrixLabels.IT.days)
    }

    @Test
    fun `les unites du renderer suivent la langue posee`() {
        val renderer = LapseRenderer(labels = MatrixLabels.EN)
        assertEquals(
            listOf("Y", "M", "D", "H", "'"),
            renderer.units(fullDiff).map { it.inline },
        )
        renderer.labels = MatrixLabels.DE
        assertEquals(
            listOf("J", "M", "T", "H", "'"),
            renderer.units(fullDiff).map { it.inline },
        )
    }
}
