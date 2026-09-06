/**
 * Cibles SDK du projet, en un seul endroit (TECHNIQUE §1).
 *
 * `TARGET = 36` (Android 16) n'est pas cosmétique : c'est ce qui lève
 * l'obligation de clé API Nothing sur Nothing OS / Android 16. Le baisser
 * casserait la Glyph Matrix en release.
 */
object HaloglyphSdk {
    const val COMPILE = 36
    const val MIN = 34
    const val TARGET = 36
}
