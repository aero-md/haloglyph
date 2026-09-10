package red.suns.haloglyph.core.glyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Les écrans Glyph du téléphone, et comment y aller vraiment.
 *
 * Il y en a deux, et les confondre était le défaut : la **liste des toys
 * activés** — ceux que le bouton fait défiler, dans l'ordre — et le
 * **gestionnaire**, qui montre tous les toys installés, permet de les activer et
 * de les réordonner. Un lien vers « les réglages Glyph » en général n'ouvre ni
 * l'un ni l'autre.
 *
 * ## Pourquoi on énumère au lieu de coder un nom en dur
 *
 * Nothing ne publie aucune action documentée pour ces écrans : `com.nothing.glyph.SETTINGS`
 * ne résout pas, et l'app retombait alors sur les réglages du système — d'où
 * « le bouton ne mène qu'aux paramètres du téléphone ». La seule chose vérifiée
 * est un nom de composant relevé dans une app tierce qui l'ouvre et fonctionne
 * ([MANAGER]). Coder les deux en dur, ce serait parier sur un nom jamais observé
 * et redonner un bouton mort à la première mise à jour de Nothing OS.
 *
 * On demande donc au système ce que le paquet expose réellement — la
 * déclaration `<queries>` de ce module le rend visible — et on ne montre que les
 * écrans qui existent sur **ce** téléphone. Un bouton absent vaut mieux qu'un
 * bouton qui ouvre autre chose.
 */
object GlyphSettings {

    /** Le paquet qui héberge les Glyph Toys. Déjà déclaré dans `<queries>`. */
    private const val PACKAGE = "com.nothing.thirdparty"

    /** Gestionnaire des toys — nom observé, pas deviné. */
    private const val MANAGER = "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"

    /** Le sous-paquet des toys de la matrice : tout ce qui nous intéresse est là. */
    private const val TOYS = ".matrix.toys."

    /**
     * Ce que ce téléphone-ci sait ouvrir. `null` = l'écran n'existe pas ici, donc
     * pas de bouton.
     */
    data class Screens(val active: String?, val manager: String?) {
        val any: Boolean get() = active != null || manager != null
    }

    fun screens(context: Context): Screens = pick(exportedActivities(context))

    /**
     * Ouvre un écran renvoyé par [screens].
     *
     * @return `false` si le lancement échoue — composant retiré entre-temps,
     * activité finalement non exportée. L'appelant décide quoi en faire ; ici on
     * ne fait pas semblant d'avoir réussi.
     */
    fun open(context: Context, activity: String): Boolean = runCatching {
        context.startActivity(Intent().setComponent(ComponentName(PACKAGE, activity)))
    }.isSuccess

    /**
     * Le tri, isolé du framework pour être testable.
     *
     * Le gestionnaire d'abord, par son nom exact quand il est là ; à défaut par
     * « Manager », parce que c'est le mot que Nothing utilise et qu'un
     * renommage complet reste plus probable qu'un renommage de ce mot-là. La
     * liste des toys actifs est l'autre écran de `.matrix.toys.` — celui qui
     * reste une fois le gestionnaire mis de côté, le moins enfoui d'abord :
     * l'entrée principale d'un paquet vit rarement trois sous-dossiers plus bas.
     */
    internal fun pick(activities: List<String>): Screens {
        val toys = activities.filter { it.contains(TOYS) }
        val manager = toys.firstOrNull { it == MANAGER }
            ?: toys.firstOrNull { it.simpleName().contains("Manager") }
        val active = toys
            .filter { it != manager && it.simpleName().contains("Toys") }
            .minByOrNull { it.count { c -> c == '.' } }
        return Screens(active = active, manager = manager)
    }

    private fun String.simpleName(): String = substringAfterLast('.')

    /**
     * Les activités exportées du paquet Glyph.
     *
     * `enabled` compte autant que `exported` : Nothing désactive des composants
     * selon le modèle, et un composant désactivé lève au lancement au lieu de
     * s'ouvrir.
     */
    private fun exportedActivities(context: Context): List<String> = runCatching {
        context.packageManager
            .getPackageInfo(PACKAGE, PackageManager.GET_ACTIVITIES)
            .activities
            ?.filter { it.exported && it.enabled }
            ?.map { it.name }
            .orEmpty()
    }.getOrDefault(emptyList())
}
