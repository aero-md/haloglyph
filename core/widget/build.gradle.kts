plugins {
    id("haloglyph.android.library")
    // Pour l'écran de réglages d'un widget : il est en Compose comme tout le
    // reste de l'interface. Le rendu du widget lui-même, lui, reste une bitmap.
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph.core.widget"
}

dependencies {
    api(project(":core:matrix"))
    api(project(":core:look"))
    api(project(":core:config"))
    // L'écran de réglages d'un widget est un écran de l'app : mêmes cartes,
    // mêmes sélecteurs, même aperçu de matrice. Il n'a aucune raison d'avoir son
    // propre vocabulaire visuel — et `core:ui` ne dépend pas de nous.
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
}
