import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// See https://github.com/JetBrains/gradle-intellij-plugin/
plugins {
    id("org.jetbrains.intellij.platform")
    id("java")
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
}

group = "edu.kit.kastel.sdq"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // See https://kotlinlang.org/docs/java-interop.html#nullability-annotations
    compilerOptions {
        freeCompilerArgs.add("-Xnullability-annotations=@org.jspecify.annotations:warn")
    }
}

dependencies {
    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.2")

    intellijPlatform {
        intellijIdea("2026.1.1")
        pluginVerifier()
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)

        val localJbr = System.getenv("LOCAL_JBR")
        if (localJbr != null) {
            jetbrainsRuntimeLocal(localJbr)
        }
    }

    implementation("edu.kit.kastel.sdq:artemis4j:9.2.0-SNAPSHOT")
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
        // Note: Added *.gradle.kts alongside *.gradle for the new build script
        target("*.gradle.kts", "*.gradle", ".gitattributes", ".gitignore", "*.md")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        toggleOffOn("@formatter:off", "@formatter:on")
        palantirJavaFormat("2.50.0").formatJavadoc(false)
        licenseHeaderFile("header.txt")
        importOrderFile("spotless.importorder")
    }
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
