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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CacheableTask
public abstract class CheckSourceTask extends DefaultTask {
    @Internal
    public abstract ConfigurableFileCollection getMainJavaSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainJavaSourceFiles();

    @Internal
    public abstract ConfigurableFileCollection getTestJavaSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestJavaSourceFiles();

    @Internal
    public abstract ConfigurableFileCollection getMainKotlinSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainKotlinSourceFiles();

    @Internal
    public abstract ConfigurableFileCollection getTestKotlinSourceRoots();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestKotlinSourceFiles();

    @Input
    @Optional
    public abstract Property<String> getTopPackage();

    @Input
    public abstract MapProperty<String, List<String>> getBannedImports();

    @Input
    public abstract Property<Boolean> getIncludeKotlin();

    @Input
    public abstract Property<Boolean> getIncludeTest();

    @Internal
    public abstract Property<Boolean> getKotlinPluginPresent();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void run() {
        var bannedImports = getBannedImports().getOrElse(Map.of());
        if (!bannedImports.isEmpty() && !getTopPackage().isPresent()) {
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

        var sourceRoots = new ArrayList<File>();
        var sourceFiles = new ArrayList<File>();
        addSources(sourceRoots, sourceFiles, getMainJavaSourceRoots(), getMainJavaSourceFiles());
        if (getIncludeTest().getOrElse(false)) {
            addSources(sourceRoots, sourceFiles, getTestJavaSourceRoots(), getTestJavaSourceFiles());
        }
        if (getIncludeKotlin().getOrElse(false)) {
            addKotlinSources(
                    sourceRoots, sourceFiles, getMainKotlinSourceRoots(), getMainKotlinSourceFiles());
            if (getIncludeTest().getOrElse(false)) {
                addKotlinSources(
                        sourceRoots, sourceFiles, getTestKotlinSourceRoots(), getTestKotlinSourceFiles());
            }
        }
        var violations = SourceBoundaryChecker.check(
                sourceRoots.stream().map(File::toPath).toList(),
                sourceFiles.stream().map(File::toPath).toList(),
                getTopPackage().getOrElse(""),
                bannedImports);

        try {
            var report = String.join(
                    "\n",
                    violations.stream().map(SourceBoundaryChecker.Violation::message).toList());
            if (!report.isEmpty()) {
                report += "\n";
            }
            java.nio.file.Files.writeString(reportFile, report);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (!violations.isEmpty()) {
            throw new GradleException("checkSource found violations. See " + reportFile);
        }
    }

    private static void addSources(
            List<File> sourceRoots,
            List<File> sourceFiles,
            ConfigurableFileCollection roots,
            ConfigurableFileCollection files) {
        sourceRoots.addAll(roots.getFiles());
        sourceFiles.addAll(files.getFiles());
    }

    private static void addKotlinSources(
            List<File> sourceRoots,
            List<File> sourceFiles,
            ConfigurableFileCollection roots,
            ConfigurableFileCollection files) {
        var includedRoots = roots.getFiles().stream()
                .filter(CheckSourceTask::isNotGeneratedKotlin)
                .toList();
        sourceRoots.addAll(includedRoots);
        files.getFiles().stream()
                .filter(file -> includedRoots.stream().anyMatch(root -> file.toPath().startsWith(root.toPath())))
                .forEach(sourceFiles::add);
    }

    private static boolean isNotGeneratedKotlin(File sourceRoot) {
        for (var path : sourceRoot.toPath().normalize()) {
            if (path.toString().equals("generatedKotlin")) {
                return false;
            }
        }
        return true;
    }
}
