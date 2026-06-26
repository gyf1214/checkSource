package org.shsts.checksource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SourceBoundaryChecker {
    private SourceBoundaryChecker() {}

    public static List<Violation> check(
            List<Path> sourceRoots, String topPackage, Map<String, List<String>> bannedImports) {
        var sourceFiles = sourceRoots.stream()
                .map(sourceRoot -> sourceRoot.resolve(topPackage.replace('.', '/')))
                .filter(Files::isDirectory)
                .flatMap(packageRoot -> javaFiles(packageRoot).stream())
                .toList();
        return check(sourceRoots, sourceFiles, topPackage, bannedImports);
    }

    public static List<Violation> check(
            List<Path> sourceRoots,
            List<Path> sourceFiles,
            String topPackage,
            Map<String, List<String>> bannedImports) {
        var violations = new ArrayList<Violation>();
        var fullyQualifiedType = Pattern.compile(
                "\\b(?:[a-z_][\\w]*\\.){2,}[A-Z][\\w]*(?:\\.[A-Z][\\w]*)*\\b");

        sourceFiles.stream()
                .filter(Files::isRegularFile)
                .filter(SourceBoundaryChecker::isSupportedSourceFile)
                .sorted(Comparator.comparing(path -> relativePath(sourceRoots, path)))
                .forEach(sourceFile -> checkFile(
                        violations,
                        sourceRoots,
                        sourceFile,
                        topPackage,
                        bannedImports,
                        fullyQualifiedType));
        return List.copyOf(violations);
    }

    private static void checkFile(
            List<Violation> violations,
            List<Path> sourceRoots,
            Path sourceFile,
            String topPackage,
            Map<String, List<String>> bannedImports,
            Pattern fullyQualifiedType) {
        try {
            var relativePath = relativePath(sourceRoots, sourceFile);
            var lines = Files.readAllLines(sourceFile);
            var declaredPackage = declaredPackage(lines);
            var bannedPackages = sourcePackage(topPackage, declaredPackage)
                    .map(sourcePackage -> bannedImports.getOrDefault(sourcePackage, List.of()))
                    .orElse(List.of());

            for (var i = 0; i < lines.size(); i++) {
                var importedType = importedType(lines.get(i));
                if (importedType == null) {
                    continue;
                }
                for (var bannedPackage : bannedPackages) {
                    if (importedType.startsWith(topPackage + "." + bannedPackage + ".")) {
                        violations.add(new Violation(relativePath, i + 1, "banned import " + importedType));
                    }
                }
            }

            var strippedLines = stripNonCode(Files.readString(sourceFile)).split("\\R", -1);
            for (var i = 0; i < strippedLines.length; i++) {
                var line = strippedLines[i].stripLeading();
                if (line.startsWith("package ") || line.startsWith("import ")) {
                    continue;
                }
                var matcher = fullyQualifiedType.matcher(strippedLines[i]);
                while (matcher.find()) {
                    violations.add(new Violation(
                            relativePath,
                            i + 1,
                            "fully qualified type " + matcher.group()));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<Path> javaFiles(Path packageRoot) {
        try (var stream = Files.walk(packageRoot)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> packageRoot.relativize(path).toString()))
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static boolean isSupportedSourceFile(Path sourceFile) {
        var filename = sourceFile.getFileName().toString();
        return filename.endsWith(".java") || filename.endsWith(".kt");
    }

    private static String relativePath(List<Path> sourceRoots, Path sourceFile) {
        return sourceRoots.stream()
                .filter(sourceRoot -> sourceFile.startsWith(sourceRoot))
                .max(Comparator.comparingInt(sourceRoot -> sourceRoot.getNameCount()))
                .map(sourceRoot -> sourceRoot.relativize(sourceFile))
                .orElse(sourceFile.getFileName())
                .toString()
                .replace('\\', '/');
    }

    private static String declaredPackage(List<String> lines) {
        for (var line : lines) {
            var trimmed = line.strip();
            if (!trimmed.startsWith("package ")) {
                continue;
            }
            var packageName = trimmed.substring("package ".length()).strip();
            if (packageName.endsWith(";")) {
                packageName = packageName.substring(0, packageName.length() - 1).strip();
            }
            return packageName;
        }
        return "";
    }

    private static Optional<String> sourcePackage(String topPackage, String declaredPackage) {
        var prefix = topPackage + ".";
        if (!declaredPackage.startsWith(prefix)) {
            return Optional.empty();
        }
        var remainder = declaredPackage.substring(prefix.length());
        var dot = remainder.indexOf('.');
        if (dot >= 0) {
            return Optional.of(remainder.substring(0, dot));
        }
        return remainder.isEmpty() ? Optional.empty() : Optional.of(remainder);
    }

    private static String importedType(String line) {
        var trimmed = line.strip();
        if (!trimmed.startsWith("import ")) {
            return null;
        }
        trimmed = trimmed.substring("import ".length()).strip();
        if (trimmed.startsWith("static ")) {
            trimmed = trimmed.substring("static ".length()).strip();
        }
        var alias = trimmed.indexOf(" as ");
        if (alias >= 0) {
            trimmed = trimmed.substring(0, alias).strip();
        }
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripNonCode(String source) {
        var result = new StringBuilder(source.length());
        var i = 0;
        var state = State.CODE;
        while (i < source.length()) {
            var c = source.charAt(i);
            switch (state) {
                case CODE -> {
                    if (startsWith(source, i, "\"\"\"")) {
                        result.append("   ");
                        i += 3;
                        state = State.TEXT_BLOCK;
                    } else if (startsWith(source, i, "//")) {
                        result.append("  ");
                        i += 2;
                        state = State.LINE_COMMENT;
                    } else if (startsWith(source, i, "/*")) {
                        result.append("  ");
                        i += 2;
                        state = State.BLOCK_COMMENT;
                    } else if (c == '"') {
                        result.append(' ');
                        i++;
                        state = State.STRING;
                    } else if (c == '\'') {
                        result.append(' ');
                        i++;
                        state = State.CHAR;
                    } else {
                        result.append(c);
                        i++;
                    }
                }
                case LINE_COMMENT -> {
                    if (c == '\n' || c == '\r') {
                        result.append(c);
                        i++;
                        state = State.CODE;
                    } else {
                        result.append(' ');
                        i++;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (startsWith(source, i, "*/")) {
                        result.append("  ");
                        i += 2;
                        state = State.CODE;
                    } else {
                        result.append(c == '\n' || c == '\r' ? c : ' ');
                        i++;
                    }
                }
                case STRING -> {
                    if (c == '\\' && i + 1 < source.length()) {
                        result.append("  ");
                        i += 2;
                    } else if (c == '"') {
                        result.append(' ');
                        i++;
                        state = State.CODE;
                    } else {
                        result.append(c == '\n' || c == '\r' ? c : ' ');
                        i++;
                    }
                }
                case CHAR -> {
                    if (c == '\\' && i + 1 < source.length()) {
                        result.append("  ");
                        i += 2;
                    } else if (c == '\'') {
                        result.append(' ');
                        i++;
                        state = State.CODE;
                    } else {
                        result.append(c == '\n' || c == '\r' ? c : ' ');
                        i++;
                    }
                }
                case TEXT_BLOCK -> {
                    if (startsWith(source, i, "\"\"\"")) {
                        result.append("   ");
                        i += 3;
                        state = State.CODE;
                    } else {
                        result.append(c == '\n' || c == '\r' ? c : ' ');
                        i++;
                    }
                }
            }
        }
        return result.toString();
    }

    private static boolean startsWith(String source, int index, String value) {
        return source.regionMatches(index, value, 0, value.length());
    }

    private enum State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHAR,
        TEXT_BLOCK
    }

    public record Violation(String relativePath, int lineNumber, String reason) {
        public String message() {
            return relativePath + ":" + lineNumber + ": " + reason;
        }
    }
}
