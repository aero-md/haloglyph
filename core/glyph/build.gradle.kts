plugins {
    id("haloglyph.android.library")
    id("haloglyph.glyph.sdk")
}

android {
    namespace = "red.suns.haloglyph.core.glyph"

    // Les règles R8 voyagent avec le module qui en a besoin, pas avec l'app.
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:matrix"))
    implementation(libs.androidx.core.ktx)
}
