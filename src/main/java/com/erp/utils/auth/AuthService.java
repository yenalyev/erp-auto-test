package com.erp.utils.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.erp.enums.UserRole;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

import java.util.*;

@Slf4j
public class AuthService {

    private final String baseUrl;
    private final String keycloakUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private final Map<String, TokenInfo> tokenCache = new HashMap<>();
    private final Map<String, SessionInfo> sessionCache = new HashMap<>();

    private PlaywrightSessionProvider playwrightSessionProvider;

    public void setPlaywrightSessionProvider(PlaywrightSessionProvider provider) {
        this.playwrightSessionProvider = provider;
        log.info("🎭 PlaywrightSessionProvider registered in AuthService");
    }

    public AuthService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.keycloakUrl = ConfigProvider.getKeycloakUrl();
        this.realm = ConfigProvider.getKeycloakRealm();
        this.clientId = ConfigProvider.getKeycloakClientId();
        this.clientSecret = ConfigProvider.getKeycloakClientSecret();

        log.info("🔐 AuthService initialized");
        log.debug("   Keycloak URL: {}", keycloakUrl);
        log.debug("   Realm: {}", realm);
        log.debug("   Client ID: {}", clientId);
    }

    @Step("Get access token for user: {username}")
    public String getAccessToken(String username, String password) {
        String cacheKey = username + ":" + password;

        // Перевіряємо кеш
        if (tokenCache.containsKey(cacheKey)) {
            TokenInfo tokenInfo = tokenCache.get(cacheKey);
            if (!isTokenExpired(tokenInfo.token)) {
                log.debug("✅ Using cached token for user: {}", username);
                return tokenInfo.token;
            } else {
                log.debug("🔄 Cached token expired for user: {}", username);
                tokenCache.remove(cacheKey);
            }
        }

        // Запитуємо новий токен
        log.info("🔑 Requesting new token for user: {}", username);
        String token = requestToken(username, password);

        // Зберігаємо в кеш
        tokenCache.put(cacheKey, new TokenInfo(token, System.currentTimeMillis()));

        return token;
    }

    // ==================== Session-based Authentication ====================

    /**
     * Отримати сесійні куки для користувача з кешуванням
     * Використовується для тестування endpoints, які працюють через session
     */
    @Step("Get session cookies for user: {username}")
    public Map<String, String> getSessionForUser(String username, String password) {
        return getSessionForUser(username, password, "/");
    }

    /**
     * Отримати сесійні куки для користувача з кешуванням та вказаним targetRoute
     */
    @Step("Get session cookies for user: {username}, target: {targetRoute}")
    public Map<String, String> getSessionForUser(String username, String password, String targetRoute) {
        String cacheKey = username + ":" + password;

        // Перевіряємо кеш
        if (sessionCache.containsKey(cacheKey)) {
            SessionInfo sessionInfo = sessionCache.get(cacheKey);
            // Перевіряємо чи сесія ще валідна (використовуємо TTL 15 хвилин)
            if (System.currentTimeMillis() - sessionInfo.timestamp < 900000) { // 15 хвилин
                log.debug("✅ Using cached session for user: {}", username);
                return new HashMap<>(sessionInfo.cookies);
            } else {
                log.debug("🔄 Cached session expired for user: {}", username);
                sessionCache.remove(cacheKey);
            }
        }

        // Виконуємо новий логін через браузерний flow
        log.info("🍪 Requesting new session for user: {}", username);
        Map<String, String> sessionCookies = loginWithRedirectUri(username, password, targetRoute);

        // Зберігаємо в кеш
        sessionCache.put(cacheKey, new SessionInfo(sessionCookies, System.currentTimeMillis()));

        return sessionCookies;
    }

    @Step("Full browser login flow for user: {username}")
    public Map<String, String> loginViaBrowserFlow(String username, String password, String targetRoute) {
        log.info("🚀 Starting browser-like login flow. Target: {}", targetRoute);

        // 1. Ініціюємо OAuth потік через прямий тригер бекенда
        Response step1 = RestAssured.given()
                .urlEncodingEnabled(false)
                .redirects().follow(false)
                //.get(baseUrl+"?redirectUri=http://backend:8080/api/v1/resources");
                .get(baseUrl + "/oauth2/authorization/keycloak");

        String nextLocation = step1.getHeader("Location");
        String springSessionId = step1.getCookie("JSESSIONID");

        if (nextLocation == null) {
            throw new RuntimeException("❌ Failed to get redirect from /oauth2/authorization/keycloak. Check backend logs.");
        }

        // 2. Переходимо до Keycloak (обробляємо можливу проміжну сторінку вибору)
        Response step2 = RestAssured.given()
                .urlEncodingEnabled(false)
                .redirects().follow(false)
                .get(nextLocation);

        String keycloakUrl = step2.getHeader("Location") != null ? step2.getHeader("Location") : nextLocation;
        Response loginPageResponse = RestAssured.given()
                .urlEncodingEnabled(false)
                .get(keycloakUrl);

        // Перевірка: якщо ми все ще на бекенді (сторінка вибору), клікаємо по лінку
        if (loginPageResponse.asString().contains("Login with OAuth 2.0")) {
            log.warn("⚠️ Landed on Spring selection page. Extracting provider link...");
            String providerUrl = loginPageResponse.htmlPath().getString("**.find { it.name() == 'a' }.@href");
            keycloakUrl = providerUrl.startsWith("http") ? providerUrl : baseUrl + providerUrl;
            loginPageResponse = RestAssured.given()
                    .urlEncodingEnabled(false) // 👈 ДОДАЙ ТУТ
                    .get(keycloakUrl);
        }

        // 3. Парсимо сторінку логіну Keycloak
        String formActionUrl = loginPageResponse.htmlPath().getString("**.find { it.@id == 'kc-form-login' }.@action");
        String authSessionId = loginPageResponse.getCookie("AUTH_SESSION_ID");

        if (formActionUrl == null) {
            throw new RuntimeException("❌ Keycloak login form not found. Check if Keycloak is reachable.");
        }

        // 4. Надсилаємо дані форми в Keycloak
        Response postLoginResponse = RestAssured.given()
                .urlEncodingEnabled(false) // 👈 ДОДАЙ ТУТ (хоча для POST form params це менш критично)
                .contentType("application/x-www-form-urlencoded")
                .cookie("AUTH_SESSION_ID", authSessionId)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("credentialId", "")
                .redirects().follow(false)
                .post(formActionUrl);

        // 5. Отримуємо Callback URL від Keycloak
        String callbackUrl = postLoginResponse.getHeader("Location");
        if (callbackUrl == null) {
            throw new RuntimeException("❌ Keycloak login failed (check credentials). No redirect Location found.");
        }

        // 6. ФІКС: Виконуємо Callback на бекенд з ВИМКНЕНИМ енкодуванням
        // Це найважливіший момент для коректного порівняння 'state'
        Response finalResponse = RestAssured.given()
                .urlEncodingEnabled(false) // 👈 ВЖЕ Є
                .redirects().follow(false)
                .cookie("JSESSIONID", springSessionId)
                .get(callbackUrl);

        // 7. Перевіряємо, чи не виникла помилка (редирект на /login?error)
        String finalRedirect = finalResponse.getHeader("Location");
        if (finalRedirect != null && finalRedirect.contains("error")) {
            log.error("❌ Backend rejected the OAuth code. Check 'state' mismatch or client-secret.");
            throw new RuntimeException("Login failed: Backend returned redirect to " + finalRedirect);
        }

        // Збираємо куки. Якщо бекенд не прислав нову JSESSIONID, лишаємо стару (вона тепер авторизована)
        Map<String, String> sessionCookies = new HashMap<>(finalResponse.getCookies());
        sessionCookies.putIfAbsent("JSESSIONID", springSessionId);

        log.info("✅ Browser session established successfully.");
        return sessionCookies;
    }

    private String requestToken(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token",
                keycloakUrl, realm);

        try {
            Response response = RestAssured
                    .given()
                    .contentType("application/x-www-form-urlencoded")
                    .formParam("grant_type", "password")
                    .formParam("client_id", clientId)
                    .formParam("client_secret", clientSecret)
                    .formParam("username", username)
                    .formParam("password", password)
                    .when()
                    .post(tokenUrl)
                    .then()
                    .extract()
                    .response();

            if (response.statusCode() != 200) {
                log.error("❌ Authentication failed for user {}: HTTP {} {}",
                        username, response.statusCode(), response.statusLine());
                log.error("Response body: {}", response.body().asString());
                throw new RuntimeException("Authentication failed: " + response.statusLine());
            }

            String token = response.jsonPath().getString("access_token");
            log.info("✅ Token received for user: {}", username);

            return token;

        } catch (Exception e) {
            log.error("❌ Error during authentication", e);
            throw new RuntimeException("Authentication failed", e);
        }
    }

    // ==================== JWT Token Analysis ====================

    /**
     * Декодувати JWT token і отримати claims
     */
    public DecodedJWT decodeToken(String token) {
        try {
            return JWT.decode(token);
        } catch (Exception e) {
            log.error("❌ Failed to decode token: {}", e.getMessage());
            throw new RuntimeException("Invalid token", e);
        }
    }

    /**
     * Витягти username з токена
     */
    public String getUsername(String token) {
        DecodedJWT jwt = decodeToken(token);
        String username = jwt.getClaim("preferred_username").asString();
        if (username == null) {
            username = jwt.getSubject();
        }
        return username;
    }

    /**
     * Витягти ролі з токена
     */
    public List<String> getRoles(String token) {
        DecodedJWT jwt = decodeToken(token);
        List<String> roles = jwt.getClaim("role").asList(String.class);
        return roles != null ? roles : Collections.emptyList();
    }

    /**
     * Витягти permissions з токена
     */
    public List<String> getPermissions(String token) {
        DecodedJWT jwt = decodeToken(token);
        List<String> permissions = jwt.getClaim("permissions").asList(String.class);
        return permissions != null ? permissions : Collections.emptyList();
    }

    /**
     * Перевірити чи користувач має певну роль
     */
    public boolean hasRole(String token, String role) {
        List<String> roles = getRoles(token);
        boolean hasRole = roles.stream()
                .anyMatch(r -> r.equalsIgnoreCase(role));

        log.debug("🔍 User has role '{}': {}", role, hasRole);
        return hasRole;
    }

    /**
     * Перевірити чи користувач має певний permission
     */
    public boolean hasPermission(String token, String permission) {
        List<String> permissions = getPermissions(token);
        boolean hasPermission = permissions.contains(permission);

        log.debug("🔍 User has permission '{}': {}", permission, hasPermission);
        return hasPermission;
    }

    /**
     * Створити Spring Security Authentication об'єкт з токена
     * Використовується для тестування Spring Security RBAC
     */
    public Authentication getAuthentication(String token) {
        log.debug("🔐 Creating Spring Security Authentication from token");
        return SecurityMockProvider.getMockAuthentication(token);
    }

    /**
     * Вивести всю інформацію про токен (для debugging)
     */
    public void printTokenInfo(String token) {
        DecodedJWT jwt = decodeToken(token);

        log.info("📋 Token Information:");
        log.info("   Subject: {}", jwt.getSubject());
        log.info("   Username: {}", getUsername(token));
        log.info("   Roles: {}", getRoles(token));
        log.info("   Permissions: {}", getPermissions(token));
        log.info("   Issued At: {}", jwt.getIssuedAt());
        log.info("   Expires At: {}", jwt.getExpiresAt());
        log.info("   Issuer: {}", jwt.getIssuer());
    }

    // ==================== Token Validation ====================

    public boolean isTokenExpired(String token) {
        if (token == null || token.isEmpty()) {
            return true;
        }

        try {
            DecodedJWT jwt = JWT.decode(token);
            Date expiresAt = jwt.getExpiresAt();

            if (expiresAt == null) {
                log.warn("⚠️  Token has no expiration date");
                return true;
            }

            // Додаємо buffer 60 секунд
            boolean expired = expiresAt.getTime() - System.currentTimeMillis() < 60000;

            if (expired) {
                log.debug("⏰ Token expired at: {}", expiresAt);
            }

            return expired;

        } catch (Exception e) {
            log.error("❌ Failed to decode token: {}", e.getMessage());
            return true;
        }
    }

    public void clearCache() {
        tokenCache.clear();
        sessionCache.clear(); // 👈 Додав очищення кешу сесій
        log.debug("🗑️  Token and session cache cleared");
    }

    // ==================== Inner Classes ====================

    private static class TokenInfo {
        final String token;
        final long timestamp;

        TokenInfo(String token, long timestamp) {
            this.token = token;
            this.timestamp = timestamp;
        }
    }

    private static class SessionInfo {
        final Map<String, String> cookies;
        final long timestamp;

        SessionInfo(Map<String, String> cookies, long timestamp) {
            this.cookies = cookies;
            this.timestamp = timestamp;
        }
    }

    // ==================== Session Cache Management ====================

    /**
     * Очистити тільки кеш сесій (залишити токени)
     */
    public void clearSessionCache() {
        sessionCache.clear();
        log.debug("🗑️  Session cache cleared");
    }

    /**
     * Перевірити чи сесія для користувача ще валідна в кеші
     */
    public boolean isSessionValid(String username, String password) {
        String cacheKey = username + ":" + password;

        if (!sessionCache.containsKey(cacheKey)) {
            log.debug("❌ No cached session for user: {}", username);
            return false;
        }

        SessionInfo sessionInfo = sessionCache.get(cacheKey);
        // Перевіряємо TTL (15 хвилин)
        boolean isValid = System.currentTimeMillis() - sessionInfo.timestamp < 900000;

        if (!isValid) {
            log.debug("⏰ Cached session expired for user: {}", username);
            sessionCache.remove(cacheKey);
        } else {
            log.debug("✅ Valid cached session exists for user: {}", username);
        }

        return isValid;
    }

    /**
     * Перевірити чи сесія для ролі ще валідна в кеші
     */
    public boolean isSessionValidForRole(UserRole role) {
        if (role == UserRole.ANONYMOUS) {
            return true; // Анонімна "сесія" завжди валідна
        }
        return isSessionValid(role.getUsername(), role.getPassword());
    }

    /**
     * Видалити конкретну сесію з кешу
     */
    public void invalidateSession(String username, String password) {
        String cacheKey = username + ":" + password;
        sessionCache.remove(cacheKey);
        log.debug("🗑️  Session invalidated for user: {}", username);
    }

    /**
     * Отримати інформацію про кешовані сесії (для debugging)
     */
    public void printSessionCacheInfo() {
        log.info("📊 Session Cache Information:");
        log.info("   Total cached sessions: {}", sessionCache.size());

        sessionCache.forEach((key, sessionInfo) -> {
            long ageMinutes = (System.currentTimeMillis() - sessionInfo.timestamp) / 60000;
            log.info("   - User: {}, Age: {} min, Cookies: {}",
                    key.split(":")[0], ageMinutes, sessionInfo.cookies.keySet());
        });
    }


    // ==================== Session Cache Management ====================


    /**
     * Перевірити чи cookies ще валідні (за JSESSIONID)
     * Використовується для перевірки вже отриманих сесійних кук
     */
    public boolean isSessionValid(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            log.debug("❌ No cookies provided");
            return false;
        }

        String jsessionId = cookies.get("JSESSIONID");
        if (jsessionId == null || jsessionId.isEmpty()) {
            log.debug("❌ No JSESSIONID in cookies");
            return false;
        }

        // Шукаємо сесію з таким JSESSIONID в кеші
        for (Map.Entry<String, SessionInfo> entry : sessionCache.entrySet()) {
            SessionInfo sessionInfo = entry.getValue();
            String cachedJSessionId = sessionInfo.cookies.get("JSESSIONID");

            if (jsessionId.equals(cachedJSessionId)) {
                // Знайшли відповідну сесію, перевіряємо TTL
                boolean isValid = System.currentTimeMillis() - sessionInfo.timestamp < 900000;

                if (!isValid) {
                    log.debug("⏰ Session expired for JSESSIONID: {}", jsessionId.substring(0, 8) + "...");
                    sessionCache.remove(entry.getKey());
                } else {
                    log.debug("✅ Valid session found for JSESSIONID: {}", jsessionId.substring(0, 8) + "...");
                }

                return isValid;
            }
        }

        log.debug("❌ No cached session found for provided JSESSIONID");
        return false;
    }

    /**
     * Вивести статистику по всіх кешах (токени + сесії)
     */
    public void logCacheStats() {
        log.info("📊 AuthService Cache Statistics:");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Token cache stats
        log.info("🔑 Token Cache:");
        log.info("   Total cached tokens: {}", tokenCache.size());
        if (!tokenCache.isEmpty()) {
            tokenCache.forEach((key, tokenInfo) -> {
                String username = key.split(":")[0];
                long ageMinutes = (System.currentTimeMillis() - tokenInfo.timestamp) / 60000;
                boolean expired = isTokenExpired(tokenInfo.token);
                log.info("   - {}: age={}min, expired={}", username, ageMinutes, expired);
            });
        }

        log.info("");

        // Session cache stats
        log.info("🍪 Session Cache:");
        log.info("   Total cached sessions: {}", sessionCache.size());
        if (!sessionCache.isEmpty()) {
            sessionCache.forEach((key, sessionInfo) -> {
                String username = key.split(":")[0];
                long ageMinutes = (System.currentTimeMillis() - sessionInfo.timestamp) / 60000;
                boolean expired = System.currentTimeMillis() - sessionInfo.timestamp >= 900000;
                String jsessionId = sessionInfo.cookies.get("JSESSIONID");
                String shortJSessionId = jsessionId != null ? jsessionId.substring(0, 8) + "..." : "N/A";
                log.info("   - {}: age={}min, expired={}, JSESSIONID={}",
                        username, ageMinutes, expired, shortJSessionId);
            });
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }



    /**
     * Новий browser login flow через /login?redirectUri=...
     * Використовується після оновлення API
     */
    @Step("Browser login with redirect URI for user: {username}")
    public Map<String, String> loginWithRedirectUri(String username, String password, String targetUrl) {
        if (playwrightSessionProvider != null) {
            log.info("🎭 Delegating login to Playwright (headless browser) for user: {}", username);
            return playwrightSessionProvider.getSession(username, password);
        }

        log.info("🚀 Starting NEW browser login flow with redirectUri");
        log.info("   Username: {}", username);
        log.info("   Target URL: {}", targetUrl);

        // 1. Переходимо на /login?redirectUri=<target> - backend ініціює OAuth flow
        String loginUrl = baseUrl + "/login?redirectUri=" + targetUrl;
        log.info("📍 Step 1: Accessing login endpoint: {}", loginUrl);

        Response step1 = RestAssured.given()
                .urlEncodingEnabled(false)
                .redirects().follow(false)
                .get(loginUrl);

        String keycloakRedirect = step1.getHeader("Location");
        String springSessionId = step1.getCookie("JSESSIONID");

        log.info("   ✅ Received redirect to Keycloak");
        log.info("   ✅ Got JSESSIONID: {}", springSessionId != null ? springSessionId.substring(0, 8) + "..." : "null");

        if (keycloakRedirect == null) {
            log.error("❌ No redirect to Keycloak received");
            throw new RuntimeException("❌ Failed to get redirect to Keycloak from /login?redirectUri=...");
        }

        // 2. Переходимо до /oauth2/authorization/keycloak (ПЕРЕДАЄМО JSESSIONID!)
        log.info("📍 Step 2: Following redirect to Keycloak: {}", keycloakRedirect);

        Response step2 = RestAssured.given()
                .urlEncodingEnabled(false)
                .redirects().follow(false)
                .cookie("JSESSIONID", springSessionId) // 👈 ДОДАЙ КУКУ!
                .get(keycloakRedirect);

        // Оновлюємо JSESSIONID якщо backend видав нову
        String updatedSessionId = step2.getCookie("JSESSIONID");
        if (updatedSessionId != null) {
            log.info("   ⚠️ Backend updated JSESSIONID: {}", updatedSessionId.substring(0, 8) + "...");
            springSessionId = updatedSessionId;
        }

        // Може бути ще один проміжний редірект
        String finalKeycloakUrl = step2.getHeader("Location");
        if (finalKeycloakUrl != null) {
            log.info("   ⚠️ Additional redirect detected: {}", finalKeycloakUrl);
            keycloakRedirect = finalKeycloakUrl;
        } else {
            keycloakRedirect = step2.getHeader("Location") != null ? step2.getHeader("Location") : keycloakRedirect;
        }

        // 3. Отримуємо Keycloak login page
        log.info("📍 Step 3: Loading Keycloak login page");

        Response loginPageResponse = RestAssured.given()
                .urlEncodingEnabled(false)
                .get(keycloakRedirect);

        String loginPageHtml = loginPageResponse.asString();

        // Перевірка чи ми на правильній сторінці
        if (loginPageHtml.contains("Login with OAuth 2.0")) {
            log.warn("⚠️ Still on Spring selection page, trying to extract Keycloak link...");
            String providerUrl = loginPageResponse.htmlPath().getString("**.find { it.name() == 'a' }.@href");
            keycloakRedirect = providerUrl.startsWith("http") ? providerUrl : baseUrl + providerUrl;

            loginPageResponse = RestAssured.given()
                    .urlEncodingEnabled(false)
                    .get(keycloakRedirect);

            loginPageHtml = loginPageResponse.asString();
        }

        // 4. Парсимо форму логіну
        log.info("📍 Step 4: Parsing Keycloak login form");

        String formActionUrl = loginPageResponse.htmlPath().getString("**.find { it.@id == 'kc-form-login' }.@action");
        String authSessionId = loginPageResponse.getCookie("AUTH_SESSION_ID");

        if (formActionUrl == null) {
            log.error("❌ Keycloak login form not found in response");
            log.debug("Response body preview: {}", loginPageHtml.substring(0, Math.min(500, loginPageHtml.length())));
            throw new RuntimeException("❌ Keycloak login form not found. Check if Keycloak is reachable.");
        }

        log.info("   ✅ Form action URL: {}", formActionUrl);
        log.info("   ✅ AUTH_SESSION_ID: {}", authSessionId != null ? authSessionId.substring(0, 20) + "..." : "null");

        // 5. Надсилаємо credentials до Keycloak
        log.info("📍 Step 5: Submitting credentials to Keycloak");

        Response postLoginResponse = RestAssured.given()
                .urlEncodingEnabled(false)
                .contentType("application/x-www-form-urlencoded")
                .cookie("AUTH_SESSION_ID", authSessionId)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("credentialId", "")
                .redirects().follow(false)
                .post(formActionUrl);

        // 6. Отримуємо callback URL від Keycloak
        String callbackUrl = postLoginResponse.getHeader("Location");

        if (callbackUrl == null) {
            log.error("❌ No redirect after login - authentication failed");
            log.error("Response status: {}", postLoginResponse.statusCode());
            log.error("Response body: {}", postLoginResponse.asString());
            throw new RuntimeException("❌ Keycloak login failed (check credentials). No redirect Location found.");
        }

        log.info("   ✅ Received callback URL: {}", callbackUrl);

        // Збираємо всі Keycloak cookies
        Map<String, String> keycloakCookies = postLoginResponse.getCookies();
        log.info("   ✅ Keycloak cookies: {}", keycloakCookies.keySet());

        // 7. Виконуємо OAuth callback на backend (з ПРАВИЛЬНОЮ JSESSIONID!)
        log.info("📍 Step 6: Executing OAuth callback to backend");
        log.info("   Using JSESSIONID: {}", springSessionId.substring(0, 8) + "..."); // 👈 Логуємо яку сесію використовуємо

        Response callbackResponse = RestAssured.given()
                .urlEncodingEnabled(false)
                .redirects().follow(false)
                .cookie("JSESSIONID", springSessionId) // 👈 Використовуємо оновлену з кроку 2!
                .get(callbackUrl);

        // 8. Перевіряємо результат callback
        String finalRedirect = callbackResponse.getHeader("Location");

        log.info("   Callback response status: {}", callbackResponse.statusCode());
        log.info("   Final redirect: {}", finalRedirect);

        // Перевірка на помилку
        if (finalRedirect != null && finalRedirect.contains("error")) {
            log.error("❌ Backend rejected the OAuth code");
            log.error("   Redirect URL: {}", finalRedirect);
            throw new RuntimeException("❌ Login failed: Backend returned redirect to " + finalRedirect);
        }

        // 9. Можливо треба ще один редірект на targetUrl
        Map<String, String> finalCookies = new HashMap<>(callbackResponse.getCookies());

        // Якщо бекенд не видав нову JSESSIONID, використовуємо поточну (вона тепер авторизована)
        finalCookies.putIfAbsent("JSESSIONID", springSessionId);

        // Якщо є редірект на targetUrl, виконуємо його щоб отримати фінальні cookies
        if (finalRedirect != null && !finalRedirect.contains("error") && !finalRedirect.contains("login")) {
            log.info("📍 Step 7: Following final redirect to target: {}", finalRedirect);

            Response finalResponse = RestAssured.given()
                    .urlEncodingEnabled(false)
                    .redirects().follow(false)
                    .cookies(finalCookies)
                    .get(finalRedirect);

            // Оновлюємо cookies після фінального редіректу
            Map<String, String> updatedCookies = finalResponse.getCookies();
            if (!updatedCookies.isEmpty()) {
                finalCookies.putAll(updatedCookies);
            }

            log.info("   Final response status: {}", finalResponse.statusCode());
        }

        log.info("✅ Browser session established successfully");
        log.info("   Final cookies: {}", finalCookies.keySet());
        log.info("   JSESSIONID: {}", finalCookies.get("JSESSIONID") != null ?
                finalCookies.get("JSESSIONID").substring(0, 8) + "..." : "null");

        return finalCookies;
    }


}