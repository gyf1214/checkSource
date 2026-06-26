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
        writeSource("""
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

        var error = assertThrows(GradleException.class, task::run);

        assertTrue(error.getMessage().contains(reportFile.toString()));
        assertEquals("api/Api.java:3: banned import org.example.core.CoreType\n", Files.readString(reportFile));
    }

    @Test
    void cleanSourcesPassAndProduceEmptyReportFile() throws IOException {
        writeSource("""
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

    private CheckSourceTask task(Path reportFile) {
        var project = ProjectBuilder.builder().build();
        var task = project.getTasks().create("checkSource", CheckSourceTask.class);
        task.getReportFile().set(reportFile.toFile());
        return task;
    }

    private void writeSource(String content) throws IOException {
        var directory = tempDir.resolve("src/main/java/org/example/api");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("Api.java"), content);
    }
}
