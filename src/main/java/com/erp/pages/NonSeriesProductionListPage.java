package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page Object for the Non-Series Production list page.
 * URL: /non-series-production
 */
@Slf4j
public class NonSeriesProductionListPage extends BasePage {

    public static final String PATH = "/non-series-production";
    public static final String PATH_CREATE = "/non-series-production/create";

    private static final String TITLE_TEXT = "Несерійне виробництво";
    private static final String NEW_ITEM_BUTTON = "Новий виріб";
    private static final String PRODUCT_FILTER_PLACEHOLDER = "Назва продукту...";
    private static final String STATUS_DONE = "Завершено";
    private static final String STATUS_IN_PROGRESS = "В роботі";
    private static final Pattern TOTAL_VOLUME_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*од");

    public NonSeriesProductionListPage(Page page) {
        super(page);
    }

    public NonSeriesProductionListPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Non-Series Production list: {}", url);
        navigateTo(url, TITLE_TEXT);
        return waitForLoaded();
    }

    public NonSeriesProductionListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }

        Locator pageReady = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(TITLE_TEXT))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_ITEM_BUTTON)))
                .or(page.locator("table").first())
                .first();

        pageReady.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public NonSeriesProductionFormPage clickNewItem() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEW_ITEM_BUTTON)).click();
        return new NonSeriesProductionFormPage(page).waitForLoaded();
    }

    public NonSeriesProductionListPage filterByProduct(String productName) {
        page.getByPlaceholder(PRODUCT_FILTER_PLACEHOLDER).fill(productName);
        waitForTotalApiResponse();
        waitForFilterResult(productName);
        return this;
    }

    public NonSeriesProductionListPage filterByStatus(String statusLabel) {
        statusCombobox().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(statusLabel)).click();
        waitForTotalApiResponse();
        return this;
    }

    /** Both requests must carry the same term; waits are installed before the UI action. */
    public NonSeriesProductionListPage awaitResourceFilter(String term, Runnable action) {
        page.waitForResponse(r -> matchesFilter(r.url(), true, term) && r.status() == 200, () ->
                page.waitForResponse(r -> matchesFilter(r.url(), false, term) && r.status() == 200, action));
        return this;
    }

    public NonSeriesProductionListPage filterByResource(String term) {
        return awaitResourceFilter(term, () -> resourceSearchInput().fill(term));
    }

    public NonSeriesProductionListPage clearAllFilters(String currentResourceTerm) {
        // A different resource term ensures that a real filter change is awaited.
        if (currentResourceTerm == null || currentResourceTerm.isEmpty()) {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Очистити").setExact(true)).click();
            return waitForLoaded();
        }
        return awaitResourceFilter("", () -> page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Очистити").setExact(true)).click());
    }

    public Locator resourceSearchInput() {
        return page.getByTestId("non-series-production-resource-search")
                .or(page.getByPlaceholder("Назва матеріалу...")).first();
    }

    public NonSeriesProductionListPage expectProducts(List<String> products) {
        if (products.isEmpty()) {
            com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                    page.getByText("Записів не знайдено", new Page.GetByTextOptions().setExact(true))).isVisible();
        }
        page.waitForCondition(() -> {
            List<String> actual = page.locator("tbody tr td:nth-child(2)").allTextContents()
                    .stream().map(String::trim).sorted().toList();
            return actual.equals(products.stream().sorted().toList());
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public byte[] exportFiltered(String resourceTerm) throws IOException {
        var download = page.waitForDownload(() -> page.waitForResponse(
                r -> URI.create(r.url()).getPath().endsWith("/api/v1/non-series-production/export")
                        && resourceTerm.equals(queryValue(r.url(), "resourceSearch")) && r.status() == 200,
                () -> page.getByTestId("non-series-production-export").click()));
        if (download.failure() != null) {
            throw new IllegalStateException("Non-series export failed: " + download.failure());
        }
        try { return Files.readAllBytes(download.path()); }
        finally { Files.deleteIfExists(download.path()); }
    }

    private static boolean matchesFilter(String url, boolean total, String term) {
        String suffix = "/api/v1/non-series-production" + (total ? "/total" : "");
        return URI.create(url).getPath().endsWith(suffix) && term.equals(queryValue(url, "resourceSearch"));
    }

    private static String queryValue(String url, String name) {
        String query = URI.create(url).getRawQuery();
        if (query == null) return "";
        return Arrays.stream(query.split("&")).map(pair -> pair.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst().orElse("");
    }

    public BigDecimal getDisplayedTotalVolume() {
        Locator totalValue = page.locator("span.font-semibold")
                .filter(new Locator.FilterOptions().setHasText(" од"))
                .first();
        totalValue.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        String text = totalValue.textContent();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Total volume text is empty on non-series production list");
        }
        Matcher matcher = TOTAL_VOLUME_PATTERN.matcher(text.trim());
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot parse total volume from: " + text);
        }
        return new BigDecimal(matcher.group(1).replace(',', '.'));
    }

    public NonSeriesProductionListPage waitForDisplayedTotalVolume(BigDecimal expected) {
        int timeoutMs = uiTimeoutMs();
        page.waitForCondition(
                () -> getDisplayedTotalVolume().compareTo(expected) == 0,
                new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        return this;
    }

    private void waitForTotalApiResponse() {
        page.waitForTimeout(600);
    }

    private Locator statusCombobox() {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Статус"))
                .locator("xpath=..")
                .getByRole(AriaRole.COMBOBOX)
                .first();
    }

    private void waitForFilterResult(String productName) {
        int timeoutMs = uiTimeoutMs();
        log.debug("Waiting up to {}s for filter result: {}", timeoutMs / 1000, productName);

        rowForProduct(productName).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    public NonSeriesProductionFormPage clickEditForProduct(String productName) {
        Locator row = rowForProduct(productName);
        Locator namedEdit = row.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Редагувати"));
        Locator iconEdit = row.locator("button[title='Редагувати'], button:has(svg.lucide-pencil)");
        if (namedEdit.count() > 0) {
            namedEdit.click();
        } else {
            iconEdit.first().click();
        }
        return new NonSeriesProductionFormPage(page).waitForEditLoaded();
    }

    public boolean isProductVisible(String productName) {
        return rowForProduct(productName).count() > 0;
    }

    public boolean isStatusVisibleForProduct(String productName, String statusLabel) {
        Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(productName));
        return row.count() > 0 && row.first().getByText(statusLabel).isVisible();
    }

    public boolean isDoneStatusVisibleForProduct(String productName) {
        return isStatusVisibleForProduct(productName, STATUS_DONE);
    }

    public boolean isInProgressStatusVisibleForProduct(String productName) {
        return isStatusVisibleForProduct(productName, STATUS_IN_PROGRESS);
    }

    public boolean isOnListPage() {
        return page.url().contains(PATH) && !page.url().contains("/create") && !page.url().contains("/update");
    }

    private Locator rowForProduct(String productName) {
        return page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(productName));
    }
}
