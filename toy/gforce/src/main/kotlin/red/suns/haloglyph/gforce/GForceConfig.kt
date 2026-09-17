package red.suns.haloglyph.gforce

import android.content.Context
import android.content.SharedPreferences
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.gforce.engine.GForceMode

/**
 * Ce que le toy garde d'une session à l'autre : la face du cadran, et c'est tout.
 *
 * ## Les pics ne sont **pas** ici, et c'est le sujet
 *
 * Un pic de g est l'histoire d'un trajet. Le persister en ferait un record, donc
 * une chose qu'on regarde au lieu de conduire — et un chiffre qui ne veut plus
 * rien dire au bout d'un mois, parce qu'il vient d'un freinage d'urgence qu'on a
 * oublié. Ils vivent donc dans l'instance du moteur, et ferment avec le toy.
 *
 * ## Pas de portée non plus
 *
 * Contrairement à Plumb, l'échelle est fixe : les deux graduations valent 0,5 g et
 * 1,0 g, qui sont les deux nombres qu'un conducteur reconnaît. Un réglage ne
 * changerait que l'endroit où tombent deux traits qui, eux, ont une valeur. Voir
 * `GForceScale`.
 */
object GForceConfig {

    /** Identifiant du toy : fichier de préférences, clé de catalogue, id de widget. */
    const val TOY_ID = "gforce"

    private const val KEY_MODE = "mode"

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    /** La face affichée. Une clé inconnue retombe au défaut. */
    fun mode(prefs: SharedPreferences): GForceMode =
        GForceMode.byKey(prefs.getString(KEY_MODE, null))

    fun setMode(prefs: SharedPreferences, mode: GForceMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}
