plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph.core.ui"
}

dependencies {
    api(project(":core:matrix"))
    api(project(":core:look"))

    // Pour `BackHandler` : une liste déroulante se referme au retour système, et
    // elle n'est plus une fenêtre qui le ferait toute seule. Voir HaloOverlay.
    implementation(libs.androidx.activity.compose)

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
