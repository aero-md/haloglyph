plugins {
    id("haloglyph.kotlin.pure")
}

dependencies {
    // `api` : `Frame` et `MatrixSpec` apparaissent dans la signature publique du
    // renderer, le module Android qui consomme celui-ci doit les voir.
    api(project(":core:matrix"))
}

// Aucune dépendance Android, et c'est vérifié à chaque `check`. Le moteur et le
// renderer de Lapse tournent sur une JVM nue — c'est ce qui permet au widget de
// les instancier en direct, sans jamais binder le service Glyph.
