plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph.sono"
}

dependencies {
    // Le moteur, le DSP et le renderer, purs. `api` : le module `app` en a
    // besoin pour l'aperçu du hub.
    api(project(":toy:sono:engine"))

    implementation(project(":core:config"))
    implementation(project(":core:glyph"))

    // Pas de `core:widget`. Un widget qui écoute le micro en permanence est
    // indéfendable — batterie, vie privée, et l'indicateur micro d'Android
    // allumé en continu sur l'écran d'accueil. Sono reste sur la surface Glyph.

    // L'écran de réglages : le mode courant, et l'autorisation micro.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
