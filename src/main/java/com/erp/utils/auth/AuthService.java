package com.erp.utils.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.erp.utils.config.ConfigReader;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AuthService {

    private final String baseUrl;
    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    // Кеш токенів для різних користувачів
    private static final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    // Час до експірації коли потрібно оновити токен (5 хвилин)
    private static final long REFRESH_BEFORE_EXPIRY_MS = 5 * 60 * 1000;

    public AuthService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.keycloakUrl = ConfigReader.getProperty("auth.keycloak.url");
        this.realm = ConfigReader.getProperty("auth.keycloak.realm");
        this.clientId = ConfigReader.getProperty("auth.keycloak.client.id");
        this.clientSecret = ConfigReader.getProperty("auth.keycloak.client.secret", "");

        log.info("🔐 AuthService initialized");
        log.debug("   Keycloak URL: {}", keycloakUrl);
        log.debug("   Realm: {}", realm);
        log.debug("   Client ID: {}", clientId);
    }

    /**
     * Отримати access token для користувача (з кешуванням)
     */
    @Step("Get access token for user: {username}")
    public String getAccessToken(String username, String password) {
        String cacheKey = username + ":" + password;

        // Перевіряємо кеш
        TokenCache cached = tokenCache.get(cacheKey);
        if (cached != null && !isTokenExpiringSoon(cached.accessToken)) {
            log.debug("✅ Using cached token for user: {}", username);
            return cached.accessToken;
        }

        // Отримуємо новий токен
        log.info("🔄 Requesting new token for user: {}", username);
        TokenResponse tokenResponse = requestToken(username, password);

        // Кешуємо
        tokenCache.put(cacheKey, new TokenCache(
                tokenResponse.accessToken,
                tokenResponse.refreshToken,
                System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
        ));

        log.info("✅ Token obtained successfully for user: {}", username);
        return tokenResponse.accessToken;
    }

    /**
     * Отримати токен через client credentials (для service account)
     */
    @Step("Get service account token")
    public String getServiceAccountToken() {
        log.info("🔄 Requesting service account token");

        String tokenEndpoint = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        Map<String, String> formParams = new HashMap<>();
        formParams.put("grant_type", "client_credentials");
        formParams.put("client_id", clientId);
        formParams.put("client_secret", clientSecret);

        Response response = RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParams(formParams)
                .post(tokenEndpoint);

        if (response.statusCode() != 200) {
            log.error("❌ Failed to get service account token: {}", response.asString());
            throw new RuntimeException("Service account authentication failed: " + response.statusLine());
        }

        String token = response.jsonPath().getString("access_token");
        log.info("✅ Service account token obtained");
        return token;
    }

    /**
     * Запит токена від Keycloak
     */
    private TokenResponse requestToken(String username, String password) {
        String tokenEndpoint = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        Map<String, String> formParams = new HashMap<>();
        formParams.put("grant_type", "password");
        formParams.put("client_id", clientId);
        formParams.put("username", username);
        formParams.put("password", password);

        // Додаємо client_secret якщо є
        if (clientSecret != null && !clientSecret.isEmpty()) {
            formParams.put("client_secret", clientSecret);
        }

        Response response = RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParams(formParams)
                .post(tokenEndpoint);

        if (response.statusCode() != 200) {
            log.error("❌ Authentication failed for user {}: {}", username, response.asString());
            throw new RuntimeException("Authentication failed: " + response.statusLine());
        }

        return new TokenResponse(
                response.jsonPath().getString("access_token"),
                response.jsonPath().getString("refresh_token"),
                response.jsonPath().getInt("expires_in")
        );
    }

    /**
     * Оновити токен через refresh token
     */
    @Step("Refresh access token")
    public String refreshToken(String refreshToken) {
        log.info("🔄 Refreshing access token");

        String tokenEndpoint = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        Map<String, String> formParams = new HashMap<>();
        formParams.put("grant_type", "refresh_token");
        formParams.put("client_id", clientId);
        formParams.put("refresh_token", refreshToken);

        if (clientSecret != null && !clientSecret.isEmpty()) {
            formParams.put("client_secret", clientSecret);
        }

        Response response = RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParams(formParams)
                .post(tokenEndpoint);

        if (response.statusCode() != 200) {
            log.error("❌ Token refresh failed: {}", response.asString());
            throw new RuntimeException("Token refresh failed: " + response.statusLine());
        }

        log.info("✅ Token refreshed successfully");
        return response.jsonPath().getString("access_token");
    }

    /**
     * Перевірка чи токен expired або скоро expired
     */
    public boolean isTokenExpired(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            Date expiresAt = jwt.getExpiresAt();

            if (expiresAt == null) {
                log.warn("⚠️  Token has no expiration date");
                return true;
            }

            boolean expired = expiresAt.before(new Date());

            if (expired) {
                log.debug("⏰ Token expired at: {}", expiresAt);
            }

            return expired;

        } catch (Exception e) {
            log.error("❌ Failed to decode token: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Перевірка чи токен скоро expired (протягом 5 хвилин)
     */
    private boolean isTokenExpiringSoon(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            Date expiresAt = jwt.getExpiresAt();

            if (expiresAt == null) {
                return true;
            }

            long timeUntilExpiry = expiresAt.getTime() - System.currentTimeMillis();
            boolean expiringSoon = timeUntilExpiry < REFRESH_BEFORE_EXPIRY_MS;

            if (expiringSoon) {
                log.debug("⏰ Token expires soon (in {} seconds)", timeUntilExpiry / 1000);
            }

            return expiringSoon;

        } catch (Exception e) {
            log.error("❌ Failed to check token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Отримати інформацію про користувача з токена
     */
    public UserInfo getUserInfo(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);

            return new UserInfo(
                    jwt.getClaim("sub").asString(),
                    jwt.getClaim("preferred_username").asString(),
                    jwt.getClaim("email").asString(),
                    jwt.getClaim("realm_access").asMap()
            );

        } catch (Exception e) {
            log.error("❌ Failed to extract user info from token: {}", e.getMessage());
            throw new RuntimeException("Failed to get user info", e);
        }
    }

    /**
     * Очистити кеш токенів
     */
    public static void clearTokenCache() {
        log.info("🧹 Clearing token cache");
        tokenCache.clear();
    }

    /**
     * Logout користувача (invalidate token)
     */
    @Step("Logout user")
    public void logout(String refreshToken) {
        log.info("🔓 Logging out user");

        String logoutEndpoint = String.format("%s/realms/%s/protocol/openid-connect/logout",
                keycloakUrl, realm);

        Map<String, String> formParams = new HashMap<>();
        formParams.put("client_id", clientId);
        formParams.put("refresh_token", refreshToken);

        if (clientSecret != null && !clientSecret.isEmpty()) {
            formParams.put("client_secret", clientSecret);
        }

        Response response = RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParams(formParams)
                .post(logoutEndpoint);

        if (response.statusCode() == 204) {
            log.info("✅ User logged out successfully");
        } else {
            log.warn("⚠️  Logout response: {}", response.statusLine());
        }
    }

    // Inner classes

    private static class TokenCache {
        final String accessToken;
        final String refreshToken;
        final long expiresAtMs;

        TokenCache(String accessToken, String refreshToken, long expiresAtMs) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static class TokenResponse {
        final String accessToken;
        final String refreshToken;
        final int expiresIn;

        TokenResponse(String accessToken, String refreshToken, int expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    public static class UserInfo {
        private final String userId;
        private final String username;
        private final String email;
        private final Map<String, Object> roles;

        public UserInfo(String userId, String username, String email, Map<String, Object> roles) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.roles = roles;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public Map<String, Object> getRoles() { return roles; }

        @Override
        public String toString() {
            return String.format("UserInfo{username='%s', email='%s'}", username, email);
        }
    }
}