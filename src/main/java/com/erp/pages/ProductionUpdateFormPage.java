package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

public class ProductionUpdateFormPage extends BasePage {

    public ProductionUpdateFormPage(Page page) {
        super(page);
    }

    public ProductionUpdateFormPage open(Long productionId, Long storageId) {
        navigateTo(ConfigProvider.getBaseUrl() + "/production/update/" + productionId
                + "?storageId=" + storageId, "Редагування запису");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Редагування запису"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isTechMapDisabled() {
        return inputAfterLabel("Технологічна карта").isDisabled();
    }

    public boolean isBatchNumberDisabled() {
        return inputAfterLabel("Номер партії").isDisabled();
    }

    public String getBatchNumber() {
        return inputAfterLabel("Номер партії").inputValue();
    }

    public boolean hasImmutableBatchHint() {
        Locator hint = page.getByText("Номер партії змінити не можна - створіть новий запис");
        return hint.count() > 0 && hint.first().isVisible();
    }

    private Locator inputAfterLabel(String label) {
        return page.locator("label")
                .filter(new Locator.FilterOptions().setHasText(label))
                .locator("xpath=following::input[1]")
                .first();
    }
}
