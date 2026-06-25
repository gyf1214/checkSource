package org.shsts.checksource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckSourcePluginTest {
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
                "api/Api.java:3: banned import org.example.core.CoreType\n",
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
                "api/ApiTest.java:3: banned import org.example.core.CoreType\n",
                Files.readString(projectDir.resolve("build/reports/checkSource/violations.txt")));
    }

    @Test
    void methodBasedKotlinDslWorksFromConsumerBuild() throws IOException {
        writeBuild("""
                plugins { id("org.shsts.checksource") }

                checkSource {
                    topPackage("org.example")
                    banImport("api", "core", "integration")
                    banImport("core", "integration")
                    includeTest()
                }
                """);
        writeSource("src/main/java/org/example/api/Api.java", """
                package org.example.api;

                class Api {
                }
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
