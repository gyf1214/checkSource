package org.shsts.checksource;

import org.gradle.api.Project;

public interface KotlinSourceRootConfigurator {
    void configure(Project project, CheckSourceTask task);
}
