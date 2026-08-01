package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for Project Production templates list.
 * <p>
 * Template create/edit form uses the same «Етапи виробництва» + «Додати етап» DOM as production;
 * reuse {@link com.erp.pages.components.ProjectProductionStagesSection} when adding a template form page object.
 */
@Slf4j
public class ProjectProductionTemplateListPage extends BasePage {

    public static final String PATH = "/project-production-template";
    private static final String TAB_LABEL = "Шаблони проєктного виробництва";
    private static final String CREATE_PRODUCTION_BUTTON = "Створити виробництво";

    public ProjectProductionTemplateListPage(Page page) {
        super(page);
    }

    public ProjectProductionTemplateListPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        log.info("Opening Project Production templates: {}", url);
        navigateTo(url, TAB_LABEL);
        return waitForLoaded();
    }

    public ProjectProductionTemplateListPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(uiTimeoutMs()));
        } catch (Exception e) {
            log.debug("NETWORKIDLE not reached — proceeding: {}", e.getMessage());
        }
        Locator ready = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(TAB_LABEL))
                .or(page.locator("table").first())
                .first();
        ready.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public ProjectProductionTemplateListPage createProductionFromTemplate(String templateName) {
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(templateName));
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CREATE_PRODUCTION_BUTTON))
                .click();
        // Confirm dialog if present
        Locator confirm = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Підтвердити"))
                .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Створити")));
        if (confirm.count() > 0 && confirm.first().isVisible()) {
            confirm.first().click();
        }
        page.waitForTimeout(1000);
        return this;
    }

    public boolean hasTemplateNamed(String templateName) {
        return page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(templateName))
                .count() > 0;
    }
}
