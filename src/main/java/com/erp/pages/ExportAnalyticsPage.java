package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/** Page Object for /export-analytics */
@Slf4j
public class ExportAnalyticsPage extends BasePage {

    private static final String PATH = "/export-analytics";
    private static final String PAGE_TITLE_TEXT = "Експорт в БД аналітики";
    private static final String SIDEBAR_LINK_TEXT = "Експорт даних";
    private static final String EXPORT_BUTTON_TEXT = "Завантажити в Excel";
    private static final String SUCCESS_TOAST_FRAGMENT = "успішно";
    private static final String ERROR_TOAST_FRAGMENT = "Не вдалося";

    public ExportAnalyticsPage(Page page) {
        super(page);
    }

    public ExportAnalyticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Експорт даних (/export-analytics)");
        return waitForLoaded();
    }

    public ExportAnalyticsPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        AppSidebarPage sidebar = new AppSidebarPage(page);
        sidebar.isSidebarVisible();
        page.getByText(PAGE_TITLE_TEXT)
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isLoaded() {
        return page.getByText(PAGE_TITLE_TEXT).isVisible();
    }

    public boolean isSidebarLinkVisible() {
        return new AppSidebarPage(page).isNavItemVisible(SIDEBAR_LINK_TEXT);
    }

    public ExportAnalyticsPage selectRemaindersExport() {
        page.getByRole(AriaRole.COMBOBOX).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("Залишки")).click();
        return this;
    }

    public ExportAnalyticsPage clickExport() {
        exportButton().click();
        return this;
    }

    public ExportDownloadResult clickExportAndDownload() {
        Download download = page.waitForDownload(() -> exportButton().click());
        String suggestedFilename = download.suggestedFilename();
        long sizeBytes = download.path() != null ? download.path().toFile().length() : 0L;
        log.info("Export download: {} ({} bytes)", suggestedFilename, sizeBytes);
        return new ExportDownloadResult(suggestedFilename, sizeBytes);
    }

    public boolean isExportSuccessToastVisible() {
        Locator toast = page.locator("[data-sonner-toast], [role='status']")
                .filter(new Locator.FilterOptions().setHasText(SUCCESS_TOAST_FRAGMENT));
        try {
            toast.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
            return toast.first().isVisible();
        } catch (Exception e) {
            return page.getByText(SUCCESS_TOAST_FRAGMENT, new Page.GetByTextOptions().setExact(false)).isVisible();
        }
    }

    public ExportAnalyticsPage assertExportSuccessToast() {
        if (!isExportSuccessToastVisible()) {
            throw new AssertionError("Очікувався toast про успішний експорт");
        }
        return this;
    }

    public boolean isExportErrorToastVisible() {
        Locator toast = page.locator("[data-sonner-toast], [role='status']")
                .filter(new Locator.FilterOptions().setHasText(ERROR_TOAST_FRAGMENT));
        try {
            toast.first().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
            return toast.first().isVisible();
        } catch (Exception e) {
            return page.getByText(ERROR_TOAST_FRAGMENT, new Page.GetByTextOptions().setExact(false)).isVisible();
        }
    }

    public ExportAnalyticsPage assertExportErrorToast() {
        if (!isExportErrorToastVisible()) {
            throw new AssertionError("Очікувався toast про помилку експорту (403 / немає доступу)");
        }
        return this;
    }

    private Locator exportButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(EXPORT_BUTTON_TEXT));
    }

    public record ExportDownloadResult(String suggestedFilename, long sizeBytes) {}
}
