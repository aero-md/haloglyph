package red.suns.haloglyph.gforce.engine

/**
 * Les deux faces du cadran.
 *
 * Un accéléromètre de bord répond à deux questions qui ne se posent pas au même
 * moment : **qu'est-ce que je subis maintenant** — qu'on regarde du coin de l'œil,
 * en roulant — et **qu'est-ce que j'ai pris**, qu'on lit à l'arrêt. Les afficher
 * ensemble sur vingt-cinq LEDs donnerait deux informations illisibles au lieu
 * d'une claire.
 *
 * L'appui long passe de l'une à l'autre, comme partout ailleurs dans le pack ; le
 * tap d'un hublot fait le même geste.
 */
enum class GForceMode {

    /** La bille et les graduations. Ce qu'on regarde en roulant. */
    VIF,

    /** Les quatre pics, en croix. Ce qu'on lit à l'arrêt. */
    PICS;

    val next: GForceMode get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = VIF

        /** Lecture tolérante d'une préférence : une clé inconnue retombe au défaut. */
        fun byKey(key: String?): GForceMode = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
