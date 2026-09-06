package red.suns.haloglyph.core.matrix

import kotlin.math.roundToInt

/**
 * Tracé de texte dans le disque.
 *
 * Ces primitives étaient dupliquées dans chaque renderer ; elles sont ici,
 * paramétrées par la [MatrixSpec] portée par la [Frame].
 *
 * Le point non évident est le **centrage conscient du disque** : une ligne
 * centrée arithmétiquement peut mordre le bord dans les bandes hautes et basses,
 * là où la matrice ne fait que 7 ou 11 LEDs de large. [bestOffset] essaie
 * quelques décalages horizontaux et retient celui qui perd le moins de pixels.
 * Ce comportement vit dans le tracé, donc il est identique sur la matrice
 * physique et dans les widgets — gratuitement.
 */

/** Décalages essayés par [bestOffset], du plus discret au plus visible. */
private val NUDGES = intArrayOf(0, -1, 1, -2, 2, -3, 3)

/** Dessine [text] avec son coin haut-gauche en ([x0], [y0]). */
fun Frame.drawText(font: Font, text: String, x0: Int, y0: Int, brightness: Float = 1f) {
    var x = x0
    for (c in text) {
        if (c == ' ') {
            x += 1
            continue
        }
        val glyph = font.glyphs[c]
        if (glyph == null) {
            // Caractère inconnu : on avance d'une largeur plausible plutôt que
            // d'empiler les lettres. Un carré de remplacement serait pire — sur
            // 25 LEDs, il mangerait la ligne entière.
            x += 4
            continue
        }
        for (r in 0 until font.height) {
            val row = glyph[r]
            for (k in row.indices) if (row[k] == '1') set(x + k, y0 + r, brightness)
        }
        x += glyph[0].length + 1
    }
}

/** Nombre de pixels de [text] qui tomberaient hors du disque à cette position. */
fun Frame.clippedPixels(font: Font, text: String, x0: Int, y0: Int): Int {
    var clipped = 0
    var x = x0
    for (c in text) {
        if (c == ' ') {
            x += 1
            continue
        }
        val glyph = font.glyphs[c]
        if (glyph == null) {
            x += 4
            continue
        }
        for (r in 0 until font.height) {
            val row = glyph[r]
            for (k in row.indices) {
                if (row[k] == '1' && !spec.isLed(x + k, y0 + r)) clipped++
            }
        }
        x += glyph[0].length + 1
    }
    return clipped
}

/**
 * Meilleur décalage horizontal autour de [x0].
 *
 * @return (décalage retenu, pixels encore perdus). Un second membre non nul
 * signifie que la ligne ne rentre pas, quel que soit le décalage — au renderer
 * de resserrer ou de changer de police.
 */
fun Frame.bestOffset(font: Font, text: String, x0: Int, y0: Int): Pair<Int, Int> {
    var best = 0
    var bestClip = Int.MAX_VALUE
    for (dx in NUDGES) {
        val clip = clippedPixels(font, text, x0 + dx, y0)
        if (clip < bestClip) {
            bestClip = clip
            best = dx
            if (clip == 0) break
        }
    }
    return best to bestClip
}

/** Abscisse de départ d'un texte centré horizontalement. */
fun Frame.centeredX(font: Font, text: String): Int =
    ((spec.size - font.textWidth(text)) / 2.0).roundToInt()

/**
 * Une ligne centrée, décalée pour tenir dans le disque.
 *
 * Si la ligne contient des séparateurs de groupe et déborde encore, on retente
 * sans eux : perdre l'espacement vaut mieux que perdre un chiffre.
 */
fun Frame.drawLine(font: Font, text: String, y: Int, brightness: Float = 1f) {
    var content = text
    var x0 = centeredX(font, text)
    var fit = bestOffset(font, text, x0, y)

    if (fit.second > 0 && ' ' in text) {
        val tight = text.replace(" ", "")
        val tightX = centeredX(font, tight)
        val tightFit = bestOffset(font, tight, tightX, y)
        if (tightFit.second < fit.second) {
            content = tight
            x0 = tightX
            fit = tightFit
        }
    }
    drawText(font, content, x0 + fit.first, y, brightness)
}

/** Un bloc de lignes centré verticalement, interligne de 1 px. */
fun Frame.drawLines(font: Font, lines: List<String>, brightness: Float = 1f) {
    if (lines.isEmpty()) return
    val y0 = ((spec.size - font.blockHeight(lines.size)) / 2.0).roundToInt()
    lines.forEachIndexed { i, line ->
        drawLine(font, line, y0 + i * (font.height + 1), brightness)
    }
}

/** Une ligne centrée sur la hauteur [centerY] (et non sur son coin haut). */
fun Frame.drawCentered(font: Font, text: String, centerY: Int, brightness: Float = 1f) {
    drawLine(font, text, (centerY - font.height / 2.0).roundToInt(), brightness)
}
