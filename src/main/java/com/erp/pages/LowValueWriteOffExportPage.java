package com.erp.pages;

import com.erp.pages.components.AppliedDateRangePickerComponent;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Uses the actual journal and browser download, without assuming an unimplemented export API. */
public class LowValueWriteOffExportPage extends BasePage {
    public LowValueWriteOffExportPage(Page page) { super(page); }

    @Step("Відкрити історію списань МШП")
    public LowValueWriteOffExportPage open() {
        reload(() -> navigateTo(ConfigProvider.getBaseUrl() + LowValueWriteOffPage.HISTORY, "Списання МШП"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Списання МШП").setExact(true))).isVisible();
        return this;
    }

    public Locator exportButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName("Експорт в Excel").setExact(true));
    }

    @Step("Застосувати пошук ресурсу: {value}")
    public LowValueWriteOffExportPage resource(String value) {
        reload(() -> page.getByTestId("low-value-write-off-filter-resource-name").fill(value));
        return this;
    }

    @Step("Змінити категорію: {id}")
    public LowValueWriteOffExportPage toggleCategory(long id) {
        page.getByTestId("low-value-write-off-filter-categories").click();
        reload(() -> page.getByTestId("category-multi-select-item-" + id).click());
        page.keyboard().press("Escape");
        return this;
    }

    @Step("Обрати локацію: {name}")
    public LowValueWriteOffExportPage toggleLocation(String name) {
        Locator field = page.locator("div").filter(new Locator.FilterOptions()
                .setHas(page.getByText("Локації", new Page.GetByTextOptions().setExact(true))))
                .filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.COMBOBOX))).last();
        field.getByRole(AriaRole.COMBOBOX).click();
        page.getByPlaceholder("Пошук...", new Page.GetByPlaceholderOptions().setExact(true)).fill(name);
        reload(() -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true)).click());
        page.keyboard().press("Escape");
        return this;
    }

    @Step("Застосувати період {from} — {to}")
    public LowValueWriteOffExportPage dates(LocalDate from, LocalDate to) {
        reload(() -> new AppliedDateRangePickerComponent(page, uiTimeoutMs()).setRange(from, to));
        return this;
    }

    @Step("Скинути всі фільтри")
    public LowValueWriteOffExportPage reset() {
        reload(() -> page.getByTestId("low-value-write-off-filter-reset").click());
        return this;
    }

    public LowValueWriteOffExportPage expectRows(List<Map<String, Object>> expected) {
        if (expected.isEmpty()) {
            assertThat(page.getByText("Немає записів", new Page.GetByTextOptions().setExact(true))).isVisible();
        } else {
            assertThat(page.locator("table tbody tr")).hasCount(expected.size());
            for (var row : expected) assertThat(page.getByTestId("low-value-write-off-row-" + row.get("id"))).isVisible();
        }
        return this;
    }

    @Step("Перейти на другу сторінку з розміром 25")
    public LowValueWriteOffExportPage secondPage() {
        var size = page.getByRole(AriaRole.COMBOBOX).last();
        if (!size.innerText().trim().equals("25")) {
            size.click();
            reload(() -> page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("25").setExact(true)).click());
        }
        assertThat(page.getByText("Сторінка 1 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        reload(() -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Наступна").setExact(true)).click());
        assertThat(page.getByText("Сторінка 2 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.locator("table tbody tr")).hasCount(1);
        return this;
    }

    @Step("Натиснути експорт і отримати справжній XLSX")
    public byte[] download() throws Exception {
        assertThat(exportButton()).isVisible();
        assertThat(exportButton()).isEnabled();
        Response[] response = new Response[1];
        var download = page.waitForDownload(() -> response[0] = page.waitForResponse(r ->
                URI.create(r.url()).getPath().endsWith(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_EXPORT.getPath()),
                exportButton()::click));
        org.assertj.core.api.Assertions.assertThat(response[0].status()).as("Actual browser export API").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response[0].url()).doesNotContain("page=", "size=");
        Allure.addAttachment("Export request", "text/plain", response[0].request().method() + " " + response[0].url());
        org.assertj.core.api.Assertions.assertThat(download.failure()).isNull();
        org.assertj.core.api.Assertions.assertThat(download.suggestedFilename()).endsWith(".xlsx");
        try {
            byte[] bytes = Files.readAllBytes(download.path());
            Allure.addAttachment(download.suggestedFilename(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(bytes), ".xlsx");
            return bytes;
        } finally { download.delete(); }
    }

    private void reload(Runnable action) {
        var response = page.waitForResponse(r -> "GET".equals(r.request().method())
                && URI.create(r.url()).getPath().endsWith("/api/v1/storages/inventory/low-value-resources"), action);
        org.assertj.core.api.Assertions.assertThat(response.status()).as("History reload: %s", response.url()).isEqualTo(200);
    }
}
