package dev.checksource;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class CheckSourcePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("checkSourceSmoke", task -> {
            task.setGroup("verification");
            task.setDescription("Runs a smoke check for the checkSource Gradle plugin.");
            task.doLast(action -> project.getLogger().lifecycle("checkSource plugin smoke task executed"));
        });
    }
}
