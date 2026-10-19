package com.erp.tests.functional.analytics;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.assertions.PlanAnalyticsExportAssertions;
import com.erp.utils.helpers.PlanAnalyticsRowsSnapshot;
import io.qameta.allure.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Analytics")
@Feature("Plan analytics Excel export")
public class PlanAnalyticsExcelExportApiTest extends BaseFunctionalTest {
    private Map<String, Object> selection;
    private PlanAnalyticsRowsSnapshot baseline;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareReadOnlySelection() {
        var categories = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        assertThat(categories.statusCode()).as("Повний довідник категорій").isEqualTo(200);
        List<Long> categoryIds = categories.jsonPath().getList("id", Long.class);
        assertThat(categoryIds).as("Категорії для повного каталогу, включно з ресурсами поза першою сторінкою").isNotEmpty();
        selection = period();
        selection.put("categoryIds", categoryIds.stream().distinct().toList());
        baseline = PlanAnalyticsRowsSnapshot.load(apiExecutor, selection, 100);
        assertThat(baseline.rows()).as("Потрібні дані для перевірки експорту за межами сторінки").hasSizeGreaterThan(2);
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-API-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-PLAN-ANL-EXCEL / AC-01..03. GET export з тими самими categoryIds/fromDate/toDate, що rows. "
            + "Два аркуші у визначеному порядку, кожна числова комірка та підсумки за одиницею. "
            + "Навіть page=1&size=1 в export не обрізають повну вибірку. Дані ERP лише читаються.")
    public void workbookIncludesAllPagesAndTotalsByUnit() throws Exception {
        var paged = new LinkedHashMap<>(selection);
        paged.put("page", 1);
        paged.put("size", 1);
        PlanAnalyticsExportAssertions.assertMatches(export(paged), baseline.rows(), baseline.totals());
        assertThat(baseline.totals()).as("Контроль різних одиниць без змішування").hasSizeGreaterThan(1);
    }

    @DataProvider(name = "filters")
    public Object[][] filters() {
        var first = baseline.rows().getFirst();
        var second = baseline.rows().get(1);
        return new Object[][] {
                {"resource", Map.of("resourceIds", List.of(first.get("resourceId")))},
                {"resources", Map.of("resourceIds", List.of(first.get("resourceId"), second.get("resourceId")))},
                {"category", Map.of("categoryIds", List.of(first.get("resourceCategoryId")))},
                {"resource AND category", Map.of("resourceIds", List.of(first.get("resourceId")),
                        "categoryIds", List.of(first.get("resourceCategoryId")))},
                {"supplier", Map.of("supplier", List.of("МОУ"))},
                {"suppliers OR", Map.of("supplier", List.of("МОУ", "Інші"))},
                {"empty supplier", Map.of("supplier", List.of("autotest-no-supplier-" + System.nanoTime()))},
                {"empty factory AND supplier", Map.of("factory", List.of("autotest-no-factory-" + System.nanoTime()),
                        "supplier", List.of("МОУ", "Інші"))},
                {"three months", Map.of("fromDate", YearMonth.from(LocalDate.now()).minusMonths(3).atDay(1).toString())}
        };
    }

    @Test(dataProvider = "filters")
    @TestCaseId("TC-PLAN-ANL-XLSX-API-002")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-PLAN-ANL-EXCEL / AC-03. Експорт і всі сторінки rows отримують ідентичні бізнес-фільтри: "
            + "ресурси, категорії, повторювані supplier/factory, період та їх перетин. "
            + "Порівнюються всі рядки, п'ять метрик і підсумки; для неможливого збігу обидві вибірки порожні.")
    public void exportMatchesFilteredRows(String scenario, Map<String, Object> changes) throws Exception {
        var filters = new LinkedHashMap<>(selection);
        filters.putAll(changes);
        var expected = PlanAnalyticsRowsSnapshot.load(apiExecutor, filters, 100);
        if (scenario.startsWith("empty")) assertThat(expected.rows()).as(scenario).isEmpty();
        if (scenario.equals("resource")) assertThat(expected.rows()).as(scenario).hasSize(1);
        if (scenario.equals("resources")) assertThat(expected.rows()).as(scenario).hasSize(2);
        PlanAnalyticsExportAssertions.assertMatches(export(filters), expected.rows(), expected.totals());
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-API-003")
    @Description("REQ-PLAN-ANL-EXCEL / AC-04. Узгоджено користувачем 2026-10-15: без resourceIds/categoryIds "
            + "прямий export повертає весь каталог за періодом. UI rows без вибору залишається порожнім, кнопка прихована. "
            + "Еталон — усі сторінки rows з повним довідником categoryIds. Кількість ресурсів не фіксується числом 1929.")
    public void exportWithoutSelectionContainsWholeCatalog() throws Exception {
        var withoutSelection = PlanAnalyticsRowsSnapshot.load(apiExecutor, period(), 20);
        assertThat(withoutSelection.rows()).isEmpty();
        assertThat(withoutSelection.totals()).isEmpty();
        var expected = PlanAnalyticsRowsSnapshot.load(apiExecutor, selection, 100);
        assertThat(expected.rows()).as("Каталог містить дані для перевірки експорту без вибору").isNotEmpty();
        Allure.addAttachment("Кількість ресурсів у повному каталозі", String.valueOf(expected.rows().size()));
        PlanAnalyticsExportAssertions.assertMatches(export(period()), expected.rows(), expected.totals());
    }

    @Test
    @TestCaseId("TC-PLAN-ANL-XLSX-API-004")
    @Severity(SeverityLevel.CRITICAL)
    @Description("REQ-PLAN-ANL-EXCEL / AC-03. Позитивний Завод: як UI, GET rows/export передає "
            + "categoryIds, fromDate/toDate та factory=<значення з category_property_options[Завод]>. "
            + "Непорожня очікувана вибірка визначається незалежно за properties[name=Завод] каталогу ресурсів. "
            + "Перевіряються всі відповідні ресурси, виключення інших заводів/ресурсів без властивості, "
            + "п'ять числових показників і незалежно підраховані підсумки за одиницями. Дані лише читаються.")
    public void factoryExportIncludesMatchingResourcesAndExcludesOthers() throws Exception {
        var config = apiExecutor.execute(ApiEndpointDefinition.APP_CONFIG_GET_ALL, UserRole.ADMIN);
        assertThat(config.statusCode()).as("Опції фільтра Завод, які використовує UI").isEqualTo(200);
        List<String> factories = config.jsonPath().getList(
                "find { it.name == 'category_property_options' }.value.find { it.name == 'Завод' }.values", String.class);
        assertThat(factories).as("Потрібні налаштовані значення Завод").isNotEmpty();

        Map<Long, List<String>> resourceFactories = loadResourceFactories();
        var baselineIds = baseline.rows().stream().map(PlanAnalyticsExcelExportApiTest::resourceId).toList();
        assertThat(resourceFactories.keySet()).as("Каталог охоплює всі ресурси аналітики")
                .containsAll(baselineIds);
        String factory = factories.stream().filter(value -> {
            long count = baselineIds.stream().filter(id -> resourceFactories.get(id).contains(value)).count();
            return count > 0 && count < baselineIds.size();
        }).findFirst().orElseThrow(() -> new AssertionError(
                "Потрібен налаштований завод із непорожньою вибіркою та ресурсами поза нею"));

        var expectedRows = baseline.rows().stream()
                .filter(row -> resourceFactories.get(resourceId(row)).contains(factory)).toList();
        var expectedIds = expectedRows.stream().map(PlanAnalyticsExcelExportApiTest::resourceId).toList();
        var excludedIds = baselineIds.stream().filter(id -> !resourceFactories.get(id).contains(factory)).toList();
        assertThat(expectedIds).as("Позитивний сценарій не може пройти на порожніх даних").isNotEmpty();
        assertThat(excludedIds).as("Контроль виключення інших ресурсів").isNotEmpty();
        var filters = new LinkedHashMap<>(selection);
        filters.put("factory", List.of(factory));
        var filtered = PlanAnalyticsRowsSnapshot.load(apiExecutor, filters, 100);
        assertThat(filtered.rows().stream().map(PlanAnalyticsExcelExportApiTest::resourceId).toList())
                .as("factory=%s: точна вибірка за реальною властивістю ресурсу", factory)
                .containsExactlyInAnyOrderElementsOf(expectedIds)
                .doesNotContainAnyElementsOf(excludedIds);

        byte[] bytes = export(filters);
        Allure.addAttachment("Позитивний Завод — еталон", "factory=" + factory
                + "\nincluded=" + expectedIds.size() + "\nexcluded=" + excludedIds.size()
                + "\nresourceIds=" + expectedIds);
        Allure.addAttachment("Позитивний Завод — Excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new java.io.ByteArrayInputStream(bytes), ".xlsx");
        PlanAnalyticsExportAssertions.assertMatches(bytes, expectedRows, sumTotalsByUnit(expectedRows));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<String>> loadResourceFactories() {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        int expectedCount = -1;
        for (int page = 0; page < 1000; page++) {
            var response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_GET_ALL,
                    UserRole.ADMIN, Map.of("page", page, "size", 500, "sort", "name,ASC"));
            assertThat(response.statusCode()).as("Повний каталог ресурсів, page=%s", page).isEqualTo(200);
            List<Map<String, Object>> content = response.jsonPath().getList("content");
            assertThat(content).isNotNull();
            if (page == 0) expectedCount = response.jsonPath().getInt("page.totalElements");
            assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(expectedCount);
            for (var resource : content) {
                List<Map<String, Object>> properties = (List<Map<String, Object>>) resource.get("properties");
                List<String> values = properties == null ? List.of() : properties.stream()
                        .filter(property -> "Завод".equals(property.get("name")))
                        .filter(property -> property.get("value") != null)
                        .map(property -> property.get("value").toString()).toList();
                assertThat(result.put(resourceId(resource), values)).as("Ресурс не дублюється між сторінками").isNull();
            }
            if (result.size() >= expectedCount) {
                assertThat(result).hasSize(expectedCount);
                return result;
            }
            assertThat(content).as("Незавершений каталог повинен мати наступні рядки").isNotEmpty();
        }
        throw new AssertionError("Каталог ресурсів перевищив 1000 сторінок");
    }

    private static long resourceId(Map<String, Object> row) {
        return ((Number) (row.containsKey("resourceId") ? row.get("resourceId") : row.get("id"))).longValue();
    }

    private static List<Map<String, Object>> sumTotalsByUnit(List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> totals = new LinkedHashMap<>();
        for (var row : rows) {
            var total = totals.computeIfAbsent(row.get("unit"), unit -> {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("unit", unit);
                for (String metric : PlanAnalyticsExportAssertions.KEYS) values.put(metric, BigDecimal.ZERO);
                return values;
            });
            for (String metric : PlanAnalyticsExportAssertions.KEYS) {
                total.put(metric, ((BigDecimal) total.get(metric)).add(new BigDecimal(row.get(metric).toString())));
            }
        }
        return new ArrayList<>(totals.values());
    }

    private byte[] export(Map<String, Object> filters) {
        var response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.PLAN_ANALYTIC_EXPORT_GET,
                UserRole.ADMIN, filters);
        assertThat(response.statusCode()).as("GET plan/export").isEqualTo(200);
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").containsIgnoringCase(".xlsx");
        return response.asByteArray();
    }

    public static Map<String, Object> period() {
        var month = YearMonth.from(LocalDate.now()).minusMonths(1);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fromDate", month.atDay(1).toString());
        params.put("toDate", month.atEndOfMonth().toString());
        return params;
    }
}
