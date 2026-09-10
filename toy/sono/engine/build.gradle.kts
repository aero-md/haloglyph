plugins {
    id("haloglyph.kotlin.pure")
}

dependencies {
    // `api` : `Frame` et `MatrixSpec` sont dans la signature du renderer, le
    // module Android qui le consomme doit les voir.
    api(project(":core:matrix"))
}

// Tout le traitement du son est ici, et rien de tout ça n'a besoin d'Android :
// une pondération A est une cascade de biquads, une FFT est une FFT. C'est ce
// qui permet de vérifier la conformité du filtre à la norme dans un test JVM qui
// dure une milliseconde, au lieu de la vérifier à l'oreille sur un téléphone.
//
// `checkNoAndroidDeps` le tient : le jour où quelqu'un importe `AudioRecord`
// ici, le build casse. La capture vit dans le module Android, et elle n'y fait
// qu'une chose — pousser des échantillons dans ce moteur.
