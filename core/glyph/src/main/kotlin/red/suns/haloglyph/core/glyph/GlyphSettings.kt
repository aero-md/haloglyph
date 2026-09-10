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
 * ## Ce que le téléphone expose réellement
 *
 * Relevé sur un Phone (3), Nothing OS 4 — les deux actions portent
 * `category.DEFAULT`, donc elles se résolvent normalement :
 *
 * | Écran | Action | Cible |
 * |---|---|---|
 * | Toys actifs | `android.settings.ACTION_GLYPHS_SETTINGS` | `com.android.settings/…NtSettings$GlyphsSettingsActivity` |
 * | Gestionnaire | `com.nothing.glyph.TOYS_MANAGER` | `com.nothing.thirdparty/.matrix.toys.preview.ToysPreviewActivity` |
 *
 * ## Pourquoi une action d'abord, un composant ensuite
 *
 * `ToysManagerActivity`, le composant qu'une app tierce ouvre en dur, **n'existe
 * pas sur ce firmware** — le gestionnaire y vit sous `ToysPreviewActivity`. Un
 * nom de classe est un détail d'implémentation de Nothing et il a déjà bougé ;
 * l'action est un contrat, elle a survécu au déménagement. On tente donc
 * l'action, puis les composants observés ailleurs pour les firmwares plus
 * anciens, et on ne montre un bouton que s'il ouvrira quelque chose. Un bouton
 * absent vaut mieux qu'un bouton qui ouvre autre chose.
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

    /** Le gestionnaire : tous les toys installés, activation et ordre. */
    private val MANAGER = listOf(
        Target.Action("com.nothing.glyph.TOYS_MANAGER"),
        // Firmwares où le gestionnaire n'a pas encore déménagé sous `preview`.
        Target.Component(
            "com.nothing.thirdparty",
            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
        ),
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
     * Isolé du framework pour être testable : c'est ici que se joue « action
     * d'abord, composant ensuite », et c'est la règle qu'on ne veut pas voir
     * s'inverser à la faveur d'une refonte.
     */
    internal fun choose(candidates: List<Target>, resolves: (Target) -> Boolean): Target? =
        candidates.firstOrNull(resolves)

    internal fun intentFor(target: Target): Intent = when (target) {
        is Target.Action -> Intent(target.action)
        is Target.Component -> Intent().setComponent(ComponentName(target.pkg, target.cls))
    }

    /**
     * `resolveActivity` et non un `startActivity` optimiste : le hub doit savoir
     * **avant** d'afficher le bouton. La visibilité des paquets (Android 11+) est
     * couverte par le `<queries>` de ce module, qui déclare les deux actions.
     */
    private fun Context.resolves(target: Target): Boolean = runCatching {
        packageManager.resolveActivity(intentFor(target), 0) != null
    }.getOrDefault(false)
}
