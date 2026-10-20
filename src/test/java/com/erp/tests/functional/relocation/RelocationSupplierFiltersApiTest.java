package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.RelocationSupplierFiltersFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.math.BigDecimal;
import java.util.*;

import static com.erp.fixtures.RelocationSupplierFiltersFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("REQ-REL-SUPPLIER — Постачальник у Видати/Отримати")
@Severity(SeverityLevel.CRITICAL)
public class RelocationSupplierFiltersApiTest extends BaseFunctionalTest {
    private RelocationSupplierFiltersFixture data;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void arrange() {
        data = new RelocationSupplierFiltersFixture(testContext, apiExecutor);
        data.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() { if (data != null) data.cleanup(); }

    @DataProvider(name = "tabs")
    public Object[][] tabs() { return new Object[][] {{"active"}, {"sent"}, {"received"}, {"lost"}}; }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-API-001")
    @Description("Client RelocationListFilterBar/useRelocationListFilters + api/relocation.ts: repeated suppliers "
            + "in GET /relocations, /totals, /export; type=RESOURCE, states, fromIds/toIds, category, "
            + "productIds, startDate/endDate. Supplier is the resource property Постачальник.")
    public void singleSupplierTrimsCargoAndPreservesQuantities(String tab) {
        Map<String, Object> query = data.query(tab);
        data.assertRows(query, data.expectedIds(tab), data.first, data.sameCategory, data.otherCategory);
        query.put("suppliers", List.of(MOU));
        data.assertRows(query, data.expectedIds(tab), data.first);
        query.put("suppliers", List.of(OTHER));
        data.assertRows(query, data.expectedIds(tab), data.sameCategory);
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-API-002")
    public void multipleSuppliersUseOrAndUnknownValueMatchesNothing(String tab) {
        Map<String, Object> query = data.query(tab);
        query.put("suppliers", List.of(MOU, OTHER));
        data.assertRows(query, data.expectedIds(tab), data.first, data.sameCategory);
        query.put("suppliers", List.of(data.marker + "-unknown"));
        data.assertRows(query, List.of());
        query.put("suppliers", List.of("МО"));
        data.assertRows(query, List.of()); // Exact property value, not a substring search.
        query.put("suppliers", List.of(MOU, data.marker + "-unknown"));
        data.assertRows(query, data.expectedIds(tab), data.first);
        query.remove("suppliers");
        data.assertRows(query, data.expectedIds(tab), data.first, data.sameCategory, data.otherCategory);
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-API-003")
    public void supplierIntersectsTheSameItemAndOtherFilters(String tab) {
        Map<String, Object> query = data.query(tab);
        query.put("suppliers", List.of(MOU));
        query.put("category", data.categoryA);
        query.put("productIds", List.of(data.first.getId(), data.sameCategory.getId()));
        data.assertRows(query, data.expectedIds(tab), data.first);
        query.put("productIds", List.of(data.sameCategory.getId()));
        data.assertRows(query, List.of()); // A matching supplier on a different item must not satisfy AND.
        query.remove("productIds");
        query.put("category", data.categoryB);
        data.assertRows(query, List.of());
        query.remove("category");
        query.put("fromIds", List.of(data.alternateParent.getId()));
        data.assertRows(query, List.of());
        query = data.query(tab);
        query.put("suppliers", List.of(MOU));
        query.put("toIds", List.of(data.source.getId()));
        data.assertRows(query, List.of());
        query.put("toIds", List.of(data.destination.getId()));
        query.put("startDate", data.today.plusDays(1).toString());
        query.put("endDate", data.today.plusDays(1).toString());
        data.assertRows(query, List.of());
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-API-004")
    public void filteredTotalsAndPaginationUseTheWholeSelection(String tab) {
        Map<String, Object> query = data.query(tab);
        query.put("suppliers", List.of(MOU));
        query.put("size", 1);
        query.put("sort", "id,ASC");
        Set<Long> ids = new HashSet<>();
        int count = data.expectedIds(tab).size();
        for (int index = 0; index < count; index++) {
            query.put("page", index);
            Response response = data.page(query);
            assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(count);
            assertThat(response.jsonPath().getInt("page.totalPages")).isEqualTo(count);
            List<Long> pageIds = response.jsonPath().getList("content.id", Long.class);
            assertThat(pageIds).hasSize(1);
            assertThat(ids.add(pageIds.getFirst())).as("No duplicates across pages").isTrue();
            assertThat(response.jsonPath().getList("content[0].items.resource.id", Long.class))
                    .containsExactly(data.first.getId());
        }
        assertThat(ids).containsExactlyInAnyOrderElementsOf(data.expectedIds(tab));
        data.assertTotals(query, BigDecimal.valueOf(count * 2L).toPlainString());
        query.put("suppliers", List.of(MOU, OTHER));
        data.assertTotals(query, BigDecimal.valueOf(count * 5L).toPlainString());
        query.put("suppliers", List.of(data.marker + "-unknown"));
        data.assertTotals(query, "0");
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-API-005")
    public void excelAppliesSupplierAndIgnoresPageSize(String tab) {
        Map<String, Object> query = data.query(tab);
        query.put("size", 1);
        query.put("suppliers", List.of(MOU));
        data.assertExcel(data.excel(query), data.expectedIds(tab).size(), data.first);
        query.put("suppliers", List.of(MOU, OTHER));
        data.assertExcel(data.excel(query), data.expectedIds(tab).size(), data.first, data.sameCategory);
        query.put("productIds", List.of(data.otherCategory.getId()));
        data.assertExcel(data.excel(query), 0);
    }

    @Test
    @TestCaseId("TC-REL-SUP-API-006")
    @Description("Arrange existing relocations, change ONLY the owned resource supplier, replay list/totals/Excel "
            + "with old and new suppliers; remove property and replay. Restore in finally; sibling unchanged.")
    public void existingHistoryUsesCurrentSupplierAfterUpdateAndRemoval() {
        try {
            data.supplier(data.first, OTHER);
            for (String tab : List.of("active", "sent", "received", "lost")) {
                Map<String, Object> query = data.query(tab);
                query.put("suppliers", List.of(MOU));
                data.assertRows(query, List.of());
                data.assertTotals(query, "0");
                data.assertExcel(data.excel(query), 0);
                query.put("suppliers", List.of(OTHER));
                data.assertRows(query, data.expectedIds(tab), data.first, data.sameCategory);
                data.assertExcel(data.excel(query), data.expectedIds(tab).size(), data.first, data.sameCategory);
                data.assertTotals(query, String.valueOf(data.expectedIds(tab).size() * 5));
            }
            data.supplier(data.first, null);
            Map<String, Object> query = data.query("sent");
            query.put("suppliers", List.of(MOU, OTHER));
            data.assertRows(query, data.expectedIds("sent"), data.sameCategory);
            assertThat(data.supplierValue(data.sameCategory)).isEqualTo(OTHER);
            assertThat(data.supplierValue(data.otherCategory)).isNull();
        } finally { data.supplier(data.first, MOU); }
    }

    @Test
    @TestCaseId("TC-REL-SUP-API-007")
    public void externalReceiptFiltersByResourcePropertyRatherThanSenderLocation() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("states", List.of("AUTO_FINISHED"));
        query.put("toIds", List.of(data.source.getId()));
        query.put("type", "RESOURCE");
        query.put("startDate", data.today.minusDays(3).toString());
        query.put("endDate", data.today.minusDays(3).toString());
        List<Long> ids = data.rows(query).stream().map(row -> row.getId()).toList();
        assertThat(ids).hasSize(1);
        query.put("suppliers", List.of(MOU));
        var rows = data.rows(query);
        assertThat(rows).extracting(row -> row.getId()).containsExactlyElementsOf(ids);
        assertThat(rows.getFirst().getItems()).extracting(item -> item.getResource().getId())
                .containsExactly(data.first.getId());
        assertThat(rows.getFirst().getItems().getFirst().getAmount()).isEqualByComparingTo("100");
        query.put("suppliers", List.of(OTHER));
        assertThat(data.rows(query).getFirst().getItems()).extracting(item -> item.getResource().getId())
                .containsExactly(data.sameCategory.getId());
    }
}
