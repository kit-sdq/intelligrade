import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// This file is based on https://github.com/JetBrains/intellij-platform-plugin-template
// it is recommended to look there when updating.

// See https://github.com/JetBrains/intellij-platform-gradle-plugin
plugins {
    id("java")
    id("org.jetbrains.intellij.platform")

    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

group = "edu.kit.kastel.sdq"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
    // See https://kotlinlang.org/docs/java-interop.html#nullability-annotations
    compilerOptions {
        freeCompilerArgs.add("-Xnullability-annotations=@org.jspecify.annotations:warn")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Note: Plugins should use `testFramework(TestFrameworkType.Platform)` like done below. It seems like IntelliJ
    // depends on Junit4, which are added here.
    //
    // The Junit5 dependencies are added as well to not force us to write Junit4 tests.
    testRuntimeOnly(libs.junit4)

    testRuntimeOnly(libs.archunit.junit5)
    testImplementation(libs.archunit.core)

    intellijPlatform {
        intellijIdea(
            libs.versions.intellij.idea
                .get(),
        )
        pluginVerifier()
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)

        val localJbr = System.getenv("LOCAL_JBR")
        localJbr?.let {
            jetbrainsRuntimeLocal(localJbr)
        }
    }

    implementation(libs.artemis4j)
}

tasks {
    patchPluginXml {
        changeNotes = """"""
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

    runIde {
        autoReload = true
    }

    buildSearchableOptions {
        enabled = false
    }
}

spotless {
    ratchetFrom("origin/main")

    format("misc") {
        target("*.gradle", ".gitattributes", ".gitignore", "*.md")

        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        toggleOffOn("@formatter:off", "@formatter:on")
        palantirJavaFormat(libs.versions.palantir.get()).formatJavadoc(false)
        removeUnusedImports()
        licenseHeaderFile("header.txt")
        importOrderFile("spotless.importorder")
    }

    kotlin {
        ktlint()
    }

    kotlinGradle {
        ktlint()
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
}
