import com.erp.enums.UserRole;
import com.erp.utils.auth.PlaywrightSessionProvider;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import java.nio.file.*;
import java.util.*;

/** Read-only diagnosis of deployed export. A missing marker prevents exporting unrelated data. */
public class MshpExportReadOnlyProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("env", "dev");
        try (var provider = new PlaywrightSessionProvider(ConfigProvider.getBackendUrl());
             var context = provider.getBrowser().newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true).setAcceptDownloads(true))) {
            var role = UserRole.ADMIN;
            var cookies = provider.getSession(role.getUsername(), role.getPassword());
            String domain = java.net.URI.create(ConfigProvider.getBaseUrl()).getHost();
            context.addCookies(cookies.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue()).setDomain(domain).setPath("/")).toList());
            context.addInitScript("localStorage.clear();localStorage.setItem('selectedStorageId','all');");
            var page = context.newPage();
            page.setDefaultTimeout(30000);
            page.navigate(ConfigProvider.getBaseUrl() + "/inventory/low-value-write-offs");
            page.waitForResponse(r -> r.url().contains("resourceName=mshp-export-contract-probe-missing"),
                () -> page.getByTestId("low-value-write-off-filter-resource-name").fill("mshp-export-contract-probe-missing"));
            page.onResponse(r -> { if (r.url().contains("low-value-resources/export")) System.out.println("EXPORT " + r.status() + " " + r.url()); });
            var download = page.waitForDownload(() -> page.getByTestId("low-value-write-off-export").click());
            if (download.failure() != null) throw new AssertionError(download.failure());
            var bytes = Files.readAllBytes(download.path());
            Files.write(Path.of(args[0]), bytes);
            System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(XlsxWorkbookReader.sheets(bytes)));
        }
    }
}
