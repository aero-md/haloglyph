package red.suns.haloglyph.sono.render

import red.suns.haloglyph.core.matrix.Fonts
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.drawLine

/**
 * Le repos de Sono **dans un hublot d'écran d'accueil**, et là seulement.
 *
 * ## Pourquoi il s'écarte du toy
 *
 * Les deux surfaces d'un toy montrent la même chose, c'est la règle du pack — et
 * c'est une règle qui suppose que les deux soient dans la même situation. Ici
 * elles ne le sont pas. Sur la matrice, le repos de Sono dit « pas de mesure » et
 * n'a rien d'autre à dire : on y arrive en retournant le téléphone, il n'y a
 * aucun geste à suggérer. Dans un hublot, le repos est ce qu'on voit **99 % du
 * temps**, et il lui manque la seule chose qui compte — comment le réveiller.
 *
 * ```
 *        TAP        ← le geste, en haut, comme partout ailleurs
 *   ─╫╫┼╫─┼╫╫┼─     ← l'onde : la marque du toy
 * ```
 *
 * ## `MIC` est parti, et le mot a changé de place
 *
 * Il y a eu trois étages : `MIC` en haut, l'onde au milieu, `TAP` en bas. Deux
 * mots pour un hublot de vingt-cinq LEDs, dont un qui ne disait rien que le toy
 * ne dise déjà — ce qui s'ouvre au tap se voit dès la première image de mesure,
 * et l'autorisation manquante a son propre affichage.
 *
 * Ce qui reste est la grammaire commune à tous les hublots du pack : le **mot**
 * en haut, la **marque** en dessous, même police 3×4, même ligne. Voir
 * `FloatRenderer.renderIdle` et `GForceRenderer.renderIdle` — ce qui distingue
 * les hublots est leur marque, pas leur façon d'écrire « tape-moi ».
 *
 * ## L'onde est un **dessin**, et c'est un renoncement assumé
 *
 * Elle a été rendue par [SonoRenderer], le vrai, à qui on poussait un profil de
 * niveaux inventé : même axe, même symétrie, même conversion niveau → pixels que
 * sur la matrice. C'était plus honnête d'un cheveu et ça coûtait un renderer
 * construit pour une image figée, plus une histoire d'onde remplie une fois et
 * jamais relue.
 *
 * Elle est maintenant posée cellule par cellule, dans [WAVE]. Ce n'est plus une
 * onde rendue, c'est une **vignette d'identité** — au même titre que la rose de
 * Float ou le cadran de G-Forces, qui n'ont jamais prétendu mesurer quoi que ce
 * soit. Un repos qui ferait croire à une mesure serait pire qu'un repos vide.
 */
object SonoWidgetIdle {

    /** Le geste, et le seul mot qui reste. */
    private const val WORD = "TAP"

    /** Première ligne du mot. Le disque est étroit au-dessus ; en deçà, ça rogne. */
    private const val WORD_Y = 4

    /** Première ligne de la marque : une ligne de gouttière sous le mot. */
    private const val WAVE_Y = 6

    /**
     * L'onde, treize lignes sur vingt-cinq, axe compris.
     *
     * L'axe est **plein sur toute la largeur** du disque — c'est lui qui fait
     * l'onde, sans lui il ne resterait que des barres. Elle est symétrique
     * haut/bas de part et d'autre de l'axe : six lignes au-dessus, six en
     * dessous, miroir exact. Les deux crêtes — colonnes 3 et 19 — ne montent
     * pas à la même hauteur, ce qui la sauve d'avoir l'air d'un pochoir, mais
     * chacune est elle-même symétrique.
     */
    private val WAVE = arrayOf(
        "0001000000000000000000000",
        "0001000000000000000100000",
        "0001100000000000000100000",
        "0001100000100000001100010",
        "0001110000100000001100110",
        "1011111000110001011111110",
        "1111111111111111111111111",
        "1011111000110001011111110",
        "0001110000100000001100110",
        "0001100000100000001100010",
        "0001100000000000000100000",
        "0001000000000000000100000",
        "0001000000000000000000000",
    )

    /**
     * Le repos, sur la frame donnée.
     *
     * Aucune géométrie en paramètre : [WAVE] est un dessin de vingt-cinq colonnes,
     * pas une figure calculée, et la frame porte déjà son masque. Le jour où une
     * autre matrice entre dans le périmètre, c'est le dessin qu'il faudra refaire,
     * pas l'appel.
     */
    fun render(frame: Frame) {
        frame.drawLine(Fonts.F4, WORD, WORD_Y, 1f)
        for (row in WAVE.indices) {
            val line = WAVE[row]
            for (col in line.indices) {
                if (line[col] == '1') frame.set(col, WAVE_Y + row, 1f)
            }
        }
    }
}
