plugins {
    id("haloglyph.android.application")
    id("haloglyph.compose")
}

android {
    namespace = "red.suns.haloglyph"

    defaultConfig {
        // Irréversible après la première publication (TECHNIQUE §14). Le préfixe
        // reverse-DNS correspond à un domaine réellement possédé : suns.red.
        applicationId = "red.suns.haloglyph"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:matrix"))
    implementation(project(":core:config"))
    implementation(project(":core:glyph"))
    implementation(project(":core:widget"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Un toy = une ligne. Il apporte son service, son widget, son fragment de
    // manifeste et ses chaînes ; AGP fusionne. `app/` n'a pas à changer pour en
    // accueillir un de plus.
    implementation(project(":toy:lapse"))
}
