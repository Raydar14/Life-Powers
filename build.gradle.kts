// Plugins are declared per module so the pure Kotlin core can build on machines
// without access to Google's Maven repository. See settings.gradle.kts for repositories.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
