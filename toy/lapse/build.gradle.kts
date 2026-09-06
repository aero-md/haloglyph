plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
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

    // Quatrième surface : l'écran de réglages. Il vit dans le module du toy, pas
    // dans `app` — le hub ne sait pas ce que Lapse règle, seulement où le régler.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
}
