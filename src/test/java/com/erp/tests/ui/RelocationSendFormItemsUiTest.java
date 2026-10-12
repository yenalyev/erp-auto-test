package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI: форма «Видача» — нумерація рядків «Список продукції» та згортання «Доступні партії».
 */
@Slf4j
@Epic("Relocation")
@Feature("Relocation UI")
@Story("Send form product list UX")
public class RelocationSendFormItemsUiTest extends BaseUITest {

    private static final String RESOURCE_PREFIX = "ui-rel-items-";

    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private long storageId;
    private Long resourceId;
    private String resourceName;
    private String resourceUnit;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.fetchSharedUnit(3);
        resourceFixture.fetchSharedResourceCategory();

        storageId = ConfigProvider.getOwner1StorageId();
        ResourceResponse resource = resourceFixture.createUniqueResource(RESOURCE_PREFIX);
        resourceId = resource.getId();
        resourceName = resource.getName();
        resourceUnit = resource.getUnit().getShortName();
        testContext.set(ContextKey.RELOCATION_RESOURCE_ID, resourceId);

        injectOwner1Session();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        relocationFixture.ensureStock(storageId, resourceId, 50.0);
    }

    @Test(priority = 5)
    @TestCaseId({"TC-WMS-004", "TC-UI-WMS-ITEM-002", "TC-UI-WMS-ITEM-003"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-WMS-001-01-03 AC-01: у формі «Видача» поле «Список продукції» показує
            запаси поточного бізнес-юніта (ресурс із залишками на локації Owner1).
            """)
    public void productListShowsBusinessUnitStock() {
        RelocationCreateOutputPage form = openSendForm();

        String searchTerm = resourceName.length() > 12
                ? resourceName.substring(0, 12)
                : resourceName;
        List<String> options = form.searchAndCollectResourceOptions(searchTerm);
        form.attachScreenshot("TC-WMS-004 — product list options");

        assertThat(options)
                .as("Autocomplete «Список продукції» містить ресурс із залишком на БЮ")
                .anyMatch(label -> label.contains(resourceName) || label.contains(searchTerm));
        assertThat(options)
                .as("Пошук за частиною назви показує назву, доступну кількість і одиницю виміру")
                .anyMatch(label -> label.contains(resourceName)
                        && label.contains("доступно:")
                        && label.contains(resourceUnit));
    }

    @Test(priority = 7)
    @TestCaseId("TC-UI-WMS-ITEM-005")
    @Severity(SeverityLevel.NORMAL)
    @Description("Обраний ресурс не пропонується повторно в іншому рядку тієї самої видачі.")
    public void selectedResourceIsNotOfferedInSecondRow() {
        RelocationCreateOutputPage form = openSendForm();
        form.selectOutputResourceByName(resourceName);
        form.clickAddPosition();

        String searchTerm = resourceName.length() > 12
                ? resourceName.substring(0, 12)
                : resourceName;
        List<String> secondRowOptions = form.searchAndCollectResourceOptions(1, searchTerm);

        assertThat(secondRowOptions)
                .as("Той самий ресурс не можна додати до видачі вдруге")
                .noneMatch(label -> label.contains(resourceName));
    }

    @Test(priority = 7)
    @TestCaseId("TC-UI-WMS-ITEM-006")
    @Severity(SeverityLevel.NORMAL)
    @Description("Одиниця виміру з'являється в рядку видачі після вибору ресурсу.")
    public void unitAppearsAfterResourceSelection() {
        RelocationCreateOutputPage form = openSendForm();
        assertThat(form.getOutputUnitText(0)).as("До вибору ресурсу одиниця виміру порожня")
                .isEmpty();

        form.selectOutputResourceByName(resourceName);
        assertThat(form.getOutputUnitText(0)).as("Одиниця виміру вибраного ресурсу")
                .isEqualTo(resourceUnit);
    }

    @Test(priority = 7)
    @TestCaseId("TC-UI-WMS-ITEM-004")
    @Severity(SeverityLevel.NORMAL)
    @Description("Дробову кількість у видачі можна ввести з крапкою або комою.")
    public void decimalQuantityAcceptsBothKeyboardSeparators() {
        RelocationCreateOutputPage form = openSendForm();
        form.selectOutputResourceByName(resourceName);
        var quantity = page.getByPlaceholder("Кількість").first();

        quantity.fill("1.5");
        assertThat(form.getOutputQuantityValue(0)).isEqualTo("1.5");

        quantity.fill("");
        quantity.press("1");
        quantity.press("Comma");
        quantity.press("5");
        assertThat(form.getOutputQuantityValue(0))
                .as("Кома з української розкладки означає дробову частину")
                .isEqualTo("1.5");
    }

    @Test(priority = 8)
    @TestCaseId("TC-UI-WMS-ITEM-009")
    @Severity(SeverityLevel.NORMAL)
    @Description("Видачу без жодного рядка продукції неможливо підтвердити.")
    public void issueWithoutProductRowsIsRejected() {
        RelocationCreateOutputPage form = openSendForm();
        List<String> recipients = form.openRecipientDropdown().collectRecipientOptionLabels();
        assertThat(recipients).as("Доступний одержувач для форми видачі").isNotEmpty();
        form.selectRecipientByLabel(recipients.getFirst());

        page.locator("button:has(i.fa-trash-alt)").first().click();
        assertThat(form.productRowCount()).isZero();
        assertThat(page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Підтвердити")).isEnabled())
                .as("Порожню видачу неможливо підтвердити")
                .isFalse();
        assertThat(page.url()).contains(RelocationCreateOutputPage.PATH);
    }

    @Test(priority = 9)
    @TestCaseId("TC-UI-WMS-ITEM-007")
    @Severity(SeverityLevel.NORMAL)
    @Description("Видалення одного рядка залишає інший ресурс доступним для оформлення видачі.")
    public void removingOneProductRowPreservesTheOther() {
        ResourceResponse second = resourceFixture.createUniqueResource("ui-rel-items-second-");
        relocationFixture.ensureStock(storageId, second.getId(), 10.0);

        RelocationCreateOutputPage form = openSendForm();
        form.selectOutputResourceByName(resourceName);
        form.clickAddPosition();
        form.selectOutputResourceByName(1, second.getName());

        page.locator("button:has(i.fa-trash-alt)").first().click();

        assertThat(form.productRowCount()).isEqualTo(1);
        assertThat(form.getSelectedResourceValue(0)).contains(second.getName());
        form.fillOutputQuantity("1");
        assertThat(form.getOutputQuantityValue(0)).isEqualTo("1");
    }

    @Test(priority = 11)
    @TestCaseId("TC-UI-WMS-ITEM-008")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Видачу понад доступний залишок неможливо підтвердити.")
    public void quantityAboveAvailableStockBlocksIssue() {
        RelocationCreateOutputPage form = openSendForm();
        List<String> recipients = form.openRecipientDropdown().collectRecipientOptionLabels();
        assertThat(recipients).as("Доступний одержувач для форми видачі").isNotEmpty();
        form.selectRecipientByLabel(recipients.getFirst());
        form.selectOutputResourceByName(resourceName);
        form.fillOutputQuantity("1");

        var confirm = page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Підтвердити"));
        assertThat(confirm.isEnabled()).as("Кількість у межах залишку дозволена").isTrue();

        form.fillOutputQuantity("1000000");
        assertThat(confirm.isEnabled()).as("Кількість понад залишок заборонена").isFalse();
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-REL-016")
    @Severity(SeverityLevel.NORMAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_016)
    public void productListRowsAreNumbered() {
        RelocationCreateOutputPage form = openSendForm();

        assertThat(form.productRowCount())
                .as("Початково один рядок у «Список продукції»")
                .isEqualTo(1);
        assertThat(form.getProductRowNumberText(0))
                .as("Перший рядок пронумерований як 1.")
                .isEqualTo("1.");
        form.attachScreenshot("TC-UI-REL-016 — initial row number");

        assertThat(form.isAddPositionEnabled())
                .as("«Додати позицію» disabled, поки рядок порожній")
                .isFalse();

        form.selectOutputResourceByName(resourceName);
        assertThat(form.isAddPositionEnabled())
                .as("Після вибору ресурсу «Додати позицію» активна")
                .isTrue();

        form.clickAddPosition();
        assertThat(form.productRowCount())
                .as("Після «Додати позицію» — два рядки")
                .isEqualTo(2);
        assertThat(form.getProductRowNumberText(0)).isEqualTo("1.");
        assertThat(form.getProductRowNumberText(1)).isEqualTo("2.");
        form.attachScreenshot("TC-UI-REL-016 — two numbered rows");
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-REL-017")
    @Severity(SeverityLevel.NORMAL)
    @Description(StorageRegionsAllureDescriptions.TC_UI_REL_017)
    public void availableBatchesCollapseExpand() {
        String batchNumber = RelocationDataFactory.uniqueBatchNumber();
        // Форма «Видача» вантажить партії з isProduced=true (див. useRelocationCreateOutput.loadBatches).
        relocationFixture.createExternalReceive(
                UserRole.ADMIN, storageId, resourceId, 15.0, batchNumber, true);

        RelocationCreateOutputPage form = openSendForm();
        form.selectOutputResourceByName(resourceName);
        form.waitForAvailableBatchesToggle(0);
        form.waitForAvailableBatchChips(0);

        assertThat(form.getAvailableBatchesToggleText(0))
                .as("Кнопка згортання містить «Доступні партії (N)»")
                .matches("(?s).*Доступні партії \\(\\d+\\).*");
        assertThat(form.isAvailableBatchesExpanded(0))
                .as("За замовчуванням партії розгорнуті")
                .isTrue();
        assertThat(form.visibleAvailableBatchChipCount(0))
                .as("У розгорнутому стані є хоча б один чіп партії")
                .isGreaterThan(0);
        form.attachScreenshot("TC-UI-REL-017 — batches expanded");

        form.toggleAvailableBatches(0);
        assertThat(form.isAvailableBatchesExpanded(0))
                .as("Після кліку партії згорнуті (chevron -rotate-90)")
                .isFalse();
        assertThat(form.visibleAvailableBatchChipCount(0))
                .as("Чіпи партій приховані після згортання")
                .isZero();
        form.attachScreenshot("TC-UI-REL-017 — batches collapsed");

        form.toggleAvailableBatches(0);
        assertThat(form.isAvailableBatchesExpanded(0)).isTrue();
        assertThat(form.visibleAvailableBatchChipCount(0))
                .as("Чіпи партій знову видимі після розгортання")
                .isGreaterThan(0);
        form.attachScreenshot("TC-UI-REL-017 — batches re-expanded");
    }

    private RelocationCreateOutputPage openSendForm() {
        RelocationCreateOutputPage form = new RelocationCreateOutputPage(page);
        form.navigateTo(ConfigProvider.getBaseUrl() + RelocationCreateOutputPage.PATH, "Форма видачі");
        return form.waitForLoaded();
    }

    private void injectOwner1Session() {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.OWNER_1.getUsername(), UserRole.OWNER_1.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }
}
