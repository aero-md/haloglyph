plugins {
    id("haloglyph.kotlin.pure")
}

// Aucune dépendance. C'est le point : ce module doit rester compilable et
// testable sur une JVM nue. `checkNoAndroidDeps` le vérifie à chaque `check`.
