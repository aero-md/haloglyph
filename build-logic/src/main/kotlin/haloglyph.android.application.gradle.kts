import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * Application Android. Une seule copie de ce qui était copié-collé dans les
 * quatre dépôts d'origine : signature, buildTypes, localeConfig, nommage d'APK.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature release : keystore.properties à la racine, jamais versionné.
// Absent (CI sans secrets) -> release retombe sur la signature debug.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasKeystore = keystoreProps.containsKey("storeFile")

android {
    compileSdk = HaloglyphSdk.COMPILE

    defaultConfig {
        minSdk = HaloglyphSdk.MIN
        targetSdk = HaloglyphSdk.TARGET
    }

    signingConfigs {
        if (hasKeystore) {
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
            // Package distinct : cohabite avec l'installation Play, deux icônes
            // et deux jeux de toys séparés dans Glyph Interface.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 activé (TECHNIQUE §12.1) : le SDK Nothing est appelé par
            // réflexion côté système, ses classes sont gardées par proguard-rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // localeConfig déduit des dossiers `values-*/` présents, la langue du dossier
    // non qualifié venant de `res/resources.properties`. Généré plutôt qu'écrit :
    // une liste tenue à la main finit par mentir sur ce qui est traduit.
    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Pas de renommage d'APK ici, contrairement aux dépôts d'origine : l'ancienne
// `applicationVariants` est incompatible avec le cache de configuration, et la
// livraison se fait en AAB. Le nom du fichier ne vaut pas ce prix.

dependencies {
    add("testImplementation", "junit:junit:4.13.2")
}
