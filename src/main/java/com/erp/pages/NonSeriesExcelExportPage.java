package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Export controls and exact query checks, shared with the real journal Page Object. */
public class NonSeriesExcelExportPage extends NonSeriesProductionListPage {
    private final Page exportPage;

    public NonSeriesExcelExportPage(Page page) { super(page); exportPage = page; }

    public NonSeriesExcelExportPage changeFilters(Map<String, Object> expected, Runnable action) {
        var response = exportPage.waitForResponse(r -> URI.create(r.url()).getPath().endsWith("/api/v1/non-series-production")
                && businessQuery(r.url()).equals(normalize(expected)), action);
        assertThat(response.status()).isEqualTo(200);
        return this;
    }

    public void product(String term) { exportPage.getByPlaceholder("Назва продукту...").fill(term); }

    public void status(String label) {
        exportPage.locator("label").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Статус"))
                .locator("xpath=..").getByRole(AriaRole.COMBOBOX).click();
        exportPage.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(label).setExact(true)).click();
    }

    public void dates(LocalDate from, LocalDate to) {
        new DateRangePickerComponent(exportPage, uiTimeoutMs()).setRange(from, to);
    }

    public void clear() {
        exportPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Очистити").setExact(true)).click();
    }

    public byte[] download(Map<String, Object> expected) throws Exception {
        com.microsoft.playwright.Response[] response = new com.microsoft.playwright.Response[1];
        var download = exportPage.waitForDownload(() -> response[0] = exportPage.waitForResponse(
                r -> URI.create(r.url()).getPath().endsWith("/api/v1/non-series-production/export"),
                () -> exportPage.getByTestId("non-series-production-export").click()));
        assertThat(response[0].status()).isEqualTo(200);
        assertThat(businessQuery(response[0].url())).as("Export sends the current UI filters").isEqualTo(normalize(expected));
        assertThat(query(response[0].url()).keySet()).doesNotContain("page", "size");
        assertThat(download.failure()).isNull();
        assertThat(download.suggestedFilename()).isEqualTo("Несерійне виробництво.xlsx");
        try {
            byte[] bytes = Files.readAllBytes(download.path());
            Allure.addAttachment(download.suggestedFilename(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(bytes), ".xlsx");
            return bytes;
        } finally { Files.deleteIfExists(download.path()); }
    }

    private static Map<String, List<String>> businessQuery(String url) {
        var params = query(url);
        params.keySet().removeAll(Set.of("page", "size", "sort"));
        params.values().forEach(values -> values.removeIf(String::isBlank));
        params.entrySet().removeIf(e -> e.getValue().isEmpty());
        return params;
    }

    private static Map<String, List<String>> query(String url) {
        Map<String, List<String>> result = new TreeMap<>();
        String raw = URI.create(url).getRawQuery();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        result.values().forEach(Collections::sort);
        return result;
    }

    private static Map<String, List<String>> normalize(Map<String, Object> params) {
        Map<String, List<String>> result = new TreeMap<>();
        params.forEach((key, value) -> {
            var values = value instanceof Collection<?> collection ? collection.stream().map(Object::toString).sorted().toList()
                    : List.of(value.toString());
            if (!values.isEmpty()) result.put(key, values);
        });
        return result;
    }
}
