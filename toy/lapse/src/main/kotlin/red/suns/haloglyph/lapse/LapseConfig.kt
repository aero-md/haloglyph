package red.suns.haloglyph.lapse

import android.content.Context
import android.content.SharedPreferences
import red.suns.haloglyph.core.config.ToyPrefs
import red.suns.haloglyph.lapse.engine.LapseEngine
import java.time.ZoneId

/**
 * Persistance de la configuration de Lapse, partagée par ses trois surfaces :
 * le service Glyph écrit (appui long → lapse suivant), l'écran de réglages
 * écrit, le widget lit.
 *
 * Plusieurs « lapse » indépendants (date, format, animation, activation) : le
 * lapse 0 est toujours actif, les suivants sont activables. Le lapse affiché
 * sur la matrice est [KEY_ACTIVE]. Les dates favorites ([KEY_SAVED]) sont
 * communes à tous les lapse.
 *
 * Les noms de clés sont ceux de GlyphLapse. Ils ne servent plus à la compatibilité
 * ascendante — l'`applicationId` change, donc aucune installation existante ne
 * sera relue — mais les garder évite une réécriture sans bénéfice, et laisse les
 * traces d'exécution lisibles à qui connaît le toy d'origine.
 */
object LapseConfig {

    /** Identifiant du toy : fichier de préférences, clé de catalogue, id de widget. */
    const val TOY_ID = "lapse"

    // Lapse 0 : clés historiques.
    const val KEY_REF = "ref_epoch_millis"
    const val KEY_FORMAT = "format"
    const val KEY_SECONDS = "seconds_mode"

    const val KEY_ACTIVE = "active_lapse"
    const val KEY_SAVED = "saved_dates"

    /** Nombre de lapse configurables (onglets). */
    const val LAPSE_COUNT = 3

    /** Nombre maximum de dates favorites conservées. */
    const val SAVED_MAX = 5

    /** État complet d'un lapse. Le lapse 0 est toujours [enabled]. */
    data class Lapse(
        val ref: Long,
        val format: LapseEngine.Format,
        val seconds: LapseEngine.SecondsMode,
        val enabled: Boolean,
    )

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    private fun refKey(i: Int) = if (i == 0) KEY_REF else "ref_$i"
    private fun formatKey(i: Int) = if (i == 0) KEY_FORMAT else "format_$i"
    private fun secondsKey(i: Int) = if (i == 0) KEY_SECONDS else "seconds_$i"
    private fun enabledKey(i: Int) = "enabled_$i"

    /** Référence par défaut : début d'année (lapse 0/1), fin d'année (lapse 2). */
    fun defaultRef(index: Int, zone: ZoneId): Long =
        if (index == 2) LapseEngine.endOfYearRef(zone) else LapseEngine.defaultRef(zone)

    /** Index du lapse affiché sur la matrice, borné à [0, LAPSE_COUNT). */
    fun activeIndex(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, LAPSE_COUNT - 1)

    fun setActiveIndex(prefs: SharedPreferences, index: Int) {
        prefs.edit().putInt(KEY_ACTIVE, index.coerceIn(0, LAPSE_COUNT - 1)).apply()
    }

    fun readLapse(prefs: SharedPreferences, index: Int, zone: ZoneId): Lapse {
        val ref = prefs.getLong(refKey(index), defaultRef(index, zone))
        val format = runCatching {
            LapseEngine.Format.valueOf(prefs.getString(formatKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.Format.DETAIL2)
        val seconds = runCatching {
            LapseEngine.SecondsMode.valueOf(prefs.getString(secondsKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.SecondsMode.RING)
        // Le lapse 0 est toujours actif ; les autres sont désactivés par défaut.
        val enabled = index == 0 || prefs.getBoolean(enabledKey(index), false)
        return Lapse(ref, format, seconds, enabled)
    }

    fun writeLapse(prefs: SharedPreferences, index: Int, cfg: Lapse) {
        val e = prefs.edit()
            .putLong(refKey(index), cfg.ref)
            .putString(formatKey(index), cfg.format.name)
            .putString(secondsKey(index), cfg.seconds.name)
        if (index != 0) e.putBoolean(enabledKey(index), cfg.enabled)
        e.apply()
    }

    /** Applique le lapse actif à l'engine (idempotent, sans transition). */
    fun applyActive(prefs: SharedPreferences, engine: LapseEngine, zone: ZoneId) {
        val cfg = readLapse(prefs, activeIndex(prefs), zone)
        if (cfg.ref != engine.refMillis) engine.setRef(cfg.ref)
        if (cfg.format != engine.format) engine.setFormatQuiet(cfg.format)
        engine.secondsMode = cfg.seconds
    }

    /** Dates favorites : epoch millis, persistées en CSV, communes à tous les lapse. */
    fun savedDates(prefs: SharedPreferences): List<Long> =
        prefs.getString(KEY_SAVED, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    fun setSavedDates(prefs: SharedPreferences, dates: List<Long>) {
        prefs.edit()
            .putString(KEY_SAVED, dates.joinToString(",") { it.toString() })
            .apply()
    }
}
