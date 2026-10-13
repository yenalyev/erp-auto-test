package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.DisassembleFixture;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Production")
@Feature("Розбір")
public class DisassembleBatchNumberUiTest extends BaseUITest {

    private long storageId;
    private long inputResourceId;
    private String inputResourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        DisassembleFixture fixture = new DisassembleFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        inputResourceId = fixture.inputResourceId();
        inputResourceName = fixture.inputResourceName();
    }

    @TestCaseId("TC-DIS-008")
    @Test
    @Story("Початковий номер партії розбору")
    @Severity(SeverityLevel.NORMAL)
    @Description("Форма розбору пропонує номер із позначкою «Р-» за датою, локацією та предметом; зміна дати формує новий номер.")
    public void disassembleBatchNumberFollowsDateLocationAndItem() {
        injectOwnerSession();
        page.navigate(ConfigProvider.getBaseUrl() + "/production/createDisassemble");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Запис про розбір"))
                .waitFor(new Locator.WaitForOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));

        Locator dateInput = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Дата розбирання"))
                .locator("xpath=following-sibling::input[1]").first();
        Locator batchInput = page.locator("label")
                .filter(new Locator.FilterOptions().setHasText("Номер партії"))
                .locator("xpath=following-sibling::input[1]").first();
        LocalDate initialDate = LocalDate.parse(dateInput.inputValue());
        String expectedInitial = expectedNumber(initialDate, null);
        assertThat(batchInput.inputValue()).isEqualTo(expectedInitial);

        Locator productInput = page.getByPlaceholder("Оберіть продукт...");
        String searchTerm = inputResourceName.substring(0, Math.min(12, inputResourceName.length()));
        productInput.fill(searchTerm);
        page.locator("[data-slot='combobox-item']")
                .filter(new Locator.FilterOptions().setHasText(inputResourceName))
                .first().click();
        String expectedWithItem = expectedNumber(initialDate, inputResourceId);
        page.waitForCondition(() -> expectedWithItem.equals(batchInput.inputValue()),
                new Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));
        assertThat(batchInput.inputValue()).isEqualTo(expectedWithItem);

        String manualNumber = "manual-dis-" + System.currentTimeMillis() % 1_000_000;
        batchInput.fill(manualNumber);
        assertThat(batchInput.inputValue()).isEqualTo(manualNumber);

        LocalDate changedDate = initialDate.minusDays(1);
        dateInput.fill(changedDate.toString());
        String expectedAfterDateChange = expectedNumber(changedDate, inputResourceId);
        page.waitForCondition(() -> expectedAfterDateChange.equals(batchInput.inputValue()),
                new Page.WaitForConditionOptions()
                        .setTimeout(ConfigProvider.getUiTimeoutSeconds() * 1000L));
        assertThat(batchInput.inputValue()).isEqualTo(expectedAfterDateChange);
    }

    private String expectedNumber(LocalDate date, Long resourceId) {
        return "Р-" + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                + "-" + storageId + (resourceId != null ? "-" + resourceId : "");
    }

    private void injectOwnerSession() {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl().replaceFirst("https?://", "").split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }
}
