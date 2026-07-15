package com.vernflow.config;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads root-level .env defaults for local development without overriding real process env vars.
 */
public final class ProjectEnvLoader {

    private static final String ENV_FILE_NAME = ".env";
    private static final String ENV_LOCAL_FILE_NAME = ".env.local";

    private ProjectEnvLoader() {
    }

    public static Map<String, Object> loadDefaults() {
        Path projectRoot = locateProjectRoot();
        Map<String, Object> defaults = new LinkedHashMap<>();
        mergeEnvFile(defaults, projectRoot.resolve(ENV_FILE_NAME));
        mergeEnvFile(defaults, projectRoot.resolve(ENV_LOCAL_FILE_NAME));
        return defaults;
    }

    private static Path locateProjectRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve("settings.gradle.kts")) || Files.exists(cursor.resolve(ENV_FILE_NAME))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private static void mergeEnvFile(Map<String, Object> target, Path envFile) {
        if (!Files.exists(envFile)) {
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return;
        }

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line) || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            if (!StringUtils.hasText(key) || System.getenv().containsKey(key) || System.getProperties().containsKey(key)) {
                continue;
            }
            target.put(key, value);
        }
    }
}
