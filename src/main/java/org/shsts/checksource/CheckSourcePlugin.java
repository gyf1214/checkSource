package org.shsts.checksource;

import org.gradle.api.Plugin;
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
            task.getReportFile().convention(project.getLayout()
                    .getBuildDirectory()
                    .file("reports/checkSource/violations.txt"));
            task.getSourceRoots().from(mainSourceSet.getJava().getSrcDirs());
            task.getSourceRoots().from(project.provider(() -> extension.getIncludeTest().get() ?
                    testSourceSet.getJava().getSrcDirs() :
                    java.util.List.of()));
        });

        project.getTasks()
                .named(JavaBasePlugin.CHECK_TASK_NAME)
                .configure(task -> task.dependsOn(checkSource));
    }
}
