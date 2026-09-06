import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signature release : keystore.properties à la racine, jamais versionné (.gitignore).
// Absent (ex. CI sans secrets) -> release retombe sur la signature debug.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.aero.glyphlapse"
    // targetSdk 36 (Android 16) : la restriction de clé API Glyph est levée à
    // partir d'Android 16 pour les apps qui le ciblent (cf. Glyph-Developer-Kit)
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.aero.glyphlapse"
        minSdk = 34
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Package distinct : cohabite avec l'APK de prod signé (aucun conflit
            // de signature), deux icônes et deux toys Glyph séparés.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProps.containsKey("storeFile")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Nom de l'APK : GlyphLapse-<version>.apk plutôt que app-release.apk
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "GlyphLapse-${variant.versionName}.apk"
        }
    }
}

// GlyphMatrixSDK.aar : téléchargé au premier build, non versionné (.gitignore)
val glyphSdkAar = layout.projectDirectory.file("libs/GlyphMatrixSDK.aar").asFile
val downloadGlyphSdk = tasks.register("downloadGlyphSdk") {
    description = "Télécharge GlyphMatrixSDK.aar depuis le GlyphMatrix-Developer-Kit de Nothing"
    outputs.file(glyphSdkAar)
    onlyIf { !glyphSdkAar.exists() }
    doLast {
        glyphSdkAar.parentFile.mkdirs()
        val url = URI(
            "https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/raw/main/glyph-matrix-sdk-2.0.aar"
        ).toURL()
        url.openStream().use { input ->
            glyphSdkAar.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadGlyphSdk) }

dependencies {
    implementation(files("libs/GlyphMatrixSDK.aar"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
