package org.shsts.checksource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceBoundaryCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsBannedNormalImport() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class Api {
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertEquals(List.of("api/Api.java:3: banned import org.example.core.CoreType"), messages(violations));
    }

    @Test
    void reportsBannedStaticImport() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                import static org.example.core.CoreType.VALUE;

                class Api {
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertEquals(List.of("api/Api.java:3: banned import org.example.core.CoreType.VALUE"), messages(violations));
    }

    @Test
    void allowsUnbannedProjectImportsAndExternalImports() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                import java.util.List;
                import org.example.model.ModelType;

                class Api {
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    @Test
    void reportsFullyQualifiedNamesInJavaSourceBodies() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                class Api {
                    private org.example.core.CoreType value;
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertEquals(List.of("api/Api.java:4: fully qualified type org.example.core.CoreType"), messages(violations));
    }

    @Test
    void reportsExternalFullyQualifiedNamesInJavaSourceBodies() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                class Api {
                    private java.util.ArrayList<String> value;
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertEquals(List.of("api/Api.java:4: fully qualified type java.util.ArrayList"), messages(violations));
    }

    @Test
    void ignoresFullyQualifiedNamesInPackageAndImportLines() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                import org.example.core.CoreType;

                class Api {
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of());

        assertTrue(violations.isEmpty());
    }

    @Test
    void ignoresFullyQualifiedNamesInCommentsStringsCharsAndTextBlocks() throws IOException {
        writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                class Api {
                    // org.example.core.LineComment
                    /* org.example.core.BlockComment */
                    String text = "org.example.core.StringType";
                    char dot = '.';
                    String block = \"\"\"
                            org.example.core.TextBlockType
                            \"\"\";
                }
                """);

        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    @Test
    void scansFilesUnderConfiguredJavaSourceRoots() throws IOException {
        writeSource("src/main/java", "api", "MainApi.java", """
                package org.example.api;

                class MainApi {
                    org.example.core.CoreType value;
                }
                """);
        writeSource("src/generated/java", "api", "GeneratedApi.java", """
                package org.example.api;

                class GeneratedApi {
                    org.example.core.GeneratedType value;
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java"), tempDir.resolve("src/generated/java")),
                Map.of("api", List.of("core")));

        assertEquals(List.of(
                "api/MainApi.java:4: fully qualified type org.example.core.CoreType",
                "api/GeneratedApi.java:4: fully qualified type org.example.core.GeneratedType"), messages(violations));
    }

    @Test
    void skipsMissingPackageRoots() {
        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    private List<SourceBoundaryChecker.Violation> check(
            List<Path> sourceRoots, Map<String, List<String>> bannedImports) {
        return SourceBoundaryChecker.check(sourceRoots, "org.example", bannedImports);
    }

    private static List<String> messages(List<SourceBoundaryChecker.Violation> violations) {
        return violations.stream()
                .map(SourceBoundaryChecker.Violation::message)
                .toList();
    }

    private void writeSource(
            String sourceRoot, String sourcePackage, String filename, String content) throws IOException {
        var directory = tempDir.resolve(sourceRoot).resolve("org/example").resolve(sourcePackage);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), content);
    }
}
