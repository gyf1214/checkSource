package org.shsts.checksource;

import org.gradle.api.Plugin;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public final class CheckSourcePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        var extension = project.getExtensions().create("checkSource", CheckSourceExtension.class);
        var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        var mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        var testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);

        var checkSource = project.getTasks().register("checkSource", CheckSourceTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Checks source package boundaries.");
            task.getTopPackage().set(extension.getTopPackage());
            task.getBannedImports().set(extension.getBannedImports());
            task.getIncludeKotlin().set(extension.getIncludeKotlin());
            task.getKotlinPluginPresent().convention(false);
            task.getReportFile().convention(project.getLayout()
                    .getBuildDirectory()
                    .file("reports/checkSource/violations.txt"));
            task.getSourceRoots().from(mainSourceSet.getJava().getSrcDirs());
            task.getSourceFiles().from(mainSourceSet.getJava());
            task.getSourceRoots().from(project.provider(() -> extension.getIncludeTest().get() ?
                    testSourceSet.getJava().getSrcDirs() :
                    java.util.List.of()));
            task.getSourceFiles().from(project.provider(() -> extension.getIncludeTest().get() ?
                    testSourceSet.getJava() :
                    java.util.List.of()));
        });

        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", ignored ->
                checkSource.configure(task -> {
                    task.getKotlinPluginPresent().set(true);
                    kotlinSourceRootConfigurator().configure(project, extension, task);
                }));

        project.getTasks()
                .named(JavaBasePlugin.CHECK_TASK_NAME)
                .configure(task -> task.dependsOn(checkSource));
    }

    private static KotlinSourceRootConfigurator kotlinSourceRootConfigurator() {
        try {
            return (KotlinSourceRootConfigurator) Class.forName(
                            "org.shsts.checksource.KotlinGradlePluginSourceRootConfigurator")
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new GradleException("Unable to load Kotlin source root configurator", ex);
        }
    }
}
