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
 * Plusieurs « lapse » indépendants (nom, date, format, animation), entre
 * [LAPSE_MIN] et [LAPSE_MAX] — comptés par [KEY_LAPSE_COUNT], géré par
 * `LapseManageActivity`. Le lapse affiché sur la matrice est [KEY_ACTIVE]. Les
 * dates favorites ([KEY_SAVED]) sont communes à tous les lapse.
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
    const val KEY_LAPSE_COUNT = "lapse_count"

    /** Bornes du nombre de lapse configurables : trois plancher, cinq plafond. */
    const val LAPSE_MIN = 3
    const val LAPSE_MAX = 5

    /** Nombre maximum de dates favorites conservées. */
    const val SAVED_MAX = 5

    /** Numérotation par défaut d'un lapse jamais renommé. « Lapse » ne se
     *  traduit pas, comme `toy_lapse_name` : c'est le nom du toy, pas une phrase. */
    private val ROMAN = listOf("I", "II", "III", "IV", "V")

    /** État complet d'un lapse. */
    data class Lapse(
        val name: String,
        val ref: Long,
        val format: LapseEngine.Format,
        val seconds: LapseEngine.SecondsMode,
    )

    fun prefs(context: Context): SharedPreferences = ToyPrefs.of(context, TOY_ID)

    private fun nameKey(i: Int) = "name_$i"
    private fun refKey(i: Int) = if (i == 0) KEY_REF else "ref_$i"
    private fun formatKey(i: Int) = if (i == 0) KEY_FORMAT else "format_$i"
    private fun secondsKey(i: Int) = if (i == 0) KEY_SECONDS else "seconds_$i"

    fun defaultName(index: Int): String = "Lapse " + ROMAN.getOrElse(index) { (index + 1).toString() }

    /** Référence par défaut : début d'année (lapse 0/1), fin d'année (lapse 2). */
    fun defaultRef(index: Int, zone: ZoneId): Long =
        if (index == 2) LapseEngine.endOfYearRef(zone) else LapseEngine.defaultRef(zone)

    /** Nombre de lapse configurés, borné à [LAPSE_MIN, LAPSE_MAX]. */
    fun lapseCount(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_LAPSE_COUNT, LAPSE_MIN).coerceIn(LAPSE_MIN, LAPSE_MAX)

    /** Index du lapse affiché sur la matrice, borné à [0, lapseCount). */
    fun activeIndex(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, lapseCount(prefs) - 1)

    fun setActiveIndex(prefs: SharedPreferences, index: Int) {
        prefs.edit().putInt(KEY_ACTIVE, index.coerceIn(0, lapseCount(prefs) - 1)).apply()
    }

    fun readLapse(prefs: SharedPreferences, index: Int, zone: ZoneId): Lapse {
        val name = prefs.getString(nameKey(index), null) ?: defaultName(index)
        val ref = prefs.getLong(refKey(index), defaultRef(index, zone))
        val format = runCatching {
            LapseEngine.Format.valueOf(prefs.getString(formatKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.Format.DETAIL2)
        val seconds = runCatching {
            LapseEngine.SecondsMode.valueOf(prefs.getString(secondsKey(index), null) ?: "")
        }.getOrDefault(LapseEngine.SecondsMode.RING)
        return Lapse(name, ref, format, seconds)
    }

    fun writeLapse(prefs: SharedPreferences, index: Int, cfg: Lapse) {
        prefs.edit()
            .putString(nameKey(index), cfg.name)
            .putLong(refKey(index), cfg.ref)
            .putString(formatKey(index), cfg.format.name)
            .putString(secondsKey(index), cfg.seconds.name)
            .apply()
    }

    /** Tous les lapse configurés, dans l'ordre du quick switch et de la liste
     *  de gestion. */
    fun readAll(prefs: SharedPreferences, zone: ZoneId): List<Lapse> =
        (0 until lapseCount(prefs)).map { readLapse(prefs, it, zone) }

    /**
     * Réécrit la liste entière — ce que fait `LapseManageActivity`, qui
     * manipule la liste en mémoire (ajout, suppression, réordonnancement,
     * renommage) et persiste le résultat d'un coup plutôt qu'un index à la fois.
     *
     * Les emplacements au-delà de la nouvelle taille sont nettoyés : sans ça,
     * ajouter puis retirer un lapse laisserait ses réglages traîner dans les
     * préférences, prêts à ressurgir si [lapseCount] remontait par erreur.
     */
    fun writeAll(prefs: SharedPreferences, list: List<Lapse>) {
        val count = list.size.coerceIn(LAPSE_MIN, LAPSE_MAX)
        val e = prefs.edit()
        for (i in 0 until LAPSE_MAX) {
            if (i < count) {
                val cfg = list[i]
                e.putString(nameKey(i), cfg.name)
                    .putLong(refKey(i), cfg.ref)
                    .putString(formatKey(i), cfg.format.name)
                    .putString(secondsKey(i), cfg.seconds.name)
            } else {
                e.remove(nameKey(i)).remove(refKey(i)).remove(formatKey(i)).remove(secondsKey(i))
            }
        }
        e.putInt(KEY_LAPSE_COUNT, count).apply()
    }

    /** Applique le lapse actif à l'engine (idempotent, sans transition). */
    fun applyActive(prefs: SharedPreferences, engine: LapseEngine, zone: ZoneId) =
        applyAt(prefs, engine, zone, activeIndex(prefs))

    /**
     * Applique **un lapse nommé** à l'engine, idempotent et sans transition.
     *
     * Le même travail que [applyActive], pour qui sait déjà lequel il veut. Les
     * hublots d'écran d'accueil en sont là : chacun tient son propre index —
     * deux hublots côte à côte affichent deux lapses différents — et l'index
     * actif de la matrice ne leur sert que de valeur de départ.
     */
    fun applyAt(prefs: SharedPreferences, engine: LapseEngine, zone: ZoneId, index: Int) {
        val cfg = readLapse(prefs, index.coerceIn(0, lapseCount(prefs) - 1), zone)
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
