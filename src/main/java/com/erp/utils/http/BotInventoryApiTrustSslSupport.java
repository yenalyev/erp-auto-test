package com.erp.utils.http;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Outbound HTTPS for bot OAuth2 and internal API — same trust-all behaviour as whatsapp-bot
 * {@code InventoryApiTrustSslSupport} (closed network).
 */
public class BotInventoryApiTrustSslSupport {

    private static final HostnameVerifier TRUST_ALL_HOSTNAMES = (hostname, session) -> true;

    private volatile SSLContext ctx;

    public void applyTo(HttpsURLConnection connection) {
        try {
            connection.setSSLSocketFactory(sslContext().getSocketFactory());
            connection.setHostnameVerifier(TRUST_ALL_HOSTNAMES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SSL for bot Inventory API", e);
        }
    }

    private SSLContext sslContext() throws GeneralSecurityException {
        SSLContext c = ctx;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            c = ctx;
            if (c != null) {
                return c;
            }
            TrustManager[] trustAll = new TrustManager[]{new TrustAllX509()};
            SSLContext out = SSLContext.getInstance("TLS");
            out.init(null, trustAll, new SecureRandom());
            ctx = out;
            return out;
        }
    }

    private static final class TrustAllX509 implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
