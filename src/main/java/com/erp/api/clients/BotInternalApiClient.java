package com.erp.api.clients;

import com.erp.utils.config.ConfigProvider;
import com.erp.utils.http.BotInventoryApiTrustSslSupport;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * Bearer-authenticated GET to {@code /api/v1/internal/**} — mirrors whatsapp-bot {@code InventoryApiClient}.
 */
@Slf4j
public class BotInternalApiClient {

    private final BotInventoryApiTrustSslSupport trustSsl = new BotInventoryApiTrustSslSupport();

    public record TimedResponse(long elapsedMs, int statusCode, byte[] body) {}

    @Step("Bot internal API GET: {requestUrl}")
    public TimedResponse get(String requestUrl, String bearerToken) {
        int connectMs = ConfigProvider.getBotApiConnectTimeoutMs();
        int readMs = ConfigProvider.getBotApiReadTimeoutMs();
        long maxBytes = ConfigProvider.getBotApiMaxResponseBytes();

        long t0 = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = openConnection(requestUrl, connectMs, readMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);

            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                byte[] err = readLimited(errorStream(conn), maxBytes, conn);
                long elapsedMs = System.currentTimeMillis() - t0;
                log.info("GET {} completed in {} ms (HTTP {})", requestUrl, elapsedMs, code);
                return new TimedResponse(elapsedMs, code, err);
            }

            byte[] body = readLimited(conn.getInputStream(), maxBytes, conn);
            long elapsedMs = System.currentTimeMillis() - t0;
            log.info("GET {} completed in {} ms (HTTP {})", requestUrl, elapsedMs, code);
            return new TimedResponse(elapsedMs, code, body);
        } catch (SocketTimeoutException e) {
            throw new RuntimeException("Bot internal API timeout: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Bot internal API I/O error: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String url, int connectMs, int readMs) throws IOException {
        URL u = URI.create(url).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        if (c instanceof HttpsURLConnection https) {
            trustSsl.applyTo(https);
        }
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        return c;
    }

    private static InputStream errorStream(HttpURLConnection conn) {
        InputStream is = conn.getErrorStream();
        return is != null ? is : InputStream.nullInputStream();
    }

    private byte[] readLimited(InputStream rawIn, long maxBytes, HttpURLConnection conn) throws IOException {
        String enc = conn.getHeaderField("Content-Encoding");
        InputStream in = "gzip".equalsIgnoreCase(enc) ? new GZIPInputStream(rawIn) : rawIn;
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    Math.min(64 * 1024, (int) Math.min(maxBytes, Integer.MAX_VALUE)));
            byte[] buf = new byte[8 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new RuntimeException("API response exceeds max allowed bytes (" + maxBytes + ")");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
