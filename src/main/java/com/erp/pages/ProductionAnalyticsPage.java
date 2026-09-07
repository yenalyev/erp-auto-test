package com.erp.pages;

import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

/**
 * Аналітика виробництва — {@code /analytics/production}.
 * DateRangePicker sits inside PeriodStepper (prev/next chevrons around the trigger).
 */
@Slf4j
public class ProductionAnalyticsPage extends BasePage {

    private static final String PATH = "/analytics/production";
    public static final String TAB_DAILY = "Поденне";
    private static final String PERIOD_LABEL = "Період";

    public ProductionAnalyticsPage(Page page) {
        super(page);
    }

    public ProductionAnalyticsPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, "Аналітика виробництва (/analytics/production)");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        return waitForLoaded();
    }

    public ProductionAnalyticsPage waitForLoaded() {
        try {
            page.waitForCondition(
                    () -> isDailyTabVisible() || isAccessForbidden() || isLoginFormVisible(),
                    new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        } catch (TimeoutError e) {
            throw new AssertionError(pageDump("Сторінка /analytics/production не завантажилась"), e);
        }
        if (isLoginFormVisible()) {
            throw new AssertionError(pageDump("Немає сесії — редірект на Keycloak login"));
        }
        if (isAccessForbidden()) {
            throw new AssertionError(pageDump("403 на /analytics/production"));
        }
        periodLabel().waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isDailyTabVisible() {
        return dailyTab().count() > 0 && dailyTab().first().isVisible();
    }

    public DateRangePickerComponent periodPicker() {
        return new DateRangePickerComponent(page, periodFilterGroup(), uiTimeoutMs());
    }

    public LocalDate browserToday() {
        String iso = (String) page.evaluate("""
                () => {
                  const d = new Date();
                  const month = String(d.getMonth() + 1).padStart(2, '0');
                  const day = String(d.getDate()).padStart(2, '0');
                  return `${d.getFullYear()}-${month}-${day}`;
                }
                """);
        return LocalDate.parse(iso);
    }

    public ProductionAnalyticsPage waitUntilTriggerChanges(String previousText) {
        try {
            page.waitForCondition(
                    () -> !previousText.equals(periodPicker().getTriggerText()),
                    new Page.WaitForConditionOptions().setTimeout(5_000));
        } catch (TimeoutError e) {
            log.warn("DateRangePicker trigger did not change after preset (was '{}')", previousText);
        }
        return this;
    }

    private Locator dailyTab() {
        return page.locator("[data-slot='tabs-trigger']")
                .filter(new Locator.FilterOptions().setHasText(TAB_DAILY));
    }

    private Locator periodLabel() {
        return page.getByText(PERIOD_LABEL, new Page.GetByTextOptions().setExact(true));
    }

    private Locator periodFilterGroup() {
        return periodLabel().locator("xpath=ancestor::div[1]");
    }

    private boolean isAccessForbidden() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("403")).count() > 0
                || page.getByText(AccessForbiddenPage.FORBIDDEN_MESSAGE).count() > 0;
    }

    private boolean isLoginFormVisible() {
        return page.locator("#username").count() > 0 && page.locator("#kc-login").count() > 0;
    }

    private String pageDump(String reason) {
        List<String> tabs = page.locator("[data-slot='tabs-trigger']").allTextContents();
        return "%s. url=%s heading='%s' tabs=%s".formatted(
                reason,
                page.url(),
                page.locator("h1").first().count() > 0 ? page.locator("h1").first().innerText().trim() : "",
                tabs);
    }
}
