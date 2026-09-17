package red.suns.haloglyph.float.engine

/**
 * Les deux instruments du toy.
 *
 * Un seul toy et non deux entrées dans Glyph Interface, pour la raison qui avait
 * déjà réuni les modes de Sono : les deux lisent la **pose du téléphone**, ils
 * partagent le filtre, le disque et la moitié du dessin, et ne diffèrent que par
 * ce qu'ils vont chercher dans l'orientation. Deux lignes dans la liste du
 * système pour un seul capteur ne se défendent pas.
 *
 * L'appui long passe au suivant, comme partout ailleurs dans le pack.
 */
enum class FloatMode {

    /** La bulle. Voir [FloatScale] pour ce qui la sépare d'un niveau ordinaire. */
    NIVEAU,

    /** La rose des vents, et le cap en chiffres au centre. */
    BOUSSOLE;

    val next: FloatMode get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = NIVEAU

        /** Lecture tolérante d'une préférence : une clé inconnue retombe au défaut. */
        fun byKey(key: String?): FloatMode = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
