package red.suns.haloglyph.core.matrix

/**
 * L'apparence d'une Glyph Matrix **émulée**, en un seul endroit.
 *
 * La matrice physique n'a pas d'apparence à décider : elle allume ses LEDs, un
 * point c'est tout. Les deux autres surfaces, elles, la *dessinent* — l'aperçu
 * dans les réglages et le widget d'écran d'accueil — et si elles ne la
 * dessinent pas pareil, la promesse du produit tombe : « même moteur, même
 * rendu, trois surfaces ».
 *
 * Ces valeurs sont celles de l'aperçu de GlyphLapse, mesurées sur le rendu qui
 * tourne déjà. Elles ne sont pas décoratives :
 *
 * - **une LED est un carré**, pas un disque. Les LEDs de la matrice sont des
 *   pavés carrés ; des points ronds donnent une grille de perles qui ne
 *   ressemble à rien de ce qu'on voit au dos du téléphone ;
 * - **[LED_RATIO] = 0,66** laisse un tiers de gouttière. Plus serré, la grille
 *   se referme en aplat ; plus lâche, elle se disperse ;
 * - **[OFF_ALPHA] = 5 %** : une LED éteinte reste devinable. À zéro, une matrice
 *   au repos a l'air cassée ;
 * - le champ est un **disque** [FIELD_ARGB], pas un carré. C'est lui qui donne
 *   la silhouette, avant même qu'une LED s'allume.
 *
 * Les couleurs sont des entiers ARGB et non des types de plateforme : ce module
 * est du Kotlin pur, et c'est justement ce qui permet à Compose et à un `Canvas`
 * Android de lire la même table.
 */
object MatrixLook {

    /** Côté d'une LED, rapporté au pas de la grille. */
    const val LED_RATIO = 0.66f

    /** Opacité d'une LED éteinte, sur le champ. */
    const val OFF_ALPHA = 0.05f

    /** Blanc légèrement chaud d'une LED allumée — le blanc de la matrice, pas #FFFFFF. */
    const val LIT_ARGB: Int = 0xFFF8F8F4.toInt()

    /** Le disque derrière les LEDs. Presque noir, jamais tout à fait. */
    const val FIELD_ARGB: Int = 0xFF0B0B0D.toInt()

    /**
     * En dessous de cette luminosité (sur 255), on ne peint pas la LED allumée :
     * elle serait moins visible que l'état éteint, et l'aperçu scintillerait
     * sur les valeurs de fond du sablier.
     */
    const val MIN_VISIBLE = 5

    /** Décalage d'une LED dans sa cellule, pour un pas de [pitch]. */
    fun inset(pitch: Float): Float = pitch * (1f - LED_RATIO) / 2f

    /** Côté d'une LED, pour un pas de [pitch]. */
    fun ledSize(pitch: Float): Float = pitch * LED_RATIO
}
