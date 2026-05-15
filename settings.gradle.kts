import org.gradle.kotlin.dsl.maven
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "intelligrade"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.0-RC"
        id("org.jetbrains.changelog") version "2.5.0"
        id("com.diffplug.spotless") version "7.2.0"
    }
}

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

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }

        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots")
        }
        gradlePluginPortal()
    }
}
