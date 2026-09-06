import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Bibliothèque Android : les modules `core/` qui touchent au framework. */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = HaloglyphSdk.COMPILE

    defaultConfig {
        minSdk = HaloglyphSdk.MIN
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    add("testImplementation", "junit:junit:4.13.2")
}
