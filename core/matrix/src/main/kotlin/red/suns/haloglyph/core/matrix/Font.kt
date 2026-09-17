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
 * Les polices de l'écosystème.
 *
 * [F3], [F4] et [F5] sont celles du portail, au dessin près de ses préviews web.
 * [R4] est née ici et n'existe que dans l'app — voir son en-tête.
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
            // Ni une unité ni un chiffre : les mots que Sono écrit dans le
            // hublot, `TAP` et `MIC`. Ils manquaient, et ça ne se voyait pas —
            // une lettre absente a une largeur nulle, donc `MIC` s'affichait
            // « MI » sans que rien ne le signale.
            'P' to arrayOf("111", "101", "111", "100", "100"),
            'C' to arrayOf("111", "100", "100", "100", "111"),
            // Les points cardinaux de Float. `N` et `S` étaient déjà là pour
            // d'autres raisons ; ces deux-là manquaient, et un caractère absent a
            // une largeur nulle — « NE » se serait affiché « N » sans que rien ne
            // le signale. `W` est large de cinq comme `M`, dont il est le miroir :
            // la règle des largeurs de chiffres ne vaut que pour les étiquettes
            // d'unité, qui doivent tenir sur une ligne quelle que soit la langue.
            'E' to arrayOf("111", "100", "110", "100", "111"),
            'W' to arrayOf("10001", "10001", "10101", "11011", "01010"),
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
            // `TAP` et `MIC`, les deux mots du repos d'un hublot — et c'est
            // maintenant la police de **tous** les repos du pack.
            //
            // Le `P` a une épaule arrondie et non un angle droit : `111/101/111`
            // donnait un rectangle plein en haut, qui se lisait comme un bloc et
            // non comme une lettre. Un pixel de moins au coin, et la boucle se
            // referme — c'est le même geste que sur le `C`, qui l'avait déjà.
            'P' to arrayOf("110", "101", "110", "100"),
            'I' to arrayOf("111", "010", "010", "111"),
            'C' to arrayOf("111", "100", "100", "111"),
            // Les points cardinaux de Float, une seconde fois : la boussole les
            // écrit **sous** le cap chiffré, donc dans la police courte, pour que
            // les degrés puissent occuper le centre du disque.
            'N' to arrayOf("1001", "1101", "1011", "1001"),
            'S' to arrayOf("011", "100", "001", "110"),
            'E' to arrayOf("111", "100", "110", "111"),
            'W' to arrayOf("10001", "10001", "10101", "01010"),
            '\'' to arrayOf("1", "1", "0", "0"),
        ),
    )

    /**
     * 4×5 arrondie — les chiffres de G-Forces et de la boussole de Float, et les
     * points cardinaux en bas de casse.
     *
     * ## Pourquoi une quatrième police
     *
     * Les trois autres échouaient chacune pour sa raison. La **5×7** est juste, et
     * trop grande : elle occupe sept lignes sur vingt-cinq et ne laisse la place
     * qu'à une valeur. Les **3×5** et **3×4** tiennent partout et dessinent des
     * chiffres qui sont des rectangles — `0` et `8` n'y diffèrent que d'une cellule,
     * ce qui ne suffit pas pour un cadran qu'on consulte d'un coup d'œil, en
     * voiture ou le téléphone à bout de bras.
     *
     * Quatre colonnes suffisent à donner de vraies rondeurs : un `0` ovale, un `6`
     * à boucle, un `9` fermé. Et quatre colonnes tiennent encore à **quatre valeurs
     * par cadran** — `9 + 1 + ... + 1 + 9` cellules sur vingt-cinq pour la ligne
     * médiane de G-Forces.
     *
     * ## Pas de virgule, et c'est voulu
     *
     * Elle coûterait deux colonnes par valeur — sa largeur plus un séparateur — et
     * la ligne médiane de G-Forces déborderait. Le renderer la dessine donc
     * lui-même, d'un seul pixel posé une ligne **sous** la ligne de base, dans la
     * gouttière qui sépare déjà les deux chiffres. Ça ne coûte aucune colonne, et
     * ça tombe exactement là où une virgule se pose.
     *
     * ## Les bas de casse
     *
     * Quatre lettres, `n e s w`, pour la boussole. Elles occupent la même boîte que
     * les chiffres et non une boîte raccourcie : à cette taille, un bas de casse
     * d'x-hauteur réduite devient une tache, et on ne lit plus la lettre mais sa
     * position. Le `w` prend cinq colonnes parce que deux vallées ne tiennent pas
     * sur quatre, quelle que soit la casse.
     */
    val R4 = Font(
        5,
        mapOf(
            '0' to arrayOf("0110", "1001", "1001", "1001", "0110"),
            '1' to arrayOf("0010", "0110", "0010", "0010", "0111"),
            '2' to arrayOf("0110", "1001", "0010", "0100", "1111"),
            '3' to arrayOf("1110", "0001", "0110", "0001", "1110"),
            '4' to arrayOf("0010", "0110", "1010", "1111", "0010"),
            '5' to arrayOf("1111", "1000", "1110", "0001", "1110"),
            '6' to arrayOf("0110", "1000", "1110", "1001", "0110"),
            '7' to arrayOf("1111", "0001", "0010", "0100", "0100"),
            '8' to arrayOf("0110", "1001", "0110", "1001", "0110"),
            '9' to arrayOf("0110", "1001", "0111", "0001", "0110"),
            'n' to arrayOf("0111", "1001", "1001", "1001", "1001"),
            'e' to arrayOf("0110", "1001", "1111", "1000", "0110"),
            's' to arrayOf("0111", "1000", "0110", "0001", "1110"),
            'w' to arrayOf("10001", "10001", "10101", "10101", "01010"),
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
            // `MIC`, que le toy écrit depuis toujours et n'affichait qu'à un
            // tiers : un caractère absent a une largeur nulle, donc il ne
            // dessine rien et ne décale même pas le suivant. Le symptôme était
            // un « M » seul au milieu de la matrice, et rien pour dire pourquoi.
            'I' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
            'C' to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            '-' to arrayOf("000", "000", "000", "111", "000", "000", "000"),
            '\'' to arrayOf("1", "1", "1", "0", "0", "0", "0"),
        ),
    )
}
