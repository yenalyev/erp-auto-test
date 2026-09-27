package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

public class BatchInspectorPage extends BasePage {

    public static final String PATH = "/batch-inspector";
    public static final String NAV_LABEL = "Інспектор партії";

    public BatchInspectorPage(Page page) {
        super(page);
    }

    public BatchInspectorPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Batch Inspector");
        batchNameInput().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public BatchInspectorPage search(String batchName) {
        batchNameInput().fill(batchName);
        waitForResponseTolerant(
                response -> response.url().contains("/api/v1/batches/inspect")
                        && response.request().method().equals("GET"),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Знайти").setExact(true)).click(),
                "Batch Inspector search");
        waitForConditionTolerant(
                () -> inspectionCards(batchName).count() > 0 || notFoundMessage(batchName).count() > 0,
                "Batch Inspector result for " + batchName);
        return this;
    }

    public boolean hasInspection(String batchName) {
        Locator cards = inspectionCards(batchName);
        return cards.count() > 0 && cards.first().isVisible();
    }

    public String definitionValue(String batchName, String label) {
        Locator card = inspectionCards(batchName).first();
        Locator term = card.locator("dt")
                .filter(new Locator.FilterOptions().setHasText(label))
                .first();
        return normalize(term.locator("xpath=following-sibling::dd[1]").innerText());
    }

    public List<String> sectionRows(String batchName, String sectionTitle) {
        return section(batchName, sectionTitle).locator("tbody tr").allInnerTexts().stream()
                .map(BatchInspectorPage::normalize)
                .toList();
    }

    public boolean sectionShowsNoData(String batchName, String sectionTitle) {
        Locator section = section(batchName, sectionTitle);
        Locator empty = section.getByText("Немає даних",
                new Locator.GetByTextOptions().setExact(true));
        return empty.count() > 0 && empty.first().isVisible();
    }

    public boolean isNotFoundVisible(String batchName) {
        Locator message = notFoundMessage(batchName);
        return message.count() > 0 && message.first().isVisible();
    }

    public boolean isDocumentInteractive(String batchName, String documentNumber) {
        Locator movement = section(batchName, "Рух по складах");
        Locator interactive = movement.locator("a,button")
                .filter(new Locator.FilterOptions().setHasText(documentNumber));
        return interactive.count() > 0 && interactive.first().isVisible();
    }

    public String currentUrl() {
        return page.url();
    }

    private Locator batchNameInput() {
        return page.getByLabel("Номер партії", new Page.GetByLabelOptions().setExact(true));
    }

    private Locator inspectionCards(String batchName) {
        return page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(batchName).setExact(true))
                .locator("xpath=ancestor::div[contains(@class,'rounded-xl')][1]");
    }

    private Locator section(String batchName, String sectionTitle) {
        Locator card = inspectionCards(batchName).first();
        return card.getByRole(AriaRole.HEADING,
                        new Locator.GetByRoleOptions().setName(sectionTitle).setExact(true))
                .locator("xpath=..");
    }

    private Locator notFoundMessage(String batchName) {
        return page.getByText("Партію «" + batchName + "» не знайдено",
                new Page.GetByTextOptions().setExact(true));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
