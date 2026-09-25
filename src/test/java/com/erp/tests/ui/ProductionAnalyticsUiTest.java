package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.LocationProfileFixture;
import com.erp.fixtures.NonSeriesProductionFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ShiftFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.request.ShiftRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.models.response.ResourceResponse;
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
import java.util.List;
import java.util.Map;
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

    private final List<Long> nonSeriesProductionIds = new ArrayList<>();
    private LocationProfileFixture locationProfiles;
    private UserFixture users;
    private NonSeriesProductionFixture nonSeriesProductions;
    private ProductionFixture productions;
    private ResourceFixture resources;
    private ShiftFixture shifts;
    private TechnologicalMapFixture technologicalMaps;
    private StorageResponse location;
    private ResourceResponse material;
    private final List<ResourceResponse> productionResources = new ArrayList<>();
    private TechnologicalMapResponse productionTechMap;
    private ShiftResponse journalShift;
    private ManufacturingItemResponse journalProduction;
    private String firstProduct;
    private String secondProduct;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        locationProfiles = new LocationProfileFixture(testContext, apiExecutor);
        users = new UserFixture(testContext, apiExecutor);
        nonSeriesProductions = new NonSeriesProductionFixture(testContext, apiExecutor);
        productions = new ProductionFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        shifts = new ShiftFixture(testContext, apiExecutor);
        technologicalMaps = new TechnologicalMapFixture(testContext, apiExecutor);

        location = locationProfiles.create(LocationProfile.TSUK_PRODUCTION, 1)
                .locations().getFirst();
        UserFixture.BusinessActor actor = users.createBusinessActor(
                getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_OWNER,
                List.of(location));
        apiExecutor.setSessionForRole(ACTOR, actor.username(), actor.password());

        injectSessionCookies(
                getPlaywrightSessionProvider().getSession(actor.username(), actor.password()),
                sessionCookieDomain());
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + location.getId() + "');");

        resources.fetchSharedUnit(1);
        resources.fetchSharedResourceCategory();
        material = resources.createUniqueResource("analytics-nsp-material-");
        new RelocationFixture(testContext, apiExecutor)
                .seedExactStock(location.getId(), material.getId(), 50.0, ACTOR);

        firstProduct = "analytics-nsp-product-a-" + System.nanoTime();
        secondProduct = "analytics-nsp-product-b-" + System.nanoTime();

        productionResources.add(resources.createUniqueResource("analytics-daily-input-a-"));
        productionResources.add(resources.createUniqueResource("analytics-daily-input-b-"));
        productionResources.add(resources.createUniqueResource("analytics-daily-product-"));
        RelocationFixture relocation = new RelocationFixture(testContext, apiExecutor);
        relocation.seedExactStock(location.getId(), productionResources.get(0).getId(), 100.0, ACTOR);
        relocation.seedExactStock(location.getId(), productionResources.get(1).getId(), 100.0, ACTOR);
        productionTechMap = technologicalMaps.createTechMapWithRequest(
                UserRole.ADMIN,
                TechnologicalMapDataFactory.createProductionTechMap(
                        productionResources, location.getId()).build());
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
        createNonSeriesProduction(firstProduct, FIRST_PRODUCT_AMOUNT, FIRST_USAGE_PER_UNIT);
        createNonSeriesProduction(secondProduct, SECOND_PRODUCT_AMOUNT, SECOND_USAGE_PER_UNIT);

        log.info("Production analytics fixture: location={} ({}), actor={}, material={} ({}), "
                        + "journalProduction={}, product={}, shift={} workers={}",
                location.getId(), location.getName(), actor.username(), material.getId(), material.getName(),
                journalProduction.getId(), journalProduction.getProduct().getName(),
                journalShift.getName(), journalShift.getWorkerQty());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupProductionAnalyticsFixture() {
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
        if (locationProfiles != null) {
            locationProfiles.cleanup();
        }
        if (resources != null) {
            List<ResourceResponse> createdResources = new ArrayList<>(productionResources);
            if (material != null) {
                createdResources.add(material);
            }
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

        Response locationFiltered = waitForTimelineResponse(
                () -> analytics.selectDailyFilterOption("Локації", location.getName()));
        log.info("Location-filtered timeline URL: {}", locationFiltered.url());
        assertThat(locationFiltered.url()).contains(String.valueOf(location.getId()));
        assertTimelineMatchesJournal(locationFiltered, journalProduction);
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
        NonSeriesProductionResponse created = nonSeriesProductions.createAs(
                ACTOR,
                location.getId(),
                NonSeriesProductionStatus.DONE,
                product,
                amount,
                material.getId(),
                usagePerUnit);
        nonSeriesProductionIds.add(created.getId());
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
}
