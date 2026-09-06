import com.android.build.gradle.LibraryExtension
import com.android.build.api.dsl.ApplicationExtension

/**
 * Active Compose. S'applique par-dessus `haloglyph.android.library` ou
 * `haloglyph.android.application` — on branche `buildFeatures.compose` sur
 * l'extension effectivement présente plutôt que sur `CommonExtension`, dont
 * la signature générique bouge d'une version d'AGP à l'autre.
 */

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

plugins.withId("com.android.library") {
    extensions.configure<LibraryExtension>("android") {
        buildFeatures.compose = true
    }
}

plugins.withId("com.android.application") {
    extensions.configure<ApplicationExtension>("android") {
        buildFeatures.compose = true
    }
}
