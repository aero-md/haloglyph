import java.net.URI

/**
 * Récupération du GlyphMatrixSDK.
 *
 * L'AAR n'est pas versionné (licence Nothing, et un binaire dans git est une
 * dette qu'on ne rembourse jamais) : il est téléchargé au premier build. Cette
 * tâche existait à l'identique dans les quatre dépôts d'origine ; elle ne vit
 * plus qu'ici, et n'est appliquée que par `core:glyph` — le seul module qui a
 * le droit de voir `com.nothing.ketchum`.
 */

val sdkVersion = "2.0"
val glyphSdkAar = layout.projectDirectory.file("libs/glyph-matrix-sdk-$sdkVersion.aar").asFile
val sdkUrl = "https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/" +
    "raw/main/glyph-matrix-sdk-$sdkVersion.aar"

val downloadGlyphSdk = tasks.register("downloadGlyphSdk") {
    group = "build setup"
    description = "Télécharge glyph-matrix-sdk-$sdkVersion.aar depuis le GlyphMatrix-Developer-Kit"
    // Seules des valeurs sérialisables traversent vers l'exécution. Un `onlyIf`
    // ou un `doLast` qui lirait directement une propriété du script capturerait
    // le script lui-même, et le cache de configuration refuserait la tâche.
    val target = glyphSdkAar
    val url = sdkUrl

    outputs.file(target)
    onlyIf { !target.exists() }
    doLast {
        target.parentFile.mkdirs()
        // Fichier temporaire puis renommage : un téléchargement interrompu ne
        // doit pas laisser un AAR tronqué que le build prendrait pour valide.
        val partial = File(target.parentFile, target.name + ".part")
        URI(url).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        partial.renameTo(target)
    }
}

// `files(downloadGlyphSdk)` et non `files(chemin)` : la dépendance porte la
// tâche qui produit le fichier. Sans ça, le premier build peut tenter de
// transformer un AAR encore en cours de téléchargement — ce qui échoue, une
// fois sur deux, sans rien dire de compréhensible.
dependencies {
    add("api", files(downloadGlyphSdk))
}
