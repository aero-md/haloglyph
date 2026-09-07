package red.suns.haloglyph.core.matrix

import kotlin.math.pow

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
     * En dessous de cette luminosité (sur 255), on ne peint pas la LED allumée.
     *
     * Le motif d'origine — « elle serait moins visible que l'état éteint » — a
     * disparu avec [perceived] : une consigne de 1/255 se perçoit déjà à 8 %,
     * au-dessus de [OFF_ALPHA]. Ce qui reste est un garde-fou contre le bruit de
     * quantification, une ou deux marches sur 255 qu'aucun renderer ne veut
     * vraiment allumer — le creux sombre du sablier, par exemple.
     */
    const val MIN_VISIBLE = 5

    /**
     * Gamma de l'œil. Le nombre usuel des écrans, et il vaut ici pour la même
     * raison : c'est la réponse d'un observateur, pas celle d'un afficheur.
     */
    const val GAMMA = 2.2f

    /**
     * L'opacité **perçue** d'une consigne de LED, de 0 à 255.
     *
     * C'est la conversion qui manquait, et son absence faisait mentir la
     * promesse du produit. La valeur poussée à une LED est un **rapport
     * cyclique** ; l'œil, lui, ne le lit pas linéairement. Une consigne de 0,18
     * se voit à 46 %, une de 0,03 à 20 %. Les renderers raisonnent donc en
     * rapport cyclique — c'est ce que le matériel attend — et les deux surfaces
     * émulées, qui composent en alpha linéaire, affichaient jusqu'ici la
     * *consigne* au lieu du *résultat*.
     *
     * Concrètement : la carcasse d'un dé sortait à 18 % d'opacité au lieu de
     * 46 %, et le lavis de ses faces à 3 % — sous le seuil de perception, donc
     * un dé en fil de fer flottant sur rien. Le sable du sablier de Lapse
     * souffrait du même écart, en plus discret. Sur la matrice, les deux sont
     * parfaitement lisibles : c'était l'émulation qui était fausse.
     *
     * Vit ici et pas dans chaque surface, pour la raison qui a fait exister ce
     * fichier : l'aperçu Compose et la bitmap du widget doivent afficher le même
     * dessin, et ils ne le feront que s'ils lisent la même table.
     */
    fun perceived(brightness: Int): Float {
        if (brightness <= 0) return 0f
        val duty = (brightness.coerceAtMost(255) / 255f)
        return duty.pow(1f / GAMMA)
    }

    /** Décalage d'une LED dans sa cellule, pour un pas de [pitch]. */
    fun inset(pitch: Float): Float = pitch * (1f - LED_RATIO) / 2f

    /** Côté d'une LED, pour un pas de [pitch]. */
    fun ledSize(pitch: Float): Float = pitch * LED_RATIO
}
