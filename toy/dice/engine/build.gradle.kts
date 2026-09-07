plugins {
    id("haloglyph.kotlin.pure")
}

dependencies {
    // `api` : `Frame` et `MatrixSpec` apparaissent dans la signature du
    // renderer, le module Android qui le consomme doit les voir.
    api(project(":core:matrix"))
}

// Le dé est de la géométrie : quatre solides, des quaternions, un lancer de
// rayons. Rien là-dedans n'a besoin d'Android, et `checkNoAndroidDeps` le
// vérifie à chaque `check`. C'est ce qui permet au widget de jouer un jet dans
// son propre processus sans jamais binder le service Glyph.
