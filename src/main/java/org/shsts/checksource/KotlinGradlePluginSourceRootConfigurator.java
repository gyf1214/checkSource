package org.shsts.checksource;

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public final class KotlinGradlePluginSourceRootConfigurator implements KotlinSourceRootConfigurator {
    @Override
    public void configure(Project project, CheckSourceExtension extension, CheckSourceTask task) {
        var sourceSets = kotlinSourceSets(project);
        configureSourceSet(project, task, extension.getIncludeKotlin(), kotlinSourceSet(sourceSets, "main"));
        configureSourceSet(project, task, extension.getIncludeKotlin().zip(extension.getIncludeTest(), (kotlin, test) -> kotlin && test),
                kotlinSourceSet(sourceSets, "test"));
    }

    private static void configureSourceSet(
            Project project,
            CheckSourceTask task,
            Provider<Boolean> enabled,
            SourceDirectorySet kotlinSources) {
        var sourceRoots = project.provider(() -> {
            if (!enabled.getOrElse(false)) {
                return List.<File>of();
            }
            return kotlinSources.getSrcDirs().stream()
                    .filter(KotlinGradlePluginSourceRootConfigurator::isNotGeneratedKotlin)
                    .toList();
        });
        task.getSourceRoots().from(sourceRoots);
        task.getSourceFiles().from(project.files(sourceRoots).getAsFileTree().matching(pattern -> pattern.include("**/*.kt")));
    }

    @SuppressWarnings("unchecked")
    private static NamedDomainObjectContainer<Object> kotlinSourceSets(Project project) {
        try {
            var kotlinExtension = project.getExtensions().getByName("kotlin");
            return (NamedDomainObjectContainer<Object>) kotlinExtension.getClass()
                    .getMethod("getSourceSets")
                    .invoke(kotlinExtension);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new GradleException("Unable to inspect Kotlin JVM source sets", ex);
        }
    }

    private static SourceDirectorySet kotlinSourceSet(NamedDomainObjectContainer<Object> sourceSets, String name) {
        try {
            var sourceSet = sourceSets.getByName(name);
            return (SourceDirectorySet) sourceSet.getClass()
                    .getMethod("getKotlin")
                    .invoke(sourceSet);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassCastException ex) {
            throw new GradleException("Unable to inspect Kotlin JVM source set '" + name + "'", ex);
        }
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
