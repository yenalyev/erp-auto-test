package com.erp.utils.config;


import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class ConfigReader {
    private static Properties properties;
    private static final String CONFIG_FILE_PATTERN = "config-%s.properties";

    static {
        loadProperties();
    }

    private static void loadProperties() {
        String profile = System.getProperty("profile", "dev");  // ✅ За замовчуванням dev
        String fileName = String.format(CONFIG_FILE_PATTERN, profile);

        properties = new Properties();

        try (InputStream input = ConfigReader.class.getClassLoader()
                .getResourceAsStream(fileName)) {

            if (input == null) {
                log.warn("⚠️  Config file not found: {}, trying defaults...", fileName);
                // Спробуємо завантажити dev.properties як fallback
                tryLoadFallback();
                return;
            }

            properties.load(input);
            log.info("✅ Loaded configuration: {}", fileName);
            log.debug("   Profile: {}", profile);

        } catch (IOException e) {
            log.error("❌ Failed to load config: {}", e.getMessage());
            tryLoadFallback();
        }
    }

    private static void tryLoadFallback() {
        try (InputStream input = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config-dev.properties")) {

            if (input != null) {
                properties.load(input);
                log.info("✅ Loaded fallback configuration: config-dev.properties");
            } else {
                log.error("❌ No configuration file found!");
            }
        } catch (IOException e) {
            log.error("❌ Failed to load fallback config: {}", e.getMessage());
        }
    }

    public static String getProperty(String key) {
        String value = properties.getProperty(key);

        // Підтримка environment variables (формат ${VAR_NAME:default})
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            return resolveEnvironmentVariable(value);
        }

        return value;
    }

    public static String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    public static int getPropertyAsInt(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("⚠️  Invalid integer value for '{}': {}, using default: {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    public static boolean getPropertyAsBoolean(String key, boolean defaultValue) {
        String value = getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Resolve environment variable з формату ${VAR_NAME:default}
     */
    private static String resolveEnvironmentVariable(String value) {
        // Видаляємо ${ і }
        String content = value.substring(2, value.length() - 1);

        // Розділяємо на ім'я і default значення
        String[] parts = content.split(":", 2);
        String varName = parts[0];
        String defaultVal = parts.length > 1 ? parts[1] : "";

        // Шукаємо в environment variables
        String envValue = System.getenv(varName);

        if (envValue != null && !envValue.isEmpty()) {
            log.debug("✅ Resolved env variable: {} = {}", varName, "***");
            return envValue;
        }

        log.debug("⚠️  Env variable '{}' not found, using default: {}", varName, defaultVal);
        return defaultVal;
    }

    /**
     * Reload configuration (корисно для тестів)
     */
    public static void reload() {
        loadProperties();
    }

    /**
     * Отримати поточний профіль
     */
    public static String getCurrentProfile() {
        return System.getProperty("profile", "dev");
    }
}