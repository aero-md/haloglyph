package red.suns.haloglyph.dice

import android.content.Context
import android.content.SharedPreferences
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.dice.engine.Dice
import red.suns.haloglyph.dice.engine.Die
import red.suns.haloglyph.dice.engine.Quat

/**
 * Le peu d'état que ce toy possède, partagé par ses deux surfaces : le service
 * Glyph et le widget.
 *
 * Il n'y a **pas d'écran de réglages**, et ce n'est pas un manque : un dé n'a
 * rien à configurer. Le solide se change à l'appui long sur le Glyph Button, le
 * reste est le geste. Ce qui est écrit ici n'est donc pas une préférence mais
 * une **mémoire** — le dé en main et sa dernière face — pour que la matrice, le
 * widget et la vignette du hub montrent tous les trois le même dé posé.
 *
 * Le cran de rotation est persisté avec la valeur pour la même raison : la pose
 * de repos doit être identique partout, sinon le widget repose sa silhouette
 * d'un quart de tour à côté de la dernière image de son animation.
 */
object DiceConfig {

    /** Identifiant du toy : fichier de préférences, clé de catalogue, id de widget. */
    const val TOY_ID = "dice"

    private const val KEY_DIE = "die"
    private const val KEY_VALUE = "last_value"
    private const val KEY_TWIST = "last_twist"

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    /** Le dé en main. Une clé inconnue retombe sur le d6, jamais sur une erreur. */
    fun die(prefs: SharedPreferences): Die = Dice.byKey(prefs.getString(KEY_DIE, null))

    fun setDie(prefs: SharedPreferences, die: Die) {
        prefs.edit().putString(KEY_DIE, die.id.key).apply()
    }

    /**
     * La dernière face obtenue, bornée au dé courant.
     *
     * Le bornage n'est pas défensif pour rien : changer de solide garde la
     * mémoire du précédent, et un 17 relu sur un d6 n'a aucune face où se poser.
     */
    fun lastValue(prefs: SharedPreferences, die: Die): Int =
        prefs.getInt(KEY_VALUE, 1).coerceIn(1, die.faceCount)

    fun setResult(prefs: SharedPreferences, value: Int, twist: Int) {
        prefs.edit().putInt(KEY_VALUE, value).putInt(KEY_TWIST, twist).apply()
    }

    /** L'orientation du dé posé, telle que les trois surfaces doivent la voir. */
    fun restQuat(prefs: SharedPreferences, die: Die): Quat =
        die.restQuat(lastValue(prefs, die), prefs.getInt(KEY_TWIST, 0).coerceIn(0, die.spin - 1))
}
