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

        assertEquals(
                List.of("org/example/api/Api.java:3: banned import org.example.core.CoreType"),
                messages(violations));
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

        assertEquals(
                List.of("org/example/api/Api.java:3: banned import org.example.core.CoreType.VALUE"),
                messages(violations));
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

        assertEquals(
                List.of("org/example/api/Api.java:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
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

        assertEquals(
                List.of("org/example/api/Api.java:4: fully qualified type java.util.ArrayList"),
                messages(violations));
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
        var mainApi = writeSource("src/main/java", "api", "MainApi.java", """
                package org.example.api;

                class MainApi {
                    org.example.core.CoreType value;
                }
                """);
        var generatedApi = writeSource("src/generated/java", "api", "GeneratedApi.java", """
                package org.example.api;

                class GeneratedApi {
                    org.example.core.GeneratedType value;
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java"), tempDir.resolve("src/generated/java")),
                List.of(mainApi, generatedApi),
                Map.of("api", List.of("core")));

        assertEquals(List.of(
                "org/example/api/GeneratedApi.java:4: fully qualified type org.example.core.GeneratedType",
                "org/example/api/MainApi.java:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void scansKotlinFilesOnlyWhenSelectedByCaller() throws IOException {
        var kotlinApi = writeSource("src/main/kotlin", "api", "Api.kt", """
                package org.example.api

                class Api {
                    private val value: org.example.core.CoreType? = null
                }
                """);

        var defaultViolations = check(
                List.of(tempDir.resolve("src/main/kotlin")),
                List.of(),
                Map.of("api", List.of("core")));
        var kotlinViolations = check(
                List.of(tempDir.resolve("src/main/kotlin")),
                List.of(kotlinApi),
                Map.of("api", List.of("core")));

        assertTrue(defaultViolations.isEmpty());
        assertEquals(
                List.of("org/example/api/Api.kt:4: fully qualified type org.example.core.CoreType"),
                messages(kotlinViolations));
    }

    @Test
    void ignoresNonSourceFilesUnderSourceRoots() throws IOException {
        var resource = tempDir.resolve("src/main/java/org/example/api/config.properties");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "type=org.example.core.CoreType");

        var violations = check(List.of(tempDir.resolve("src/main/java")), List.of(), Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    @Test
    void appliesFullyQualifiedTypeChecksOutsideTopPackage() throws IOException {
        var externalSource = tempDir.resolve("src/main/java/com/acme/Other.java");
        Files.createDirectories(externalSource.getParent());
        Files.writeString(externalSource, """
                package com.acme;

                class Other {
                    org.example.core.CoreType value;
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java")),
                List.of(externalSource),
                Map.of("api", List.of("core")));

        assertEquals(
                List.of("com/acme/Other.java:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void skipsBannedImportsForDeclaredPackagesOutsideTopPackage() throws IOException {
        var externalSource = tempDir.resolve("src/main/java/com/acme/Other.java");
        Files.createDirectories(externalSource.getParent());
        Files.writeString(externalSource, """
                package com.acme.api;

                import org.example.core.CoreType;

                class Other {
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java")),
                List.of(externalSource),
                Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    @Test
    void derivesBannedImportSourcePackageFromDeclaredPackage() throws IOException {
        var misplacedSource = tempDir.resolve("src/main/java/incorrect/path/Api.java");
        Files.createDirectories(misplacedSource.getParent());
        Files.writeString(misplacedSource, """
                package org.example.api;

                import org.example.core.CoreType;

                class Api {
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java")),
                List.of(misplacedSource),
                Map.of("api", List.of("core")));

        assertEquals(
                List.of("incorrect/path/Api.java:3: banned import org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void reportsPathsRelativeToSourceRoots() throws IOException {
        var source = writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                class Api {
                    org.example.core.CoreType value;
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/java")),
                List.of(source),
                Map.of("api", List.of("core")));

        assertEquals(
                List.of("org/example/api/Api.java:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void parsesKotlinImportsWithoutSemicolonsAndAliases() throws IOException {
        var source = writeSource("src/main/kotlin", "api", "Api.kt", """
                package org.example.api

                import org.example.core.CoreType
                import org.example.core.AliasedType as LocalType

                class Api {
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/kotlin")),
                List.of(source),
                Map.of("api", List.of("core")));

        assertEquals(List.of(
                "org/example/api/Api.kt:3: banned import org.example.core.CoreType",
                "org/example/api/Api.kt:4: banned import org.example.core.AliasedType"), messages(violations));
    }

    @Test
    void reportsFullyQualifiedNamesInKotlinSourceBodies() throws IOException {
        var source = writeSource("src/main/kotlin", "api", "Api.kt", """
                package org.example.api

                class Api {
                    private val value = org.example.core.CoreType()
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/kotlin")),
                List.of(source),
                Map.of("api", List.of("core")));

        assertEquals(
                List.of("org/example/api/Api.kt:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void ignoresFullyQualifiedNamesInsideKotlinRawStrings() throws IOException {
        var source = writeSource("src/main/kotlin", "api", "Api.kt", """
                package org.example.api

                class Api {
                    val text = \"\"\"
                        org.example.core.CoreType
                    \"\"\"
                }
                """);

        var violations = check(
                List.of(tempDir.resolve("src/main/kotlin")),
                List.of(source),
                Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    @Test
    void skipsMissingOrEmptySourceRoots() throws IOException {
        var source = writeSource("src/main/java", "api", "Api.java", """
                package org.example.api;

                class Api {
                    org.example.core.CoreType value;
                }
                """);

        var violations = check(
                List.of(
                        tempDir.resolve("missing"),
                        tempDir.resolve("src/empty/java"),
                        tempDir.resolve("src/main/java")),
                List.of(source),
                Map.of("api", List.of("core")));

        assertEquals(
                List.of("org/example/api/Api.java:4: fully qualified type org.example.core.CoreType"),
                messages(violations));
    }

    @Test
    void skipsMissingPackageRoots() {
        var violations = check(List.of(tempDir.resolve("src/main/java")), Map.of("api", List.of("core")));

        assertTrue(violations.isEmpty());
    }

    private List<SourceBoundaryChecker.Violation> check(
            List<Path> sourceRoots, Map<String, List<String>> bannedImports) {
        var topPackagePath = Path.of("org/example");
        var sourceFiles = sourceRoots.stream()
                .map(root -> root.resolve(topPackagePath))
                .filter(Files::isDirectory)
                .flatMap(root -> {
                    try {
                        return Files.walk(root);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
                .toList();
        return check(sourceRoots, sourceFiles, bannedImports);
    }

    private List<SourceBoundaryChecker.Violation> check(
            List<Path> sourceRoots, List<Path> sourceFiles, Map<String, List<String>> bannedImports) {
        return SourceBoundaryChecker.check(sourceRoots, sourceFiles, "org.example", bannedImports);
    }

    private static List<String> messages(List<SourceBoundaryChecker.Violation> violations) {
        return violations.stream()
                .map(SourceBoundaryChecker.Violation::message)
                .toList();
    }

    private Path writeSource(
            String sourceRoot, String sourcePackage, String filename, String content) throws IOException {
        var directory = tempDir.resolve(sourceRoot).resolve("org/example").resolve(sourcePackage);
        Files.createDirectories(directory);
        var source = directory.resolve(filename);
        Files.writeString(source, content);
        return source;
    }
}
