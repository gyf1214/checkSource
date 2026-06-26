package org.shsts.checksource;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckSourceTaskTest {
    @TempDir
    Path tempDir;

    @Test
    void sourceViolationsFailTaskAndWriteReportFile() throws IOException {
        var source = writeSource("Api.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class Api {
                }
                """);
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);
        task.getTopPackage().set("org.example");
        task.getBannedImports().set(Map.of("api", List.of("core")));
        task.getSourceRoots().from(tempDir.resolve("src/main/java"));
        task.getSourceFiles().from(source);

        var error = assertThrows(GradleException.class, task::run);

        assertTrue(error.getMessage().contains(reportFile.toString()));
        assertEquals(
                "org/example/api/Api.java:3: banned import org.example.core.CoreType\n",
                Files.readString(reportFile));
    }

    @Test
    void cleanSourcesPassAndProduceEmptyReportFile() throws IOException {
        var source = writeSource("Api.java", """
                package org.example.api;

                import org.example.model.ModelType;

                class Api {
                }
                """);
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);
        task.getTopPackage().set("org.example");
        task.getBannedImports().set(Map.of("api", List.of("core")));
        task.getSourceRoots().from(tempDir.resolve("src/main/java"));
        task.getSourceFiles().from(source);

        task.run();

        assertEquals("", Files.readString(reportFile));
    }

    @Test
    void missingTopPackageFailsWithClearTaskError() {
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);

        var error = assertThrows(GradleException.class, task::run);

        assertEquals("checkSource requires topPackage(...)", error.getMessage());
    }

    @Test
    void includeKotlinWithoutKotlinJvmPluginFailsClearly() throws IOException {
        var source = writeSource("Api.kt", """
                package org.example.api

                class Api {
                }
                """);
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);
        task.getTopPackage().set("org.example");
        task.getBannedImports().set(Map.of());
        task.getSourceRoots().from(tempDir.resolve("src/main/java"));
        task.getSourceFiles().from(source);
        task.getIncludeKotlin().set(true);
        task.getKotlinPluginPresent().set(false);

        var error = assertThrows(GradleException.class, task::run);

        assertEquals("checkSource includeKotlin() requires the org.jetbrains.kotlin.jvm plugin", error.getMessage());
    }

    @Test
    void passesOnlyFilteredSourceFilesToChecker() throws IOException {
        var included = writeSource("Included.java", """
                package org.example.api;

                class Included {
                }
                """);
        writeSource("Excluded.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class Excluded {
                }
                """);
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);
        task.getTopPackage().set("org.example");
        task.getBannedImports().set(Map.of("api", List.of("core")));
        task.getSourceRoots().from(tempDir.resolve("src/main/java"));
        task.getSourceFiles().from(included);

        task.run();

        assertEquals("", Files.readString(reportFile));
    }

    @Test
    void nonSourceFilesUnderSourceRootsDoNotAffectCheckerBehavior() throws IOException {
        var source = writeSource("Api.java", """
                package org.example.api;

                class Api {
                }
                """);
        var resource = tempDir.resolve("src/main/java/org/example/api/config.properties");
        Files.writeString(resource, "type=org.example.core.CoreType");
        var reportFile = tempDir.resolve("build/reports/checkSource/violations.txt");
        var task = task(reportFile);
        task.getTopPackage().set("org.example");
        task.getBannedImports().set(Map.of("api", List.of("core")));
        task.getSourceRoots().from(tempDir.resolve("src/main/java"));
        task.getSourceFiles().from(source);

        task.run();

        assertEquals("", Files.readString(reportFile));
    }

    private CheckSourceTask task(Path reportFile) {
        var project = ProjectBuilder.builder().build();
        var task = project.getTasks().create("checkSource", CheckSourceTask.class);
        task.getReportFile().set(reportFile.toFile());
        return task;
    }

    private Path writeSource(String filename, String content) throws IOException {
        var directory = tempDir.resolve("src/main/java/org/example/api");
        Files.createDirectories(directory);
        var source = directory.resolve(filename);
        Files.writeString(source, content);
        return source;
    }
}
