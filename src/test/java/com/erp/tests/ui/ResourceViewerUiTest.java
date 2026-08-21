package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.request.TechnologicalMapRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.PagedResourceRelocationViewerResponse;
import com.erp.models.response.ResourceRelocationSumViewerResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ResourceRelocationViewerPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UI coverage for Resource Viewer (wolf) — sidebar smoke + journal search/sum.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("UI — Відстеження ресурсів")
public class ResourceViewerUiTest extends BaseUITest {

    private static final String SIDEBAR_LABEL = "Відстеження ресурсів";
    private static final double ALC_PER_UNIT = 2.0;
    private static final double PRODUCE_AMOUNT = 5.0;
    private static final double RELOCATE_AMOUNT = 5.0;
    private static final double STOCK_PAD = 100.0;

    private TechnologicalMapFixture techMapFixture;
    private ProductionFixture productionFixture;
    private RelocationFixture relocationFixture;
    private ResourceFixture resourceFixture;

    private Long productionStorageId;
    private Long receiverUnitId;
    private TechnologicalMapResponse techMap;
    private ResourceResponse alcohol;
    private ResourceResponse product;
    private double expectedAlcoholSum;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);

        techMapFixture.prepareContext();
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();

        productionStorageId = ConfigProvider.getOwner1StorageId();
        receiverUnitId = relocationFixture.resolveUnitStorageId(UserRole.ADMIN);
        techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        String suffix = String.valueOf(System.currentTimeMillis());
        alcohol = resourceFixture.createUniqueResource("UI-RVW-ALC-" + suffix);
        product = resourceFixture.createUniqueResource("UI-RVW-P-" + suffix);

        TechnologicalMapRequest request = TechnologicalMapDataFactory
                .createProductionMapWithStorages(
                        "UI-RVW-BOM",
                        List.of(new ResourceUsageRequest(alcohol.getId(), ALC_PER_UNIT)),
                        List.of(new ResourceUsageRequest(product.getId(), 1.0)),
                        Set.of(productionStorageId))
                .build();
        techMap = techMapFixture.createTechMapWithRequest(UserRole.ADMIN, request);

        productionFixture.ensureStockForTechMapInputs(productionStorageId, techMap, STOCK_PAD);
        ManufacturingItemResponse produced = productionFixture.createWithUniqueBatch(
                UserRole.ADMIN, productionStorageId, techMap, PRODUCE_AMOUNT);
        relocationFixture.createSendWithBatch(
                UserRole.ADMIN,
                productionStorageId,
                receiverUnitId,
                product.getId(),
                RELOCATE_AMOUNT,
                produced.getBatchNumber(),
                true);
        expectedAlcoholSum = RELOCATE_AMOUNT * ALC_PER_UNIT;

        injectWolfSession();
        browserContext.addInitScript("localStorage.removeItem('resourceRelocationFilters');");
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        if (techMap != null && techMapFixture != null && productionStorageId != null) {
            try {
                techMapFixture.deactivateTechMap(UserRole.OWNER_1, techMap.getId(), productionStorageId);
            } catch (RuntimeException e) {
                log.warn("Tech map deactivate failed: {}", e.getMessage());
            }
            try {
                techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.READ_ONLY);
            } catch (RuntimeException e) {
                log.warn("Restore READ_ONLY failed: {}", e.getMessage());
            }
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-RVW-001")
    @Story("Sidebar smoke for wolf")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            RESOURCE_VIEWER (wolf): у sidebar видимий пункт «Відстеження ресурсів»;
            сторінка /resources-viewer/relocation відкривається з h1 журналу.
            """)
    public void resourceViewerSidebarAndPageOpen() {
        ResourceRelocationViewerPage viewer = Allure.step(
                "Відкрити журнал через URL",
                () -> new ResourceRelocationViewerPage(page).open());
        assertThat(viewer.isLoaded()).isTrue();
        viewer.attachScreenshot("TC-UI-RVW-001 — page loaded");

        AppSidebarPage sidebar = new AppSidebarPage(page);
        Allure.step("Перевірити sidebar wolf", () -> {
            assertThat(sidebar.isSidebarVisible()).isTrue();
            assertThat(sidebar.isNavItemVisible(SIDEBAR_LABEL))
                    .as("Wolf має бачити «Відстеження ресурсів»")
                    .isTrue();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-RVW-002")
    @Story("Search shows table and summary matching API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після вибору Alcohol + «Інші» + Шукати:
            картка «Сумарно переміщено» збігається з sums з GET /relocations;
            у таблиці видно назви Alcohol / Product.
            """)
    public void resourceViewerSearchMatchesApiSum() {
        double apiSum = fetchApiSum(alcohol.getId());
        assertThat(apiSum).isCloseTo(expectedAlcoholSum, within(0.001));

        ResourceRelocationViewerPage viewer = Allure.step(
                "Відкрити viewer і виконати пошук",
                () -> {
                    ResourceRelocationViewerPage pageObject = new ResourceRelocationViewerPage(page).open();
                    pageObject.clearFilters();
                    pageObject.selectResource(alcohol.getName());
                    pageObject.enableOthersReceivers();
                    pageObject.search();
                    return pageObject;
                });

        viewer.attachScreenshot("TC-UI-RVW-002 — after search");

        Allure.step("Картка «Сумарно переміщено»", () -> {
            assertThat(viewer.isSummaryCardVisible()).isTrue();
            Double uiAmount = viewer.summaryAmountForResource(alcohol.getName());
            assertThat(uiAmount)
                    .as("UI sum для Alcohol має збігатися з API")
                    .isNotNull()
                    .isCloseTo(apiSum, within(0.05));
        });

        Allure.step("Таблиця містить Alcohol і Product", () -> {
            assertThat(viewer.tableContainsText(alcohol.getName())).isTrue();
            assertThat(viewer.tableContainsText(product.getName())).isTrue();
        });
    }

    private void injectWolfSession() {
        log.info("Injecting RESOURCE_VIEWER (wolf) session for UI tests");
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(
                getPlaywrightSessionProvider().getSession(
                        UserRole.RESOURCE_VIEWER.getUsername(),
                        UserRole.RESOURCE_VIEWER.getPassword()),
                domain);
    }

    private double fetchApiSum(Long resourceId) {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceIds", List.of(resourceId));
        params.put("receiverIds", receiverUnitId);
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_VIEWER_RELOCATIONS_GET,
                UserRole.RESOURCE_VIEWER,
                params);
        assertThat(response.statusCode()).isEqualTo(200);
        PagedResourceRelocationViewerResponse page = response.as(PagedResourceRelocationViewerResponse.class);
        List<ResourceRelocationSumViewerResponse> sums =
                page.getSums() != null ? page.getSums() : List.of();
        return sums.stream()
                .filter(s -> resourceId.equals(s.getResourceId()))
                .map(ResourceRelocationSumViewerResponse::getAmount)
                .filter(a -> a != null)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }
}
