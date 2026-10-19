package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** The global, read-only relocation journal at {@code /logistics}. */
public class LogisticsPage extends BasePage {

    public static final String PATH = "/logistics";
    public static final String ACTIVE_TABLE_ID = "logistics-relocations-active";
    public static final String HISTORY_TABLE_ID = "logistics-relocations-history";

    private static final String TABLE = "[data-slot='table-container']";

    public LogisticsPage(Page page) {
        super(page);
    }

    public LogisticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Логістика");
        return waitForLoaded();
    }

    public LogisticsPage waitForLoaded() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Журнал переміщень"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(uiTimeoutMs()));
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("В дорозі"))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(uiTimeoutMs()));
        return waitForTable();
    }

    public LogisticsPage waitForTable() {
        page.waitForCondition(() -> page.locator(TABLE).count() > 0
                        || page.getByText("Не вдалося завантажити дані").count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public LogisticsPage openHistory() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Історія переміщень"))
                .click();
        return waitForTable();
    }

    public LogisticsPage openActive() {
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("В дорозі")).click();
        return waitForTable();
    }

    public boolean hasTab(String label) {
        Locator tab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label));
        return tab.count() > 0 && tab.isVisible();
    }

    public LogisticsPage selectLocation(String label, String locationName) {
        Locator control = locationControl(label);
        control.click();
        Locator menu = page.locator("[id='" + control.getAttribute("aria-controls") + "']");
        menu.getByPlaceholder("Пошук...").fill(locationName);
        menu
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(locationName).setExact(true)).click();
        page.keyboard().press("Escape");
        return waitForTable();
    }

    public String selectedLocation(String label) {
        return locationControl(label).innerText().trim();
    }

    public LogisticsPage searchProduct(String name) {
        page.locator("input[placeholder='Пошук по назві...']").fill(name);
        return this;
    }

    public LogisticsPage clearFilters() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Очистити"))
                .click();
        return waitForTable();
    }

    public LogisticsPage sortBy(String header) {
        page.getByRole(AriaRole.COLUMNHEADER, new Page.GetByRoleOptions().setName(header))
                .click();
        return waitForTable();
    }

    public List<String> rowTexts() {
        return page.locator(TABLE + " tbody tr").allInnerTexts();
    }

    public boolean hasRowContaining(String text) {
        return rowTexts().stream().anyMatch(row -> row.contains(text));
    }

    public boolean hasHeader(String label) {
        return page.locator(TABLE + " thead th").allInnerTexts().stream()
                .map(String::trim)
                .anyMatch(label::equals);
    }

    public LogisticsPage setColumnVisible(String label, boolean visible) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Колонки")).click();
        Locator item = page.getByRole(AriaRole.MENUITEMCHECKBOX,
                new Page.GetByRoleOptions().setName(label).setExact(true));
        boolean checked = "true".equals(item.getAttribute("aria-checked"));
        if (checked != visible) {
            item.click();
        }
        page.keyboard().press("Escape");
        return this;
    }

    public ExportedFile export() {
        Download download = page.waitForDownload(
                new Page.WaitForDownloadOptions().setTimeout(120_000),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Експорт").setExact(true)).click());
        try {
            return new ExportedFile(download.suggestedFilename(), Files.size(download.path()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read logistics export", e);
        }
    }

    private Locator locationControl(String label) {
        String currentLabel = switch (label) {
            case "Відправник" -> "Від кого";
            case "Отримувач" -> "Кому";
            default -> label;
        };
        return page.locator("div.space-y-1\\.5")
                .filter(new Locator.FilterOptions().setHas(
                        page.locator("label").getByText(currentLabel, new Locator.GetByTextOptions().setExact(true))))
                .getByRole(AriaRole.COMBOBOX);
    }

    public record ExportedFile(String filename, long sizeBytes) {}
}
