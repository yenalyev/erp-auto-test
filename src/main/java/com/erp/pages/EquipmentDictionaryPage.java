package com.erp.pages;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class EquipmentDictionaryPage extends BasePage {
    public EquipmentDictionaryPage(Page page) { super(page); }

    @Step("UI: відкрити довідник обладнання")
    public EquipmentDictionaryPage open() {
        awaitData(() -> navigateTo(ConfigProvider.getBaseUrl() + "/equipment-models"));
        return this;
    }

    @Step("UI: знайти модель за назвою {name}")
    public EquipmentDictionaryPage search(String name) {
        awaitData(() -> page.getByTestId("equipment-models-filter-name").fill(name));
        return this;
    }

    @Step("UI: змінити фільтр категорії {id}")
    public EquipmentDictionaryPage toggleCategory(Long id) {
        page.getByTestId("equipment-models-filter-category").click();
        awaitData(() -> page.getByTestId("equipment-models-filter-category-option-" + id).click());
        page.keyboard().press("Escape");
        return this;
    }

    @Step("UI: скинути категорії")
    public EquipmentDictionaryPage clearCategories() {
        page.getByTestId("equipment-models-filter-category").click();
        awaitData(() -> page.getByTestId("equipment-models-filter-category-reset").click());
        page.keyboard().press("Escape");
        return this;
    }

    @Step("UI: переключити сортування {column}")
    public EquipmentDictionaryPage clickSort(String column) {
        // The deployed table handles clicks on the header itself; newer builds nest a full-width button.
        // Clicking the header works for both; the awaited GET still proves a real sort action occurred.
        awaitData(() -> page.locator("table thead th")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile(column))).click());
        return this;
    }

    public List<String> headers() {
        return page.locator("table thead th").allTextContents().stream().map(String::trim).toList();
    }

    public Locator rows() { return page.locator("tr[data-testid^='equipment-models-row-']"); }
    public Locator row(Long modelId) { return page.getByTestId("equipment-models-row-" + modelId); }

    public EquipmentDictionaryPage status(String status) {
        page.getByTestId("equipment-models-filter-status").click();
        awaitData(() -> page.getByTestId("equipment-models-filter-status-" + status.toLowerCase()).click());
        return this;
    }

    public Locator archiveButton(Long id) { return page.getByTestId("equipment-models-row-" + id + "-archive"); }

    public void openArchive(Long id) {
        archiveButton(id).click();
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).isVisible();
    }

    public com.microsoft.playwright.Response confirmArchive(Long id) {
        return page.waitForResponse(r -> r.url().endsWith("/equipment/models/" + id + "/archive")
                && r.request().method().equals("PUT"), () -> page.getByRole(AriaRole.ALERTDIALOG)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Архівувати").setExact(true)).click());
    }

    public void expectNames(List<String> names) {
        assertThat(rows().locator("td:first-child")).hasText(names.toArray(String[]::new));
    }

    public List<String> names() {
        return rows().locator("td:first-child").allTextContents().stream().map(String::trim).toList();
    }

    public boolean canSort(String column) {
        return page.getByRole(AriaRole.COLUMNHEADER,
                new Page.GetByRoleOptions().setName(column).setExact(true)).getByRole(AriaRole.BUTTON).count() > 0;
    }

    public void awaitData(Runnable action) {
        var response = page.waitForResponse(r -> r.url().split("\\?")[0]
                .endsWith(ApiEndpointDefinition.EQUIPMENT_MODELS_GET_PAGE.getPath())
                && r.request().method().equals("GET"), action);
        if (response.status() != 200) {
            throw new AssertionError("Equipment dictionary GET failed: HTTP " + response.status());
        }
        assertThat(page.locator("table")).isVisible();
    }
}
