package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for the equipment batch-create form.
 * URL: /equipment/create
 */
@Slf4j
public class EquipmentCreatePage extends BasePage {

    public static final String PATH = "/equipment/create";

    private static final String PAGE_TITLE = "Нове обладнання";
    private static final String SUPPLIER_CHECKBOX_LABEL = "Обрати постачальника";
    private static final String SUPPLIER_PLACEHOLDER = "Оберіть постачальника";
    private static final String ADD_ITEM_BUTTON = "Додати ще одне обладнання";
    private static final String NO_SERIAL_LABEL = "Без серійного номера";
    private static final String COMBOBOX_ITEM_SELECTOR = "[data-slot='combobox-item']";
    private static final String CARD_SELECTOR = "[data-slot='card']";

    public EquipmentCreatePage(Page page) {
        super(page);
    }

    public EquipmentCreatePage openForStorage(Long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH;
        page.navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (storageId != null) {
            page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
            page.reload();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        }
        return waitForLoaded();
    }

    public EquipmentCreatePage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PAGE_TITLE))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        itemCard(0).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Ensures supplier block is active and selects the given supplier by name.
     * When equipment inventory is open, the checkbox must be enabled first;
     * when closed, the checkbox is forced on and disabled.
     */
    public EquipmentCreatePage ensureSupplier(String supplierName) {
        Locator supplierInput = page.getByPlaceholder(SUPPLIER_PLACEHOLDER);
        if (supplierInput.count() == 0 || !supplierInput.first().isVisible()) {
            Locator checkbox = page.getByLabel(SUPPLIER_CHECKBOX_LABEL);
            if (checkbox.count() > 0 && checkbox.isEnabled()) {
                checkbox.check();
            }
            supplierInput = page.getByPlaceholder(SUPPLIER_PLACEHOLDER);
            supplierInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(uiTimeoutMs()));
        }
        supplierInput.click();
        supplierInput.fill(supplierName);
        waitForComboboxOptionsSettled();
        page.locator(COMBOBOX_ITEM_SELECTOR)
                .filter(new Locator.FilterOptions().setHasText(supplierName))
                .first()
                .click();
        return this;
    }

    public EquipmentCreatePage fillItem(int index, String name, String categoryName) {
        Locator card = itemCard(index);
        card.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        ensureCardExpanded(card);

        nameInput(card).fill(name);

        Locator noSerial = card.getByLabel(NO_SERIAL_LABEL);
        if (!noSerial.isChecked()) {
            noSerial.check();
        }

        categoryTrigger(card).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(categoryName).setExact(true))
                .first()
                .click();
        return this;
    }

    public EquipmentCreatePage addAnotherItem() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_ITEM_BUTTON)).click();
        itemCard(itemCardCount() - 1).waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public int positionCount() {
        Locator counter = page.locator("span")
                .filter(new Locator.FilterOptions().setHasText("Позицій"))
                .locator("xpath=preceding-sibling::span[1]");
        if (counter.count() == 0) {
            return itemCardCount();
        }
        String text = counter.first().innerText().trim();
        return Integer.parseInt(text);
    }

    public int itemCardCount() {
        return page.locator(CARD_SELECTOR).count();
    }

    public EquipmentListPage submitAll(int expectedCount) {
        String buttonName = expectedCount > 1
                ? "Зберегти всі " + expectedCount
                : "Зберегти";
        Locator submit = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(buttonName));
        submit.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        page.waitForResponse(
                r -> r.url().contains("/equipment")
                        && "POST".equals(r.request().method())
                        && r.status() >= 200
                        && r.status() < 300,
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                submit::click);
        page.waitForURL(
                url -> url.contains("/equipment") && !url.contains("/create"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        return new EquipmentListPage(page).waitForLoaded();
    }

    private Locator itemCard(int index) {
        return page.locator(CARD_SELECTOR).nth(index);
    }

    private void ensureCardExpanded(Locator card) {
        Locator content = card.locator("[data-slot='card-content']");
        if (content.count() > 0 && content.first().isVisible()) {
            return;
        }
        card.locator("[data-slot='card-header']").click();
        content.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
    }

    private Locator nameInput(Locator card) {
        return card.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Назва"))
                .locator("xpath=following::input[1]")
                .first();
    }

    private Locator categoryTrigger(Locator card) {
        return card.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Категорія"))
                .locator("xpath=following::button[@role='combobox'][1]")
                .first();
    }
}
