package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;

/**
 * Форма редагування ресурсу. URL: {@code /resources/update/:id}.
 */
@Slf4j
public class ResourceEditPage extends BasePage {

    private static final String PATH = "/resources/update/";
    private static final String HEADING = "Редагування ресурсу";
    private static final String DELETE_PHOTO = "Видалити фото";
    private static final String SAVE = "Зберегти";

    public ResourceEditPage(Page page) {
        super(page);
    }

    @Step("UI: відкрити /resources/update/{resourceId}")
    public ResourceEditPage open(long resourceId, long storageId) {
        String url = ConfigProvider.getBaseUrl() + PATH + resourceId;
        waitForResponseTolerant(
                response -> response.url().contains("/api/v1/resources/" + resourceId)
                        && "GET".equalsIgnoreCase(response.request().method())
                        && !response.url().contains("/image"),
                () -> {
                    navigateTo(url, "Редагування ресурсу " + resourceId);
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                },
                "GET /resources/{id}");
        page.evaluate("localStorage.setItem('selectedStorageId', '" + storageId + "');");
        return waitForLoaded();
    }

    public ResourceEditPage waitForLoaded() {
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(HEADING))
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(uiTimeoutMs()));
        saveButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    public boolean isPhotoAssigned() {
        Locator button = deletePhotoButton();
        return button.count() > 0 && button.first().isVisible();
    }

    @Step("UI: дочекатися кнопки «Видалити фото»")
    public ResourceEditPage waitForPhotoAssigned() {
        deletePhotoButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    @Step("UI: клікнути «Видалити фото»")
    public ResourceEditPage clickDeletePhoto() {
        deletePhotoButton().click();
        deletePhotoButton().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(uiTimeoutMs()));
        return this;
    }

    /**
     * Category «Ресурс» has required property «Постачальник». Empty value blocks save.
     */
    @Step("UI: заповнити порожні обов'язкові селекти")
    public ResourceEditPage fillEmptyRequiredSelects() {
        Locator rows = page.locator("div.grid").filter(
                new Locator.FilterOptions().setHas(page.locator("span.text-red-500")));
        int count = rows.count();
        for (int i = 0; i < count; i++) {
            Locator row = rows.nth(i);
            Locator trigger = row.getByRole(AriaRole.COMBOBOX);
            if (trigger.count() == 0) {
                continue;
            }
            String current = trigger.first().innerText();
            if (current != null && !current.contains("Виберіть значення")) {
                continue;
            }
            trigger.first().click();
            waitForComboboxOptionsSettled();
            page.getByRole(AriaRole.OPTION).first().click();
            dismissComboboxOverlay();
        }
        return this;
    }

    @Step("UI: Зберегти і повернутися до /resources")
    public void saveAndReturnToList() {
        page.waitForResponse(
                response -> response.url().contains("/api/v1/resources/")
                        && "PUT".equalsIgnoreCase(response.request().method()),
                new Page.WaitForResponseOptions().setTimeout(uiTimeoutMs()),
                () -> saveButton().click());
        page.waitForURL(
                url -> url.contains("/resources") && !url.contains("/update") && !url.contains("/create"),
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
    }

    private Locator deletePhotoButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DELETE_PHOTO));
    }

    private Locator saveButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE));
    }
}
