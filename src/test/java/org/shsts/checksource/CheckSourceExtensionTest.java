package org.shsts.checksource;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckSourceExtensionTest {
    @Test
    void topPackageStoresPackageValue() {
        var extension = extension();

        extension.topPackage("org.example");

        assertEquals("org.example", extension.getTopPackage().get());
    }

    @Test
    void banImportAppendsAllBannedTargetsForSourcePackage() {
        var extension = extension();

        extension.banImport("api", "core", "integration");

        assertEquals(List.of("core", "integration"), extension.getBannedImports().get().get("api"));
    }

    @Test
    void repeatedBanImportCallsAppendForSameSourcePackage() {
        var extension = extension();

        extension.banImport("api", "core");
        extension.banImport("api", "integration", "content");

        assertEquals(List.of("core", "integration", "content"), extension.getBannedImports().get().get("api"));
    }

    @Test
    void includeTestFlipsTestSourceScanningOn() {
        var extension = extension();

        extension.includeTest();

        assertTrue(extension.getIncludeTest().get());
    }

    @Test
    void includeTestDefaultsToFalse() {
        var extension = extension();

        assertFalse(extension.getIncludeTest().get());
    }

    private static CheckSourceExtension extension() {
        var project = ProjectBuilder.builder().build();
        return project.getExtensions().create("checkSource", CheckSourceExtension.class);
    }
}
