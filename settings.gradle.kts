pluginManagement {
    // build-logic est une *included build* : ses conventions sont disponibles comme
    // n'importe quel plugin, sans publication ni buildSrc (qui invalide tout le
    // cache de configuration au moindre changement).
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Haloglyph"

// core/ : la plomberie, partagée par tous les toys.
include(":core:matrix")   // Kotlin pur — masque 489, polices, primitives, FrameSink
include(":core:config")   // SharedPreferences partagées entre les trois surfaces
include(":core:glyph")    // SEUL module qui connaît le SDK Nothing
include(":core:widget")   // émulation de la matrice en widget d'écran d'accueil
include(":core:ui")       // thème + MatrixPreview Compose

// toy/ : un toy = ses trois surfaces + son fragment de manifeste + ses chaînes.
// Le moteur et le renderer sont un module à part, en Kotlin pur : c'est ce qui
// interdit mécaniquement à un moteur de dépendre d'Android.
include(":toy:lapse")
include(":toy:lapse:engine")
include(":toy:dice")
include(":toy:dice:engine")
include(":toy:sono")
include(":toy:sono:engine")

// app/ : le hub, qui assemble les toys embarqués.
include(":app")

// `app-slot/` (seconde app Play, PEGI 18) est volontairement absent : Slot est mis
// de côté. Le jour où il revient, c'est une ligne ici et un module d'assemblage.
