package com.erp.tests.functional.analytics;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET {@code /api/v1/analytics/plan} — дані по виробу для планування
 * (вироблено / відвантажено / використано / залишок Цукрарня vs увесь ПМ).
 */
@Slf4j
@Epic("Analytics")
@Feature("Plan analytics")
public class PlanAnalyticApiTest extends BaseFunctionalTest {

    private static final String[] METRIC_KEYS = {
            "produced", "relocated", "used", "stockTotal", "stockInRoot"};

    private ResourceFixture resourceFixture;
    private long resourceId;
    private LocalDate lastMonthFrom;
    private LocalDate lastMonthTo;
    private LocalDate threeMonthsFrom;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupPlanAnalytics() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        List<ResourceResponse> resources = resourceFixture.getPage(UserRole.ADMIN, true, null);
        assertThat(resources)
                .as("Потрібен хоча б один активний ресурс для plan analytics")
                .isNotEmpty();

        YearMonth lastFull = YearMonth.from(LocalDate.now()).minusMonths(1);
        lastMonthFrom = lastFull.atDay(1);
        lastMonthTo = lastFull.atEndOfMonth();
        threeMonthsFrom = lastFull.minusMonths(2).atDay(1);

        resourceId = pickResourceWithActivity(resources);
        log.info("Plan analytics probe resourceId={} period={}…{} / 3m from {}",
                resourceId, lastMonthFrom, lastMonthTo, threeMonthsFrom);
    }

    @Test(priority = 10)
    @TestCaseId("TC-PLAN-ANL-001")
    @Story("Empty selection")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-01.
            tk-ui PlanAnalyticsPage не шле rows без resourceIds/categoryIds.
            GET /analytics/plan/rows без вибору має повернути порожній content і нульові totals
            (не агрегат по всьому каталогу). Anonymous — 401/403.

            Query: fromDate/toDate = попередній повний календарний місяць (пресет «Місяць»).
            """)
    public void rowsStayEmptyUntilResourceOrCategoryIsSelected() {
        Response ok = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET,
                UserRole.ADMIN,
                periodParams(lastMonthFrom, lastMonthTo));
        assertThat(ok.statusCode()).as("ADMIN rows without selection: %s", ok.asString()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(ok, ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET);

        JsonPath json = ok.jsonPath();
        assertThat(json.getList("content")).as("content без вибору").isEmpty();
        assertThat(json.getInt("page.totalElements")).as("totalElements без вибору").isZero();
        for (String key : METRIC_KEYS) {
            assertThat(decimal(json, "totals." + key))
                    .as("totals.%s без вибору", key)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        Response anon = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET,
                UserRole.ANONYMOUS,
                periodParams(lastMonthFrom, lastMonthTo));
        assertThat(anon.statusCode())
                .as("anonymous rows: %s", anon.asString())
                .isIn(401, 403);
    }

    @Test(priority = 20)
    @TestCaseId("TC-PLAN-ANL-002")
    @Story("Produced / issued / used / stock")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-01.
            Клієнт шле resourceIds + fromDate/toDate.
            produced — виготовлення в дереві Цукрарні; relocated — видача з кореня назовні;
            used — WRITE_OFF; stockTotal — скрізь по ПМ; stockInRoot — дерево Цукрарні.

            «Місяць» = попередній повний місяць; «3 місяці» = три повні місяці до нього включно.
            Накопичувані метрики за 3 місяці ≥ за 1 місяць; stockInRoot ≤ stockTotal.
            """)
    public void rowsExposeMonthAndThreeMonthProductFigures() {
        Response oneMonth = requestRows(resourceId, lastMonthFrom, lastMonthTo);
        assertThat(oneMonth.statusCode()).as("1m rows: %s", oneMonth.asString()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(oneMonth, ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET);
        assertNonNegativeMetrics(oneMonth.jsonPath());

        Response threeMonths = requestRows(resourceId, threeMonthsFrom, lastMonthTo);
        assertThat(threeMonths.statusCode()).as("3m rows: %s", threeMonths.asString()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(threeMonths, ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET);
        assertNonNegativeMetrics(threeMonths.jsonPath());

        for (String key : List.of("produced", "relocated", "used")) {
            BigDecimal monthValue = decimal(oneMonth.jsonPath(), "totals." + key);
            BigDecimal quarterValue = decimal(threeMonths.jsonPath(), "totals." + key);
            assertThat(quarterValue)
                    .as("%s за 3 місяці (%s) має бути ≥ за попередній місяць (%s)", key, quarterValue, monthValue)
                    .isGreaterThanOrEqualTo(monthValue);
        }
    }

    @Test(priority = 30)
    @TestCaseId("TC-PLAN-ANL-003")
    @Story("Stock locations")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            REQ-PLAN-ANL / AC-02.
            Розгортання рядка на «Для плану» → GET /analytics/plan/stock/{resourceId}.
            insideRoot=true — склади дерева Цукрарні; false — інші локації ПМ.
            Сума insideRoot = stockInRoot рядка; сума всіх amount = stockTotal (якщо рядок є).
            """)
    public void stockLocationsSplitTsukrarniaFromOtherPmStorages() {
        Response stock = apiExecutor.execute(
                ApiEndpointDefinition.PLAN_ANALYTIC_STOCK_GET, UserRole.ADMIN, String.valueOf(resourceId));
        assertThat(stock.statusCode()).as("stock: %s", stock.asString()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(stock, ApiEndpointDefinition.PLAN_ANALYTIC_STOCK_GET);

        List<Map<String, Object>> locations = stock.jsonPath().getList("$");
        assertThat(locations).as("stock response is a list").isNotNull();
        BigDecimal inRoot = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> location : locations) {
            assertThat(location.get("storageId")).as("storageId").isNotNull();
            assertThat(location.get("storageName")).as("storageName").isNotNull();
            assertThat(location.get("insideRoot")).as("insideRoot").isInstanceOf(Boolean.class);
            BigDecimal amount = toDecimal(location.get("amount"));
            assertThat(amount).as("amount ≥ 0").isGreaterThanOrEqualTo(BigDecimal.ZERO);
            total = total.add(amount);
            if (Boolean.TRUE.equals(location.get("insideRoot"))) {
                inRoot = inRoot.add(amount);
            }
        }

        Response rows = requestRows(resourceId, lastMonthFrom, lastMonthTo);
        assertThat(rows.statusCode()).isEqualTo(200);
        List<Map<String, Object>> content = rows.jsonPath().getList("content");
        if (content == null || content.isEmpty()) {
            assertThat(total)
                    .as("без рядка rows залишок на складах теж 0 (фільтр amount<>0)")
                    .isEqualByComparingTo(BigDecimal.ZERO);
            return;
        }
        Map<String, Object> row = content.getFirst();
        assertThat(inRoot)
                .as("сума insideRoot = stockInRoot")
                .isEqualByComparingTo(toDecimal(row.get("stockInRoot")));
        assertThat(total)
                .as("сума всіх amount = stockTotal")
                .isEqualByComparingTo(toDecimal(row.get("stockTotal")));
    }

    private Response requestRows(long id, LocalDate from, LocalDate to) {
        Map<String, Object> params = periodParams(from, to);
        params.put("resourceIds", id);
        return apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET, UserRole.ADMIN, params);
    }

    private Map<String, Object> periodParams(LocalDate from, LocalDate to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fromDate", from.toString());
        params.put("toDate", to.toString());
        params.put("page", 0);
        params.put("size", 20);
        return params;
    }

    private long pickResourceWithActivity(List<ResourceResponse> resources) {
        int limit = Math.min(resources.size(), 40);
        for (int i = 0; i < limit; i++) {
            long id = resources.get(i).getId();
            Response threeMonths = requestRows(id, threeMonthsFrom, lastMonthTo);
            if (threeMonths.statusCode() != 200) {
                continue;
            }
            JsonPath json = threeMonths.jsonPath();
            boolean hasTotals = METRIC_KEYS.length > 0
                    && METRIC_KEYS[0] != null
                    && decimal(json, "totals.produced")
                    .add(decimal(json, "totals.relocated"))
                    .add(decimal(json, "totals.used"))
                    .add(decimal(json, "totals.stockTotal"))
                    .compareTo(BigDecimal.ZERO) > 0;
            if (hasTotals) {
                return id;
            }
        }
        return resources.getFirst().getId();
    }

    private static void assertNonNegativeMetrics(JsonPath json) {
        for (String key : METRIC_KEYS) {
            BigDecimal total = decimal(json, "totals." + key);
            assertThat(total).as("totals.%s ≥ 0", key).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        BigDecimal stockTotal = decimal(json, "totals.stockTotal");
        BigDecimal stockInRoot = decimal(json, "totals.stockInRoot");
        assertThat(stockInRoot)
                .as("stockInRoot ≤ stockTotal")
                .isLessThanOrEqualTo(stockTotal);

        List<Map<String, Object>> content = json.getList("content");
        if (content == null) {
            return;
        }
        for (Map<String, Object> row : content) {
            for (String key : METRIC_KEYS) {
                assertThat(toDecimal(row.get(key)))
                        .as("row %s %s ≥ 0", row.get("resourceName"), key)
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
            assertThat(toDecimal(row.get("stockInRoot")))
                    .as("row stockInRoot ≤ stockTotal")
                    .isLessThanOrEqualTo(toDecimal(row.get("stockTotal")));
        }
    }

    private static BigDecimal decimal(JsonPath json, String path) {
        return toDecimal(json.get(path));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
