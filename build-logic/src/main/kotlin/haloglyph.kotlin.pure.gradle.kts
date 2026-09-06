import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Module Kotlin pur : moteurs, renderers, géométrie de la matrice.
 *
 * C'est ici que vit la règle centrale du projet (TECHNIQUE §3) : un moteur ne
 * connaît ni Android ni Nothing. Elle est tenue par la construction — pas de
 * plugin Android, donc `android.*` et `com.nothing.*` ne compilent pas — et
 * doublée par [checkNoAndroidDeps], qui attrape le cas où quelqu'un ajouterait
 * une dépendance Android *transportable* (un artefact androidx en jar, par ex.).
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    add("testImplementation", "junit:junit:4.13.2")
}

val forbiddenPrefixes = listOf("androidx.", "com.android", "com.google.android", "com.nothing")

val checkNoAndroidDeps = tasks.register("checkNoAndroidDeps") {
    group = "verification"
    description = "Échoue si un module Kotlin pur déclare une dépendance Android."
}

afterEvaluate {
    val offenders = configurations
        .flatMap { conf -> conf.dependencies.map { "${it.group}:${it.name}" } }
        .filter { coord -> forbiddenPrefixes.any { coord.startsWith(it) } }
        .distinct()
        .sorted()

    checkNoAndroidDeps.configure {
        doLast {
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "Dépendances Android interdites dans un module pur : " +
                        offenders.joinToString(", ") +
                        "\nVoir TECHNIQUE §3 — les moteurs ne connaissent pas Nothing."
                )
            }
        }
    }
}

tasks.named("check") { dependsOn(checkNoAndroidDeps) }
