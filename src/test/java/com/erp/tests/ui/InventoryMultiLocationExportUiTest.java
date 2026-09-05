package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.IsolatedMultiLocationOwnerScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.UnitManagementPage;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.erp.utils.helpers.XlsxContentAssertions;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI regression: multi-location owner exports aggregated remainders from «Всі локації».
 */
@Epic("Inventory")
@Feature("REQ-WMS Manual Coverage UI")
public class InventoryMultiLocationExportUiTest extends BaseUITest {

    private StorageFixture storageFixture;
    private UserFixture userFixture;
    private InventoryFixture inventoryFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;
    private IsolatedMultiLocationOwnerScope multiLocationScope;

    private IsolatedMultiLocationOwnerScope.Context ownerContext;
    private ResourceResponse resourceA;
    private ResourceResponse resourceB;
    private ResourceResponse decoyResource;
    private static final UserRole OWNER = UserRole.OWNER_2;
    private static final double STOCK_A = 11.0;
    private static final double STOCK_B = 17.0;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        userFixture = new UserFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        resourceFixture.prepareContext();

        multiLocationScope = new IsolatedMultiLocationOwnerScope(
                storageFixture,
                userFixture,
                apiExecutor,
                getPlaywrightSessionProvider());
        ownerContext = multiLocationScope.acquire(OWNER);

        resourceA = resourceFixture.createUniqueResource("mloc-ui-a-");
        resourceB = resourceFixture.createUniqueResource("mloc-ui-b-");
        decoyResource = resourceFixture.createUniqueResource("mloc-decoy-ui-");
        relocationFixture.ensureStock(ownerContext.storageAId(), resourceA.getId(), STOCK_A);
        relocationFixture.ensureStock(ownerContext.storageBId(), resourceB.getId(), STOCK_B);

        long forbiddenStorageId = storageFixture.createUniqueStorage("mloc-forbidden-ui-").getId();
        relocationFixture.ensureStock(forbiddenStorageId, decoyResource.getId(), 99.0);

        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageAId(), resourceA.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                ownerContext.storageBId(), resourceB.getId(), OWNER, 15_000);
        inventoryFixture.requireItemForResourceWithRetry(
                forbiddenStorageId, decoyResource.getId(), UserRole.ADMIN, 15_000);
    }

    @AfterClass(alwaysRun = true)
    public void releaseMultiLocationScope() {
        if (multiLocationScope != null) {
            multiLocationScope.release();
        }
    }

    @Test
    @TestCaseId("TC-WMS-007-019")
    @Story("Multi-location owner export UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Ephemeral multi-location owner (2 UNIT), залишки на обох локаціях.
            UI (CPMA-762): «Всі локації» → «Експорт в Excel»
            → GET /export-analytics/inventory?locations=allowedActiveStorageIds.
            Очікується: XLSX з обома ресурсами та їх кількостями; без decoy з чужої локації.
            """)
    public void multiLocationOwnerExportsAllLocationsFromUi() {
        Allure.parameter("ownerUsername", ownerContext.owner().username());
        Allure.parameter("resourceA", resourceA.getName());
        Allure.parameter("resourceB", resourceB.getName());

        injectAllLocationsSession(ownerContext.owner());

        UnitManagementPage stock = new UnitManagementPage(page)
                .openForAllLocations()
                .waitForLoaded();

        assertThat(stock.isAllLocationsTableVisible())
                .as("Aggregated inventory table must load in all-locations mode")
                .isTrue();
        assertThat(stock.isExportToExcelButtonEnabled())
                .as("Export button must stay enabled in all-locations mode")
                .isTrue();
        stock.attachScreenshot("TC-WMS-007-019 — all locations before export");

        UnitManagementPage.ExportDownloadResult download = stock.clickExportToExcelAndDownload();
        Allure.parameter("downloadSizeBytes", download.sizeBytes());
        UiDownloadAssertions.assertNonEmptyXlsx(
                download.path(), download.sizeBytes(), "Multi-location all-locations export");
        assertThat(XlsxContentAssertions.zipContainsText(download.path(), resourceA.getName()))
                .as("UI export must include resource from storage A")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(download.path(), resourceB.getName()))
                .as("UI export must include resource from storage B")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsAmount(download.path(), STOCK_A))
                .as("UI export must include stock quantity for resource A")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsAmount(download.path(), STOCK_B))
                .as("UI export must include stock quantity for resource B")
                .isTrue();
        assertThat(XlsxContentAssertions.zipContainsText(download.path(), decoyResource.getName()))
                .as("UI export must not leak decoy from forbidden storage")
                .isFalse();
        assertThat(XlsxContentAssertions.zipContainsAmount(download.path(), 99.0))
                .as("UI export must not include decoy stock quantity")
                .isFalse();
        stock.attachScreenshot("TC-WMS-007-019 — export downloaded");
    }

    private void injectAllLocationsSession(UserFixture.RestrictedOwnerUser owner) {
        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(owner.username(), owner.password());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', 'all');");
    }
}
