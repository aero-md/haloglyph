plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph.gforce"
}

dependencies {
    // Le moteur et le renderer, purs. `api` : les trois surfaces du toy les
    // exposent, et le module `app` en a besoin pour l'aperçu du hub.
    api(project(":toy:gforce:engine"))

    implementation(project(":core:config"))
    implementation(project(":core:glyph"))
    implementation(project(":core:widget"))

    // L'écran de réglages : la face du cadran, et un aperçu qui mesure pour de
    // vrai — la seule façon d'essayer ce toy sans prendre la voiture.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
}
