package red.suns.haloglyph

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * La langue de l'application — **une valeur pour tout le pack**, matrice comprise.
 *
 * Ce réglage vivait dans l'écran de Lapse, hérité de l'époque où Lapse *était*
 * l'app. Dans un pack, trois toys proposeraient trois fois le même sélecteur pour
 * une seule valeur : il remonte donc d'un étage (PRODUIT §5.4). La règle de tri
 * est là et pas ailleurs — un réglage qui a la même valeur pour tous les toys est
 * global, un réglage qui n'a de sens que pour un toy reste chez lui.
 *
 * Rien n'est stocké ici. Le système retient le choix hors de nos préférences, le
 * montre dans Paramètres → Applications → Langue, et **reconfigure le processus**
 * — donc aussi les services de toy, qui relisent leurs étiquettes de matrice à
 * chaque frame (TECHNIQUE §11). Une préférence maison aurait demandé de tout
 * recâbler à la main et n'aurait rien dit au système.
 */
object AppLanguage {

    /**
     * Les langues proposées, lues du `localeConfig` que génère AGP.
     *
     * Rien n'est listé ici : poser un `values-xx/strings.xml` suffit à faire
     * apparaître la langue, exactement comme un fichier de plus dans le
     * `locales/` du portail. [LocaleConfig] est de l'API 34, soit le `minSdk` —
     * aucun garde-fou de version à écrire.
     *
     * Le sélecteur ne montre que la **langue**, pas la région : `en-US` et
     * `en-GB` sont un seul choix pour qui veut lire l'app en anglais, et les
     * ressources sont écrites par langue.
     */
    fun supported(context: Context): List<String> {
        val locales = runCatching { LocaleConfig(context).supportedLocales }.getOrNull()
        val codes = buildList {
            if (locales != null) for (i in 0 until locales.size()) add(locales[i].language)
        }
        return codes.filter { it.isNotEmpty() }.distinct().sorted().ifEmpty { FALLBACK }
    }

    /**
     * Le nom d'une langue, dans cette langue — « Deutsch », pas « Allemand ».
     *
     * Sinon il faut déjà comprendre la langue affichée pour savoir en sortir, ce
     * qui est précisément le cas de celui qui cherche le sélecteur. Déduit du
     * code plutôt que tenu dans une table, pour la même raison que le reste :
     * une langue de plus ne demande rien à écrire.
     */
    fun endonym(code: String): String {
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayLanguage(locale)
            .replaceFirstChar { it.titlecase(locale) } // « français » → « Français »
            .ifEmpty { code.uppercase() }
    }

    /** La langue forcée pour l'app, ou `null` si elle suit le système. */
    fun current(context: Context): String? =
        context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales
            ?.takeUnless { it.isEmpty() }
            ?.get(0)
            ?.language

    /** Pose la langue de l'app, ou rend la main au système si [code] est `null`. */
    fun set(context: Context, code: String?) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (code == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
    }

    /**
     * Dernier recours, si la ressource générée était illisible : les cinq langues
     * de l'écosystème (TECHNIQUE §11).
     *
     * Ce n'est pas la liste de référence — celle-ci est l'ensemble des dossiers
     * `values-xx` du projet, et elle seule. Mieux vaut cependant un sélecteur qui
     * propose cinq langues qu'une carte vide laissant croire l'app monolingue.
     */
    private val FALLBACK = listOf("de", "en", "es", "fr", "it")
}
