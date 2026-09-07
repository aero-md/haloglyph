plugins {
    id("haloglyph.android.library")
}

android {
    namespace = "red.suns.haloglyph.dice"
}

dependencies {
    // La géométrie et le rendu, purs. `api` : les deux surfaces du toy les
    // exposent, et le module `app` en a besoin pour l'aperçu du hub.
    api(project(":toy:dice:engine"))

    implementation(project(":core:config"))
    implementation(project(":core:glyph"))
    implementation(project(":core:widget"))
}

// Ni `haloglyph.compose` ni `androidx` : ce toy n'a pas d'écran de réglages, et
// il n'a rien à régler — un dé qu'on secoue et un solide qu'on change à l'appui
// long. C'est le module le plus léger du pack, et c'est la conséquence directe
// de ce qu'il fait, pas une performance d'ascèse.
