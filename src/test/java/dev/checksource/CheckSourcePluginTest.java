package dev.checksource;

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
    void pluginRegistersSmokeTask() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"smoke\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), "plugins { id(\"dev.checksource.plugin\") }\n");

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withGradleVersion("8.5")
                .withArguments("checkSourceSmoke", "--stacktrace")
                .build();

        assertTrue(result.getOutput().contains("checkSource plugin smoke task executed"));
        assertTrue(result.task(":checkSourceSmoke").getOutcome() == TaskOutcome.SUCCESS);
    }
}
