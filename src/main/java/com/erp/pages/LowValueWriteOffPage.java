package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Selectors follow the real form shared with resource issuance. No network mocking. */
public class LowValueWriteOffPage {
    public static final String HISTORY = "/inventory/low-value-write-offs";
    public static final String FORM = "/inventory/low-value-write-off";
    private final Page page;
    public LowValueWriteOffPage(Page page) { this.page = page; }

    public void open(String path) { page.navigate(ConfigProvider.getBaseUrl() + path); }
    public Locator submit() { return page.getByTestId("low-value-write-off-submit"); }
    public Locator row(long id) { return page.getByTestId("low-value-write-off-row-" + id); }
    public Locator search() { return page.getByTestId("low-value-write-off-filter-resource-name"); }

    public void selectResource(String name) {
        Locator input = page.getByPlaceholder("Оберіть ресурс...").last();
        input.fill(name);
        Response batches = page.waitForResponse(r -> r.url().contains("/batches") && r.request().method().equals("GET"),
                () -> page.getByRole(AriaRole.OPTION).filter(new Locator.FilterOptions().setHasText(name)).first().click());
        assertThat(batches.status()).isEqualTo(200);
        page.waitForCondition(() -> input.inputValue().startsWith(name));
        // Populated FIFO batch choices also make the aggregate amount read-only. Select one explicitly.
        if (!io.restassured.path.json.JsonPath.from(batches.text()).getList("").isEmpty()) {
            page.locator("button").filter(new Locator.FilterOptions()
                    .setHasText(java.util.regex.Pattern.compile("^.*\\(\\d+(?:\\.\\d+)?\\)$")))
                    .filter(new Locator.FilterOptions().setHasNotText("Доступні партії")).first().click();
        }
    }

    public void amount(String value) {
        // A FIFO resource offers one aggregate batch. The total stays read-only once it is selected.
        Locator fields = page.getByPlaceholder("Кількість");
        for (int i = 0; i < fields.count(); i++) {
            if (fields.nth(i).isEnabled()) { fields.nth(i).fill(value); return; }
        }
        throw new AssertionError("No editable quantity in the shared issuance form");
    }

    public long save(long storage, com.erp.fixtures.LowValueWriteOffFixture fixture) {
        Response response = page.waitForResponse(r -> r.url().contains("/low-value-resources/write-off")
                && r.request().method().equals("POST"), () -> submit().click());
        // Track before any assertions, also if the browser failed to navigate after a successful save.
        if (response.ok()) {
            long id = io.restassured.path.json.JsonPath.from(response.text()).getLong("id");
            fixture.track(id, storage);
        }
        assertThat(response.status()).as("UI POST: %s", response.text()).isEqualTo(200);
        return io.restassured.path.json.JsonPath.from(response.text()).getLong("id");
    }

    public static String url(Response r) { return URLDecoder.decode(r.url(), StandardCharsets.UTF_8); }
    public static boolean listResponse(Response r) {
        return r.request().method().equals("GET") && r.url().contains("/low-value-resources?");
    }
    public Response search(String name) {
        Response response = page.waitForResponse(r -> listResponse(r) && url(r).contains("resourceName=" + name),
                () -> search().fill(name));
        assertThat(response.status()).isEqualTo(200);
        return response;
    }
    public void selectLocations(List<String> names) {
        Locator field = page.locator("div.space-y-1\\.5").filter(new Locator.FilterOptions()
                .setHas(page.getByText("Локації", new Page.GetByTextOptions().setExact(true))));
        field.getByRole(AriaRole.COMBOBOX).click();
        for (String name : names) {
            page.getByPlaceholder("Пошук...", new Page.GetByPlaceholderOptions().setExact(true)).fill(name);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true)).click();
        }
        page.keyboard().press("Escape");
    }
}
