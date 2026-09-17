package red.suns.haloglyph.sono.engine

/**
 * Les trois façons de regarder la même mesure.
 *
 * Un seul toy, trois modes, l'appui long passe au suivant — comme Dice change de
 * solide. Sonoglyph en faisait **deux toys séparés** dans Glyph Interface ; les
 * réunir tient au fait qu'ils partagent tout ce qui coûte (la capture, la
 * pondération, la FFT) et ne diffèrent que par le dernier étage. Deux entrées
 * dans la liste du système pour un micro qu'on n'ouvre qu'une fois, ça ne se
 * défendait pas.
 *
 * L'ordre est celui de la rotation, et il n'est pas indifférent : on part de ce
 * qui se regarde ([SPECTRE]), on passe par ce qui se lit ([AIGUILLE]), on finit
 * par ce qui défile ([ONDE]).
 */
enum class SonoMode {
    /** Barres symétriques autour de l'axe médian, une par bande de fréquence. */
    SPECTRE,

    /** VU-mètre à aiguille, niveau chiffré en dessous. */
    AIGUILLE,

    /**
     * La forme d'onde qui défile de droite à gauche.
     *
     * Le même dessin que [SPECTRE] — des barres symétriques autour de l'axe —
     * mais l'abscisse y est le **temps** et non la fréquence : la colonne de
     * droite est l'instant présent, chaque colonne glisse d'un cran vers la
     * gauche, et ce qui sort par le bord a une seconde et demie.
     */
    ONDE;

    val next: SonoMode get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = SPECTRE

        /** Lecture tolérante d'une préférence : une clé inconnue retombe au défaut. */
        fun byKey(key: String?): SonoMode =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
