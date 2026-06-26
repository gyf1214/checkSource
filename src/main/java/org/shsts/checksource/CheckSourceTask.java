package org.shsts.checksource;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

@CacheableTask
public abstract class CheckSourceTask extends DefaultTask {
    @Internal
    public abstract ConfigurableFileCollection getSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @Input
    public abstract Property<String> getTopPackage();

    @Input
    public abstract MapProperty<String, List<String>> getBannedImports();

    @Input
    public abstract Property<Boolean> getIncludeKotlin();

    @Internal
    public abstract Property<Boolean> getKotlinPluginPresent();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void run() {
        if (!getTopPackage().isPresent()) {
            throw new GradleException("checkSource requires topPackage(...)");
        }
        if (getIncludeKotlin().getOrElse(false) && !getKotlinPluginPresent().getOrElse(false)) {
            throw new GradleException("checkSource includeKotlin() requires the org.jetbrains.kotlin.jvm plugin");
        }

        var reportFile = getReportFile().get().getAsFile().toPath();
        try {
            var reportParent = reportFile.getParent();
            if (reportParent != null) {
                java.nio.file.Files.createDirectories(reportParent);
            }
            java.nio.file.Files.writeString(reportFile, "");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        var sourceRoots = getSourceRoots().getFiles().stream()
                .map(file -> file.toPath())
                .toList();
        var sourceFiles = getSourceFiles().getFiles().stream()
                .map(file -> file.toPath())
                .toList();
        var violations = SourceBoundaryChecker.check(
                sourceRoots,
                sourceFiles,
                getTopPackage().get(),
                getBannedImports().getOrElse(Map.of()));

        try {
            var report = String.join(
                    System.lineSeparator(),
                    violations.stream().map(SourceBoundaryChecker.Violation::message).toList());
            if (!report.isEmpty()) {
                report += System.lineSeparator();
            }
            java.nio.file.Files.writeString(reportFile, report);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (!violations.isEmpty()) {
            throw new GradleException("checkSource found violations. See " + reportFile);
        }
    }
}
