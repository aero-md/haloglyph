plugins {
    id("haloglyph.kotlin.pure")
}

dependencies {
    // `api` : `Frame` et `MatrixSpec` apparaissent dans la signature publique du
    // renderer, le module Android qui consomme celui-ci doit les voir.
    api(project(":core:matrix"))
}

// Aucune dépendance Android, et c'est vérifié à chaque `check`. Tout ce qui est
// ici — le filtrage de l'accéléromètre, l'échelle logarithmique, l'azimut tiré
// d'un quaternion — est de l'arithmétique sur des flottants. `SensorManager`
// sait faire une partie de ce calcul ; l'avoir en Kotlin pur est ce qui permet
// au hublot et à la vignette du hub d'afficher exactement la même chose.
