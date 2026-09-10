package red.suns.haloglyph.core.glyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Les deux écrans Glyph du téléphone, et comment y aller vraiment.
 *
 * Les confondre était le défaut. Il y a la **liste des toys actifs** — Glyph
 * Interface, ceux que le bouton fait défiler — et le **gestionnaire**, qui
 * montre tous les toys installés, les active et les réordonne. Un lien vers
 * « les réglages Glyph » en général n'ouvre ni l'un ni l'autre : c'est
 * exactement ce que faisait le hub, qui visait `com.nothing.glyph.SETTINGS`,
 * action qui n'existe pas, et retombait chaque fois sur les réglages système.
 *
 * ## Les quatre écrans, et les deux qui nous intéressent
 *
 * Relevé sur un Phone (3), Nothing OS 4, manifeste de `NtThirdParty.apk` à
 * l'appui :
 *
 * | Écran | Activité | Exportée | Filtre |
 * |---|---|---|---|
 * | Glyph Interface | `com.android.settings/…NtSettings$GlyphsSettingsActivity` | oui | `android.settings.ACTION_GLYPHS_SETTINGS` |
 * | Glyph Toys | `…matrix.toys.preview.ToysPreviewActivity` | oui | `com.nothing.glyph.TOYS_MANAGER` |
 * | **Gérer les jeux Glyph** | `…matrix.toys.manager.ToysManagerActivity` | oui | **aucun** |
 * | Paramètres des jeux Glyph | `…matrix.toys.settings.ToyTimeoutSettingsActivity` | **non** | aucun |
 *
 * Le hub mène au premier et au troisième : voir ses toys actifs, et aller les
 * ranger. Le deuxième n'est qu'une vitrine, et le quatrième n'est pas exporté —
 * il n'est pas à nous de le lancer.
 *
 * ## Action ou composant, et dans quel ordre
 *
 * Pas de règle générale ici, une décision par écran. Glyph Interface a une
 * action publique : on la prend, et le nom de classe ne sert que de repli si
 * elle disparaît — c'est le même écran par deux chemins.
 *
 * « Gérer les jeux Glyph » n'a **aucun filtre d'intent** : le composant explicite
 * est la seule façon d'y aller. C'est pour ça qu'on ne le trouve pas en
 * interrogeant les actions, et qu'un `dumpsys` ne le montre pas — la table de
 * résolution ne liste que ce qui a un filtre. L'action `TOYS_MANAGER` reste
 * derrière, mais comme **repli dégradé** : elle ouvre la vitrine, pas le
 * gestionnaire. Un écran voisin quand le bon a disparu, pas un synonyme.
 *
 * Dans tous les cas on ne montre un bouton que s'il ouvrira quelque chose : un
 * bouton absent vaut mieux qu'un bouton qui ouvre autre chose.
 */
object GlyphSettings {

    /** Une façon d'atteindre un écran. */
    sealed interface Target {
        /** Contrat public : ce qu'on tente en premier. */
        data class Action(val action: String) : Target

        /** Classe précise : repli pour les firmwares qui n'ont pas l'action. */
        data class Component(val pkg: String, val cls: String) : Target
    }

    /** Ce que **ce** téléphone sait ouvrir. `null` = pas de bouton. */
    data class Screens(val active: Target?, val manager: Target?) {
        val any: Boolean get() = active != null || manager != null
    }

    /** Glyph Interface : les toys actifs, dans l'ordre où le bouton les fait défiler. */
    private val ACTIVE = listOf(
        Target.Action("android.settings.ACTION_GLYPHS_SETTINGS"),
        Target.Component(
            "com.android.settings",
            "com.nothing.settings.NtSettings\$GlyphsSettingsActivity",
        ),
    )

    /** « Gérer les jeux Glyph » : tous les toys installés, activation et ordre. */
    private val MANAGER = listOf(
        // Exportée mais sans filtre : le composant explicite est le seul chemin.
        Target.Component(
            "com.nothing.thirdparty",
            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
        ),
        // Repli dégradé, pas un synonyme : la vitrine Glyph Toys, faute du
        // gestionnaire. Mieux que rien si Nothing renomme la classe.
        Target.Action("com.nothing.glyph.TOYS_MANAGER"),
    )

    fun screens(context: Context): Screens {
        val resolves: (Target) -> Boolean = { context.resolves(it) }
        return Screens(
            active = choose(ACTIVE, resolves),
            manager = choose(MANAGER, resolves),
        )
    }

    /**
     * Ouvre un écran renvoyé par [screens].
     *
     * @return `false` si le lancement échoue — composant désactivé entre la
     * résolution et le clic, activité finalement non exportée. L'appelant décide
     * quoi en faire ; on ne fait pas semblant d'avoir réussi.
     */
    fun open(context: Context, target: Target): Boolean =
        runCatching { context.startActivity(intentFor(target)) }.isSuccess

    /**
     * Le premier candidat que le téléphone sait ouvrir, dans l'ordre donné.
     *
     * Isolé du framework pour être testable. L'ordre porte tout le sens et se
     * décide écran par écran, à la déclaration : le meilleur chemin d'abord,
     * les replis ensuite — et pour le gestionnaire le dernier repli ouvre un
     * écran voisin, ce qui n'est acceptable que parce qu'il est dernier.
     */
    internal fun choose(candidates: List<Target>, resolves: (Target) -> Boolean): Target? =
        candidates.firstOrNull(resolves)

    internal fun intentFor(target: Target): Intent = when (target) {
        is Target.Action -> Intent(target.action)
        is Target.Component -> Intent().setComponent(ComponentName(target.pkg, target.cls))
    }

    /**
     * `resolveActivity` et non un `startActivity` optimiste : le hub doit savoir
     * **avant** d'afficher le bouton. Il répond aussi pour un intent explicite,
     * ce dont dépend « Gérer les jeux Glyph », qui n'a pas de filtre.
     *
     * Côté visibilité des paquets (Android 11+), le `<queries>` de ce module
     * déclare les actions et les deux paquets. Sur le Phone (3) les deux sont de
     * toute façon `forceQueryable`, mais on ne fait pas reposer une décision
     * d'affichage sur une propriété du firmware.
     */
    private fun Context.resolves(target: Target): Boolean = runCatching {
        packageManager.resolveActivity(intentFor(target), 0) != null
    }.getOrDefault(false)
}
