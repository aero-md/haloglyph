package red.suns.haloglyph.core.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Accès aux préférences d'un toy.
 *
 * Les trois surfaces d'un même toy — le service Glyph, le widget, l'écran de
 * réglages — écrivent et lisent le même état. Le mécanisme est
 * `SharedPreferences`, et c'est un choix, pas un héritage :
 *
 * - **pas de DataStore.** DataStore est asynchrone ; la configuration est lue
 *   *dans la boucle de rendu, en synchrone*, jusqu'à 30 fois par seconde.
 *   `SharedPreferences` est en mémoire après la première lecture. « Moderniser »
 *   ici serait une régression mesurable.
 * - **un seul processus.** Ne jamais déclarer `android:process` sur un service
 *   de toy : les `OnSharedPreferenceChangeListener` ne traversent pas les
 *   processus et `MODE_MULTI_PROCESS` est déprécié et non fiable. La
 *   synchronisation entre surfaces casserait *en silence*.
 *
 * Un fichier de préférences par toy (`haloglyph_lapse`, `haloglyph_dice`…) :
 * les toys sont indépendants, leurs clés ne doivent pas se marcher dessus, et
 * un toy retiré emporte ses réglages.
 */
object ToyPrefs {

    private const val PREFIX = "haloglyph_"

    fun fileName(toyId: String): String = PREFIX + toyId

    fun of(context: Context, toyId: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName(toyId), Context.MODE_PRIVATE)
}

/**
 * Abonnement aux changements de préférences, avec la référence forte qui va
 * bien.
 *
 * `SharedPreferences` ne garde qu'une référence **faible** sur ses listeners :
 * un lambda passé directement à `registerOnSharedPreferenceChangeListener` est
 * ramassé par le GC à un moment imprévisible, et la surface arrête de se mettre
 * à jour sans la moindre erreur. Ce piège a coûté assez cher pour mériter une
 * classe.
 *
 * ```
 * private val watch = PrefsWatcher(prefs) { reloadConfig() }
 * override fun onStart() { watch.start() }
 * override fun onStop()  { watch.stop() }
 * ```
 */
class PrefsWatcher(
    private val prefs: SharedPreferences,
    private val onChange: (key: String?) -> Unit,
) {
    // La référence forte, justement.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        onChange(key)
    }

    private var started = false

    fun start() {
        if (started) return
        prefs.registerOnSharedPreferenceChangeListener(listener)
        started = true
    }

    fun stop() {
        if (!started) return
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        started = false
    }
}
