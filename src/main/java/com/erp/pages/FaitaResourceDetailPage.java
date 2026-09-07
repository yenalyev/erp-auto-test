package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Картка виробу FAITA. URL: {@code /faita-resources/:resourceId}. h1 = назва виробу.
 */
@Slf4j
public class FaitaResourceDetailPage extends BasePage {

    public static final String RECONCILIATION_CARD = "Зіставлення";
    public static final String IMPLICIT_CARD = "Використання додаткових ресурсів";
    public static final String ADD_RESOURCE = "Додати ресурс";
    public static final String ASSIGN_RESOURCE = "Призначити ресурс";
    public static final String DIALOG_TITLE = "Знайдіть відповідний ресурс у системі";

    public FaitaResourceDetailPage(Page page) {
        super(page);
    }

    public FaitaResourceDetailPage open(String externalId) {
        String encoded = URLEncoder.encode(externalId, StandardCharsets.UTF_8);
        String url = ConfigProvider.getBaseUrl() + FaitaResourceListPage.PATH + "/" + encoded;
        waitForResponseTolerant(
                this::isFaitaResourcesGet,
                () -> {
                    navigateTo(url, "Картка FAITA " + externalId);
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                },
                "GET /integrations/faita/resources (detail)");
        return waitForLoaded(externalId);
    }

    public FaitaResourceDetailPage waitForLoaded(String externalId) {
        page.getByText(externalId, new Page.GetByTextOptions().setExact(true)).first()
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        card(RECONCILIATION_CARD).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public Locator card(String title) {
        return page.locator("[data-slot='card']")
                .filter(new Locator.FilterOptions().setHasText(title))
                .first();
    }

    public boolean isReconciliationListed(String erpName) {
        return card(RECONCILIATION_CARD).getByText(erpName).count() > 0
                && card(RECONCILIATION_CARD).getByText(erpName).first().isVisible();
    }

    public boolean isImplicitListed(String implicitName) {
        return card(IMPLICIT_CARD).getByText(implicitName).count() > 0
                && card(IMPLICIT_CARD).getByText(implicitName).first().isVisible();
    }

    public boolean isImplicitAddVisible() {
        return card(IMPLICIT_CARD)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_RESOURCE))
                .count() > 0
                && card(IMPLICIT_CARD)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_RESOURCE))
                .first()
                .isVisible();
    }

    public FaitaResourceDetailPage addReconciliation(String erpName) {
        card(RECONCILIATION_CARD)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_RESOURCE))
                .click();
        page.getByText(DIALOG_TITLE).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));

        Locator search = page.getByPlaceholder(FaitaResourceListPage.SEARCH_PLACEHOLDER);
        search.last().fill(erpName);
        page.waitForCondition(
                () -> page.getByText(erpName, new Page.GetByTextOptions().setExact(true)).count() > 0,
                new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        page.getByText(erpName, new Page.GetByTextOptions().setExact(true)).last().click();

        waitForResponseTolerant(
                r -> r.url().contains("/resources/reconciliations") && "POST".equalsIgnoreCase(r.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ASSIGN_RESOURCE)).click(),
                "POST reconciliations");
        waitForConditionTolerant(
                () -> isReconciliationListed(erpName),
                "ERP name in Зіставлення after assign");
        return this;
    }

    public FaitaResourceDetailPage removeReconciliation(String erpName) {
        clickMinusInCard(RECONCILIATION_CARD, erpName);
        waitForConditionTolerant(
                () -> !isReconciliationListed(erpName),
                "ERP removed from Зіставлення");
        return this;
    }

    public FaitaResourceDetailPage addImplicit(String implicitName) {
        card(IMPLICIT_CARD)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_RESOURCE))
                .click();
        Locator combo = card(IMPLICIT_CARD).getByRole(AriaRole.COMBOBOX);
        combo.click();
        page.getByPlaceholder("Пошук або нова назва...").fill(implicitName);
        waitForComboboxOptionsSettled();
        page.getByRole(AriaRole.OPTION)
                .filter(new Locator.FilterOptions().setHasText(implicitName))
                .first()
                .click();
        waitForResponseTolerant(
                r -> r.url().contains("/implicit-resources") && "PUT".equalsIgnoreCase(r.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зберегти")).click(),
                "PUT implicit-resources");
        waitForConditionTolerant(
                () -> isImplicitListed(implicitName),
                "implicit name after save");
        return this;
    }

    public FaitaResourceDetailPage removeImplicit(String implicitName) {
        clickMinusInCard(IMPLICIT_CARD, implicitName);
        waitForConditionTolerant(
                () -> !isImplicitListed(implicitName),
                "implicit removed");
        return this;
    }

    private void clickMinusInCard(String cardTitle, String itemName) {
        Locator row = card(cardTitle).locator("li")
                .filter(new Locator.FilterOptions().setHasText(itemName))
                .first();
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        row.locator("button").click();
    }

    private boolean isFaitaResourcesGet(com.microsoft.playwright.Response response) {
        return response.url().contains("/integrations/faita/resources")
                && "GET".equalsIgnoreCase(response.request().method());
    }
}
