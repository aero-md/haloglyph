package red.suns.haloglyph.core.matrix

/**
 * L'apparence d'une Glyph Matrix **émulée**, en un seul endroit.
 *
 * La matrice physique n'a pas d'apparence à décider : elle allume ses LEDs, un
 * point c'est tout. Les deux autres surfaces, elles, la *dessinent* — l'aperçu
 * dans l'app et le widget d'écran d'accueil — et si elles ne la dessinent pas
 * pareil, la promesse du produit tombe : « même moteur, même rendu, trois
 * surfaces ».
 *
 * ## D'où viennent ces nombres
 *
 * De [GlyphPortal](https://glyph.suns.red), qui émule les mêmes matrices dans un
 * navigateur depuis plus longtemps que ce dépôt, et dont le rendu `sharp` est la
 * référence visuelle du pack. Les valeurs sont recopiées de son `thumb.ts` — pas
 * réinterprétées, pas « adaptées à Android » : deux réglages qui divergent d'un
 * cheveu donnent deux appareils différents, et c'est exactement ce qu'on cherche
 * à éviter en les écrivant ici plutôt que dans chaque surface.
 *
 * Le portail en a **deux**, et [LedStyle] les porte toutes les deux. Ce qui suit
 * décrit `sharp`, la référence ; `soft` est décrit sur l'énumération.
 *
 * Ce que le portail a établi et qui n'est pas négociable :
 *
 * - **une LED est un carré**, pas un disque. Des points ronds donnent une grille
 *   de perles qui ne ressemble à rien de ce qu'on voit au dos du téléphone ;
 * - **[DUTY] = 0,72** de côté occupé, donc un peu plus d'un quart de gouttière ;
 * - **[OFF_ARGB]** est une couleur pleine et non le blanc des LEDs à 5 %. Une
 *   LED éteinte n'est pas une LED faible : c'est du plastique gris sur du noir,
 *   et le mélange d'alpha lui donnait une teinte qui suivait celle des LEDs
 *   allumées ;
 * - **[FLOOR] = 0,25** : une LED à 1 % doit se lire comme allumée. La rampe est
 *   linéaire au-dessus de ce plancher parce que c'est le **halo** qui porte
 *   l'intensité, pas l'opacité du carré ;
 * - **[HALO]** : le débordement lumineux, proportionnel à la luminosité. C'est
 *   lui qui fait la différence entre une grille de rectangles et des diodes.
 *
 * Une correction gamma vivait ici — la consigne poussée à une LED est un rapport
 * cyclique, l'œil ne le lit pas linéairement. Elle disparaît parce que le couple
 * plancher + halo répond au même problème et mieux : la carcasse d'un dé, qui
 * était le cas d'école, sortait à 18 % d'opacité et sort maintenant à 39 %, avec
 * en plus un halo qui la décolle du fond.
 *
 * Les couleurs sont des entiers ARGB et non des types de plateforme : ce module
 * est du Kotlin pur, et c'est justement ce qui permet à Compose, à un `Canvas`
 * Android et au port TypeScript du portail de lire la même table.
 */
object MatrixLook {

    /** Côté d'une LED, rapporté au pas de la grille. */
    const val DUTY = 0.72f

    /** Blanc légèrement chaud d'une LED allumée — le blanc de la matrice, pas #FFFFFF. */
    const val LIT_ARGB: Int = 0xFFF2F2EF.toInt()

    /** Le disque derrière les LEDs. Presque noir, jamais tout à fait. */
    const val FIELD_ARGB: Int = 0xFF08080A.toInt()

    /** Une LED éteinte : le gris du plastique, opaque. */
    const val OFF_ARGB: Int = 0xFF1B1B20.toInt()

    /**
     * En dessous de cette consigne (sur 255), la LED est traitée comme éteinte.
     *
     * Garde-fou contre le bruit de quantification — une ou deux marches sur 255
     * qu'aucun renderer ne veut vraiment allumer, comme le creux sombre du
     * sablier de Lapse.
     */
    const val MIN_VISIBLE = 5

    /** Opacité plancher d'une LED allumée. Voir l'en-tête : à 1 % elle est allumée. */
    const val FLOOR = 0.25f

    /**
     * Rayon du halo à pleine luminosité, en fraction du pas de la grille.
     *
     * Le portail en a deux — 0,55 pour la grande préview qu'on regarde de près,
     * 0,45 pour les vignettes de son sommaire. C'est la seconde qui vaut ici :
     * les deux surfaces de Haloglyph sont des vignettes, un aperçu de 72 dp et un
     * widget de deux cellules, et à cette taille un halo trop large se lit comme
     * du flou plutôt que comme de la lumière.
     */
    const val HALO = 0.45f

    /** Opacité du halo à pleine luminosité. */
    const val HALO_ALPHA = 0.8f

    /**
     * Cerne du hublot, en largeurs de LED : ce qui sépare la dernière LED de la
     * découpe.
     *
     * Décoratif — le disque émulé est *dessiné*, son bord n'existe que parce
     * qu'on le peint — et donc réglé à l'œil, comme celui des vignettes du
     * portail. Un cerne calé sur la cote réelle du hublot (0,75) disparaît à
     * cette taille, et le disque ne se lit plus comme un objet mais comme un
     * carré de LEDs aux coins rognés.
     */
    const val RING = 1.6f

    // ---------- le rendu « soft » ----------

    /**
     * Le champ, en soft : **plus clair** que les LEDs éteintes.
     *
     * Le contraste s'inverse, et c'est le principe du style : sans halo pour
     * porter la lumière, c'est le creux sombre des LEDs éteintes qui donne sa
     * trame au disque.
     */
    const val SOFT_FIELD_ARGB: Int = 0xFF131316.toInt()

    /** Une LED éteinte, en soft : le trou noir de la grille. */
    const val SOFT_OFF_ARGB: Int = 0xFF08080A.toInt()

    /**
     * Plancher d'opacité en soft. Presque rien, là où sharp part de 0,25.
     *
     * Le plancher haut de sharp existe parce que le halo prend le relais pour
     * dire l'intensité ; sans halo, le même plancher écraserait tout le bas de la
     * plage sur un seul gris. La rampe redevient donc quasi linéaire, et c'est
     * exactement ce que ce style vend : les nuances se lisent.
     */
    const val SOFT_FLOOR = 0.08f

    /**
     * Arrondi des coins en soft, en part du côté de la LED.
     *
     * Il casse le carré franc, il n'en fait pas une pastille : le portail a
     * essayé 0,24, où les congés mangeaient un quart du côté et la trame se
     * lisait comme une grille de galets.
     */
    const val SOFT_CORNER = 0.18f

    /**
     * Les deux façons de dessiner la même trame.
     *
     * - [SHARP] — l'appareil, émulé au plus près : LED carrée, halo
     *   proportionnel, plancher haut. C'est ce qu'on voit au dos d'un Phone (3),
     *   et c'est le défaut partout.
     * - [SOFT] — la trame telle qu'un écran l'affiche : coins adoucis, **aucun
     *   halo**, contraste inversé, rampe quasi linéaire. Les nuances y sont
     *   lisibles LED par LED, ce que le halo de sharp noie ; en échange ce n'est
     *   plus ce que rend l'appareil, et ça ne prétend pas l'être.
     *
     * Les deux viennent du portail, où elles portent déjà ces noms. Ce n'est donc
     * pas un réglage inventé pour l'occasion mais le second rendu d'une paire qui
     * existe depuis le début, et qu'un widget posé sur un fond d'écran clair a de
     * bonnes raisons de préférer.
     *
     * L'énumération **porte la table** au lieu de la laisser se disperser en
     * `if (style == SOFT)` dans chaque peintre : une surface demande la couleur du
     * champ, pas le style qu'elle applique.
     */
    enum class LedStyle(
        /** Le disque derrière les LEDs. */
        val fieldArgb: Int,
        /** Une LED éteinte. */
        val offArgb: Int,
        /** Opacité plancher d'une LED allumée. */
        val floor: Float,
        /** Rayon du halo, en fraction du pas. `0` = pas de halo du tout. */
        val halo: Float,
        /** Arrondi des coins, en part du côté. `0` = carré franc. */
        val corner: Float,
    ) {
        SHARP(FIELD_ARGB, OFF_ARGB, FLOOR, HALO, 0f),
        SOFT(SOFT_FIELD_ARGB, SOFT_OFF_ARGB, SOFT_FLOOR, 0f, SOFT_CORNER),
        ;

        /** Opacité **affichée** d'une consigne 0..255 : plancher, puis linéaire. */
        fun alphaOf(brightness: Int): Float {
            if (brightness <= MIN_VISIBLE) return 0f
            val duty = brightness.coerceAtMost(255) / 255f
            return floor + (1f - floor) * duty
        }
    }

    /** La rampe du rendu de référence. Voir [LedStyle.alphaOf]. */
    fun alphaOf(brightness: Int): Float = LedStyle.SHARP.alphaOf(brightness)

    /** Décalage d'une LED dans sa cellule, pour un pas de [pitch]. */
    fun inset(pitch: Float): Float = pitch * (1f - DUTY) / 2f

    /** Côté d'une LED, pour un pas de [pitch]. */
    fun ledSize(pitch: Float): Float = pitch * DUTY
}
