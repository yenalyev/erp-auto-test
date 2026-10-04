package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.DisassembleFixture;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ShiftRequest;
import com.erp.models.request.ResourceUsageRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.DisassembleItemResponse;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.ResourceUsageResponse;
import com.erp.models.response.ShiftResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.pages.ProductionAnalyticsPage;
import com.erp.pages.components.DateRangePickerComponent;
import com.erp.utils.helpers.UiDownloadAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI coverage for {@code /analytics/production} with an isolated location and location head.
 */
@Slf4j
@Epic("Analytics")
@Feature("Production analytics")
public class ProductionAnalyticsUiTest extends BaseUITest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UserRole ACTOR = UserRole.DYNAMIC_LOCATION_OWNER;
    private static final double FIRST_PRODUCT_AMOUNT = 2.0;
    private static final double FIRST_USAGE_PER_UNIT = 3.0;
    private static final double SECOND_PRODUCT_AMOUNT = 4.0;
    private static final double SECOND_USAGE_PER_UNIT = 2.0;
    private static final BigDecimal EXPECTED_EXPENSE = new BigDecimal("14");
    private static final double JOURNAL_PRODUCTION_AMOUNT = 6.0;
    private static final int JOURNAL_SHIFT_WORKERS = 7;
    private static final int PAGINATION_EXPORT_MARKERS = 25;
    private static final List<String> EXPECTED_EXPORT_SHEETS = List.of(
            "Виготовлення - За виробами",
            "Виготовлення - Витрати",
            "Розбір - За виробами",
            "Розбір - Витрати",
            "Несерійне виробництво - Витрати");
    private static final Map<String, List<String>> EXPECTED_EXPORT_HEADERS = Map.of(
            EXPECTED_EXPORT_SHEETS.get(0),
            List.of("Ресурс", "Категорія", "Теги", "Кількість записів", "Обсяг", "Од. вимір"),
            EXPECTED_EXPORT_SHEETS.get(1),
            List.of("Ресурс", "Категорія", "Теги", "Обсяг", "Од. вимір"),
            EXPECTED_EXPORT_SHEETS.get(2),
            List.of("Ресурс", "Категорія", "Теги", "Кількість записів", "Обсяг", "Од. вимір"),
            EXPECTED_EXPORT_SHEETS.get(3),
            List.of("Ресурс", "Категорія", "Теги", "Обсяг", "Од. вимір"),
            EXPECTED_EXPORT_SHEETS.get(4),
            List.of("Ресурс", "Категорія", "Теги", "Обсяг", "Од. вимір"));
    private static final String MATERIAL_TOOLTIP =
            "Матеріали — ресурси які не виготовляються і не мають активних техкарт";

    private final List<Long> nonSeriesProductionIds = new ArrayList<>();
    private final List<Long> disassemblyIds = new ArrayList<>();
    private final List<ScopedId> additionalNonSeriesProductions = new ArrayList<>();
    private final List<ScopedId> additionalDisassemblies = new ArrayList<>();
    private final List<ScopedId> additionalProductions = new ArrayList<>();
    private final List<ScopedId> additionalTechMaps = new ArrayList<>();
    private final List<ResourceResponse> additionalResources = new ArrayList<>();
    private DisassembleFixture disassemblies;
    private LocationProfileFixture locationProfiles;
    private UserFixture users;
    private NonSeriesProductionFixture nonSeriesProductions;
    private ProductionFixture productions;
    private ResourceFixture resources;
    private ShiftFixture shifts;
    private TechnologicalMapFixture technologicalMaps;
    private StorageResponse location;
    private StorageResponse secondLocation;
    private StorageResponse techMapOnlyLocation;
    private ResourceResponse material;
    private ResourceResponse secondMaterial;
    private ResourceResponse inactiveMapMaterial;
    private ResourceResponse activeMapResource;
    private ResourceResponse disassemblyInput;
    private ResourceResponse disassemblyOutput;
    private ResourceResponse secondDisassemblyInput;
    private ResourceResponse secondDisassemblyOutput;
    private final List<ResourceResponse> productionResources = new ArrayList<>();
    private final List<ResourceResponse> secondProductionResources = new ArrayList<>();
    private TechnologicalMapResponse productionTechMap;
    private TechnologicalMapResponse secondProductionTechMap;
    private TechnologicalMapResponse activeExpenseResourceTechMap;
    private TechnologicalMapResponse disassemblyTechMap;
    private TechnologicalMapResponse secondDisassemblyTechMap;
    private ShiftResponse journalShift;
    private ManufacturingItemResponse journalProduction;
    private ManufacturingItemResponse secondJournalProduction;
    private DisassembleItemResponse journalDisassembly;
    private DisassembleItemResponse secondJournalDisassembly;
    private String firstProduct;
    private String secondProduct;
    private String secondLocationNonSeriesProduct;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        nonSeriesProductions = new NonSeriesProductionFixture(testContext, apiExecutor);
        disassemblies = new DisassembleFixture(testContext, apiExecutor);
        productions = new ProductionFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        shifts = new ShiftFixture(testContext, apiExecutor);
        technologicalMaps = new TechnologicalMapFixture(testContext, apiExecutor);

        LocationProfileFixture.LocationSet locations = locationProfiles.create(LocationProfile.TSUK_PRODUCTION, 3);
        location = locations.locations().getFirst();
        secondLocation = locations.locations().get(1);
        techMapOnlyLocation = locations.locations().get(2);
        UserFixture.BusinessActor actor = users.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER,
                List.of(location, secondLocation));
        apiExecutor.setSessionForRole(ACTOR, actor.username(), actor.password());

        injectSessionCookies(
                getPlaywrightSessionProvider().getSession(actor.username(), actor.password()),
                sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + location.getId() + "');");

        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        material = resources.createUniqueResource("analytics-nsp-material-");
        inactiveMapMaterial = resources.createUniqueResource("analytics-inactive-map-material-");
        activeMapResource = resources.createUniqueResource("analytics-active-map-resource-");
        disassemblyInput = resources.createUniqueResource("analytics-disassembly-input-");
        disassemblyOutput = resources.createUniqueResource("analytics-disassembly-output-");
        RelocationFixture relocation = new RelocationFixture(testContext, apiExecutor);
        relocation.seedExactStock(location.getId(), material.getId(), 50.0, ACTOR);
        relocation.seedExactStock(location.getId(), inactiveMapMaterial.getId(), 50.0, ACTOR);
        relocation.seedExactStock(location.getId(), activeMapResource.getId(), 50.0, ACTOR);
        relocation.seedExactStock(location.getId(), disassemblyInput.getId(), 50.0, ACTOR);

        firstProduct = "analytics-nsp-product-a-" + System.nanoTime();
        secondProduct = "analytics-nsp-product-b-" + System.nanoTime();

        productionResources.add(resources.createUniqueResource("analytics-daily-input-a-"));
        productionResources.add(resources.createUniqueResource("analytics-daily-input-b-"));
        productionResources.add(resources.createUniqueResource("analytics-daily-product-"));
        relocation.seedExactStock(location.getId(), productionResources.get(0).getId(), 100.0, ACTOR);
        relocation.seedExactStock(location.getId(), productionResources.get(1).getId(), 100.0, ACTOR);
        activeExpenseResourceTechMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionTechMap(
                        List.of(
                                productionResources.get(0),
                                productionResources.get(1),
                                activeMapResource),
                        techMapOnlyLocation.getId()).build());
        TechnologicalMapResponse inactiveMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionTechMap(
                        List.of(
                                productionResources.get(0),
                                productionResources.get(1),
                                inactiveMapMaterial),
                        techMapOnlyLocation.getId()).build());
        technologicalMaps.deactivateTechMap(
                UserRole.ADMIN, inactiveMap.getId(), techMapOnlyLocation.getId());
        productionTechMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "analytics-production-primary",
                        List.of(
                                new ResourceUsageRequest(productionResources.get(0).getId(), 2.75),
                                new ResourceUsageRequest(productionResources.get(1).getId(), 1.25)),
                        List.of(new ResourceUsageRequest(productionResources.get(2).getId(), 1.0)),
                        Set.of(location.getId())).build());
        journalShift = shifts.create(
                ACTOR,
                location.getId(),
                ShiftRequest.builder()
                        .name("analytics-shift-" + System.nanoTime())
                        .description("dynamic analytics production shift")
                        .timeStart(java.time.LocalTime.of(8, 0))
                        .timeEnd(java.time.LocalTime.of(16, 0))
                        .workerQty(JOURNAL_SHIFT_WORKERS)
                        .build());
        journalProduction = productions.createAsWithShift(
                ACTOR,
                location.getId(),
                productionTechMap,
                JOURNAL_PRODUCTION_AMOUNT,
                ProductionDataFactory.uniqueBatchNumber(),
                LocalDate.now(),
                journalShift);
        disassemblyTechMap = disassemblies.createDisassembleTechMapAs(
                UserRole.ADMIN,
                location.getId(),
                List.of(disassemblyInput, disassemblyOutput));
        io.restassured.response.Response disassembly = disassemblies.createAs(
                ACTOR,
                location.getId(),
                disassemblyTechMap,
                2.0,
                1.0,
                ProductionDataFactory.uniqueBatchNumber());
        List<DisassembleItemResponse> createdDisassemblies = disassembly.jsonPath()
                .getList("", DisassembleItemResponse.class);
        if (createdDisassemblies == null || createdDisassemblies.isEmpty()) {
            throw new IllegalStateException("Empty create disassembly response for analytics fixture");
        }
        createdDisassemblies.stream()
                .map(DisassembleItemResponse::getId)
                .forEach(disassemblyIds::add);
        journalDisassembly = createdDisassemblies.getFirst();
        createNonSeriesProduction(firstProduct, FIRST_PRODUCT_AMOUNT, FIRST_USAGE_PER_UNIT);
        createNonSeriesProduction(secondProduct, SECOND_PRODUCT_AMOUNT, SECOND_USAGE_PER_UNIT);
        createNonSeriesProduction(
                "analytics-inactive-map-product-" + System.nanoTime(),
                1.0,
                inactiveMapMaterial,
                1.0);
        createNonSeriesProduction(
                "analytics-active-map-product-" + System.nanoTime(),
                1.0,
                activeMapResource,
                1.0);

        setupSecondLocationAnalyticsData();

        log.info("Production analytics fixture: location={} ({}), actor={}, material={} ({}), "
                        + "journalProduction={}, product={}, shift={} workers={}",
                location.getId(), location.getName(), actor.username(), material.getId(), material.getName(),
                journalProduction.getId(), journalProduction.getProduct().getName(),
                journalShift.getName(), journalShift.getWorkerQty());
    }

    private void setupSecondLocationAnalyticsData() {
        Long primaryCategoryId = productionResources.get(2).getCategory().getId();
        Long secondCategoryId = productions.getResourceCategories().stream()
                .map(ResourceCategoryResponse::getId)
                .filter(id -> !id.equals(primaryCategoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Production analytics filter coverage requires two resource categories"));

        secondMaterial = trackResource(resources.createUniqueResource("analytics-l2-material-", secondCategoryId));
        secondDisassemblyInput = trackResource(
                resources.createUniqueResource("analytics-l2-disassembly-input-", secondCategoryId));
        secondDisassemblyOutput = trackResource(
                resources.createUniqueResource("analytics-l2-disassembly-output-", secondCategoryId));
        secondProductionResources.add(trackResource(
                resources.createUniqueResource("analytics-l2-input-a-", secondCategoryId)));
        secondProductionResources.add(trackResource(
                resources.createUniqueResource("analytics-l2-input-b-", secondCategoryId)));
        secondProductionResources.add(trackResource(
                resources.createUniqueResource("analytics-l2-product-", secondCategoryId)));

        RelocationFixture relocation = new RelocationFixture(testContext, apiExecutor);
        relocation.seedExactStock(secondLocation.getId(), secondMaterial.getId(), 100.0, ACTOR);
        relocation.seedExactStock(secondLocation.getId(), secondDisassemblyInput.getId(), 100.0, ACTOR);
        relocation.seedExactStock(secondLocation.getId(), secondProductionResources.get(0).getId(), 100.0, ACTOR);
        relocation.seedExactStock(secondLocation.getId(), secondProductionResources.get(1).getId(), 100.0, ACTOR);

        secondProductionTechMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        "analytics-production-secondary",
                        List.of(
                                new ResourceUsageRequest(secondProductionResources.get(0).getId(), 1.5),
                                new ResourceUsageRequest(secondProductionResources.get(1).getId(), 0.5)),
                        List.of(new ResourceUsageRequest(secondProductionResources.get(2).getId(), 1.0)),
                        Set.of(secondLocation.getId())).build());
        secondJournalProduction = productions.createAs(
                ACTOR,
                secondLocation.getId(),
                secondProductionTechMap,
                3.0,
                ProductionDataFactory.uniqueBatchNumber());
        additionalProductions.add(new ScopedId(secondJournalProduction.getId(), secondLocation.getId()));

        secondDisassemblyTechMap = disassemblies.createDisassembleTechMapAs(
                UserRole.ADMIN,
                secondLocation.getId(),
                List.of(secondDisassemblyInput, secondDisassemblyOutput));
        io.restassured.response.Response disassembly = disassemblies.createAs(
                ACTOR,
                secondLocation.getId(),
                secondDisassemblyTechMap,
                3.0,
                1.5,
                ProductionDataFactory.uniqueBatchNumber());
        List<DisassembleItemResponse> created = disassembly.jsonPath()
                .getList("", DisassembleItemResponse.class);
        if (created == null || created.isEmpty()) {
            throw new IllegalStateException("Empty secondary disassembly response for analytics fixture");
        }
        secondJournalDisassembly = created.getFirst();
        created.forEach(item -> additionalDisassemblies.add(
                new ScopedId(item.getId(), secondLocation.getId())));

        secondLocationNonSeriesProduct = "analytics-l2-nsp-product-" + System.nanoTime();
        NonSeriesProductionResponse nonSeries = nonSeriesProductions.createAs(
                ACTOR,
                secondLocation.getId(),
                NonSeriesProductionStatus.DONE,
                secondLocationNonSeriesProduct,
                5.0,
                secondMaterial.getId(),
                1.5);
        additionalNonSeriesProductions.add(new ScopedId(nonSeries.getId(), secondLocation.getId()));
    }

    @AfterClass(alwaysRun = true)
    public void cleanupProductionAnalyticsFixture() {
        for (ScopedId item : additionalDisassemblies.reversed()) {
            try {
                disassemblies.deleteRaw(ACTOR, item.id(), item.storageId());
            } catch (RuntimeException e) {
                log.warn("Could not delete additional disassembly {}: {}", item.id(), e.getMessage());
            }
        }
        for (ScopedId item : additionalProductions.reversed()) {
            try {
                productions.deleteAs(UserRole.ADMIN, item.id(), item.storageId());
            } catch (RuntimeException e) {
                log.warn("Could not delete additional production {}: {}", item.id(), e.getMessage());
            }
        }
        for (ScopedId item : additionalNonSeriesProductions.reversed()) {
            try {
                nonSeriesProductions.deleteAs(ACTOR, item.id(), item.storageId());
            } catch (RuntimeException e) {
                log.warn("Could not delete additional non-series production {}: {}", item.id(), e.getMessage());
            }
        }
        for (Long id : disassemblyIds.reversed()) {
            try {
                disassemblies.deleteRaw(ACTOR, id, location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not delete disassembly {}: {}", id, e.getMessage());
            }
        }
        if (journalProduction != null) {
            try {
                productions.deleteAs(ACTOR, journalProduction.getId(), location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not delete production {}: {}", journalProduction.getId(), e.getMessage());
            }
        }
        for (Long id : nonSeriesProductionIds.reversed()) {
            try {
                nonSeriesProductions.deleteAs(ACTOR, id, location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not delete non-series production {}: {}", id, e.getMessage());
            }
        }
        if (journalShift != null) {
            try {
                shifts.deleteRaw(ACTOR, journalShift.getId(), location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not delete shift {}: {}", journalShift.getId(), e.getMessage());
            }
        }
        if (productionTechMap != null) {
            try {
                technologicalMaps.deactivateTechMap(
                        UserRole.ADMIN, productionTechMap.getId(), location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate tech map {}: {}", productionTechMap.getId(), e.getMessage());
            }
        }
        if (disassemblyTechMap != null) {
            try {
                technologicalMaps.deactivateTechMap(
                        UserRole.ADMIN, disassemblyTechMap.getId(), location.getId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate disassembly tech map {}: {}",
                        disassemblyTechMap.getId(), e.getMessage());
            }
        }
        for (ScopedId item : additionalTechMaps.reversed()) {
            try {
                technologicalMaps.deactivateTechMap(UserRole.ADMIN, item.id(), item.storageId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate additional tech map {}: {}", item.id(), e.getMessage());
            }
        }
        if (secondProductionTechMap != null) {
            try {
                technologicalMaps.deactivateTechMap(
                        UserRole.ADMIN, secondProductionTechMap.getId(), secondLocation.getId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate secondary production tech map {}: {}",
                        secondProductionTechMap.getId(), e.getMessage());
            }
        }
        if (secondDisassemblyTechMap != null) {
            try {
                technologicalMaps.deactivateTechMap(
                        UserRole.ADMIN, secondDisassemblyTechMap.getId(), secondLocation.getId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate secondary disassembly tech map {}: {}",
                        secondDisassemblyTechMap.getId(), e.getMessage());
            }
        }
        if (activeExpenseResourceTechMap != null) {
            try {
                technologicalMaps.deactivateTechMap(
                        UserRole.ADMIN,
                        activeExpenseResourceTechMap.getId(),
                        techMapOnlyLocation.getId());
            } catch (RuntimeException e) {
                log.warn("Could not deactivate active-map expense tech map {}: {}",
                        activeExpenseResourceTechMap.getId(), e.getMessage());
            }
        }
        if (locationProfiles != null) {
            locationProfiles.cleanup();
        }
        if (resources != null) {
            List<ResourceResponse> createdResources = new ArrayList<>(productionResources);
            if (material != null) {
                createdResources.add(material);
            }
            if (inactiveMapMaterial != null) {
                createdResources.add(inactiveMapMaterial);
            }
            if (activeMapResource != null) {
                createdResources.add(activeMapResource);
            }
            if (disassemblyInput != null) {
                createdResources.add(disassemblyInput);
            }
            if (disassemblyOutput != null) {
                createdResources.add(disassemblyOutput);
            }
            createdResources.addAll(additionalResources);
            for (ResourceResponse resource : createdResources) {
                try {
                    resources.deactivate(UserRole.ADMIN, resource.getId());
                } catch (RuntimeException e) {
                    log.warn("Could not deactivate analytics resource {}: {}", resource.getId(), e.getMessage());
                }
            }
        }
        if (users != null) {
            users.deactivateTrackedUsers();
        }
        apiExecutor.evictSessionForRole(ACTOR);
    }

    @Test(priority = 10)
    @TestCaseId("TC-ANL-UI-006")
    @Story("Daily tab")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Динамічно створений «Керівник локації» відкриває `/analytics/production`.
            Вкладка «Поденне» завантажує timeline лише для його динамічної виробничої локації;
            доступні фільтри «Період», «Локації», «Категорії», «Вироби», немає помилки завантаження.
            Обсяг звіряється з amount запису журналу, а задіяний персонал — з workerQty snapshot-зміни.
            """)
    public void dailyTabLoadsForDynamicLocationHead() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page);
        Response timeline = page.waitForResponse(
                response -> response.url().contains("/production/analytic/assembly/timeline?")
                        && !response.url().contains("timeline-filters"),
                analytics::open);

        assertThat(page.url()).contains("/analytics/production");
        assertThat(analytics.isDailyTabVisible()).as("Вкладка «Поденне»").isTrue();
        assertThat(analytics.isStatisticsTabVisible()).as("Вкладка «Статистика»").isTrue();
        assertThat(analytics.hasDailyFilters()).as("Фільтри вкладки «Поденне»").isTrue();
        assertScopedResponse(timeline, "/assembly/timeline");
        ManufacturingItemResponse journalRecord = productions.getById(
                ACTOR, journalProduction.getId(), location.getId());
        assertTimelineMatchesJournal(timeline, journalRecord);

        page.waitForCondition(analytics::isDailyContentReady);
        assertThat(analytics.hasLoadError()).as("Помилка завантаження «Поденне»").isFalse();
        assertThat(decimalFrom(analytics.dailyMetricValueText("Обсяг за період")))
                .as("UI: обсяг за період = amount у журналі виробництва")
                .isEqualByComparingTo(BigDecimal.valueOf(journalRecord.getAmount()));
        assertThat(decimalFrom(analytics.dailyMetricValueText("Людино")))
                .as("UI: задіяний персонал = workerQty snapshot-зміни у журналі")
                .isEqualByComparingTo(BigDecimal.valueOf(journalRecord.getShift().getWorkerQty()));
        analytics.attachScreenshot("TC-ANL-UI-006 — Поденне / dynamic location");
    }

    @Test(priority = 15)
    @TestCaseId("TC-ANL-UI-008")
    @Story("Daily tab filters")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Фільтри «Локації», «Категорії» та «Вироби» вкладки «Поденне» застосовуються
            до timeline-запиту. Після кожного вибору аналітика зберігає обсяг і персонал
            динамічного запису журналу виробництва.
            """)
    public void dailyFiltersKeepJournalVolumeAndShiftPersonnel() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page);
        waitForTimelineResponse(analytics::open);
        String productName = journalProduction.getProduct().getName();
        String categoryName = productionResources.get(2).getCategory().getName();

        Response productFiltered = waitForTimelineResponse(
                () -> analytics.selectDailyFilterOption("Вироби", productName));
        log.info("Product-filtered timeline URL: {}", productFiltered.url());
        assertThat(productFiltered.url()).contains(String.valueOf(journalProduction.getProduct().getId()));
        assertTimelineMatchesJournal(productFiltered, journalProduction);

        Response categoryFiltered = waitForTimelineResponse(
                () -> analytics.selectDailyFilterOption("Категорії", categoryName));
        log.info("Category-filtered timeline URL: {}", categoryFiltered.url());
        assertThat(categoryFiltered.url()).contains(String.valueOf(productionResources.get(2).getCategory().getId()));
        assertTimelineMatchesJournal(categoryFiltered, journalProduction);

        // selectedStorageId is injected before navigation, so the dynamic location is already
        // an active filter. Re-selecting the same single option does not issue a new request.
        log.info("Location-filtered timeline URL: {}", categoryFiltered.url());
        assertThat(categoryFiltered.url()).contains("storageIds=" + location.getId());
        assertTimelineMatchesJournal(categoryFiltered, journalProduction);
        analytics.attachScreenshot("TC-ANL-UI-008 — daily filters");
    }

    @Test(priority = 20)
    @TestCaseId("TC-ANL-UI-005")
    @Story("Daily tab date presets")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            На вкладці «Поденне» вибір пресетів DateRangePicker змінює період:
            «1 день» = сьогодні браузера, «7 днів» = сьогодні мінус 7 днів … сьогодні.
            Сценарій виконує динамічний керівник на динамічно створеній локації.
            """)
    public void predefinedRangesApplyOnProductionAnalyticsPicker() {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        DateRangePickerComponent picker = analytics.periodPicker();

        assertThat(picker.isVisible()).as("DateRangePicker «Період»").isTrue();
        String initialTrigger = picker.getTriggerText();

        picker.open();
        assertThat(picker.visiblePresetLabels())
                .as("Предефайни в поповері")
                .containsExactlyElementsOf(DateRangePickerComponent.PRESET_LABELS);

        LocalDate today = analytics.browserToday();
        picker.selectPreset(DateRangePickerComponent.PRESET_1_DAY);
        analytics.waitUntilTriggerChanges(initialTrigger);
        assertThat(picker.getFromIso()).isEqualTo(today.toString());
        assertThat(picker.getToIso()).isEqualTo(today.toString());

        String oneDayTrigger = picker.getTriggerText();
        picker.selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.waitUntilTriggerChanges(oneDayTrigger);
        assertThat(picker.getFromIso()).isEqualTo(today.minusDays(7).toString());
        assertThat(picker.getToIso()).isEqualTo(today.toString());
        assertThat(picker.getTriggerText()).isNotEqualTo(oneDayTrigger);
        analytics.attachScreenshot("TC-ANL-UI-005 — presets / dynamic location");
    }

    @Test(priority = 30)
    @TestCaseId("TC-ANL-UI-007")
    @Story("Statistics — non-series expenses")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Регресія «Аналітика → Несерійне виробництво → Витрати — невірна сума».
            Для одного матеріалу створюються два несерійні виробництва: 2 × 3 та 4 × 2.
            «Статистика → Витрати → Несерійне виробництво» має показати сумарну витрату 14,
            дві операції та коректний рядок матеріалу. API-запит має бути scoped до динамічної локації.
            """)
    public void statisticsShowsCorrectNonSeriesExpenseSum() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        DateRangePickerComponent picker = analytics.periodPicker();
        picker.selectPreset(DateRangePickerComponent.PRESET_7_DAYS);

        Response expenses = page.waitForResponse(
                response -> response.url().contains("/production/analytic/non-serial/input?"),
                analytics::openNonSeriesExpenses);
        assertScopedResponse(expenses, "/non-serial/input");

        JsonNode materialRow = findResourceRow(JSON.readTree(expenses.text()), material.getId());
        assertThat(materialRow.path("count").asInt()).as("Кількість операцій").isEqualTo(2);
        assertThat(new BigDecimal(materialRow.path("totalAmount").asText()))
                .as("API: загальна витрата = 2×3 + 4×2")
                .isEqualByComparingTo(EXPECTED_EXPENSE);
        assertThat(materialRow.path("operations")).hasSize(2);

        analytics.waitForExpenseResource(material.getName());
        analytics.searchExpensesByName(material.getName());
        analytics.waitForExpenseResource(material.getName());
        assertThat(analytics.hasLoadError()).as("Помилка завантаження «Статистика»").isFalse();
        assertThat(decimalFrom(analytics.statisticValueText("Обсяг, шт")))
                .as("UI: підсумковий обсяг витрат")
                .isEqualByComparingTo(EXPECTED_EXPENSE);

        String rowText = analytics.expenseResourceRowText(material.getName());
        assertThat(rowText).contains(material.getName(), material.getCategory().getName());
        assertThat(decimalFrom(rowText))
                .as("UI: витрата в рядку матеріалу")
                .isEqualByComparingTo(EXPECTED_EXPENSE);
        analytics.attachScreenshot("TC-ANL-UI-007 — non-series expense sum");
    }

    @Test(priority = 40)
    @TestCaseId("TC-ANL-UI-009")
    @Story("Statistics filters and Excel export")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            На вкладці «Статистика» перевіряються таби «За категоріями», «За тегами»,
            «За виробами», «Витрати», «Продуктивність» та типи «Виготовлення», «Розбір»,
            «Несерійне виробництво». Перемикання активує потрібний таб, після фільтрації
            відображаються дані динамічно створеної локації.
            Для відфільтрованих витрат несерійного виробництва завантажується валідний XLSX
            із поточним динамічно створеним матеріалом.
            """)
    public void statisticsProductionTypeTabsFilterAndExportExcel() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        page.waitForTimeout(500);

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_BY_TAGS);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_BY_TAGS)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_BY_PRODUCTS);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_BY_PRODUCTS)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_EXPENSES);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_EXPENSES)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_PRODUCTIVITY);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_PRODUCTIVITY)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_BY_CATEGORIES);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_BY_CATEGORIES)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_DISASSEMBLY);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_DISASSEMBLY)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_NON_SERIES);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_NON_SERIES)).isTrue();

        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_EXPENSES);
        assertThat(analytics.isTabSelected(ProductionAnalyticsPage.TAB_EXPENSES)).isTrue();
        analytics.waitForExpenseResource(material.getName());

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            UiDownloadAssertions.assertNonEmptyXlsx(
                    export.path(), export.sizeBytes(), "Production Analytics Excel");
            Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(
                    Files.readAllBytes(export.path()));
            String workbookText = workbook.values().stream()
                    .flatMap(List::stream)
                    .flatMap(List::stream)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(workbookText)
                    .as("Excel містить поточний відфільтрований матеріал")
                    .contains(material.getName());
        } finally {
            Files.deleteIfExists(export.path());
        }
        analytics.attachScreenshot("TC-ANL-UI-009 — statistics tabs and Excel export");
    }

    @Test(priority = 50)
    @TestCaseId("TC-ANL-UI-010")
    @Story("Expenses — only materials control")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            Чекбокс «Лише матеріали» видимий для Виготовлення, Розбору та Несерійного
            виробництва, за замовчуванням вимкнений, а InfoIcon показує погоджену підказку.
            """)
    public void onlyMaterialsCheckboxAndTooltipAreAvailableForEveryExpenseType() {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);

        for (String productionType : List.of(
                ProductionAnalyticsPage.TAB_ASSEMBLY,
                ProductionAnalyticsPage.TAB_DISASSEMBLY,
                ProductionAnalyticsPage.TAB_NON_SERIES)) {
            analytics.openExpenses(productionType);
            assertThat(analytics.hasOnlyMaterialsControl())
                    .as("Чекбокс для типу %s", productionType)
                    .isTrue();
            assertThat(analytics.isOnlyMaterialsChecked())
                    .as("Початковий стан для %s", productionType)
                    .isFalse();
        }
        assertThat(analytics.onlyMaterialsTooltipText()).isEqualTo(MATERIAL_TOOLTIP);
        analytics.attachScreenshot("TC-ANL-UI-010 — only materials control and tooltip");
    }

    @Test(priority = 60)
    @TestCaseId("TC-ANL-UI-011")
    @Story("Expenses — global active tech-map material filter")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            rawResources=true передається для всіх трьох типів витрат і зберігається між
            перемиканнями. У non-series ресурс без техкарти та ресурс лише з неактивною
            техкартою лишаються, а ресурс з активною техкартою іншої локації виключається.
            """)
    public void onlyMaterialsUsesGlobalActiveTechMapsAndPersistsAcrossTypes() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openExpenses(ProductionAnalyticsPage.TAB_ASSEMBLY);

        Response assembly = waitForExpenseResponse(
                "/assembly/input?", true, () -> analytics.setOnlyMaterials(true));
        assertRawResourcesRequest(assembly, true);

        Response disassembly = waitForExpenseResponse(
                "/disassembly/input?", true,
                () -> analytics.openExpenses(ProductionAnalyticsPage.TAB_DISASSEMBLY));
        assertRawResourcesRequest(disassembly, true);
        assertThat(analytics.isOnlyMaterialsChecked()).isTrue();

        Response nonSeries = waitForExpenseResponse(
                "/non-serial/input?", true,
                () -> analytics.openExpenses(ProductionAnalyticsPage.TAB_NON_SERIES));
        assertRawResourcesRequest(nonSeries, true);
        assertThat(analytics.isOnlyMaterialsChecked()).isTrue();

        JsonNode filtered = JSON.readTree(nonSeries.text());
        assertThat(findResourceRowOrNull(filtered, material.getId())).isNotNull();
        assertThat(findResourceRowOrNull(filtered, inactiveMapMaterial.getId())).isNotNull();
        assertThat(findResourceRowOrNull(filtered, activeMapResource.getId()))
                .as("Активна техкарта іншої локації виключає ресурс глобально")
                .isNull();

        Response unfiltered = waitForExpenseResponse(
                "/non-serial/input?", false, () -> analytics.setOnlyMaterials(false));
        assertRawResourcesRequest(unfiltered, false);
        assertThat(findResourceRowOrNull(JSON.readTree(unfiltered.text()), activeMapResource.getId()))
                .as("Вимкнення фільтра повертає вироблюваний ресурс")
                .isNotNull();
        analytics.attachScreenshot("TC-ANL-UI-011 — global active tech-map filter");
    }

    @Test(priority = 70)
    @TestCaseId("TC-ANL-UI-012")
    @Story("Expenses — materials filter in multi-sheet Excel export")
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Excel враховує rawResources=true, але не локальний текст «Пошук за назвою…»,
            оскільки багатолистовий export застосовує лише верхні фільтри. Workbook містить
            обидва матеріали та не містить ресурс з активною техкартою.
            """)
    public void excelExportRespectsOnlyMaterialsAndIgnoresLocalExpenseSearch() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openNonSeriesExpenses();
        waitForExpenseResponse("/non-serial/input?", true, () -> analytics.setOnlyMaterials(true));

        analytics.searchExpensesByName(material.getName());
        analytics.waitForExpenseResource(material.getName());
        assertThat(analytics.isExpenseResourceVisible(material.getName())).isTrue();
        assertThat(analytics.isExpenseResourceVisible(inactiveMapMaterial.getName())).isFalse();

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            assertThat(export.requestUrl()).contains("rawResources=true");
            UiDownloadAssertions.assertNonEmptyXlsx(
                    export.path(), export.sizeBytes(), "Production Analytics filtered Excel");
            Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(
                    Files.readAllBytes(export.path()));
            String workbookText = workbook.values().stream()
                    .flatMap(List::stream)
                    .flatMap(List::stream)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(workbookText)
                    .contains(material.getName(), inactiveMapMaterial.getName())
                    .doesNotContain(activeMapResource.getName());
        } finally {
            Files.deleteIfExists(export.path());
        }
        analytics.attachScreenshot("TC-ANL-UI-012 — filtered materials Excel");
    }

    @Test(priority = 80)
    @TestCaseId("TC-ANL-UI-013")
    @Story("Statistics — Excel download")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Кнопка «Експорт в Excel» завантажує один валідний непорожній XLSX.")
    public void excelExportButtonDownloadsValidWorkbook() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();

        assertThat(analytics.isExcelExportButtonVisible()).isTrue();
        assertThat(analytics.isExcelExportButtonEnabled()).isTrue();

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            UiDownloadAssertions.assertNonEmptyXlsx(
                    export.path(), export.sizeBytes(), "Production Analytics multi-sheet Excel");
            assertThat(export.suggestedFilename()).endsWithIgnoringCase(".xlsx");
            assertThat(XlsxWorkbookReader.sheetNames(Files.readAllBytes(export.path())))
                    .isNotEmpty();
        } finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test(priority = 81)
    @TestCaseId("TC-ANL-UI-014")
    @Story("Statistics — Excel workbook contract")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Workbook має рівно п'ять видимих аркушів із точними назвами та порядком.")
    public void excelWorkbookHasExactlyFiveRequiredVisibleSheetsInOrder() throws Exception {
        byte[] bytes = exportWorkbook(new ProductionAnalyticsPage(page).open());

        assertThat(XlsxWorkbookReader.sheetNames(bytes))
                .containsExactlyElementsOf(EXPECTED_EXPORT_SHEETS);
        assertThat(XlsxWorkbookReader.hiddenSheetNames(bytes)).isEmpty();
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(bytes);
        EXPECTED_EXPORT_HEADERS.forEach((sheet, headers) -> {
            List<List<String>> rows = workbook.get(sheet);
            assertThat(rows)
                    .as("Аркуш «%s» містить погоджений header", sheet)
                    .isNotNull()
                    .isNotEmpty();
            assertThat(rows.getFirst()).containsExactlyElementsOf(headers);
        });
    }

    @Test(priority = 82)
    @TestCaseId("TC-ANL-UI-015")
    @Story("Statistics — Excel keeps five sheets when switching tabs")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Без активних фільтрів усі п'ять аркушів зберігають дані після перемикання таба.")
    public void excelWorkbookKeepsAllSheetsWithUnchangedEffectiveFilters() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        analytics.openStatisticsProductionType(ProductionAnalyticsPage.TAB_BY_CATEGORIES);
        Map<String, List<List<String>>> byCategory = XlsxWorkbookReader.sheets(exportWorkbook(analytics));

        analytics.openExpenses(ProductionAnalyticsPage.TAB_DISASSEMBLY);
        Map<String, List<List<String>>> disassemblyExpenses = XlsxWorkbookReader.sheets(exportWorkbook(analytics));

        assertThat(byCategory).hasSize(5);
        assertThat(disassemblyExpenses).isEqualTo(byCategory);
    }

    @Test(priority = 83)
    @TestCaseId("TC-ANL-UI-016")
    @Story("Statistics — sheet Виготовлення - За виробами")
    @Severity(SeverityLevel.CRITICAL)
    public void productionSheetContainsSerialProductionOutputOnly() throws Exception {
        Map<String, List<List<String>>> workbook = exportRequiredWorkbook();
        String text = sheetText(workbook, 0, "Виготовлення - За виробами");

        assertThat(text)
                .contains(journalProduction.getProduct().getName())
                .doesNotContain(disassemblyOutput.getName(), material.getName());
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(0)),
                journalProduction.getProduct().getName(),
                productionResources.get(2),
                BigDecimal.valueOf(JOURNAL_PRODUCTION_AMOUNT));
    }

    @Test(priority = 84)
    @TestCaseId("TC-ANL-UI-017")
    @Story("Statistics — sheet Виготовлення - Витрати")
    @Severity(SeverityLevel.CRITICAL)
    public void productionExpenseSheetContainsSerialProductionInputsOnly() throws Exception {
        Map<String, List<List<String>>> workbook = exportRequiredWorkbook();
        String text = sheetText(workbook, 1, "Виготовлення - Витрати");

        assertThat(text)
                .contains(productionResources.get(0).getName(), productionResources.get(1).getName())
                .doesNotContain(disassemblyInput.getName(), material.getName());
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(1)),
                productionResources.get(0).getName(),
                productionResources.get(0),
                usageTotal(journalProduction.getInput(), JOURNAL_PRODUCTION_AMOUNT,
                        productionResources.get(0).getId()));
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(1)),
                productionResources.get(1).getName(),
                productionResources.get(1),
                usageTotal(journalProduction.getInput(), JOURNAL_PRODUCTION_AMOUNT,
                        productionResources.get(1).getId()));
    }

    @Test(priority = 85)
    @TestCaseId("TC-ANL-UI-018")
    @Story("Statistics — sheet Розбір - За виробами")
    @Severity(SeverityLevel.CRITICAL)
    public void disassemblySheetContainsDisassemblyOutputOnly() throws Exception {
        Map<String, List<List<String>>> workbook = exportRequiredWorkbook();
        String text = sheetText(workbook, 2, "Розбір - За виробами");

        assertThat(text)
                .contains(disassemblyOutput.getName())
                .doesNotContain(journalProduction.getProduct().getName(), material.getName());
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(2)),
                disassemblyOutput.getName(),
                disassemblyOutput,
                usageTotal(journalDisassembly.getOutputs(), journalDisassembly.getAmount(),
                        disassemblyOutput.getId()));
    }

    @Test(priority = 86)
    @TestCaseId("TC-ANL-UI-019")
    @Story("Statistics — sheet Розбір - Витрати")
    @Severity(SeverityLevel.CRITICAL)
    public void disassemblyExpenseSheetContainsDisassemblyInputOnly() throws Exception {
        Map<String, List<List<String>>> workbook = exportRequiredWorkbook();
        String text = sheetText(workbook, 3, "Розбір - Витрати");

        assertThat(text)
                .contains(disassemblyInput.getName())
                .doesNotContain(productionResources.get(0).getName(), material.getName());
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(3)),
                disassemblyInput.getName(),
                disassemblyInput,
                BigDecimal.valueOf(journalDisassembly.getAmount()));
    }

    @Test(priority = 87)
    @TestCaseId("TC-ANL-UI-020")
    @Story("Statistics — sheet Несерійне виробництво - Витрати")
    @Severity(SeverityLevel.CRITICAL)
    public void nonSeriesExpenseSheetContainsOnlyNonSeriesInputs() throws Exception {
        Map<String, List<List<String>>> workbook = exportRequiredWorkbook();
        String text = sheetText(workbook, 4, "Несерійне виробництво - Витрати");

        assertThat(text)
                .contains(material.getName())
                .doesNotContain(productionResources.get(0).getName(), disassemblyInput.getName());
        assertUniqueWorkbookRow(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(4)),
                material.getName(),
                material,
                EXPECTED_EXPENSE);
    }

    @Test(priority = 88)
    @TestCaseId("TC-ANL-UI-021")
    @Story("Statistics — period filter in Excel")
    @Severity(SeverityLevel.BLOCKER)
    public void excelExportUsesInclusiveSelectedPeriod() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        LocalDate today = analytics.browserToday();
        LocalDate from = today.minusDays(1);
        ResourceResponse fromBoundary = createSerialExportMarker("analytics-period-from-", from);
        ResourceResponse outsidePeriod = createSerialExportMarker(
                "analytics-period-outside-", from.minusDays(1), UserRole.ADMIN);

        analytics.periodPicker().setRange(from, today);
        analytics.openStatisticsTab();

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            assertThat(export.requestUrl())
                    .contains("fromDate=" + from)
                    .contains("toDate=" + today);
            String text = workbookText(XlsxWorkbookReader.sheets(Files.readAllBytes(export.path())));
            assertThat(text)
                    .contains(
                            journalProduction.getProduct().getName(),
                            material.getName(),
                            fromBoundary.getName())
                    .doesNotContain(outsidePeriod.getName());
        } finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test(priority = 89)
    @TestCaseId("TC-ANL-UI-022")
    @Story("Statistics — location, product and material filters in Excel")
    @Severity(SeverityLevel.BLOCKER)
    public void excelExportForwardsLocationAndProductTopFilters() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        analytics.selectFilterOption("Вироби", journalProduction.getProduct().getName());

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            assertThat(export.requestUrl())
                    .contains("storageIds=" + location.getId())
                    .contains("resourceIds=" + journalProduction.getProduct().getId());
            String filteredText = workbookText(XlsxWorkbookReader.sheets(Files.readAllBytes(export.path())));
            assertThat(filteredText)
                    .contains(journalProduction.getProduct().getName())
                    .doesNotContain(secondJournalProduction.getProduct().getName());
        } finally {
            Files.deleteIfExists(export.path());
        }

        analytics.selectFilterOption("Вироби", journalProduction.getProduct().getName());
        ProductionAnalyticsPage.ExportDownloadResult resetExport = analytics.exportStatisticsToExcel();
        try {
            assertThat(resetExport.requestUrl()).doesNotContain("resourceIds=");
            assertThat(workbookText(XlsxWorkbookReader.sheets(Files.readAllBytes(resetExport.path()))))
                    .contains(
                            journalProduction.getProduct().getName(),
                            productionResources.get(0).getName(),
                            disassemblyOutput.getName(),
                            disassemblyInput.getName(),
                            material.getName());
        } finally {
            Files.deleteIfExists(resetExport.path());
        }

        Map<String, List<List<String>>> firstLocation = exportWorkbookViaApi(
                filterQuery(List.of(location.getId()), null, null));
        Map<String, List<List<String>>> secondLocationWorkbook = exportWorkbookViaApi(
                filterQuery(List.of(secondLocation.getId()), null, null));
        Map<String, List<List<String>>> bothLocations = exportWorkbookViaApi(
                filterQuery(List.of(location.getId(), secondLocation.getId()), null, null));

        assertLocationMarkers(firstLocation, true, false);
        assertLocationMarkers(secondLocationWorkbook, false, true);
        assertLocationMarkers(bothLocations, true, true);

        Map<String, List<List<String>>> bothProducts = exportWorkbookViaApi(filterQuery(
                List.of(location.getId(), secondLocation.getId()),
                null,
                List.of(journalProduction.getProduct().getId(), secondJournalProduction.getProduct().getId())));
        assertThat(sheetText(bothProducts, 0, "Виготовлення - За виробами"))
                .contains(journalProduction.getProduct().getName(), secondJournalProduction.getProduct().getName());
        assertThat(sheetText(bothProducts, 1, "Виготовлення - Витрати"))
                .doesNotContain(productionResources.get(0).getName(), secondProductionResources.get(0).getName());

        analytics.openExpenses(ProductionAnalyticsPage.TAB_ASSEMBLY);
        analytics.waitForExpenseResource(productionResources.get(0).getName());
        analytics.selectFilterOption("Матеріали", productionResources.get(0).getName());
        ProductionAnalyticsPage.ExportDownloadResult materialExport = analytics.exportStatisticsToExcel();
        try {
            assertThat(materialExport.requestUrl())
                    .contains("storageIds=" + location.getId())
                    .contains("resourceIds=" + productionResources.get(0).getId());
            Map<String, List<List<String>>> materialWorkbook =
                    XlsxWorkbookReader.sheets(Files.readAllBytes(materialExport.path()));
            assertThat(sheetText(materialWorkbook, 1, "Виготовлення - Витрати"))
                    .contains(productionResources.get(0).getName())
                    .doesNotContain(productionResources.get(1).getName());
        } finally {
            Files.deleteIfExists(materialExport.path());
        }
    }

    @Test(priority = 90)
    @TestCaseId("TC-ANL-UI-023")
    @Story("Statistics — combined product and material filters in Excel")
    @Severity(SeverityLevel.BLOCKER)
    public void excelExportForwardsCombinedCategoryAndProductFilters() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        analytics.selectFilterOption("Категорії", productionResources.get(2).getCategory().getName());
        analytics.selectFilterOption("Вироби", journalProduction.getProduct().getName());

        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            assertThat(export.requestUrl())
                    .contains("storageIds=" + location.getId())
                    .contains("categoryIds=" + productionResources.get(2).getCategory().getId())
                    .contains("resourceIds=" + journalProduction.getProduct().getId());
        } finally {
            Files.deleteIfExists(export.path());
        }

        Long primaryCategoryId = productionResources.get(2).getCategory().getId();
        Map<String, List<List<String>>> matchingIntersection = exportWorkbookViaApi(filterQuery(
                List.of(location.getId()),
                List.of(primaryCategoryId),
                List.of(journalProduction.getProduct().getId())));
        assertThat(sheetText(matchingIntersection, 0, "Виготовлення - За виробами"))
                .contains(journalProduction.getProduct().getName())
                .doesNotContain(secondJournalProduction.getProduct().getName());
        assertThat(sheetText(matchingIntersection, 1, "Виготовлення - Витрати"))
                .doesNotContain(productionResources.get(0).getName(), secondProductionResources.get(0).getName());

        Map<String, List<List<String>>> incompatibleIntersection = exportWorkbookViaApi(filterQuery(
                List.of(secondLocation.getId()),
                List.of(primaryCategoryId),
                List.of(journalProduction.getProduct().getId())));
        String incompatibleText = workbookText(incompatibleIntersection);
        assertThat(incompatibleText)
                .doesNotContain(
                        journalProduction.getProduct().getName(),
                        productionResources.get(0).getName(),
                        secondJournalProduction.getProduct().getName(),
                        secondProductionResources.get(0).getName());

        analytics.openExpenses(ProductionAnalyticsPage.TAB_ASSEMBLY);
        analytics.waitForExpenseResource(productionResources.get(0).getName());
        analytics.selectFilterOption("Категорії матеріалів", productionResources.get(0).getCategory().getName());
        analytics.selectFilterOption("Матеріали", productionResources.get(0).getName());
        ProductionAnalyticsPage.ExportDownloadResult materialExport = analytics.exportStatisticsToExcel();
        try {
            assertThat(materialExport.requestUrl())
                    .contains("storageIds=" + location.getId())
                    .contains("categoryIds=" + productionResources.get(0).getCategory().getId())
                    .contains("resourceIds=" + productionResources.get(0).getId())
                    .doesNotContain("resourceIds=" + journalProduction.getProduct().getId());
            Map<String, List<List<String>>> materialWorkbook =
                    XlsxWorkbookReader.sheets(Files.readAllBytes(materialExport.path()));
            assertThat(sheetText(materialWorkbook, 1, "Виготовлення - Витрати"))
                    .contains(productionResources.get(0).getName())
                    .doesNotContain(productionResources.get(1).getName());
        } finally {
            Files.deleteIfExists(materialExport.path());
        }
    }

    @Test(priority = 91)
    @TestCaseId("TC-ANL-UI-024")
    @Story("Statistics — empty Excel result")
    @Severity(SeverityLevel.CRITICAL)
    public void emptyFilteredExportKeepsAllSheetsAndHeaders() throws Exception {
        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        LocalDate futureDate = analytics.browserToday().plusDays(2);
        analytics.periodPicker().setRange(futureDate, futureDate);
        analytics.openStatisticsTab();
        Map<String, List<List<String>>> workbook = XlsxWorkbookReader.sheets(downloadCurrentWorkbook(analytics));

        assertThat(workbook).hasSize(5);
        assertThat(workbook.values())
                .allSatisfy(rows -> assertThat(rows)
                        .as("Порожній аркуш зберігає лише header")
                        .hasSize(1));
    }

    @Test(priority = 92)
    @TestCaseId("TC-ANL-UI-025")
    @Story("Statistics — Excel export is not limited by UI pagination")
    @Severity(SeverityLevel.CRITICAL)
    public void excelExportContainsCompleteDatasetBeyondUiPageSize() throws Exception {
        List<ResourceResponse> markers = new ArrayList<>();
        for (int index = 0; index < PAGINATION_EXPORT_MARKERS; index++) {
            markers.add(createSerialExportMarker("analytics-page-export-%02d-".formatted(index), LocalDate.now()));
        }

        Map<String, List<List<String>>> workbook = exportWorkbookViaApi(
                filterQuery(List.of(location.getId()), null, null));
        List<List<String>> productionRows = workbook.get(EXPECTED_EXPORT_SHEETS.getFirst());
        assertThat(productionRows).isNotNull();
        for (ResourceResponse marker : markers) {
            assertThat(rowsContainingExactCell(productionRows, marker.getName()))
                    .as("Export contains the unique row beyond UI page: %s", marker.getName())
                    .hasSize(1);
        }
    }

    @Test(priority = 93)
    @TestCaseId("TC-ANL-UI-026")
    @Story("Statistics — native Excel cell types")
    @Severity(SeverityLevel.CRITICAL)
    public void exportedAmountsAreNativeNumericCells() throws Exception {
        byte[] bytes = exportWorkbook(new ProductionAnalyticsPage(page).open());

        for (String sheet : XlsxWorkbookReader.sheetNames(bytes)) {
            assertThat(XlsxWorkbookReader.numericColumnValues(bytes, sheet, "Обсяг"))
                    .as("Кожен обсяг на аркуші %s має native numeric type", sheet)
                    .isNotEmpty();
            if (EXPECTED_EXPORT_HEADERS.get(sheet).contains("Кількість записів")) {
                assertThat(XlsxWorkbookReader.numericColumnValues(bytes, sheet, "Кількість записів"))
                        .as("Кількість записів на аркуші %s має native numeric type", sheet)
                        .isNotEmpty();
            }
        }
    }

    @Test(priority = 94)
    @TestCaseId("TC-ANL-UI-027")
    @Story("Statistics — export authorization scope")
    @Severity(SeverityLevel.BLOCKER)
    public void excelExportRejectsForeignLocationScope() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("storageIds", List.of(techMapOnlyLocation.getId()));
        query.put("fromDate", LocalDate.now().minusDays(7).toString());
        query.put("toDate", LocalDate.now().toString());

        io.restassured.response.Response forbidden = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ANALYTIC_EXPORT_GET,
                ACTOR,
                query);

        assertThat(forbidden.statusCode()).as("Export чужої локації").isEqualTo(403);
        assertThat(forbidden.contentType()).doesNotContain("spreadsheet", "excel");

        UserFixture.BusinessActor noAnalyticsActor = users.createLowPrivilegeBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(location));
        apiExecutor.setSessionForRole(
                UserRole.OWNER_3, noAnalyticsActor.username(), noAnalyticsActor.password());
        try {
            io.restassured.response.Response denied = apiExecutor.executeWithQueryParams(
                    ApiEndpointDefinition.PRODUCTION_ANALYTIC_EXPORT_GET,
                    UserRole.OWNER_3,
                    filterQuery(List.of(location.getId()), null, null));
            assertThat(denied.statusCode()).as("Export without analytics permission").isIn(401, 403);
            assertThat(denied.contentType()).doesNotContain("spreadsheet", "excel");
        } finally {
            apiExecutor.restoreDefaultSessionForRole(UserRole.OWNER_3);
        }
    }

    @Test(priority = 95)
    @TestCaseId("TC-ANL-UI-028")
    @Story("Statistics — failed Excel export and retry")
    @Severity(SeverityLevel.NORMAL)
    public void failedExcelExportShowsErrorAndCanBeRetried() throws Exception {
        String exportRoute = "**/api/v1/production/analytic/export**";
        page.route(exportRoute, route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(500)
                .setContentType("application/json")
                .setBody("{\"error\":\"simulated export failure\"}")));

        ProductionAnalyticsPage analytics = new ProductionAnalyticsPage(page).open();
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        try {
            assertThat(analytics.clickExcelExportAndWaitForError()).isTrue();
        } finally {
            page.unroute(exportRoute);
        }

        byte[] retry = downloadCurrentWorkbook(analytics);
        assertThat(XlsxWorkbookReader.sheetNames(retry)).hasSize(5);

        Map<String, List<List<String>>> firstExport = exportWorkbookViaApi(
                filterQuery(List.of(location.getId()), null, null));
        Map<String, List<List<String>>> secondExport = exportWorkbookViaApi(
                filterQuery(List.of(secondLocation.getId()), null, null));
        assertLocationMarkers(firstExport, true, false);
        assertLocationMarkers(secondExport, false, true);
    }

    private Response waitForTimelineResponse(Runnable action) {
        return page.waitForResponse(
                response -> response.url().contains("/production/analytic/assembly/timeline?")
                        && !response.url().contains("timeline-filters")
                        && "GET".equals(response.request().method()),
                action);
    }

    private void assertTimelineMatchesJournal(
            Response timelineResponse,
            ManufacturingItemResponse journalRecord) throws Exception {
        JsonNode rows = JSON.readTree(timelineResponse.text());
        JsonNode day = null;
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (journalRecord.getDate().toString().equals(row.path("date").asText())) {
                    day = row;
                    break;
                }
            }
        }
        assertThat(day)
                .as("Timeline містить дату запису журналу %s", journalRecord.getDate())
                .isNotNull();
        assertThat(new BigDecimal(day.path("amount").asText()))
                .as("Timeline amount = amount запису журналу")
                .isEqualByComparingTo(BigDecimal.valueOf(journalRecord.getAmount()));
        assertThat(day.path("staffCount").asInt())
                .as("Timeline staffCount = workerQty snapshot-зміни журналу")
                .isEqualTo(journalRecord.getShift().getWorkerQty());
    }

    private void createNonSeriesProduction(String product, double amount, double usagePerUnit) {
        createNonSeriesProduction(product, amount, material, usagePerUnit);
    }

    private void createNonSeriesProduction(
            String product,
            double amount,
            ResourceResponse expenseResource,
            double usagePerUnit) {
        NonSeriesProductionResponse created = nonSeriesProductions.createAs(
                ACTOR,
                location.getId(),
                NonSeriesProductionStatus.DONE,
                product,
                amount,
                expenseResource.getId(),
                usagePerUnit);
        nonSeriesProductionIds.add(created.getId());
    }

    private ResourceResponse trackResource(ResourceResponse resource) {
        additionalResources.add(resource);
        return resource;
    }

    private ResourceResponse createSerialExportMarker(String prefix, LocalDate date) {
        return createSerialExportMarker(prefix, date, ACTOR);
    }

    private ResourceResponse createSerialExportMarker(
            String prefix,
            LocalDate date,
            UserRole creator) {
        ResourceResponse output = trackResource(resources.createUniqueResource(
                prefix,
                productionResources.get(2).getCategory().getId()));
        TechnologicalMapResponse techMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionMapWithStorages(
                        prefix + "map",
                        List.of(
                                new ResourceUsageRequest(productionResources.get(0).getId(), 1.0),
                                new ResourceUsageRequest(productionResources.get(1).getId(), 1.0)),
                        List.of(new ResourceUsageRequest(output.getId(), 1.0)),
                        Set.of(location.getId())).build());
        additionalTechMaps.add(new ScopedId(techMap.getId(), location.getId()));
        ManufacturingItemResponse production = productions.createAs(
                creator,
                location.getId(),
                techMap,
                1.0,
                ProductionDataFactory.uniqueBatchNumber(),
                date);
        additionalProductions.add(new ScopedId(production.getId(), location.getId()));
        return output;
    }

    private Map<String, Object> filterQuery(
            List<Long> storageIds,
            List<Long> categoryIds,
            List<Long> resourceIds) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("storageIds", storageIds);
        query.put("fromDate", LocalDate.now().minusDays(7).toString());
        query.put("toDate", LocalDate.now().toString());
        if (categoryIds != null) {
            query.put("categoryIds", categoryIds);
        }
        if (resourceIds != null) {
            query.put("resourceIds", resourceIds);
        }
        return query;
    }

    private Map<String, List<List<String>>> exportWorkbookViaApi(Map<String, Object> query) {
        io.restassured.response.Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PRODUCTION_ANALYTIC_EXPORT_GET,
                ACTOR,
                query);
        assertThat(response.statusCode()).as("Production analytics export HTTP status").isEqualTo(200);
        String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase();
        String disposition = response.getHeader("Content-Disposition") == null
                ? ""
                : response.getHeader("Content-Disposition").toLowerCase();
        assertThat(contentType.contains("spreadsheet")
                || contentType.contains("excel")
                || contentType.contains("octet-stream")
                || disposition.contains(".xlsx"))
                .as("Production analytics export headers: Content-Type=%s, Content-Disposition=%s",
                        contentType, disposition)
                .isTrue();
        byte[] bytes = response.asByteArray();
        assertThat(bytes).as("Production analytics export body").isNotEmpty();
        assertThat(XlsxWorkbookReader.sheetNames(bytes)).containsExactlyElementsOf(EXPECTED_EXPORT_SHEETS);
        return XlsxWorkbookReader.sheets(bytes);
    }

    private void assertLocationMarkers(
            Map<String, List<List<String>>> workbook,
            boolean expectPrimary,
            boolean expectSecondary) {
        assertMarkerPresence(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(0)),
                journalProduction.getProduct().getName(),
                secondJournalProduction.getProduct().getName(),
                expectPrimary,
                expectSecondary);
        assertMarkerPresence(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(1)),
                productionResources.get(0).getName(),
                secondProductionResources.get(0).getName(),
                expectPrimary,
                expectSecondary);
        assertMarkerPresence(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(2)),
                disassemblyOutput.getName(),
                secondDisassemblyOutput.getName(),
                expectPrimary,
                expectSecondary);
        assertMarkerPresence(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(3)),
                disassemblyInput.getName(),
                secondDisassemblyInput.getName(),
                expectPrimary,
                expectSecondary);
        assertMarkerPresence(
                workbook.get(EXPECTED_EXPORT_SHEETS.get(4)),
                material.getName(),
                secondMaterial.getName(),
                expectPrimary,
                expectSecondary);
    }

    private static void assertMarkerPresence(
            List<List<String>> rows,
            String primaryMarker,
            String secondaryMarker,
            boolean expectPrimary,
            boolean expectSecondary) {
        assertThat(rows).isNotNull();
        assertThat(rowsContainingExactCell(rows, primaryMarker).isEmpty())
                .as("Primary marker presence: %s", primaryMarker)
                .isEqualTo(!expectPrimary);
        assertThat(rowsContainingExactCell(rows, secondaryMarker).isEmpty())
                .as("Secondary marker presence: %s", secondaryMarker)
                .isEqualTo(!expectSecondary);
    }

    private static BigDecimal usageTotal(
            List<ResourceUsageResponse> usages,
            double operationAmount,
            Long resourceId) {
        ResourceUsageResponse usage = usages.stream()
                .filter(item -> item.getResource() != null && resourceId.equals(item.getResource().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing resource usage for resourceId=" + resourceId));
        double total = usage.getTotalAmount() != null
                ? usage.getTotalAmount()
                : usage.getAmount() * operationAmount;
        return BigDecimal.valueOf(total);
    }

    private static void assertUniqueWorkbookRow(
            List<List<String>> rows,
            String marker,
            ResourceResponse resource,
            BigDecimal expectedAmount) {
        List<List<String>> matchingRows = rowsContainingExactCell(rows, marker);
        assertThat(matchingRows).as("Unique workbook row for %s", marker).hasSize(1);
        List<String> row = matchingRows.getFirst();
        int unitColumn = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Од. вимір");
        int amountColumn = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Обсяг");
        assertThat(row).as("Workbook row includes unit and amount columns")
                .hasSizeGreaterThan(Math.max(unitColumn, amountColumn));
        assertThat(row.get(unitColumn))
                .as("Measurement unit for %s", marker)
                .isIn(resource.getUnit().getName(), resource.getUnit().getShortName());
        BigDecimal actualAmount = new BigDecimal(
                row.get(amountColumn).replaceAll("[\\p{Z}\\s]", "").replace(',', '.'));
        assertThat(actualAmount).as("Amount for %s", marker).isEqualByComparingTo(expectedAmount);
    }

    private static List<List<String>> rowsContainingExactCell(List<List<String>> rows, String value) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row.stream().anyMatch(value::equals))
                .toList();
    }

    private Response waitForExpenseResponse(
            String endpointFragment,
            boolean rawResources,
            Runnable action) {
        return page.waitForResponse(
                response -> response.url().contains("/production/analytic" + endpointFragment)
                        && response.url().contains("rawResources=true") == rawResources
                        && "GET".equals(response.request().method()),
                action);
    }

    private void assertRawResourcesRequest(Response response, boolean enabled) {
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.url()).contains("parentStorageId=" + location.getId());
        assertThat(response.url().contains("rawResources=true")).isEqualTo(enabled);
    }

    private void assertScopedResponse(Response response, String endpointFragment) {
        assertThat(response.status()).as("HTTP %s", endpointFragment).isEqualTo(200);
        assertThat(response.url())
                .as("Запит %s scoped до динамічної локації", endpointFragment)
                .contains("parentStorageId=" + location.getId());
    }

    private static JsonNode findResourceRow(JsonNode rows, long resourceId) {
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (row.path("resourceId").asLong() == resourceId) {
                    return row;
                }
            }
        }
        throw new AssertionError("Analytics response does not contain resourceId=" + resourceId
                + "; body=" + rows);
    }

    private static JsonNode findResourceRowOrNull(JsonNode rows, long resourceId) {
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (row.path("resourceId").asLong() == resourceId) {
                    return row;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimalFrom(String text) {
        Matcher matcher = Pattern.compile("-?\\d+(?:[\\p{Z}\\s]\\d{3})*(?:[.,]\\d+)?").matcher(text);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        if (last == null) {
            throw new AssertionError("No numeric value in: " + text);
        }
        return new BigDecimal(last.replaceAll("[\\p{Z}\\s]", "").replace(',', '.'));
    }

    private byte[] exportWorkbook(ProductionAnalyticsPage analytics) throws Exception {
        analytics.periodPicker().selectPreset(DateRangePickerComponent.PRESET_7_DAYS);
        analytics.openStatisticsTab();
        return downloadCurrentWorkbook(analytics);
    }

    private byte[] downloadCurrentWorkbook(ProductionAnalyticsPage analytics) throws Exception {
        ProductionAnalyticsPage.ExportDownloadResult export = analytics.exportStatisticsToExcel();
        try {
            UiDownloadAssertions.assertNonEmptyXlsx(
                    export.path(), export.sizeBytes(), "Production Analytics Excel");
            return Files.readAllBytes(export.path());
        } finally {
            Files.deleteIfExists(export.path());
        }
    }

    private Map<String, List<List<String>>> exportRequiredWorkbook() throws Exception {
        return XlsxWorkbookReader.sheets(exportWorkbook(new ProductionAnalyticsPage(page).open()));
    }

    private static String sheetText(
            Map<String, List<List<String>>> workbook,
            int sheetIndex,
            String requiredSheetName) {
        assertThat(workbook).as("Workbook містить п'ять предметних аркушів").hasSize(5);
        List<List<String>> rows = new ArrayList<>(workbook.values()).get(sheetIndex);
        assertThat(rows).as("Дані аркуша №%s «%s»", sheetIndex + 1, requiredSheetName).isNotNull();
        return rows.stream()
                .flatMap(List::stream)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static String workbookText(Map<String, List<List<String>>> workbook) {
        return workbook.values().stream()
                .flatMap(List::stream)
                .flatMap(List::stream)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private record ScopedId(Long id, Long storageId) {
    }
}
