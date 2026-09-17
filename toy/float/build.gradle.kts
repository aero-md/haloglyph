plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
}

android {
    // `float` seul est un mot-clé Java : invalide comme namespace AGP (même si
    // Kotlin l'accepte très bien comme segment de package, d'où
    // `red.suns.haloglyph.float.*` dans les sources de ce module). Le
    // namespace n'a pas besoin de correspondre au package Kotlin réel — seule
    // la classe R générée en dépend, réimportée dans FloatWidgetToy.kt.
    namespace = "red.suns.haloglyph.toyfloat"
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
