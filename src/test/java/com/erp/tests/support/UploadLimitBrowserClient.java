package com.erp.tests.support;

import com.erp.utils.config.ConfigProvider;
import io.restassured.path.json.JsonPath;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;

import java.util.List;
import java.util.Map;

/** Reads early size-limit responses without leaving a Java socket writing the rejected body. */
public final class UploadLimitBrowserClient {
    private UploadLimitBrowserClient() { }

    public static UploadResponse send(Browser browser, Map<String, String> cookies,
                                      String method, String path, FormData form, Long storageId) {
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
        try {
            String domain = ConfigProvider.getBaseUrl()
                    .replaceFirst("https?://", "").split("/")[0];
            for (Map.Entry<String, String> cookie : cookies.entrySet()) {
                context.addCookies(List.of(new Cookie(cookie.getKey(), cookie.getValue())
                        .setDomain(domain).setPath("/")));
            }
            RequestOptions options = RequestOptions.create()
                    .setMultipart(form)
                    .setTimeout(180_000)
                    .setFailOnStatusCode(false);
            if (storageId != null) options.setQueryParam("storageId", storageId.toString());
            String url = ConfigProvider.getBackendUrl() + path;
            APIResponse response = "PUT".equals(method)
                    ? context.request().put(url, options)
                    : context.request().post(url, options);
            return new UploadResponse(response.status(), response.text());
        } finally {
            context.close();
        }
    }

    public record UploadResponse(int status, String body) {
        public String firstMessage() {
            return JsonPath.from(body).getString("errors[0].messages[0]");
        }
    }
}
