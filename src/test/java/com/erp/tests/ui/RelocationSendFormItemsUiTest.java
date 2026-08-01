package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationCreateOutputPage;
import com.erp.pages.RelocationPage;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.storage.StorageRegionsAllureDescriptions;
import com.erp.utils.config.ConfigProvider;
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
        testContext.set(ContextKey.RELOCATION_RESOURCE_ID, resourceId);

        injectOwner1Session();
    }

    @BeforeMethod(alwaysRun = true)
    public void ensureStock() {
        relocationFixture.ensureStock(storageId, resourceId, 50.0);
    }

    @Test(priority = 5)
    @TestCaseId("TC-WMS-004")
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
        return new RelocationPage(page).open().clickSend();
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
