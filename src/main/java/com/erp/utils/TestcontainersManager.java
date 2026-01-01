package com.erp.utils;


import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@Slf4j
public class TestcontainersManager {

    private static PostgreSQLContainer<?> postgresContainer;
    private static GenericContainer<?> keycloakContainer;
    private static GenericContainer<?> appContainer; // Якщо твій Spring Boot також в контейнері
    private static Network network;
    private static boolean isStarted = false;

    /**
     * Запуск всіх контейнерів
     */
    public static void start() {
        if (isStarted) {
            log.info("⚠️  Testcontainers already started");
            return;
        }

        log.info("🐳 Starting Testcontainers...");

        // Створюємо мережу для контейнерів
        network = Network.newNetwork();

        // Запускаємо PostgreSQL
        startPostgres();

        // Запускаємо Keycloak (опціонально)
        startKeycloak();

        isStarted = true;
        log.info("✅ All Testcontainers started successfully");
    }

    /**
     * Запуск PostgreSQL контейнера
     */
    private static void startPostgres() {
        log.info("🐳 Starting PostgreSQL container...");

        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("erp_test")
                .withUsername("test")
                .withPassword("test")
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withExposedPorts(5432)
                // Якщо є init script
                // .withInitScript("db/init.sql")
                // Додаткові налаштування
                .withEnv("POSTGRES_INITDB_ARGS", "-E UTF8")
                .withReuse(true) // Переиспользование контейнера між запусками
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

        postgresContainer.start();

        log.info("✅ PostgreSQL container started");
        log.info("   JDBC URL: {}", postgresContainer.getJdbcUrl());
        log.info("   Username: {}", postgresContainer.getUsername());
        log.info("   Database: {}", postgresContainer.getDatabaseName());
        log.info("   Port: {}", postgresContainer.getMappedPort(5432));
    }

    /**
     * Запуск Keycloak контейнера
     */
    private static void startKeycloak() {
        log.info("🐳 Starting Keycloak container...");

        keycloakContainer = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:22.0.5"))
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withExposedPorts(8080)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HTTP_ENABLED", "true")
                .withEnv("KC_HOSTNAME_STRICT", "false")
                .withCommand("start-dev")
                .waitingFor(Wait.forHttp("/health/ready")
                        .forPort(8080)
                        .withStartupTimeout(Duration.ofMinutes(2)));

        keycloakContainer.start();

        String keycloakUrl = String.format("http://%s:%d",
                keycloakContainer.getHost(),
                keycloakContainer.getMappedPort(8080));

        log.info("✅ Keycloak container started");
        log.info("   URL: {}", keycloakUrl);
        log.info("   Admin: admin / admin");

        // Тут можна додати автоматичне створення realm, client, користувачів
        // configureKeycloak(keycloakUrl);
    }

    /**
     * Зупинка всіх контейнерів
     */
    public static void stop() {
        if (!isStarted) {
            log.info("⚠️  Testcontainers not started");
            return;
        }

        log.info("🐳 Stopping Testcontainers...");

        if (keycloakContainer != null && keycloakContainer.isRunning()) {
            keycloakContainer.stop();
            log.info("✅ Keycloak container stopped");
        }

        if (postgresContainer != null && postgresContainer.isRunning()) {
            postgresContainer.stop();
            log.info("✅ PostgreSQL container stopped");
        }

        if (network != null) {
            network.close();
        }

        isStarted = false;
        log.info("✅ All Testcontainers stopped");
    }

    /**
     * Отримати JDBC URL для PostgreSQL
     */
    public static String getDatabaseUrl() {
        if (postgresContainer == null || !postgresContainer.isRunning()) {
            throw new IllegalStateException("PostgreSQL container is not running");
        }
        return postgresContainer.getJdbcUrl();
    }

    /**
     * Отримати username для PostgreSQL
     */
    public static String getDatabaseUsername() {
        if (postgresContainer == null || !postgresContainer.isRunning()) {
            throw new IllegalStateException("PostgreSQL container is not running");
        }
        return postgresContainer.getUsername();
    }

    /**
     * Отримати password для PostgreSQL
     */
    public static String getDatabasePassword() {
        if (postgresContainer == null || !postgresContainer.isRunning()) {
            throw new IllegalStateException("PostgreSQL container is not running");
        }
        return postgresContainer.getPassword();
    }

    /**
     * Отримати Keycloak URL
     */
    public static String getKeycloakUrl() {
        if (keycloakContainer == null || !keycloakContainer.isRunning()) {
            throw new IllegalStateException("Keycloak container is not running");
        }
        return String.format("http://%s:%d",
                keycloakContainer.getHost(),
                keycloakContainer.getMappedPort(8080));
    }

    /**
     * Отримати URL додатку (якщо він теж в контейнері)
     */
    public static String getApplicationUrl() {
        // Якщо твій Spring Boot додаток запускається локально
        return "http://localhost:8080";

        // Якщо Spring Boot теж в контейнері:
        // if (appContainer == null || !appContainer.isRunning()) {
        //     throw new IllegalStateException("Application container is not running");
        // }
        // return String.format("http://%s:%d",
        //         appContainer.getHost(),
        //         appContainer.getMappedPort(8080));
    }

    /**
     * Перевірка чи контейнери запущені
     */
    public static boolean isRunning() {
        return isStarted &&
                postgresContainer != null &&
                postgresContainer.isRunning();
    }

    /**
     * Отримати PostgreSQL контейнер для прямого доступу
     */
    public static PostgreSQLContainer<?> getPostgresContainer() {
        return postgresContainer;
    }
}