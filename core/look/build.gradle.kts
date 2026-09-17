plugins {
    id("haloglyph.android.library")
}

android {
    namespace = "red.suns.haloglyph.core.look"
}

dependencies {
    // Rien d'autre que la géométrie et la table de couleurs. Pas de Compose : ce
    // module est justement là pour que le widget puisse en dépendre sans traîner
    // une interface graphique derrière lui.
    api(project(":core:matrix"))
}
