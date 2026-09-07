package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Список ресурсів Файти. URL: {@code /faita-resources}. Без h1 — PageTabs групи «Екіпажі».
 */
@Slf4j
public class FaitaResourceListPage extends BasePage {

    public static final String PATH = "/faita-resources";
    public static final String SEARCH_PLACEHOLDER = "Пошук за назвою...";

    public FaitaResourceListPage(Page page) {
        super(page);
    }

    public FaitaResourceListPage open() {
        String url = ConfigProvider.getBaseUrl() + PATH;
        waitForResponseTolerant(
                this::isFaitaResourcesGet,
                () -> {
                    navigateTo(url, "Ресурси Файти");
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                },
                "GET /integrations/faita/resources");
        return waitForLoaded();
    }

    public FaitaResourceListPage openViaSidebar() {
        ensureAppShell();
        waitForResponseTolerant(
                this::isFaitaResourcesGet,
                () -> new AppSidebarPage(page)
                        .waitForSidebarLoaded()
                        .navigateToGroupedPage(AppSidebarPage.GROUP_CREW, AppSidebarPage.TAB_FAITA_RESOURCES),
                "GET /integrations/faita/resources (sidebar)");
        return waitForLoaded();
    }

    public FaitaResourceListPage waitForLoaded() {
        page.getByPlaceholder(SEARCH_PLACEHOLDER).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        waitForConditionTolerant(
                () -> page.getByText("Завантаження...").count() == 0
                        || !page.getByText("Завантаження...").first().isVisible(),
                "FAITA list loading hidden");
        return this;
    }

    public FaitaResourceListPage searchByName(String name) {
        Locator input = page.getByPlaceholder(SEARCH_PLACEHOLDER).first();
        input.fill("");
        input.fill(name);
        return this;
    }

    public boolean hasTypeFilter(String label) {
        return page.getByText(label).count() > 0 && page.getByText(label).first().isVisible();
    }

    public Locator rowByExternalId(String externalId) {
        return page.locator("table").locator("tr").filter(
                new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(externalId))));
    }

    public FaitaResourceDetailPage openProduct(String externalId) {
        waitForResponseTolerant(
                this::isFaitaResourcesGet,
                () -> page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(externalId)).first().click(),
                "GET faita resources (card)");
        return new FaitaResourceDetailPage(page).waitForLoaded(externalId);
    }

    private void ensureAppShell() {
        AppSidebarPage sidebar = new AppSidebarPage(page);
        if (!sidebar.isSidebarVisible()) {
            navigateTo(ConfigProvider.getBaseUrl(), "Home");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            sidebar.waitForSidebarLoaded();
        }
    }

    private boolean isFaitaResourcesGet(com.microsoft.playwright.Response response) {
        return response.url().contains("/integrations/faita/resources")
                && "GET".equalsIgnoreCase(response.request().method());
    }
}
