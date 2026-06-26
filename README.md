# Check Source Plugin

`org.shsts.checksource` is a Gradle plugin that checks Java and opt-in Kotlin source package boundaries from Gradle source sets.

The plugin registers a cacheable `checkSource` verification task and wires it into Gradle's `check` task. It scans selected Java files, and Kotlin files when enabled, and reports:

- imports from banned sibling packages, such as `org.example.api` importing `org.example.core`
- static imports from banned sibling packages
- fully qualified type names used in source bodies, excluding package/import lines and text inside comments, string literals, char literals, and text blocks

Reports are written to `build/reports/checkSource/violations.txt`. The task fails when any violation is found.

## Installation

Add the plugin repository and apply the plugin:

```kotlin
pluginManagement {
    repositories {
        maven("https://www.shsts.org/m2")
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
plugins {
    id("org.shsts.checksource") version "0.1.1"
}
```

## Configuration

Configure the `checkSource` extension in the consuming build:

```kotlin
checkSource {
    topPackage("org.example")
    banImport("api", "core", "integration")
    banImport("core", "integration")
    includeKotlin()
    includeTest()
}
```

`topPackage(...)` is required. It defines the package root used for import-boundary rules.

`banImport(sourcePackage, bannedPackages...)` bans imports where code in `topPackage.sourcePackage` imports a type from any listed sibling package. Repeated calls for the same source package append more banned targets.

`includeTest()` is optional. By default, only `sourceSets.main.java.srcDirs` are scanned. Calling `includeTest()` also scans `sourceSets.test.java.srcDirs`.

`includeKotlin()` is optional. Kotlin scanning is off by default, even when the consuming build applies `org.jetbrains.kotlin.jvm`. Calling `includeKotlin()` requires that the consuming build apply `org.jetbrains.kotlin.jvm`; otherwise `checkSource` fails with a clear configuration error.

When `includeKotlin()` is enabled, the plugin scans `kotlin.sourceSets.main.kotlin.srcDirs`. Test Kotlin sources are scanned only when both `includeKotlin()` and `includeTest()` are enabled. Custom Kotlin source directories added through the Kotlin JVM source-set DSL are included automatically.

Kotlin roots containing a `generatedKotlin` path segment are excluded. Generated source checking can be added later as a separate option if needed.

The plugin applies Gradle's `java` plugin so Java source-set metadata is available. Additional Java source directories added to the main or test source set are included automatically. Resource roots and non-source files are not scanned.

## Running

Run the check directly:

```sh
./gradlew checkSource
```

Or run it as part of the standard verification lifecycle:

```sh
./gradlew check
```

When violations are found, Gradle fails the task and points to `build/reports/checkSource/violations.txt`.

## Development

This repository is a Java 21 Gradle plugin project using the Gradle wrapper. Run the full verification suite with:

```sh
./gradlew check
```
