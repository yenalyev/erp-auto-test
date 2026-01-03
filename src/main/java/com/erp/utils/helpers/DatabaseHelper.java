package com.erp.utils.helpers;

import com.erp.utils.TestcontainersManager;
import com.erp.utils.config.ConfigProvider;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;

@Slf4j
public class DatabaseHelper {

    private Connection connection;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseHelper() {
        String profile = System.getProperty("env", "local");

        if ("local".equals(profile)) {
            // Для Testcontainers
            if (TestcontainersManager.isRunning()) {
                this.jdbcUrl = TestcontainersManager.getDatabaseUrl();
                this.username = TestcontainersManager.getDatabaseUsername();
                this.password = TestcontainersManager.getDatabasePassword();
            } else {
                throw new IllegalStateException("Testcontainers not started for local profile");
            }
        } else {
            // ✅ Використовуємо ConfigProvider
            this.jdbcUrl = ConfigProvider.getDbUrl();
            this.username = ConfigProvider.getDbUsername();
            this.password = ConfigProvider.getDbPassword();
        }

        log.info("🗄️  DatabaseHelper initialized");
        log.debug("   JDBC URL: {}", maskPassword(jdbcUrl));

        connect();
    }

    private void connect() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            log.info("✅ Database connection established");
        } catch (ClassNotFoundException e) {
            log.error("❌ PostgreSQL Driver not found", e);
            throw new RuntimeException("PostgreSQL Driver not found", e);
        } catch (SQLException e) {
            log.error("❌ Failed to connect to database: {}", e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery(sql);
    }

    public int executeUpdate(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeUpdate(sql);
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("✅ Database connection closed");
            }
        } catch (SQLException e) {
            log.error("❌ Failed to close connection: {}", e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private String maskPassword(String url) {
        if (url == null) return "null";
        return url.replaceAll("password=[^&;]*", "password=****");
    }
}