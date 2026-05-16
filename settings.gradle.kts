import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "intelligrade"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenLocal()
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }

        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots")
        }
        gradlePluginPortal()
    }
}
