plugins {
    id("haloglyph.kotlin.pure")
}

dependencies {
    // `api` : `Frame` et `MatrixSpec` apparaissent dans la signature publique du
    // renderer, le module Android qui consomme celui-ci doit les voir.
    api(project(":core:matrix"))
}

// Aucune dépendance Android, et c'est vérifié à chaque `check`. Tout ce qui est
// ici — le passe-bas, la projection sur le plan horizontal, la recherche de
// l'avant du véhicule — est de l'arithmétique sur des flottants. C'est ce qui
// permet de tester un accéléromètre de bord **sans voiture** : on verse des
// vecteurs, on lit des g.
