package red.suns.haloglyph.sono

import android.content.Context
import android.content.SharedPreferences
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.sono.engine.SonoMode

/**
 * Le seul état que ce toy garde : le mode affiché.
 *
 * Il est persisté parce qu'il se change **sur la matrice**, à l'appui long, et
 * que l'écran de réglages doit montrer le même — comme le solide de Dice. Rien
 * de la mesure n'est écrit : ni niveau, ni spectre, ni historique. Le son
 * traverse le processus et n'en sort jamais.
 */
object SonoConfig {

    /** Identifiant du toy : fichier de préférences et clé de catalogue. */
    const val TOY_ID = "sono"

    private const val KEY_MODE = "mode"

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    /** Le mode courant. Une clé inconnue retombe sur le défaut, jamais sur une erreur. */
    fun mode(prefs: SharedPreferences): SonoMode =
        SonoMode.byKey(prefs.getString(KEY_MODE, null))

    fun setMode(prefs: SharedPreferences, mode: SonoMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}
