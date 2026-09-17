plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph.float"
}

dependencies {
    // Le moteur et le renderer, purs. `api` : les trois surfaces du toy les
    // exposent, et le module `app` en a besoin pour l'aperçu du hub.
    api(project(":toy:float:engine"))

    implementation(project(":core:config"))
    implementation(project(":core:glyph"))
    implementation(project(":core:widget"))

    // L'écran de réglages : la portée, le mode, et la remise à zéro — la seule
    // commande du toy qui n'ait pas de geste sur un écran d'accueil.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
}
