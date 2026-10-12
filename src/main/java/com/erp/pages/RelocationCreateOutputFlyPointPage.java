package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RelocationCreateOutputFlyPointPage extends BasePage {

    public static final String PATH = "/relocation/create-output-fly-point";
    private static final String TITLE = "Видача між точками вильоту";
    private static final String SUBMIT = "Підтвердити";
    private static final String FROM_LABEL = "Точка вильоту (звідки)";
    private static final String TO_LABEL = "Точка вильоту (куди)";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String QUANTITY_PLACEHOLDER = "Кількість";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";

    public RelocationCreateOutputFlyPointPage(Page page) {
        super(page);
    }

    public RelocationCreateOutputFlyPointPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))
                .waitFor();
        waitForFormBootstrap();
        return this;
    }

    private void waitForFormBootstrap() {
        page.waitForCondition(() -> {
            Locator loading = page.getByText("Завантаження...");
            if (loading.count() > 0 && loading.isVisible()) {
                return false;
            }
            return labeledInput(FROM_LABEL).isVisible() && labeledInput(TO_LABEL).isVisible();
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
    }

    public boolean isLoaded() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE)).isVisible();
    }

    public RelocationCreateOutputFlyPointPage selectSenderFlyPointByName(String flyPointName) {
        return selectFlyPoint(FROM_LABEL, flyPointName);
    }

    public RelocationCreateOutputFlyPointPage selectRecipientFlyPointByName(String flyPointName) {
        return selectFlyPoint(TO_LABEL, flyPointName);
    }

    private RelocationCreateOutputFlyPointPage selectFlyPoint(String label, String flyPointName) {
        Locator input = labeledInput(label);
        input.waitFor();
        input.click();
        input.fill(flyPointName);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(flyPointName))
                .first()
                .click();
        return this;
    }

    public RelocationCreateOutputFlyPointPage selectResourceByName(String resourceNamePart) {
        String searchTerm = resourceNamePart.length() > 12
                ? resourceNamePart.substring(0, 12)
                : resourceNamePart;
        Locator resourceInput = page.getByPlaceholder(RESOURCE_PLACEHOLDER);
        resourceInput.waitFor();
        resourceInput.click();
        resourceInput.fill(searchTerm);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(resourceNamePart))
                .first()
                .click();
        return this;
    }

    public RelocationCreateOutputFlyPointPage fillQuantity(String amount) {
        page.getByPlaceholder(QUANTITY_PLACEHOLDER).fill(amount);
        return this;
    }

    /** The archive flow preselects the point's remaining stock. */
    public RelocationCreateOutputFlyPointPage waitForPrefilledStock(String resourceName, String amount) {
        page.waitForCondition(() -> {
            Locator resource = page.getByPlaceholder(RESOURCE_PLACEHOLDER);
            Locator quantity = page.getByPlaceholder(QUANTITY_PLACEHOLDER);
            return resource.count() > 0 && quantity.count() > 0
                    && resource.inputValue().contains(resourceName)
                    && amount.equals(quantity.inputValue());
        }, new Page.WaitForConditionOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public RelocationCreateOutputFlyPointPage fillIssuer(String name, String rank) {
        page.getByText("Видав Ім'я та Прізвище")
                .locator("xpath=following::input[1]")
                .fill(name);
        page.getByText("Звання (того, хто видав)")
                .locator("xpath=following::input[1]")
                .fill(rank);
        return this;
    }

    public RelocationCreateOutputFlyPointPage fillDescription(String description) {
        page.locator("#description").fill(description);
        return this;
    }

    public boolean isSubmitDisabled() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).isDisabled();
    }

    public RelocationPage submitAndWaitForJournal() {
        page.waitForResponse(
                response -> response.url().contains("/relocations/send")
                        && "POST".equals(response.request().method()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)).click());
        return new RelocationPage(page).waitForLoaded();
    }

    public FlyPointClosePage submitAndWaitForReturn() {
        page.waitForResponse(
                response -> response.url().contains("/relocations/send")
                        && "POST".equals(response.request().method()),
                () -> page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(SUBMIT)).click());
        page.waitForURL(url -> url.contains("/fly-points/close/"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new FlyPointClosePage(page);
    }

    public RelocationCreateOutputFlyPointPage open() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH, TITLE);
        return waitForLoaded();
    }

    private Locator labeledInput(String label) {
        return page.locator("[data-slot='combobox-label']")
                .filter(new Locator.FilterOptions().setHasText(label))
                .locator("xpath=following::input[1]");
    }
}
