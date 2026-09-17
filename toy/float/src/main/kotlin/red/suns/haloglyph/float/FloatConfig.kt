package red.suns.haloglyph.float

import android.content.Context
import android.content.SharedPreferences
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.float.engine.FloatMode
import red.suns.haloglyph.float.engine.FloatRange

/**
 * Ce que le toy garde d'une session à l'autre : l'instrument et la portée.
 *
 * ## Deux réglages, et pas un de plus
 *
 * Il y a eu un **zéro** ici — deux, même, un par régime — pour rattraper le fait
 * qu'un Phone (3) ne repose pas à plat sur son dos. Il n'y en a plus : un niveau
 * à bulle n'a pas de réglage de zéro, c'est ce qui en fait un instrument. Voir
 * `FloatEngine`.
 *
 * ## L'instrument vaut partout
 *
 * Il est persisté comme le solide de Dice ou le mode de Sono, parce qu'il se
 * change **sur la matrice**, à l'appui long. Et il vaut aussi pour les hublots :
 * leur tap fait le même geste et écrit la même valeur, donc changer d'instrument
 * depuis un hublot change ce que montre le téléphone, et réciproquement.
 *
 * Ce toy a un temps rangé le choix sous chaque hublot, ce qui permettait d'en
 * poser un de chaque côté. C'est fini, et c'est cohérent : là où Dice n'a aucun
 * geste de hublot pour changer de solide, celui-ci en a un. Un réglage qu'un
 * geste peut faire n'a pas à exister deux fois.
 */
object FloatConfig {

    /** Identifiant du toy : fichier de préférences, clé de catalogue, id de widget. */
    const val TOY_ID = "float"

    private const val KEY_MODE = "mode"
    private const val KEY_RANGE = "range"

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    /** L'instrument affiché. Une clé inconnue retombe au défaut. */
    fun mode(prefs: SharedPreferences): FloatMode =
        FloatMode.byKey(prefs.getString(KEY_MODE, null))

    fun setMode(prefs: SharedPreferences, mode: FloatMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun range(prefs: SharedPreferences): FloatRange =
        FloatRange.byKey(prefs.getString(KEY_RANGE, null))

    fun setRange(prefs: SharedPreferences, range: FloatRange) {
        prefs.edit().putString(KEY_RANGE, range.name).apply()
    }
}
