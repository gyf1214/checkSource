package org.shsts.checksource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class SourceBoundaryChecker {
    private SourceBoundaryChecker() {}

    public static List<Violation> check(
            List<Path> sourceRoots, String topPackage, Map<String, List<String>> bannedImports) {
        var violations = new ArrayList<Violation>();
        var topPackagePath = topPackage.replace('.', '/');
        var fullyQualifiedType = Pattern.compile(
                "\\b" + Pattern.quote(topPackage) + "(?:\\.[a-z][A-Za-z0-9_]*)+\\.[A-Z][A-Za-z0-9_]*\\b");

        for (var sourceRoot : sourceRoots) {
            var packageRoot = sourceRoot.resolve(topPackagePath);
            if (!Files.isDirectory(packageRoot)) {
                continue;
            }
            for (var sourceFile : javaFiles(packageRoot)) {
                checkFile(violations, packageRoot, sourceFile, topPackage, bannedImports, fullyQualifiedType);
            }
        }
        return List.copyOf(violations);
    }

    private static void checkFile(
            List<Violation> violations,
            Path packageRoot,
            Path sourceFile,
            String topPackage,
            Map<String, List<String>> bannedImports,
            Pattern fullyQualifiedType) {
        try {
            var relativePath = packageRoot.relativize(sourceFile).toString().replace('\\', '/');
            var sourcePackage = sourceTopLevelPackage(packageRoot, sourceFile);
            var bannedPackages = bannedImports.getOrDefault(sourcePackage, List.of());
            var lines = Files.readAllLines(sourceFile);

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

    private static String sourceTopLevelPackage(Path packageRoot, Path sourceFile) {
        var relative = packageRoot.relativize(sourceFile);
        if (relative.getNameCount() <= 1) {
            return "";
        }
        return relative.getName(0).toString();
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
        if (!trimmed.endsWith(";")) {
            return null;
        }
        return trimmed.substring(0, trimmed.length() - 1).strip();
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
