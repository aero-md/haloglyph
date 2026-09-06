package dev.aero.glyphlapse

import android.content.Context
import android.content.SharedPreferences
import dev.aero.glyphlapse.engine.LapseEngine
import java.time.ZoneId

/**
 * Persistance de la configuration (SharedPreferences), partagée entre
 * l'app de configuration et le service toy — qui écoute les changements.
 *
 * Plusieurs « lapse » indépendants (date, format, animation, activation) :
 * le lapse 0 est toujours actif, les suivants sont activables. Le lapse
 * affiché sur la Glyph est [KEY_ACTIVE]. Les dates favorites ([KEY_SAVED])
 * sont communes à tous les lapse.
 */
object Config {
    const val PREFS = "glyphlapse"

    // Lapse 0 : clés historiques (compat ascendante).
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
    data class LapseConfig(
        val ref: Long,
        val format: LapseEngine.Format,
        val seconds: LapseEngine.SecondsMode,
        val enabled: Boolean,
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun refKey(i: Int) = if (i == 0) KEY_REF else "ref_$i"
    private fun formatKey(i: Int) = if (i == 0) KEY_FORMAT else "format_$i"
    private fun secondsKey(i: Int) = if (i == 0) KEY_SECONDS else "seconds_$i"
    private fun enabledKey(i: Int) = "enabled_$i"

    /** Référence par défaut : début d'année (lapse 0/1), fin d'année (lapse 2). */
    fun defaultRef(index: Int, zone: ZoneId): Long =
        if (index == 2) LapseEngine.endOfYearRef(zone) else LapseEngine.defaultRef(zone)

    /** Index du lapse affiché sur la Glyph (borné à [0, LAPSE_COUNT)). */
    fun activeIndex(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, LAPSE_COUNT - 1)

    fun setActiveIndex(prefs: SharedPreferences, index: Int) {
        prefs.edit().putInt(KEY_ACTIVE, index.coerceIn(0, LAPSE_COUNT - 1)).apply()
    }

    fun readLapse(prefs: SharedPreferences, index: Int, zone: ZoneId): LapseConfig {
        val ref = prefs.getLong(refKey(index), defaultRef(index, zone))
        val format = runCatching {
            LapseEngine.Format.valueOf(prefs.getString(formatKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.Format.DETAIL2)
        val seconds = runCatching {
            LapseEngine.SecondsMode.valueOf(prefs.getString(secondsKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.SecondsMode.RING)
        // Le lapse 0 est toujours actif ; les autres sont désactivés par défaut.
        val enabled = index == 0 || prefs.getBoolean(enabledKey(index), false)
        return LapseConfig(ref, format, seconds, enabled)
    }

    fun writeLapse(prefs: SharedPreferences, index: Int, cfg: LapseConfig) {
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

    /** Dates favorites : liste d'epoch millis, persistée en CSV (commune à tous les lapse). */
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
