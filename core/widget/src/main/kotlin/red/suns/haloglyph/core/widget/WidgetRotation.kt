package red.suns.haloglyph.core.widget

/**
 * La rotation d'un hublot, sans rien autour.
 *
 * Trois règles, et elles décident de ce que montre un widget après un double
 * tap. Elles vivent ici plutôt que dans [WidgetConfig] pour la même raison que
 * les moteurs des toys vivent hors de leurs services : ce sont des fonctions de
 * leurs arguments, donc elles se testent, et le seul moyen de les tester dans
 * [WidgetConfig] serait de monter un `Context`.
 *
 * [WidgetConfig] garde ce qui touche au stockage — les clés, le fichier, le fait
 * qu'une absence vaille défaut.
 */
internal object WidgetRotation {

    /** Sépare deux identifiants de toy dans la rotation enregistrée. */
    const val SEP = ","

    /**
     * Ce qui a été enregistré, confronté à ce qui existe.
     *
     * [all] donne l'ordre et fait autorité sur ce qui existe : un identifiant
     * enregistré mais absent — un toy retiré de l'app — disparaît sans que
     * personne ait à nettoyer le fichier. L'ordre reste celui du catalogue et
     * jamais celui de l'enregistrement, pour que le double tap suive toujours la
     * même suite quel que soit l'ordre dans lequel on a coché les cases.
     *
     * @param saved la valeur enregistrée, ou `null` si on n'y a jamais touché.
     * `null` vaut **tout** : c'est ce qui fait qu'un toy ajouté par une mise à
     * jour apparaît dans les hublots déjà posés. Personne n'avait dit qu'il n'en
     * voulait pas.
     */
    fun decode(saved: String?, all: List<String>): List<String> {
        if (saved == null) return all
        val kept = saved.split(SEP).toSet()
        // Jamais vide quand il existe des toys : un hublot sans rien à montrer
        // n'est pas un état qu'on puisse afficher, et le seul chemin qui y mène
        // est un fichier écrit par une version qui n'est plus là.
        return all.filter { it in kept }.ifEmpty { all }
    }

    fun encode(ids: Collection<String>): String = ids.joinToString(SEP)

    /**
     * Le toy réellement affiché, celui qui est enregistré ne faisant pas foi.
     *
     * Un toy sorti de la rotation pendant qu'il était affiché ne peut pas rester
     * à l'écran ; le premier de la rotation est le repli naturel.
     */
    fun resolve(saved: String?, rotation: List<String>): String? = when {
        rotation.isEmpty() -> null
        saved in rotation -> saved
        else -> rotation.first()
    }

    /**
     * Le suivant, cycliquement. C'est ce que fait le double tap.
     *
     * Une rotation d'un seul toy renvoie ce toy : le double tap ne fait alors
     * rien, ce qui est exactement ce qu'on a demandé en ne gardant qu'un toy.
     */
    fun next(current: String?, rotation: List<String>): String? {
        if (rotation.isEmpty()) return null
        val here = resolve(current, rotation) ?: return null
        return rotation[(rotation.indexOf(here) + 1) % rotation.size]
    }

    /**
     * La variante active d'un toy, celle qui est enregistrée ne faisant pas foi.
     *
     * Même règle que [resolve], et pour la même raison : ce qui est écrit vient
     * peut-être d'une version qui proposait autre chose. Un `d8` relu sur un jeu
     * qui n'en a plus ne doit ni lever ni afficher un dé que personne ne peut
     * choisir — il retombe sur le repli du toy, et sur la première variante si
     * même ce repli a disparu.
     *
     * @param offered les clés que le toy propose **aujourd'hui**, dans l'ordre.
     * @param fallback ce que vaut la variante tant que ce hublot n'a rien choisi.
     */
    fun variant(saved: String?, offered: List<String>, fallback: String?): String? = when {
        offered.isEmpty() -> null
        saved in offered -> saved
        fallback in offered -> fallback
        else -> offered.first()
    }
}
