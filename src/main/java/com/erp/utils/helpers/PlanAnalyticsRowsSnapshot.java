package com.erp.utils.helpers;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Complete paginated reference for exports; retains the caller's business filters. */
public record PlanAnalyticsRowsSnapshot(List<Map<String, Object>> rows, List<Map<String, Object>> totals) {
    public static PlanAnalyticsRowsSnapshot load(ApiExecutor executor, Map<String, Object> filters, int size) {
        Map<String, Object> params = new LinkedHashMap<>(filters);
        params.put("size", size);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> totals = null;
        int expectedCount = -1;
        for (int page = 0; page < 1000; page++) {
            params.put("page", page);
            var response = executor.executeWithQueryParams(ApiEndpointDefinition.PLAN_ANALYTIC_ROWS_GET,
                    UserRole.ADMIN, params);
            assertThat(response.statusCode()).as("plan/rows page=%s", page).isEqualTo(200);
            var json = response.jsonPath().using(io.restassured.path.json.config.JsonPathConfig.jsonPathConfig()
                    .numberReturnType(io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL));
            List<Map<String, Object>> pageRows = json.getList("content");
            assertThat(pageRows).isNotNull();
            if (page == 0) {
                expectedCount = json.getInt("page.totalElements");
                totals = json.getList("totals");
                assertThat(totals).isNotNull();
            }
            assertThat(json.getInt("page.totalElements")).as("Стабільна вибірка під час читання")
                    .isEqualTo(expectedCount);
            rows.addAll(pageRows);
            if (rows.size() >= expectedCount) {
                assertThat(rows).hasSize(expectedCount);
                assertThat(rows).extracting(row -> row.get("resourceId")).doesNotHaveDuplicates();
                return new PlanAnalyticsRowsSnapshot(rows, totals);
            }
            assertThat(pageRows).as("Наступна сторінка повинна містити записи").isNotEmpty();
        }
        throw new AssertionError("Plan analytics pagination exceeded 1000 pages");
    }
}
