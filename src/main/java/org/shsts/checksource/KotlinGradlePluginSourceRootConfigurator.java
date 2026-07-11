package org.shsts.checksource;

import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;

import java.lang.reflect.InvocationTargetException;

public final class KotlinGradlePluginSourceRootConfigurator implements KotlinSourceRootConfigurator {
    @Override
    public void configure(Project project, CheckSourceTask task) {
        var sourceSets = kotlinSourceSets(project);
        var mainSources = kotlinSourceSet(sourceSets, "main");
        var testSources = kotlinSourceSet(sourceSets, "test");
        task.getMainKotlinSourceRoots().from(mainSources.getSourceDirectories());
        task.getMainKotlinSourceFiles().from(mainSources.getAsFileTree()
                .matching(pattern -> pattern.include("**/*.kt")));
        task.getTestKotlinSourceRoots().from(testSources.getSourceDirectories());
        task.getTestKotlinSourceFiles().from(testSources.getAsFileTree()
                .matching(pattern -> pattern.include("**/*.kt")));
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

}
