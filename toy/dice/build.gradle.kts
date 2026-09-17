plugins {
    id("haloglyph.android.library")
    id("haloglyph.compose")
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

    // `NotificationCompat`, et rien d'autre. Le service qui pose le dé sur la
    // matrice depuis la tuile est un service de premier plan : Android exige
    // qu'il porte une notification.
    implementation(libs.androidx.core.ktx)

    // L'écran de réglages. Ce module s'en est passé longtemps, et le commentaire
    // qui expliquait pourquoi était juste : un dé n'a rien à régler **tant qu'il
    // vit au dos du téléphone**, où le solide se change à l'appui long. Posé par
    // la tuile, il n'y a plus d'appui long — et l'appui long sur une tuile, lui,
    // attend une destination. Voir `DiceSettingsActivity`.
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
}
