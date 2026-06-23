package com.erp.utils.auth;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.http.BotInventoryApiTrustSslSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * OAuth2 client_credentials — mirrors whatsapp-bot {@code OAuth2ClientCredentialsTokenProvider},
 * with fallback to {@code Authorization: Basic} when body auth returns 401.
 */
@Slf4j
public class BotOAuth2Client {

    private static final long SAFETY_MARGIN_SEC = 60;
    private static final long DEFAULT_EXPIRES_IN_SEC = 300;
    private static final int MAX_TOKEN_RESPONSE_BYTES = 32 * 1024;
    private static final int MAX_ERROR_BODY_CHARS = 400;
    private static final String FORM_URLENC_UTF8 = "application/x-www-form-urlencoded; charset=UTF-8";

    private static final String AUTH_SECRET_IN_BODY = "SECRET_IN_BODY";
    private static final String AUTH_BASIC_HEADER = "BASIC_HEADER";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BotInventoryApiTrustSslSupport trustSsl = new BotInventoryApiTrustSslSupport();

    private final Object lock = new Object();
    private volatile String cachedToken;
    private volatile long cacheExpiresAtEpochSecond;

    @Step("OAuth2 client_credentials token for bot client")
    public String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        String token = cachedToken;
        if (token != null && now < cacheExpiresAtEpochSecond) {
            return token;
        }
        synchronized (lock) {
            now = Instant.now().getEpochSecond();
            if (cachedToken != null && now < cacheExpiresAtEpochSecond) {
                return cachedToken;
            }
            return fetchAndCacheLocked();
        }
    }

    @Step("OAuth2 client_credentials token probe (timed)")
    public TimedTokenResponse requestAccessToken() {
        synchronized (lock) {
            cachedToken = null;
            cacheExpiresAtEpochSecond = 0;
        }
        TokenExchangeResult result = exchangeToken();
        return new TimedTokenResponse(result.elapsedMs(), result.accessToken(), result.expiresAtEpochSeconds());
    }

    public void invalidate() {
        synchronized (lock) {
            cachedToken = null;
            cacheExpiresAtEpochSecond = 0;
        }
    }

    private String fetchAndCacheLocked() {
        return exchangeToken().accessToken();
    }

    private TokenExchangeResult exchangeToken() {
        String tokenUrl = ConfigProvider.getBotOAuthTokenUrl().trim();
        String clientId = ConfigProvider.getBotOAuthClientId().trim();
        String clientSecret = ConfigProvider.getBotOAuthClientSecret();
        requireOAuthConfig(tokenUrl, clientId, clientSecret);

        int connectMs = ConfigProvider.getBotApiConnectTimeoutMs();
        int readMs = ConfigProvider.getBotApiReadTimeoutMs();
        String grantType = resolveGrantType();

        long t0 = System.currentTimeMillis();
        HttpPostResult first = postToken(tokenUrl, clientId, clientSecret, grantType, connectMs, readMs, AUTH_SECRET_IN_BODY);

        HttpPostResult result = first;
        if (first.statusCode() == 401) {
            if (first.connection() != null) {
                first.connection().disconnect();
            }
            result = postToken(tokenUrl, clientId, clientSecret, grantType, connectMs, readMs, AUTH_BASIC_HEADER);
        }

        long elapsedMs = System.currentTimeMillis() - t0;
        HttpURLConnection conn = result.connection();
        try {
            int code = result.statusCode();
            byte[] body = result.body();

            if (code < 200 || code >= 300) {
                log.error(
                        "Bot OAuth2 failed: HTTP {} — clientId='{}', tokenUrl='{}', body={}",
                        code, clientId, tokenUrl, excerpt(body));
                throw new RuntimeException(buildFailureMessage(code, tokenUrl, clientId, excerpt(body)));
            }

            JsonNode root = objectMapper.readTree(body);
            String access = textOrNull(root, "access_token");
            if (access == null || access.isBlank()) {
                throw new RuntimeException("OAuth2 token response has no access_token: " + excerpt(body));
            }
            long expiresIn = root.path("expires_in").asLong(DEFAULT_EXPIRES_IN_SEC);
            if (expiresIn < SAFETY_MARGIN_SEC + 1) {
                expiresIn = SAFETY_MARGIN_SEC + 1;
            }
            long nowSec = Instant.now().getEpochSecond();
            long expiresAt = nowSec + (expiresIn - SAFETY_MARGIN_SEC);
            synchronized (lock) {
                cachedToken = access;
                cacheExpiresAtEpochSecond = expiresAt;
            }
            log.info("Bot OAuth2 token received in {} ms (expires_in={}s)", elapsedMs, expiresIn);
            return new TokenExchangeResult(elapsedMs, access, expiresAt, expiresIn);
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("OAuth2 token parse error: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private HttpPostResult postToken(
            String tokenUrl,
            String clientId,
            String clientSecret,
            String grantType,
            int connectMs,
            int readMs,
            String authStyle) {
        byte[] formBytes = buildFormBody(grantType, clientId, clientSecret, authStyle);

        HttpURLConnection conn = null;
        try {
            conn = openConnection(tokenUrl, connectMs, readMs);
            prepareOAuth2TokenPost(conn, formBytes.length, clientId, clientSecret, authStyle);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(formBytes);
            }

            int code = conn.getResponseCode();
            byte[] body = readBody(conn, code);
            return new HttpPostResult(code, body, conn, authStyle);
        } catch (SocketTimeoutException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw new RuntimeException("OAuth2 token timeout: " + e.getMessage(), e);
        } catch (IOException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw new RuntimeException("OAuth2 token I/O error: " + e.getMessage(), e);
        }
    }

    private static void requireOAuthConfig(String tokenUrl, String clientId, String clientSecret) {
        if (tokenUrl.isBlank() || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new RuntimeException(
                    "OAuth2: set GET_TOKEN_URL, CLIENT_ID, CLIENT_SECRET in .env or bot.oauth.* in config");
        }
    }

    private static String resolveGrantType() {
        String grantType = ConfigProvider.getBotOAuthGrantType().trim();
        return grantType.isBlank() ? "client_credentials" : grantType;
    }

    private static byte[] buildFormBody(String grantType, String clientId, String clientSecret, String authStyle) {
        if (AUTH_BASIC_HEADER.equals(authStyle)) {
            return ("grant_type=" + encode(grantType)).getBytes(StandardCharsets.UTF_8);
        }
        String form = "grant_type=" + encode(grantType)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        return form.getBytes(StandardCharsets.UTF_8);
    }

    private HttpURLConnection openConnection(String tokenUrl, int connectMs, int readMs) throws IOException {
        URL url = URI.create(tokenUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection https) {
            trustSsl.applyTo(https);
        }
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        return conn;
    }

    private static void prepareOAuth2TokenPost(
            HttpURLConnection conn,
            int formBodyLength,
            String clientId,
            String clientSecret,
            String authStyle) throws IOException {
        try {
            conn.setRequestMethod("POST");
        } catch (ProtocolException e) {
            throw new IOException(e);
        }
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(formBodyLength);
        conn.setRequestProperty("Content-Type", FORM_URLENC_UTF8);
        conn.setRequestProperty("Accept", "application/json");
        if (AUTH_BASIC_HEADER.equals(authStyle)) {
            String credentials = clientId + ":" + clientSecret;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encoded);
        }
    }

    private static String buildFailureMessage(int statusCode, String tokenUrl, String clientId, String bodyExcerpt) {
        return "Bot OAuth2 token request failed: HTTP " + statusCode
                + " (clientId=" + clientId + ", tokenUrl=" + tokenUrl + ")"
                + (bodyExcerpt.isBlank() ? "" : ": " + bodyExcerpt);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String excerpt(byte[] body) {
        if (body.length == 0) {
            return "<empty>";
        }
        String s = new String(body, StandardCharsets.UTF_8);
        if (s.length() <= MAX_ERROR_BODY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_ERROR_BODY_CHARS) + "…";
    }

    private static byte[] readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            return new byte[0];
        }
        try (in) {
            byte[] body = in.readAllBytes();
            if (body.length > MAX_TOKEN_RESPONSE_BYTES) {
                throw new IOException("OAuth2 token response exceeds " + MAX_TOKEN_RESPONSE_BYTES + " bytes");
            }
            return body;
        }
    }

    private record HttpPostResult(int statusCode, byte[] body, HttpURLConnection connection, String authStyle) {}

    private record TokenExchangeResult(
            long elapsedMs, String accessToken, long expiresAtEpochSeconds, long expiresInSeconds) {}

    public record TimedTokenResponse(long elapsedMs, String accessToken, long expiresAtEpochSeconds) {}
}
