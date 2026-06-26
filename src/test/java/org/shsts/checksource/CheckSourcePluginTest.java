package org.shsts.checksource;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckSourcePluginTest {
    private static final String KOTLIN_VERSION = "1.9.20";

    @TempDir
    Path projectDir;

    @Test
    void applyingPluginAlsoAppliesJavaPlugin() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                tasks.register("verifyJavaPlugin") {
                    doLast {
                        check(plugins.hasPlugin("java"))
                    }
                }
                """);

        var result = runner("verifyJavaPlugin").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyJavaPlugin").getOutcome());
    }

    @Test
    void applyingPluginRegistersCheckSourceAndWiresItIntoCheck() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);

        var result = runner("check").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":check").getOutcome());
    }

    @Test
    void mainSourceViolationsFailCheckSourceAndWriteDefaultReport() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);
        writeSource("src/main/java/org/example/api/Api.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class Api {
                }
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/Api.java:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void cleanMainSourcesPassAndProduceEmptyReport() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);
        writeSource("src/main/java/org/example/api/Api.java", """
                package org.example.api;

                import org.example.model.ModelType;

                class Api {
                }
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals("", Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void extraMainJavaSourceDirectoryIsScanned() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                sourceSets {
                    main {
                        java.srcDir("src/generated/java")
                    }
                }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);
        writeSource("src/generated/java/org/example/api/GeneratedApi.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class GeneratedApi {
                }
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/GeneratedApi.java:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void testSourceViolationsAreIgnoredByDefault() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);
        writeSource("src/test/java/org/example/api/ApiTest.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class ApiTest {
                }
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals("", Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void testSourceViolationsFailAfterIncludeTest() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeTest()
                }
                """);
        writeSource("src/test/java/org/example/api/ApiTest.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class ApiTest {
                }
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/ApiTest.java:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void javaOnlyBuildsWithoutKotlinJvmPluginPassWhenIncludeKotlinIsNotCalled() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """);
        writeSource("src/main/java/org/example/api/Api.java", """
                package org.example.api;

                class Api {
                }
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
    }

    @Test
    void kotlinSourceViolationsAreIgnoredByDefaultWhenKotlinJvmPluginIsApplied() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/main/kotlin/org/example/api/Api.kt", """
                package org.example.api

                import org.example.core.CoreType

                class Api
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals("", Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void mainKotlinSourceViolationsFailAfterIncludeKotlin() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeKotlin()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/main/kotlin/org/example/api/Api.kt", """
                package org.example.api

                import org.example.core.CoreType

                class Api
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/Api.kt:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void testKotlinSourceViolationsAreIgnoredWithIncludeKotlinAlone() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeKotlin()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/test/kotlin/org/example/api/ApiTest.kt", """
                package org.example.api

                import org.example.core.CoreType

                class ApiTest
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals("", Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void testKotlinSourceViolationsFailAfterIncludeKotlinAndIncludeTest() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeKotlin()
                    includeTest()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/test/kotlin/org/example/api/ApiTest.kt", """
                package org.example.api

                import org.example.core.CoreType

                class ApiTest
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/ApiTest.kt:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void includeKotlinFailsClearlyWhenKotlinJvmPluginIsNotApplied() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    includeKotlin()
                }
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertTrue(result.getOutput().contains(
                "checkSource includeKotlin() requires the org.jetbrains.kotlin.jvm plugin"));
    }

    @Test
    void customKotlinSourceDirectoriesAreIncluded() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                kotlin {
                    sourceSets {
                        main {
                            kotlin.srcDir("src/custom/kotlin")
                        }
                    }
                }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeKotlin()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/custom/kotlin/org/example/api/CustomApi.kt", """
                package org.example.api

                import org.example.core.CoreType

                class CustomApi
                """);

        var result = runner("checkSource").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":checkSource").getOutcome());
        assertEquals(
                "org/example/api/CustomApi.kt:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void generatedKotlinSourceDirectoriesAreNotIncluded() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                kotlin {
                    sourceSets {
                        main {
                            kotlin.srcDir("build/generatedKotlin/main")
                        }
                    }
                }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core")
                    includeKotlin()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("build/generatedKotlin/main/org/example/api/GeneratedApi.kt", """
                package org.example.api

                import org.example.core.CoreType

                class GeneratedApi
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertEquals("", Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void isolatedKotlinHelperLoadsWithRealKotlinJvmPlugin() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    includeKotlin()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/main/kotlin/org/example/api/Api.kt", """
                package org.example.api

                class Api
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
    }

    @Test
    void methodBasedKotlinDslWorksFromConsumerBuild() throws IOException {
        writeBuild("""
                plugins {
                    id("org.shsts.checksource")
                    id("org.jetbrains.kotlin.jvm") version "%s"
                }

                repositories { mavenCentral() }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core", "integration")
                    banImport("core", "integration")
                    includeKotlin()
                    includeTest()
                }
                """.formatted(KOTLIN_VERSION));
        writeSource("src/main/java/org/example/api/Api.java", """
                package org.example.api;

                class Api {
                }
                """);
        writeSource("src/main/kotlin/org/example/api/KotlinApi.kt", """
                package org.example.api

                class KotlinApi
                """);

        var result = runner("checkSource").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSource").getOutcome());
        assertTrue(Files.exists(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    private void writeBuild(String buildFile) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"consumer\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), buildFile);
    }

    private void writeSource(String relativePath, String content) throws IOException {
        var sourceFile = projectDir.resolve(relativePath);
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, content);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withGradleVersion("8.5")
                .withArguments(arguments)
                .forwardOutput();
    }
}
