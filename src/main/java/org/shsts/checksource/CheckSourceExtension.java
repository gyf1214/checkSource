package org.shsts.checksource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public class CheckSourceExtension {
    private final Property<String> topPackage;
    private final MapProperty<String, List<String>> bannedImports;
    private final Property<Boolean> includeTest;

    @Inject
    public CheckSourceExtension(ObjectFactory objects) {
        topPackage = objects.property(String.class);
        bannedImports = objects.mapProperty(String.class, listType());
        includeTest = objects.property(Boolean.class).convention(false);
    }

    public Property<String> getTopPackage() {
        return topPackage;
    }

    public MapProperty<String, List<String>> getBannedImports() {
        return bannedImports;
    }

    public Property<Boolean> getIncludeTest() {
        return includeTest;
    }

    public void topPackage(String value) {
        topPackage.set(value);
    }

    public void banImport(String sourcePackage, String... bannedPackages) {
        var imports = new LinkedHashMap<>(bannedImports.getOrElse(Map.of()));
        var values = new ArrayList<>(imports.getOrDefault(sourcePackage, List.of()));
        values.addAll(List.of(bannedPackages));
        imports.put(sourcePackage, List.copyOf(values));
        bannedImports.set(imports);
    }

    public void includeTest() {
        includeTest.set(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<List<String>> listType() {
        return (Class) List.class;
    }
}
