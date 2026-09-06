package red.suns.haloglyph.core.matrix

/**
 * Police pixel à largeur variable.
 *
 * Un glyphe est un tableau de lignes de `'0'`/`'1'`, toutes de même longueur.
 * `' '` n'est pas un glyphe mais un séparateur de groupe (1 px), géré par le
 * tracé. `'` (apostrophe) sert de symbole prime pour les minutes.
 *
 * Contrainte i18n, reprise de GlyphLapse et à tenir pour tout ajout : les
 * capitales servant d'étiquettes d'unité sont dessinées **à la largeur des
 * chiffres** de leur police. Changer de langue ne change alors pas la largeur
 * d'une ligne, et ne rouvre donc pas la question du débordement hors du disque.
 */
class Font(val height: Int, val glyphs: Map<Char, Array<String>>) {

    /** Largeur d'un caractère, 0 s'il n'est pas dans la police. */
    fun charWidth(c: Char): Int = glyphs[c]?.get(0)?.length ?: 0

    /** Largeur d'une chaîne, séparateurs de 1 px inclus. */
    fun textWidth(s: String): Int {
        if (s.isEmpty()) return 0
        var w = 0
        for (c in s) w += if (c == ' ') 1 else charWidth(c) + 1
        return w - 1
    }

    /** Hauteur d'un bloc de [lines] lignes, interligne de 1 px inclus. */
    fun blockHeight(lines: Int): Int = lines * height + (lines - 1)
}

/**
 * Les trois polices de l'écosystème, identiques aux préviews web du portail.
 *
 * Elles vivent dans `core/matrix` et non dans un toy : le jour où un nouveau toy
 * écrit un nombre sur la matrice, il n'a rien à redessiner.
 */
object Fonts {

    /** 3×5 — plusieurs lignes de texte dans le disque, étiquettes, titres. */
    val F3 = Font(
        5,
        mapOf(
            '0' to arrayOf("111", "101", "101", "101", "111"),
            '1' to arrayOf("010", "110", "010", "010", "111"),
            '2' to arrayOf("111", "001", "111", "100", "111"),
            '3' to arrayOf("111", "001", "111", "001", "111"),
            '4' to arrayOf("101", "101", "111", "001", "001"),
            '5' to arrayOf("111", "100", "111", "001", "111"),
            '6' to arrayOf("111", "100", "111", "101", "111"),
            '7' to arrayOf("111", "001", "001", "010", "010"),
            '8' to arrayOf("111", "101", "111", "101", "111"),
            '9' to arrayOf("111", "101", "111", "001", "111"),
            'A' to arrayOf("010", "101", "111", "101", "101"),
            'J' to arrayOf("001", "001", "001", "101", "111"),
            'H' to arrayOf("101", "101", "111", "101", "101"),
            'S' to arrayOf("011", "100", "010", "001", "110"),
            'I' to arrayOf("111", "010", "010", "010", "111"),
            'M' to arrayOf("10001", "11011", "10101", "10001", "10001"),
            'N' to arrayOf("1001", "1101", "1011", "1001", "1001"),
            // Unités des autres langues, au dessin près de la préview web.
            'D' to arrayOf("110", "101", "101", "101", "110"),
            'G' to arrayOf("011", "100", "101", "101", "011"),
            'T' to arrayOf("111", "010", "010", "010", "010"),
            'Y' to arrayOf("101", "101", "010", "010", "010"),
            '-' to arrayOf("000", "000", "111", "000", "000"),
            '+' to arrayOf("000", "010", "111", "010", "000"),
            '\'' to arrayOf("1", "1", "0", "0", "0"),
        ),
    )

    /** 3×4 ultra-compacte — quand il faut cinq lignes dans le disque. */
    val F4 = Font(
        4,
        mapOf(
            '0' to arrayOf("111", "101", "101", "111"),
            '1' to arrayOf("110", "010", "010", "111"),
            '2' to arrayOf("111", "001", "010", "111"),
            '3' to arrayOf("111", "011", "001", "111"),
            '4' to arrayOf("101", "111", "001", "001"),
            '5' to arrayOf("111", "110", "001", "111"),
            '6' to arrayOf("100", "111", "101", "111"),
            '7' to arrayOf("111", "001", "010", "010"),
            '8' to arrayOf("111", "111", "101", "111"),
            '9' to arrayOf("111", "101", "111", "001"),
            'A' to arrayOf("010", "101", "111", "101"),
            'J' to arrayOf("001", "001", "101", "111"),
            'H' to arrayOf("101", "111", "101", "101"),
            'M' to arrayOf("10001", "11011", "10101", "10001"),
            'D' to arrayOf("110", "101", "101", "110"),
            'G' to arrayOf("011", "100", "101", "011"),
            'T' to arrayOf("111", "010", "010", "010"),
            'Y' to arrayOf("101", "101", "010", "010"),
            '\'' to arrayOf("1", "1", "0", "0"),
        ),
    )

    /** 5×7 — une ou deux lignes, format principal. */
    val F5 = Font(
        7,
        mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00110", "01000", "10000", "11111"),
            '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'J' to arrayOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
            'H' to arrayOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'M' to arrayOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'D' to arrayOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
            'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
            'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'Y' to arrayOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
            'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            '-' to arrayOf("000", "000", "000", "111", "000", "000", "000"),
            '\'' to arrayOf("1", "1", "1", "0", "0", "0", "0"),
        ),
    )
}
