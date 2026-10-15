package com.erp.tests.functional.non_series_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesResourceFilterFixture;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Non-Series Production")
@Feature("Фільтр за використаною сировиною")
@Story("resourceSearch у журналі, підсумку та Excel")
@Severity(SeverityLevel.CRITICAL)
public class NonSeriesResourceFilterApiTest extends BaseFunctionalTest {
    private NonSeriesResourceFilterFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareFilterData() {
        fixture = new NonSeriesResourceFilterFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupFilterData() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-NSP-RF-API-001")
    @Description("Клієнт надсилає resourceSearch до GET списку й /total. Повна назва, підрядок та інший регістр "
            + "знаходять фактично використану сировину. Два збіги одного виробу не дублюють запис або об'єм; "
            + "resourceUsageList зберігає також сировину, яка не збіглася.")
    public void searchesActualUsageWithoutDuplicatesOrTruncation() {
        fixture.expect(fixture.query(fixture.materialA.getName()), 16, fixture.alpha, fixture.both, fixture.older);
        fixture.expect(fixture.query(fixture.materialTerm.toUpperCase(Locale.ROOT)), 16,
                fixture.alpha, fixture.both, fixture.older);
        fixture.expect(fixture.query(fixture.materialTerm.toLowerCase(Locale.ROOT)), 16,
                fixture.alpha, fixture.both, fixture.older);
        var both = fixture.list(fixture.query(fixture.materialTerm)).stream()
                .filter(row -> row.getId().equals(fixture.both.getId())).findFirst().orElseThrow();
        assertThat(both.getResourceUsageList()).extracting(usage -> usage.getResource().getId())
                .containsExactlyInAnyOrder(fixture.materialA.getId(), fixture.materialA2.getId(), fixture.materialB.getId());
    }

    @Test
    @TestCaseId("TC-NSP-RF-API-002")
    @Description("Назва виробу й опис не підміняють використану сировину. Без збігів content=[] і total=0. "
            + "Відсутній, порожній або пробільний resourceSearch повертають усі 5 записів включно з виробом без сировини.")
    public void excludesProductOnlyMatchesAndHandlesEmptySearch() {
        fixture.expect(fixture.query(fixture.materialTerm + "-missing"), 0);
        fixture.expect(fixture.query(fixture.materialTerm), 16, fixture.alpha, fixture.both, fixture.older);
        for (String term : Arrays.asList(null, "", "   ")) {
            fixture.expect(fixture.query(term), 28, fixture.alpha, fixture.both, fixture.decoy,
                    fixture.noResources, fixture.older);
        }
    }

    @Test
    @TestCaseId("TC-NSP-RF-API-003")
    @Description("Як у tk-ui, resourceSearch комбінується через AND з productSearch, statuses, startDate/endDate. "
            + "Список та /total використовують однакові фільтри; межі періоду включні.")
    public void intersectsProductStatusAndDateFilters() {
        var query = fixture.query(fixture.materialTerm);
        fixture.expect(query.toBuilder().productSearch(fixture.alpha.getProduct()).build(), 2, fixture.alpha);
        fixture.expect(query.toBuilder().productSearch(fixture.decoy.getProduct()).build(), 0);
        fixture.expect(query.toBuilder().statuses(List.of(NonSeriesProductionStatus.DONE)).build(), 3, fixture.both);
        fixture.expect(query.toBuilder().startDate(fixture.today).endDate(fixture.today).build(),
                5, fixture.alpha, fixture.both);
        fixture.expect(query.toBuilder().productSearch(fixture.both.getProduct())
                .statuses(List.of(NonSeriesProductionStatus.DONE)).startDate(fixture.today)
                .endDate(fixture.today).build(), 3, fixture.both);
        fixture.expect(query.toBuilder().startDate(fixture.today.minusDays(10))
                .endDate(fixture.today.minusDays(10)).build(), 11, fixture.older);
    }

    @Test
    @TestCaseId("TC-NSP-RF-API-004")
    @Description("Пагінація size=1 не дублює вироби з кількома відповідними матеріалами: totalElements=3, "
            + "три різні ID на сторінках; /total=16 незалежно від page/size.")
    public void paginatesDistinctProductionsAndTotalsAllPages() {
        Set<Long> ids = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            var query = fixture.query(fixture.materialTerm).toBuilder().pageSize(1).page(page).build();
            var params = new LinkedHashMap<>(query.toListQueryParams());
            params.put("sort", "id,asc");
            var response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_GET_ALL,
                    UserRole.ADMIN, params);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(3);
            assertThat(response.jsonPath().getInt("page.totalPages")).isEqualTo(3);
            List<Long> pageIds = response.jsonPath().getList("content.id", Long.class);
            assertThat(pageIds).hasSize(1);
            assertThat(ids.add(pageIds.getFirst())).as("No duplicate ID across pages").isTrue();
            assertThat(fixture.total(query)).isEqualByComparingTo("16");
        }
        assertThat(ids).containsExactlyInAnyOrder(fixture.alpha.getId(), fixture.both.getId(), fixture.older.getId());
        assertThat(fixture.list(fixture.query(fixture.materialTerm).toBuilder().pageSize(1).page(3).build())).isEmpty();
    }

    @Test
    @TestCaseId("TC-NSP-RF-API-005")
    @Description("Однакова сировина на двох сусідніх локаціях: storageIds обмежує список і /total. "
            + "Пошук не змінює залишки ресурсів або записи сусідньої локації.")
    public void scopesByLocationAndLeavesInventoryUnchanged() {
        var before = fixture.inventorySnapshot(fixture.storageId);
        var otherBefore = fixture.inventorySnapshot(fixture.otherStorageId);
        fixture.expect(fixture.query(fixture.materialTerm), 16, fixture.alpha, fixture.both, fixture.older);
        fixture.expect(fixture.query(fixture.materialTerm).toBuilder().storageId(fixture.otherStorageId).build(),
                13, fixture.elsewhere);
        assertThat(fixture.inventorySnapshot(fixture.storageId)).isEqualTo(before);
        assertThat(fixture.inventorySnapshot(fixture.otherStorageId)).isEqualTo(otherBefore);
    }

    @Test
    @TestCaseId("TC-NSP-RF-API-006")
    @Description("GET /export передає ті самі resourceSearch/productSearch/statuses/дати/storageIds, що й журнал. "
            + "Excel містить тільки знайдені вироби, об'єм не дублюється, усі витратні матеріали зберігаються.")
    public void exportsOnlyFilteredProductionsWithAllTheirMaterials() {
        var query = fixture.query(fixture.materialTerm).toBuilder().statuses(List.of(NonSeriesProductionStatus.DONE))
                .productSearch(fixture.both.getProduct()).startDate(fixture.today).endDate(fixture.today).build();
        var response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_EXPORT,
                UserRole.ADMIN, query.toTotalQueryParams());
        assertThat(response.statusCode()).isEqualTo(200);
        var sheets = XlsxWorkbookReader.sheets(response.asByteArray());
        assertThat(sheets.keySet()).containsExactly("Несерійне виробництво");
        var rows = sheets.get("Несерійне виробництво");
        int product = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Продукт");
        int material = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Використана сировина");
        var data = rows.subList(1, rows.size());
        assertThat(data.stream().map(row -> row.get(product)).filter(value -> !value.isBlank()).toList())
                .containsExactly(fixture.both.getProduct());
        assertThat(data.stream().map(row -> row.get(material)).toList()).containsExactlyInAnyOrder(
                fixture.materialA.getName(), fixture.materialA2.getName(), fixture.materialB.getName());
        int amount = XlsxWorkbookReader.columnIndex(rows.getFirst(), "Об'єм");
        assertThat(data.stream().map(row -> row.get(amount)).filter(value -> !value.isBlank()).toList())
                .containsExactly("3");
        var emptyResponse = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_EXPORT,
                UserRole.ADMIN, fixture.query(fixture.materialTerm + "-missing").toTotalQueryParams());
        assertThat(emptyResponse.statusCode()).isEqualTo(200);
        assertThat(XlsxWorkbookReader.firstSheet(emptyResponse.asByteArray())).hasSize(1);
    }
}
