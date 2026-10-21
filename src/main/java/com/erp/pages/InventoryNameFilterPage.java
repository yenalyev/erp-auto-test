package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Closed inventory history filters; waits for the debounced value in the actual list request. */
public class InventoryNameFilterPage extends BasePage {
    public InventoryNameFilterPage(Page page) { super(page); }

    public InventoryNameFilterPage open() {
        new InventoryProcessesPage(page).open().showClosed();
        assertThat(input()).isVisible();
        return this;
    }

    public InventoryNameFilterPage search(String value) {
        String term = value.trim();
        page.waitForResponse(response -> {
            var uri = URI.create(response.url());
            return uri.getPath().endsWith("/storages/inventory/processes")
                    && response.status() == 200 && "GET".equals(response.request().method())
                    && term.equals(query(uri).getOrDefault("resourceName", ""));
        }, () -> input().fill(value));
        return this;
    }

    public InventoryNameFilterPage expectProcesses(Long... ids) {
        var expected = Arrays.stream(ids).sorted().toList();
        page.waitForCondition(() -> processIds().equals(expected),
                new Page.WaitForConditionOptions().setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000));
        org.assertj.core.api.Assertions.assertThat(processIds()).containsExactlyElementsOf(expected);
        return this;
    }

    public List<Long> processIds() {
        return page.locator("tr[data-testid^='inventory-process-row-']")
                .evaluateAll("els => els.map(el => el.dataset.testid)") instanceof List<?> names
                ? names.stream().map(Object::toString).map(name -> Long.valueOf(name.replace("inventory-process-row-", ""))).sorted().toList()
                : List.of();
    }

    public void expectDifferences(long id, String... names) {
        var entries = page.getByTestId("inventory-process-row-" + id).locator("li");
        assertThat(entries).hasCount(names.length);
        for (String name : names) assertThat(entries.filter(new Locator.FilterOptions().setHasText(name))).hasCount(1);
    }

    public byte[] download(String expectedSearch) throws java.io.IOException {
        var response = page.waitForResponse(r -> URI.create(r.url()).getPath().endsWith("/storages/inventory/processes/export"), () -> {
            Download download = page.waitForDownload(() -> page.getByTestId("inventory-processes-export").click());
            org.assertj.core.api.Assertions.assertThat(download.suggestedFilename()).endsWith(".xlsx");
            download.saveAs(java.nio.file.Path.of(System.getProperty("test.output.directory", "target"), "downloads",
                    "inventory-name-" + UUID.randomUUID() + ".xlsx"));
            lastDownload = download;
        });
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(query(URI.create(response.url())).getOrDefault("resourceName", "")).isEqualTo(expectedSearch);
        return java.nio.file.Files.readAllBytes(lastDownload.path());
    }

    private Download lastDownload;
    private Locator input() { return page.getByTestId("inventory-processes-filter-resource-name"); }
    private static Map<String, String> query(URI uri) {
        var result = new HashMap<String, String>();
        if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&")) {
            var pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), pair.length == 2
                    ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }
}
