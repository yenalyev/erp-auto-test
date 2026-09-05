package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceCalculatorFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.ResourceCalculatorPage;
import com.erp.pages.TechnologicalMapsListPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Technological Maps")
@Feature("Калькулятор розхідників (REQ-MFG-001-CALC)")
public class ResourceCalculatorUiTest extends BaseUITest {

    private ResourceCalculatorFixture fixture;
    private StorageFixture storageFixture;
    private Long ownerStorageId;

    private final List<CleanupMap> maps = new ArrayList<>();
    private final List<Long> storagesNewestFirst = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new ResourceCalculatorFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageFixture = new StorageFixture(testContext, apiExecutor);
        ownerStorageId = ConfigProvider.getOwner1StorageId();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupArtifacts() {
        for (CleanupMap map : maps) {
            try {
                fixture.cleanupTechMap(map.techMap(), map.storageId());
            } catch (Exception e) {
                log.warn("Tech map cleanup failed: {}", e.getMessage());
            }
        }
        maps.clear();
        for (Long storageId : storagesNewestFirst) {
            try {
                storageFixture.archiveStorage(UserRole.ADMIN, storageId);
                storageFixture.untrackForCleanup(storageId);
            } catch (Exception e) {
                log.warn("Storage archive failed {}: {}", storageId, e.getMessage());
            }
        }
        storagesNewestFirst.clear();
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
    }

    @Test(priority = 10)
    @TestCaseId("TC-TM-CALC-001")
    @Story("Access")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Кнопка «Калькулятор розхідників» на конкретній локації відкриває сторінку з формою.")
    public void calculatorButtonOpensPageForStorage() {
        injectRoleSession(UserRole.ADMIN, ownerStorageId);
        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page).openForStorage(ownerStorageId);

        assertThat(listPage.isCalculatorButtonVisible()).isTrue();
        assertThat(listPage.isCalculatorButtonEnabled()).isTrue();
        listPage.attachScreenshot("TC-TM-CALC-001 list button");

        ResourceCalculatorPage calculator = listPage.openCalculator();
        assertThat(calculator.isTitleVisible()).isTrue();
        assertThat(calculator.isCalculateFormVisible()).isTrue();
        assertThat(calculator.isAllLocationsBannerVisible()).isFalse();
        calculator.attachScreenshot("TC-TM-CALC-001 calculator page");
    }

    @Test(priority = 20)
    @TestCaseId("TC-TM-CALC-002")
    @Story("Access")
    @Severity(SeverityLevel.CRITICAL)
    @Description("«Всі локації» — кнопка disabled; прямий захід показує банер.")
    public void allLocationsDisablesCalculator() {
        injectAllLocationsSession();
        TechnologicalMapsListPage listPage = new TechnologicalMapsListPage(page).openAllLocations();

        assertThat(listPage.isCalculatorButtonVisible()).isTrue();
        assertThat(listPage.isCalculatorButtonEnabled()).isFalse();
        listPage.attachScreenshot("TC-TM-CALC-002 disabled button");

        ResourceCalculatorPage calculator = new ResourceCalculatorPage(page).open().waitForTitle();
        assertThat(calculator.isAllLocationsBannerVisible()).isTrue();
        assertThat(calculator.isCalculateFormVisible()).isFalse();
        calculator.attachScreenshot("TC-TM-CALC-002 banner");
    }

    @Test(priority = 30)
    @TestCaseId("TC-TM-CALC-017")
    @Story("Explosion")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI: кількість 10 дає дерево 20 / 60 / 240 по канонічному ланцюжку.")
    public void calculateShowsScaledTree() {
        IsolatedChain isolated = arrangeCanonical();
        ResourceCalculatorPage calculator = openCalculator(isolated.storageId());
        calculator.selectTechMap(isolated.chain().getProductMap().getName())
                .setAmount("10")
                .calculate()
                .waitUntilResourceVisible(isolated.chain().getChip().getName());

        assertThat(calculator.isResourceVisible(isolated.chain().getBody().getName())).isTrue();
        assertThat(calculator.isResourceVisible(isolated.chain().getBoard().getName())).isTrue();
        assertThat(calculator.isResourceVisible(isolated.chain().getChip().getName())).isTrue();
        assertThat(calculator.isAmountVisible("20")).isTrue();
        assertThat(calculator.isAmountVisible("60")).isTrue();
        assertThat(calculator.isAmountVisible("240")).isTrue();
        calculator.attachScreenshot("TC-TM-CALC-017 tree");
    }

    @Test(priority = 40)
    @TestCaseId("TC-TM-CALC-022")
    @Story("Choice")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI автопік першої техкарти розкладає неоднозначний вузол.")
    public void autoPicksFirstProducerAndExpands() {
        Long storageId = newStorage("calc-ui-pick-");
        ResourceCalculatorFixture.ChoiceChain chain = fixture.createChoiceChain(storageId, storageId);
        maps.add(new CleanupMap(chain.getProductMap(), storageId));
        maps.add(new CleanupMap(chain.getBoardMapA(), storageId));
        maps.add(new CleanupMap(chain.getBoardMapB(), storageId));

        ResourceCalculatorPage calculator = openCalculator(storageId);
        calculator.selectTechMap(chain.getProductMap().getName())
                .setAmount("10")
                .calculate()
                .waitUntilResourceVisible(chain.getChip().getName());

        assertThat(calculator.isResourceVisible(chain.getChip().getName()))
                .as("автопік має розкласти плату до чіпа")
                .isTrue();
        calculator.attachScreenshot("TC-TM-CALC-022 auto-pick");
    }

    @Test(priority = 50)
    @TestCaseId("TC-TM-CALC-030")
    @Story("Summary")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Вкладки Дерево/Зведення; зведення містить лист, не розкладений проміжний.")
    public void summaryTabShowsLeafResources() {
        IsolatedChain isolated = arrangeCanonical();
        ResourceCalculatorPage calculator = openCalculator(isolated.storageId());
        calculator.selectTechMap(isolated.chain().getProductMap().getName())
                .setAmount("10")
                .calculate()
                .waitUntilResourceVisible(isolated.chain().getChip().getName());

        assertThat(calculator.isTreeTabVisible()).isTrue();
        assertThat(calculator.isSummaryTabVisible()).isTrue();
        calculator.openSummaryTab();
        assertThat(calculator.isResourceVisible(isolated.chain().getChip().getName())).isTrue();
        assertThat(calculator.isResourceVisible(isolated.chain().getBody().getName()))
                .as("розкладений корпус не потрапляє у зведення")
                .isFalse();
        calculator.attachScreenshot("TC-TM-CALC-030 summary");
    }

    @Test(priority = 60)
    @TestCaseId("TC-TM-CALC-031")
    @Story("Location filter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("«Тільки моя локація» ховає вкладення техкарти іншого складу.")
    public void onlyMyLocationHidesForeignExplosion() {
        Long storageA = newStorage("calc-ui-loc-a-");
        Long storageB = newStorage("calc-ui-loc-b-");
        ResourceCalculatorFixture.ChoiceChain chain = fixture.createRemoteProducerChain(storageA, storageB);
        maps.add(new CleanupMap(chain.getProductMap(), storageA));
        maps.add(new CleanupMap(chain.getBoardMapA(), storageB));

        ResourceCalculatorPage calculator = openCalculator(storageA);
        calculator.selectTechMap(chain.getProductMap().getName())
                .setAmount("10")
                .calculate()
                .waitUntilResourceVisible(chain.getChip().getName());
        assertThat(calculator.isResourceVisible(chain.getChip().getName())).isTrue();
        calculator.attachScreenshot("TC-TM-CALC-031 before filter");

        calculator.setOnlyMyLocation(true);
        assertThat(calculator.isResourceVisible(chain.getBoard().getName())).isTrue();
        assertThat(calculator.isResourceVisible(chain.getChip().getName()))
                .as("чіп карти іншої локації зникає після фільтра")
                .isFalse();
        calculator.attachScreenshot("TC-TM-CALC-031 only my location");
    }

    @Test(priority = 70)
    @TestCaseId("TC-TM-CALC-033")
    @Story("Export")
    @Severity(SeverityLevel.NORMAL)
    @Description("Після розрахунку «Експорт в Excel» качає непорожній xlsx.")
    public void exportExcelAfterCalculate() {
        IsolatedChain isolated = arrangeCanonical();
        ResourceCalculatorPage calculator = openCalculator(isolated.storageId());
        calculator.selectTechMap(isolated.chain().getProductMap().getName())
                .setAmount("10")
                .calculate()
                .waitUntilResourceVisible(isolated.chain().getChip().getName());

        assertThat(calculator.isExportEnabled()).isTrue();
        Path download = calculator.exportToExcel();
        assertThat(download).isNotNull();
        calculator.attachScreenshot("TC-TM-CALC-033 exported");
    }

    private IsolatedChain arrangeCanonical() {
        Long storageId = newStorage("calc-ui-can-");
        ResourceCalculatorFixture.Chain chain = fixture.createCanonicalChain(storageId);
        maps.add(new CleanupMap(chain.getProductMap(), storageId));
        maps.add(new CleanupMap(chain.getBodyMap(), storageId));
        maps.add(new CleanupMap(chain.getBoardMap(), storageId));
        return new IsolatedChain(storageId, chain);
    }

    private ResourceCalculatorPage openCalculator(long storageId) {
        injectRoleSession(UserRole.ADMIN, storageId);
        return new ResourceCalculatorPage(page).open().waitForTitle();
    }

    private Long newStorage(String prefix) {
        StorageResponse storage = storageFixture.createChildStorage(ownerStorageId, prefix);
        storagesNewestFirst.add(0, storage.getId());
        return storage.getId();
    }

    private void injectRoleSession(UserRole role, long selectedStorageId) {
        injectSessionCookies(cachedSessionCookies(role), sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + selectedStorageId + "');");
        recreatePageAfterInject();
    }

    private void injectAllLocationsSession() {
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectAllLocationsView();
        recreatePageAfterInject();
    }

    /** Init scripts apply to newly created pages, not the one already opened in {@code @BeforeMethod}. */
    private void recreatePageAfterInject() {
        if (page != null) {
            try {
                page.close();
            } catch (Exception ignored) {
                // discarded — fresh page needed after init script
            }
        }
        page = browserContext.newPage();
        int timeoutMs = ConfigProvider.getUiTimeoutSeconds() * 1000;
        page.setDefaultTimeout(timeoutMs);
        page.setDefaultNavigationTimeout(timeoutMs);
    }

    private record IsolatedChain(Long storageId, ResourceCalculatorFixture.Chain chain) {
    }

    private record CleanupMap(TechnologicalMapResponse techMap, Long storageId) {
    }
}
