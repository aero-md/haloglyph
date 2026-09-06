package red.suns.haloglyph.core.matrix

/**
 * Table d'allocation des LEDs du Nothing Phone (3), reprise de
 * « Phone 3 Glyph Matrix LED allocation.svg » livré avec le GDK 1.1.
 *
 * Un `1` = une LED physique à cette coordonnée de la grille 25×25.
 * Répartition par ligne, de haut en bas :
 *
 * ```
 * 7, 11, 15, 17, 19, 21, 21, 23, 23,
 * 25, 25, 25, 25, 25, 25, 25,
 * 23, 23, 21, 21, 19, 17, 15, 11, 7      = 489
 * ```
 *
 * Cette table est *identique* au masque circulaire de rayon 12,5 centré sur
 * (12, 12) — vérifié ligne par ligne dans `MatrixSpecTest`. On garde la table
 * plutôt que le calcul : elle est la référence, le calcul n'en est qu'une
 * coïncidence heureuse.
 */
internal val PHONE3_ALLOCATION = arrayOf(
    "0000000001111111000000000",
    "0000000111111111110000000",
    "0000011111111111111100000",
    "0000111111111111111110000",
    "0001111111111111111111000",
    "0011111111111111111111100",
    "0011111111111111111111100",
    "0111111111111111111111110",
    "0111111111111111111111110",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "1111111111111111111111111",
    "0111111111111111111111110",
    "0111111111111111111111110",
    "0011111111111111111111100",
    "0011111111111111111111100",
    "0001111111111111111111000",
    "0000111111111111111110000",
    "0000011111111111111100000",
    "0000000111111111110000000",
    "0000000001111111000000000",
)
