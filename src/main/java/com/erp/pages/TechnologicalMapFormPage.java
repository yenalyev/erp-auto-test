package com.erp.pages;

import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

/**
 * Page Object for technological map create/update form (tk-ui {@code TechnologicalMapForm.tsx}).
 * URLs: /technological-maps/create, /technological-maps/update/{id}
 */
@Slf4j
public class TechnologicalMapFormPage extends BasePage {

    public static final String PATH_CREATE = "/technological-maps/create";
    public static final String TYPE_PRODUCTION = "Виготовлення";
    public static final String TYPE_DISASSEMBLE = "Розбирання";

    private static final String INPUT_SECTION_TITLE = "Вхідні ресурси (Витрати)";
    private static final String OUTPUT_SECTION_TITLE = "Вихідні ресурси (Готова продукція)";
    private static final String ALT_GROUPS_SECTION_TITLE = "Групи альтернативних (взаємозамінних) ресурсів";
    private static final String RESOURCE_PLACEHOLDER = "Оберіть ресурс...";
    private static final String SEARCH_PLACEHOLDER = "Пошук...";
    private static final String SUBMIT_BUTTON_TEXT = "Зберегти";
    private static final String NAME_PLACEHOLDER = "Наприклад: ПТМ-3 (з ТГА)";
    private static final String GROUP_NAME_PLACEHOLDER = "Назва групи (напр.: Пальне)";
    private static final String ADD_GROUP_BUTTON = "Додати групу";
    private static final String ADD_GROUP_RESOURCE_BUTTON = "Додати ресурс";

    public TechnologicalMapFormPage(Page page) {
        super(page);
    }

    public TechnologicalMapFormPage openCreate() {
        navigateTo(ConfigProvider.getBaseUrl() + PATH_CREATE, "Нова технологічна карта");
        return waitForLoaded();
    }

    public TechnologicalMapFormPage openUpdate(long techMapId) {
        String path = "/technological-maps/update/" + techMapId;
        navigateTo(ConfigProvider.getBaseUrl() + path, "Редагування тех. карти");
        return waitForUpdateLoaded();
    }

    public TechnologicalMapFormPage openClone(long techMapId) {
        String path = PATH_CREATE + "?cloneId=" + techMapId;
        navigateTo(ConfigProvider.getBaseUrl() + path, "Клонування тех. карти");
        waitForLoaded();
        return waitForAlternativeGroupsSection();
    }

    /** After clone/update load: type becomes PRODUCTION and alt-groups section appears. */
    public TechnologicalMapFormPage waitForAlternativeGroupsSection() {
        page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(ALT_GROUPS_SECTION_TITLE))
                .first()
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapFormPage waitForLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Нова технологічна карта"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        page.getByPlaceholder(NAME_PLACEHOLDER).waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapFormPage waitForUpdateLoaded() {
        page.waitForURL("**/technological-maps/update/**",
                new Page.WaitForURLOptions().setTimeout(uiTimeoutMs()));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Створіть нову версію тех. карти"))
                .waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        return this;
    }

    public TechnologicalMapFormPage selectType(String typeLabel) {
        // After a type is chosen the placeholder "Оберіть тип…" is replaced by the selected label.
        Locator typeCombobox = page.getByRole(AriaRole.COMBOBOX).filter(new Locator.FilterOptions()
                .setHasText(java.util.regex.Pattern.compile("Оберіть тип|Виготовлення|Розбирання")));
        typeCombobox.first().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(typeLabel).setExact(true))
                .click();
        page.waitForTimeout(300);
        return this;
    }

    public TechnologicalMapFormPage fillName(String name) {
        page.getByPlaceholder(NAME_PLACEHOLDER).fill(name);
        return this;
    }

    public TechnologicalMapFormPage clickAddInputRow() {
        sectionCard(INPUT_SECTION_TITLE)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Додати"))
                .click();
        page.waitForTimeout(200);
        return this;
    }

    public TechnologicalMapFormPage selectInputResource(int rowIndex, String resourceName) {
        selectResourceInSection(INPUT_SECTION_TITLE, rowIndex, resourceName);
        return this;
    }

    public TechnologicalMapFormPage selectOutputResource(int rowIndex, String resourceName) {
        selectResourceInSection(OUTPUT_SECTION_TITLE, rowIndex, resourceName);
        return this;
    }

    public TechnologicalMapFormPage fillInputAmount(int rowIndex, String amount) {
        fillAmountInSection(INPUT_SECTION_TITLE, rowIndex, amount);
        return this;
    }

    public TechnologicalMapFormPage fillOutputAmount(int rowIndex, String amount) {
        fillAmountInSection(OUTPUT_SECTION_TITLE, rowIndex, amount);
        return this;
    }

    public boolean isAlternativeGroupsSectionVisible() {
        return page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(ALT_GROUPS_SECTION_TITLE))
                .first()
                .isVisible();
    }

    public TechnologicalMapFormPage clickAddAlternativeGroup() {
        altGroupsSection()
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_GROUP_BUTTON))
                .click();
        page.waitForTimeout(300);
        return this;
    }

    public TechnologicalMapFormPage fillAlternativeGroupName(int groupIndex, String name) {
        altGroupsSection().getByPlaceholder(GROUP_NAME_PLACEHOLDER).nth(groupIndex).fill(name);
        return this;
    }

    public TechnologicalMapFormPage clickAddResourceInAlternativeGroup(int groupIndex) {
        Locator groupCard = alternativeGroupCard(groupIndex);
        groupCard.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ADD_GROUP_RESOURCE_BUTTON))
                .click();
        page.waitForTimeout(200);
        return this;
    }

    public TechnologicalMapFormPage selectAlternativeGroupResource(int groupIndex, int resourceIndex, String resourceName) {
        Locator groupCard = alternativeGroupCard(groupIndex);
        Locator combobox = groupCard.getByRole(AriaRole.COMBOBOX).nth(resourceIndex);
        combobox.click();

        String term = autocompleteSearchTerm(resourceName);
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        searchInput.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        searchInput.fill(term);
        waitForComboboxOptionsSettled();

        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(resourceName.trim()))
                .first()
                .click();
        page.waitForTimeout(200);
        return this;
    }

    public TechnologicalMapFormPage fillAlternativeGroupAmount(int groupIndex, int resourceIndex, String amount) {
        Locator groupCard = alternativeGroupCard(groupIndex);
        groupCard.locator("input[type='number']").nth(resourceIndex).fill(amount);
        page.waitForTimeout(200);
        return this;
    }

    public TechnologicalMapFormPage setAlternativeGroupDefault(int groupIndex, int resourceIndex) {
        Locator groupCard = alternativeGroupCard(groupIndex);
        groupCard.locator("input[type='radio'][name='group-" + groupIndex + "-default']")
                .nth(resourceIndex)
                .check();
        page.waitForTimeout(200);
        return this;
    }

    public boolean isAlternativeGroupDefaultChecked(int groupIndex, int resourceIndex) {
        return alternativeGroupCard(groupIndex)
                .locator("input[type='radio'][name='group-" + groupIndex + "-default']")
                .nth(resourceIndex)
                .isChecked();
    }

    public TechnologicalMapFormPage submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT_BUTTON_TEXT))
                .click();
        page.waitForTimeout(500);
        return this;
    }

    public boolean isErrorVisible() {
        return page.locator(".bg-red-50").isVisible();
    }

    public String getErrorText() {
        return page.locator(".bg-red-50 span").innerText().trim();
    }

    public boolean isOnCreatePage() {
        return page.url().contains(PATH_CREATE);
    }

    public boolean isOnUpdatePage() {
        return page.url().contains("/technological-maps/update/");
    }

    private Locator sectionCard(String sectionTitle) {
        return page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(sectionTitle))
                .locator("xpath=ancestor::div[contains(@class,'p-4')][1]");
    }

    private Locator altGroupsSection() {
        return page.locator("h3")
                .filter(new Locator.FilterOptions().setHasText(ALT_GROUPS_SECTION_TITLE))
                .locator("xpath=ancestor::div[contains(@class,'p-4')][1]");
    }

    private Locator alternativeGroupCard(int groupIndex) {
        return altGroupsSection()
                .locator("div.rounded-lg.border")
                .nth(groupIndex);
    }

    private void selectResourceInSection(String sectionTitle, int rowIndex, String resourceName) {
        Locator section = sectionCard(sectionTitle);
        Locator combobox = section.getByRole(AriaRole.COMBOBOX).nth(rowIndex);
        combobox.click();

        String term = autocompleteSearchTerm(resourceName);
        Locator searchInput = page.getByPlaceholder(SEARCH_PLACEHOLDER);
        searchInput.waitFor(new Locator.WaitForOptions().setTimeout(uiTimeoutMs()));
        searchInput.fill(term);
        waitForComboboxOptionsSettled();

        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(resourceName.trim()))
                .first()
                .click();
        page.waitForTimeout(200);
    }

    private void fillAmountInSection(String sectionTitle, int rowIndex, String amount) {
        Locator section = sectionCard(sectionTitle);
        Locator amountInput = section.locator("input[type='number']").nth(rowIndex);
        amountInput.fill(amount);
        page.waitForTimeout(200);
    }

    private static String autocompleteSearchTerm(String resourceName) {
        String trimmed = resourceName.trim();
        if (trimmed.length() <= 12) {
            return trimmed;
        }
        return trimmed.substring(0, 12);
    }
}
