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

    // Le hublot d'écran d'accueil. Il a longtemps été refusé, et pour une bonne
    // raison : un widget qui écoute le micro en permanence est indéfendable.
    // Celui-ci n'écoute que pendant les cinq secondes qui suivent un tap, et le
    // tap sur un widget est justement l'une des rares surfaces qui puissent
    // légalement ouvrir le micro. Voir `SonoWidgetToy`.
    implementation(project(":core:widget"))

    // L'écran de réglages : le mode courant, et l'autorisation micro.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
