package red.suns.haloglyph.lapse.render

import java.util.Locale

/**
 * Les étiquettes d'unité écrites **sur la matrice**, par langue.
 *
 * L'app de réglages tire ses textes des dossiers `values-xx`, comme n'importe quelle
 * app Android. La matrice, elle, ne le peut pas : [LapseRenderer] est du Kotlin
 * pur — pas de `Context`, pas de `Resources` — parce que c'est ce qui le rend
 * testable en JUnit et fidèle au point près à la préview web. Les étiquettes
 * arrivent donc par ici, en clair, et le renderer ne connaît que cette table.
 *
 * Ce ne sont pas des traductions mais des **abréviations d'une lettre**, et le
 * choix de la lettre est contraint par le support :
 *
 * - Une seule colonne de glyphes par police (3 px en 3×5 et 3×4, 5 px en 5×7).
 *   Toutes les lettres retenues respectent cette largeur, si bien qu'une ligne
 *   fait la même largeur dans les cinq langues et que la mise en page — nudge,
 *   resserrage, clipping dans le disque — ne change pas d'une langue à l'autre.
 *   `MatrixLabelsTest` échouera si une langue ajoutée sort de ce gabarit.
 * - Rien qui ressemble à un chiffre. L'italien dit les heures en *ore*, mais un
 *   `O` collé à un nombre se lit comme un zéro : « 12O » devient « 120 ». Les
 *   cinq langues écrivent donc `H`, qui est le symbole SI et se comprend
 *   partout. Même raison pour l'allemand, où `S` (*Stunden*) entrerait en
 *   collision avec le `S` des secondes.
 *
 * [days] sert aussi de marque au format Jours, où il précède le compte à
 * rebours : « J-42 » en français, « D-42 » en anglais — la notation du jour J
 * existe dans les cinq langues, avec chacune son initiale.
 */
data class MatrixLabels(
    /** Années. */
    val years: String,
    /** Mois. */
    val months: String,
    /** Jours — et marque du compte à rebours, « J-42 ». */
    val days: String,
    /** Heures. */
    val hours: String,
    /** Minutes, collées à leur valeur : le symbole prime, universel. */
    val minutes: String,
    /** Minutes en toutes lettres, sur la ligne du bas du format Cycle. */
    val minutesCycle: String,
    /** Secondes, quand il reste moins d'une minute à afficher. */
    val seconds: String,
) {
    companion object {

        /** Anni · mesi · giorni · ore · minuti. */
        val IT = MatrixLabels("A", "M", "G", "H", "'", "MIN", "S")

        /** Années · mois · jours · heures · minutes. */
        val FR = MatrixLabels("A", "M", "J", "H", "'", "MIN", "S")

        /** Years · months · days · hours · minutes. */
        val EN = MatrixLabels("Y", "M", "D", "H", "'", "MIN", "S")

        /** Jahre · Monate · Tage · Stunden · Minuten. */
        val DE = MatrixLabels("J", "M", "T", "H", "'", "MIN", "S")

        /** Años · meses · días · horas · minutos. */
        val ES = MatrixLabels("A", "M", "D", "H", "'", "MIN", "S")

        /**
         * Les langues servies, par code ISO 639-1 — le même jeu que le portail
         * web, pour que passer de la préview à l'app ne change pas de langue.
         */
        val ALL: Map<String, MatrixLabels> = mapOf(
            "de" to DE,
            "en" to EN,
            "es" to ES,
            "fr" to FR,
            "it" to IT,
        )

        /**
         * L'anglais en repli, et non le français bien que le toy y ait été
         * écrit : c'est ce que comprend le plus grand nombre de ceux dont la
         * langue n'est pas servie. Un utilisateur japonais ou polonais tombe
         * forcément dessus. Même arbitrage que `FALLBACK` côté portail.
         */
        val FALLBACK = EN

        /** Les étiquettes d'une langue, repli compris. */
        fun of(language: String): MatrixLabels = ALL[language.lowercase()] ?: FALLBACK

        /**
         * Les étiquettes de la langue courante du processus.
         *
         * `Locale.getDefault()` et pas une préférence à nous : sur Android 13+,
         * la langue par app posée via `LocaleManager` est appliquée au
         * processus par le système. Le service du toy vit dans ce processus, il
         * suit donc le réglage sans avoir à l'écouter — à condition de relire à
         * chaque tick, ce que font les deux boucles de rendu.
         */
        fun current(): MatrixLabels = of(Locale.getDefault().language)
    }
}
