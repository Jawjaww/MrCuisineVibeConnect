import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Logique pure (protocole, sécurité, pilotage, recettes, recherche) : testable sans Android.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // TwinIntegrationTest s'exécute seulement si tools/twin.py tourne (sinon ignoré).
    testLogging { events("passed", "skipped", "failed") }
    // I18nTest et DesignTest lisent des fichiers situés hors du module : sans ces entrées,
    // Gradle considère la tâche « à jour » et un changement de traduction passe inaperçu.
    inputs.dir("../app/src/main/assets/lang")
    inputs.dir("../app/src/main/assets/decor")
    inputs.dir("../app/src/main/kotlin")
    inputs.file("../docs/ASSETS-CREDITS.md")
}
