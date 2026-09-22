package com.erp.tests.ui;

import com.erp.annotations.DynamicResourceViewer;
import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.LocationProfile;
import com.erp.enums.StorageTechnologicalMapMode;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
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
import com.erp.utils.helpers.UiDownloadAssertions;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
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
 * UI coverage for a dynamically created Resource Viewer — sidebar smoke + journal search/sum.
 */
@Slf4j
@Epic("Resource Viewer")
@Feature("UI — Відстеження ресурсів")
@DynamicResourceViewer
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
    private LocationProfileFixture locationProfileFixture;

    private Long productionStorageId;
    private Long receiverUnitId;
    private String receiverUnitName;
    private TechnologicalMapResponse techMap;
    private ResourceResponse alcohol;
    private ResourceResponse product;
    private Long trackingCategoryId;
    private double expectedAlcoholSum;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        productionFixture = new ProductionFixture(testContext, apiExecutor);
        techMapFixture = productionFixture.getTechMapFixture();
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        locationProfileFixture = new LocationProfileFixture(testContext, apiExecutor);

        techMapFixture.prepareContext();
        resourceFixture.prepareContext();
        relocationFixture.prepareContext();

        productionStorageId = locationProfileFixture
                .create(LocationProfile.TSUK_PRODUCTION, 1)
                .locations().getFirst().getId();
        var receiver = locationProfileFixture
                .create(LocationProfile.BATTALION_UNIT, 1)
                .locations().getFirst();
        receiverUnitId = receiver.getId();
        receiverUnitName = receiver.getName();
        techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.EDIT_ALLOWED);

        String suffix = String.valueOf(System.currentTimeMillis());
        trackingCategoryId = resolveTrackingCategoryId();
        alcohol = resourceFixture.createUniqueResource(
                "UI-RVW-ALC-" + suffix,
                trackingCategoryId);
        product = resourceFixture.createUniqueResource(
                "UI-RVW-P-" + suffix,
                trackingCategoryId);

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

        injectResourceViewerSession();
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        if (techMap != null && techMapFixture != null && productionStorageId != null) {
            try {
                techMapFixture.deactivateTechMap(UserRole.ADMIN, techMap.getId(), productionStorageId);
            } catch (RuntimeException e) {
                log.warn("Tech map deactivate failed: {}", e.getMessage());
            }
            try {
                techMapFixture.setMode(productionStorageId, StorageTechnologicalMapMode.READ_ONLY);
            } catch (RuntimeException e) {
                log.warn("Restore READ_ONLY failed: {}", e.getMessage());
            }
        }
        if (locationProfileFixture != null) {
            locationProfileFixture.cleanup();
        }
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-RVW-001")
    @Story("Sidebar smoke for Resource Viewer")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Динамічний RESOURCE_VIEWER: у sidebar видимий пункт «Відстеження ресурсів»;
            сторінка /resources-viewer/relocation відкривається з h1 журналу.
            """)
    public void resourceViewerSidebarAndPageOpen() {
        ResourceRelocationViewerPage viewer = Allure.step(
                "Відкрити журнал через URL",
                () -> new ResourceRelocationViewerPage(page).open());
        assertThat(viewer.isLoaded()).isTrue();
        assertThat(viewer.areSearchControlsVisible())
                .as("на сторінці доступні фільтри та кнопка пошуку")
                .isTrue();
        assertThat(viewer.isJournalAreaVisible())
                .as("на сторінці доступна область журналу")
                .isTrue();
        viewer.attachScreenshot("TC-UI-RVW-001 — page loaded");

        AppSidebarPage sidebar = new AppSidebarPage(page);
        Allure.step("Перевірити sidebar Resource Viewer", () -> {
            assertThat(sidebar.isSidebarVisible()).isTrue();
            assertThat(sidebar.isNavItemVisible(SIDEBAR_LABEL))
                    .as("Resource Viewer має бачити «Відстеження ресурсів»")
                    .isTrue();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-RVW-002")
    @Story("Search shows table and summary matching API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після вибору Alcohol + конкретного зовнішнього отримувача + Шукати:
            картка «Сумарно переміщено» збігається з sums з GET /relocations;
            у таблиці видно назви Alcohol / Product.
            """)
    public void resourceViewerSearchMatchesApiSum() {
        double apiSum = fetchApiSum(alcohol.getId());
        assertThat(apiSum).isCloseTo(expectedAlcoholSum, within(0.001));

        ResourceRelocationViewerPage viewer = Allure.step(
                "Відкрити viewer і виконати пошук",
                () -> {
                    ResourceRelocationViewerPage pageObject = new ResourceRelocationViewerPage(page).open()
                            .restoreResourceFilters(
                                    Map.of(alcohol.getId(), alcohol.getName()),
                                    receiverUnitId,
                                    receiverUnitName);
                    pageObject.waitForJournalRowCount(product.getName(), 1);
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
            assertThat(viewer.journalRowCountContaining(product.getName()))
                    .as("фізичне переміщення продукту показане рівно один раз")
                    .isEqualTo(1);
            assertThat(viewer.journalRowContainsAll(
                    product.getName(), alcohol.getName(), "10"))
                    .as("єдиний рядок містить продукт, компонент і 10 одиниць компонента")
                    .isTrue();
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-RVW-003")
    @Story("Excel export uses current Resource Viewer filters")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Після пошуку за Компонентом Б і конкретним зовнішнім отримувачем UI завантажує непорожній XLSX")
    public void resourceViewerExportsCurrentFilteredResult() {
        ResourceRelocationViewerPage viewer = new ResourceRelocationViewerPage(page).open()
                .restoreResourceFilters(
                        Map.of(alcohol.getId(), alcohol.getName()),
                        receiverUnitId,
                        receiverUnitName)
                .waitForJournalRowCount(product.getName(), 1);

        ResourceRelocationViewerPage.ExportDownloadResult download = Allure.step(
                "Завантажити Excel поточного результату",
                viewer::exportCurrentResult);
        UiDownloadAssertions.assertNonEmptyXlsx(
                download.path(), download.sizeBytes(), "Resource Viewer Excel");
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-RVW-004")
    @Story("Category-only grouping removes duplicate movement rows")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Для пошуку лише за категорією перемикач групування доступний і ввімкнений
            за замовчуванням; готовий виріб та його компонент зі спільної категорії
            дають один рядок фізичного переміщення з позначкою «Згруповано».
            """)
    public void categoryOnlySearchGroupsProductAndComponentIntoOneMovementRow() {
        ResourceRelocationViewerPage viewer = new ResourceRelocationViewerPage(page).open()
                .restoreCategoryFilter(trackingCategoryId, receiverUnitId, receiverUnitName);

        assertThat(viewer.isLowestComponentGroupingEnabled()).isTrue();
        assertThat(viewer.isLowestComponentGroupingChecked()).isTrue();

        viewer.waitForJournalRowCount(product.getName(), 1);
        viewer.attachScreenshot("TC-UI-RVW-004 — category grouping");

        assertThat(viewer.journalRowCountContaining(product.getName()))
                .as("category-only пошук не дублює фізичне переміщення")
                .isEqualTo(1);
        assertThat(viewer.journalRowContainsAll(product.getName(), alcohol.getName()))
                .isTrue();
        assertThat(viewer.journalRowHasGroupedMark(product.getName()))
                .as("UI пояснює, що один з рівнів BOM було згорнуто")
                .isTrue();
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-RVW-005")
    @Story("Explicit product and semi-finished selection shows both decompositions")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Якщо явно вибрані готовий виріб і його напівфабрикат, перемикач category-only
            grouping вимкнений, а таблиця показує обидва логічні представлення relocation.
            """)
    public void explicitProductAndComponentSelectionShowsBothDecompositions() {
        ResourceRelocationViewerPage viewer = new ResourceRelocationViewerPage(page).open()
                .restoreResourceFilters(
                        Map.of(
                                product.getId(), product.getName(),
                                alcohol.getId(), alcohol.getName()),
                        receiverUnitId,
                        receiverUnitName);

        assertThat(viewer.isLowestComponentGroupingEnabled())
                .as("явний вибір ресурсів вимикає category-only grouping")
                .isFalse();

        viewer.waitForJournalRowCount(product.getName(), 2);
        viewer.attachScreenshot("TC-UI-RVW-005 — explicit product and component");

        assertThat(viewer.journalRowCountContaining(product.getName()))
                .as("декомпозиція готового виробу та явно вибраний рівень показані окремо")
                .isEqualTo(2);
        assertThat(viewer.tableContainsText(alcohol.getName())).isTrue();
        assertThat(viewer.groupedMarkCountInRows(product.getName()))
                .as("явно вибрані рівні не позначаються як згорнуті")
                .isZero();
    }

    private void injectResourceViewerSession() {
        var actor = dynamicResourceViewerActor();
        log.info("Injecting dynamic RESOURCE_VIEWER session for UI tests: {}", actor.username());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(
                getPlaywrightSessionProvider().getSession(
                        actor.username(),
                        actor.password()),
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

    private Long resolveTrackingCategoryId() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.APP_CONFIG_GET_ALL,
                UserRole.ADMIN);
        List<Map<String, Object>> entries = response.jsonPath().getList("");
        if (entries != null) {
            for (Map<String, Object> entry : entries) {
                if (!"resource_tracking_categories".equals(entry.get("name"))) {
                    continue;
                }
                Object value = entry.get("value");
                if (!(value instanceof List<?> options)) {
                    continue;
                }
                for (Object option : options) {
                    if (!(option instanceof Map<?, ?> optionMap)) {
                        continue;
                    }
                    Object values = optionMap.get("values");
                    if (values instanceof List<?> ids && !ids.isEmpty() && ids.getFirst() != null) {
                        return Long.parseLong(String.valueOf(ids.getFirst()));
                    }
                }
            }
        }
        throw new SkipException("На env не налаштовано resource_tracking_categories для UI fixture");
    }

}
