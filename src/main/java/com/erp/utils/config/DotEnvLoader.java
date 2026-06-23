package com.erp.utils.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code .env.{profile}} from the project root into system properties
 * before Owner merges config. Existing env vars and {@code -D} flags are not overridden.
 */
@Slf4j
final class DotEnvLoader {

    private static final Map<String, String> ENV_TO_PROPERTY = buildMappings();

    private DotEnvLoader() {
    }

    private static Map<String, String> buildMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("CLIENT_SECRET", "bot.oauth.client.secret");
        mappings.put("BOT_OAUTH_CLIENT_SECRET", "bot.oauth.client.secret");
        mappings.put("CLIENT_ID", "bot.oauth.client.id");
        mappings.put("BOT_OAUTH_CLIENT_ID", "bot.oauth.client.id");
        mappings.put("GET_TOKEN_URL", "bot.oauth.token.url");
        mappings.put("BOT_OAUTH_TOKEN_URL", "bot.oauth.token.url");
        mappings.put("GRANT_TYPE", "bot.oauth.grant.type");
        mappings.put("GET_DATA_URL", "bot.whatsapp.data.url");
        mappings.put("GET_RELOCATIONS_DATA_URL", "bot.delivery.data.url");
        return Map.copyOf(mappings);
    }

    static void loadForProfile(String env) {
        Path file = resolveEnvFile(env);
        if (file == null) {
            return;
        }
        try {
            int loaded = 0;
            for (String line : Files.readAllLines(file)) {
                loaded += applyLine(line.strip());
            }
            log.info("Loaded {} entries from {}", loaded, file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", file, e.getMessage());
        }
    }

    private static Path resolveEnvFile(String env) {
        Path root = resolveProjectRoot();
        Path direct = root.resolve(".env." + env);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if ("staging".equals(env) || "stage".equals(env)) {
            Path staging = root.resolve(".env.staging");
            if (Files.isRegularFile(staging)) {
                return staging;
            }
        }
        Path generic = root.resolve(".env");
        return Files.isRegularFile(generic) ? generic : null;
    }

    private static Path resolveProjectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("pom.xml"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) {
            return parent;
        }
        return cwd;
    }

    private static int applyLine(String line) {
        if (line.isEmpty() || line.startsWith("#")) {
            return 0;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
            return 0;
        }
        String key = line.substring(0, eq).strip();
        String value = unquote(line.substring(eq + 1).strip());
        if (value.isEmpty()) {
            return 0;
        }

        String propertyKey = ENV_TO_PROPERTY.getOrDefault(key, key);
        if (System.getenv(key) != null) {
            return 0;
        }
        if (System.getProperty(propertyKey) != null) {
            return 0;
        }
        System.setProperty(propertyKey, value);
        return 1;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
