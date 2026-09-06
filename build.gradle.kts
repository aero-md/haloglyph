// Racine volontairement vide : les plugins ne sont pas déclarés ici mais dans
// build-logic, qui les porte sur son propre classpath. Un `plugins { ... apply false }`
// ici entrerait en conflit avec les conventions.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
