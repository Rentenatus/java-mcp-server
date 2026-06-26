package com.softtek.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LombokDetector {

    public record LombokInfo(boolean present, String version, String source) {}

    private static final Pattern MAVEN_COORD = Pattern.compile(
            "<groupId>\\s*org\\.projectlombok\\s*</groupId>\\s*<artifactId>\\s*lombok\\s*</artifactId>\\s*<version>\\s*([^<\\s]+)\\s*</version>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_COORD_NO_VERSION = Pattern.compile(
            "<groupId>\\s*org\\.projectlombok\\s*</groupId>\\s*<artifactId>\\s*lombok\\s*</artifactId>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_VERSION_PROPERTY = Pattern.compile(
            "<lombok\\.version>\\s*([^<\\s]+)\\s*</lombok\\.version>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADLE_DEP = Pattern.compile(
            "org\\.projectlombok\\s*:\\s*lombok\\s*:\\s*([0-9][^\\s\"']*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADLE_DEP_BOM = Pattern.compile(
            "org\\.projectlombok\\s*:\\s*lombok\\s*:\\s*\\$",
            Pattern.CASE_INSENSITIVE);

    public LombokInfo detect(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return new LombokInfo(false, null, null);
        }

        Path pom = projectDir.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            String content = readSafely(pom);
            if (content != null) {
                Matcher m = MAVEN_COORD.matcher(content);
                if (m.find()) {
                    return new LombokInfo(true, m.group(1), "pom.xml");
                }
                if (MAVEN_COORD_NO_VERSION.matcher(content).find()) {
                    Matcher vp = MAVEN_VERSION_PROPERTY.matcher(content);
                    if (vp.find()) {
                        return new LombokInfo(true, vp.group(1), "pom.xml");
                    }
                    return new LombokInfo(true, null, "pom.xml");
                }
            }
        }

        for (String name : new String[]{"build.gradle", "build.gradle.kts"}) {
            Path gradle = projectDir.resolve(name);
            if (Files.isRegularFile(gradle)) {
                String content = readSafely(gradle);
                if (content == null) continue;
                Matcher m = GRADLE_DEP.matcher(content);
                if (m.find()) {
                    return new LombokInfo(true, m.group(1), name);
                }
                if (GRADLE_DEP_BOM.matcher(content).find()) {
                    return new LombokInfo(true, null, name);
                }
            }
        }

        return new LombokInfo(false, null, null);
    }

    public boolean hasLombokInSources(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) return false;
        Path src = projectDir.resolve("src/main/java");
        if (!Files.isDirectory(src)) {
            src = projectDir.resolve("src");
            if (!Files.isDirectory(src)) {
                src = projectDir;
            }
        }
        try (var stream = Files.walk(src, 10)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            return content.contains("import lombok.")
                                    || LOMBOK_ANNOT_PATTERN.matcher(content).find();
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    private static final Pattern LOMBOK_ANNOT_PATTERN = Pattern.compile(
            "@(Data|Getter|Setter|Builder|Value|ToString|EqualsAndHashCode|NoArgsConstructor|RequiredArgsConstructor|AllArgsConstructor|With|SneakyThrows|Log|Synchronized|Locked|NonNull|Cleanup|UtilityClass|FieldDefaults|Wither|Accessors)\\b");

    private String readSafely(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static String normalizeVersion(String version) {
        if (version == null) return null;
        String v = version.trim();
        if (v.isEmpty()) return null;
        if (v.toLowerCase(Locale.ROOT).startsWith("v")) {
            v = v.substring(1);
        }
        return v;
    }
}
