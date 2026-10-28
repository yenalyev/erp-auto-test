package com.erp.pages;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Locators follow labels/column headings because this form has no input ids. */
public class CommercialDocumentPage extends BasePage {
    public CommercialDocumentPage(Page page) { super(page); }

    public void open() {
        navigateTo(ConfigProvider.getBaseUrl() + "/virtual-storage", "Облік надходжень");
        new AppSidebarPage(page).openGroup("Облік надходжень");
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions()
                .setName("Комерційні документи").setExact(true)).click();
        addButton().click();
        assertThat(dialog().getByRole(AriaRole.HEADING)).hasText("Новий комерційний документ");
    }

    public Locator dialog() { return page.getByRole(AriaRole.DIALOG); }
    public Locator save() { return dialog().getByRole(AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName("Зберегти").setExact(true)); }
    public Locator label(String text) { return dialog().locator("label")
            .filter(new Locator.FilterOptions().setHasText(text)); }
    public Locator header(String text) { return dialog().locator("th")
            .filter(new Locator.FilterOptions().setHasText(text)); }
    public Locator number() { return label("Номер").locator("..").locator("input"); }
    public Locator comment() { return label("Коментар").locator("..").locator("input"); }
    public Locator date() { return label("Дата документа").locator("..").getByRole(AriaRole.BUTTON); }
    public void assertRequired(Locator container) {
        assertThat(container).isVisible();
        assertThat(container.getByText("*", new Locator.GetByTextOptions().setExact(true))).isVisible();
    }
    public Locator filesButton() { return dialog().getByRole(AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName("Додати файли")); }
    public Locator resourceRow(String name) { return dialog().getByRole(AriaRole.ROW)
            .filter(new Locator.FilterOptions().setHasText(name)).filter(new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.SPINBUTTON))); }
    public Locator total(String name) { return resourceRow(name).getByRole(AriaRole.SPINBUTTON); }
    public Locator documentName(String name) { return resourceRow(name).getByRole(AriaRole.TEXTBOX).nth(0); }

    public void selectReceipt(String resource) {
        Locator row = dialog().getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(resource))
                .filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.CHECKBOX)));
        row.getByRole(AriaRole.CHECKBOX).click();
    }

    public void addFile(byte[] bytes) {
        dialog().locator("input[type=file]").setInputFiles(
                new FilePayload("commercial-document.pdf", "application/pdf", bytes));
    }

    public void removeFile() { dialog().getByTitle("Прибрати", new Locator.GetByTitleOptions()
            .setExact(true)).click(); }

    public void cancel() {
        dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("Скасувати").setExact(true)).click();
        assertThat(dialog()).hasCount(0);
    }

    public void newDocument() { addButton().click(); }

    public void reopen(String number) {
        page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(number))
                .getByTitle("Редагувати", new Locator.GetByTitleOptions().setExact(true)).click();
        assertThat(dialog().getByRole(AriaRole.HEADING)).containsText(number);
    }

    public Response submit(Long storageId) {
        String path = ApiEndpointDefinition.COMMERCIAL_DOCUMENT_CREATE.getPath(storageId);
        Response response = page.waitForResponse(r -> r.request().method().equals("POST")
                && r.url().endsWith(path), () -> save().click());
        return response;
    }

    private Locator addButton() { return page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Додати комерційний документ").setExact(true)); }

}
