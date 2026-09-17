package red.suns.haloglyph.gforce.engine

/**
 * L'échelle du cadran, et **pourquoi elle est linéaire**.
 *
 * Float dépense toute sa dynamique près de zéro, parce qu'un niveau sert à
 * distinguer un dixième de degré d'un autre et que le reste sature. Ici c'est
 * l'inverse : **toute la plage est intéressante**. Un demi-g de freinage se
 * distingue d'un g comme 0,1 se distingue de 0,2, et il n'y a aucune zone où l'on
 * regarderait de plus près. Une échelle logarithmique écraserait précisément la
 * partie qu'on est venu voir.
 *
 * ## Les graduations sont les deux nombres qu'un conducteur connaît
 *
 * Le rayon est coupé en trois parts égales, donc les repères tombent à
 * [FULL_SCALE] / 3 et 2 × [FULL_SCALE] / 3. Ce n'est pas la pleine échelle qui a
 * été choisie puis divisée, c'est l'inverse : on veut des repères à **0,5 g et
 * 1,0 g**, et la pleine échelle en découle.
 *
 * Ces deux-là valent d'être marqués : un demi-g, c'est un freinage franc en ville
 * ou une bretelle prise vite ; un g, c'est la limite d'adhérence d'un pneu de
 * route sur bitume sec. Au-delà on ne mesure plus, on raconte — d'où la bille qui
 * sort du champ plutôt qu'un cadran qui s'étire.
 *
 * Il n'y a donc **pas de réglage de portée**, contrairement à Float. Une portée
 * n'a de sens que si le choix change ce qu'on regarde ; ici il ne changerait que
 * l'endroit où tombent deux traits qui, eux, ont une valeur.
 */
object GForceScale {

    /** Le g qui colle la bille au cerne. Voir l'en-tête : c'est 3 × [TICK]. */
    const val FULL_SCALE = 1.5f

    /** L'écart entre deux graduations, en g. Le premier repère est à 0,5 g. */
    const val TICK = FULL_SCALE / 3f

    /** Nombre de graduations entre le centre et le bord, sur chacun des quatre côtés. */
    const val TICKS = 2

    /**
     * Un pic, écrit comme le cadran l'écrit : deux chiffres, dixièmes compris.
     *
     * Écrêté à 9,9 g, ce qui n'est pas une précaution d'affichage mais une borne
     * physique — au-delà, personne ne lit plus son téléphone.
     */
    fun format(g: Float): String {
        val tenths = Math.round(g * 10f).coerceIn(0, 99)
        return "${tenths / 10}${tenths % 10}"
    }
}
