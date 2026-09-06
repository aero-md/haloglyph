plugins {
    id("haloglyph.android.library")
}

android {
    namespace = "red.suns.haloglyph.core.widget"
}

dependencies {
    api(project(":core:matrix"))
    api(project(":core:config"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
}
