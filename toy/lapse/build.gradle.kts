plugins {
    id("haloglyph.android.library")
}

android {
    namespace = "red.suns.haloglyph.lapse"
}

dependencies {
    // Le moteur et le renderer, purs. `api` : les trois surfaces du toy les
    // exposent, et le module `app` en a besoin pour l'aperçu du hub.
    api(project(":toy:lapse:engine"))

    implementation(project(":core:config"))
    implementation(project(":core:glyph"))
    implementation(project(":core:widget"))

    // `core:ui` et l'écran de réglages arrivent avec la maquette de DA.
}
