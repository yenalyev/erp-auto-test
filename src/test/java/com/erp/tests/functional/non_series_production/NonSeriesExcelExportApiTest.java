package com.erp.tests.functional.non_series_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesResourceFilterFixture;
import com.erp.models.query.NonSeriesProductionQuery;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.assertions.NonSeriesExportAssertions;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Non-Series Production")
@Feature("Несерійне виробництво — експорт в Excel")
@Severity(SeverityLevel.CRITICAL)
public class NonSeriesExcelExportApiTest extends BaseFunctionalTest {
    private NonSeriesResourceFilterFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareExportData() {
        fixture = new NonSeriesResourceFilterFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupExportData() { if (fixture != null) fixture.cleanup(); }

    @Test @TestCaseId("TC-NSP-XLSX-API-001")
    @Description("AC-01,06. GET export як UI: storageIds без бізнес-фільтрів. Усі п'ять виробів, "
            + "включно з виробом без сировини; точні поля, числові комірки та всі матеріали.")
    public void exportsUnfilteredSelectionAndWorkbookFields() throws Exception {
        verify(fixture.query(null), fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-002")
    @Description("AC-01. productSearch у export/list: точна назва, підрядок, інший регістр і кирилиця; "
            + "явний очікуваний виріб, контроль виключення інших записів.")
    public void filtersByProduct() throws Exception {
        for (String term : List.of(fixture.alpha.getProduct(), "альфа", "АЛЬФА")) {
            verify(fixture.query(null).toBuilder().productSearch(term).build(), fixture.alpha);
        }
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-003")
    @Description("AC-01. statuses=DONE та IN_PROGRESS окремо; повторювані statuses мають OR-семантику. "
            + "GET export передає ті самі параметри, що клієнт; перевірено точний склад XLSX.")
    public void filtersByStatus() throws Exception {
        verify(fixture.query(null).toBuilder().statuses(List.of(NonSeriesProductionStatus.DONE)).build(), fixture.both);
        verify(fixture.query(null).toBuilder().statuses(List.of(NonSeriesProductionStatus.IN_PROGRESS)).build(),
                fixture.alpha, fixture.decoy, fixture.noResources, fixture.older);
        verify(fixture.query(null).toBuilder().statuses(List.of(NonSeriesProductionStatus.values())).build(),
                fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-004")
    @Description("AC-01. Період за датою початку: включні межі, лише startDate або endDate, "
            + "одноденний історичний період. Поза періодом записи відсутні в list і Excel.")
    public void filtersByInclusiveDateRange() throws Exception {
        var query = fixture.query(null);
        verify(query.toBuilder().startDate(fixture.today).endDate(fixture.today).build(),
                fixture.alpha, fixture.both, fixture.decoy, fixture.noResources);
        verify(query.toBuilder().startDate(fixture.today).build(),
                fixture.alpha, fixture.both, fixture.decoy, fixture.noResources);
        verify(query.toBuilder().endDate(fixture.today.minusDays(1)).build(), fixture.older);
        verify(query.toBuilder().startDate(fixture.today.minusDays(10)).endDate(fixture.today.minusDays(10)).build(), fixture.older);
        verify(query.toBuilder().startDate(fixture.today.minusDays(10)).endDate(fixture.today).build(),
                fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-005")
    @Description("AC-01,02,06. resourceSearch знаходить лише фактично використаний матеріал; "
            + "виріб зі збігом тільки в назві виключено. AND назви, матеріалу, статусу й дат; усі матеріали виробу збережено.")
    public void intersectsAllFiltersAndKeepsAllMaterials() throws Exception {
        verify(fixture.query(fixture.materialTerm), fixture.alpha, fixture.both, fixture.older);
        verify(fixture.query(fixture.materialTerm.toUpperCase(Locale.ROOT)), fixture.alpha, fixture.both, fixture.older);
        verify(fixture.query(fixture.materialTerm).toBuilder().productSearch(fixture.both.getProduct())
                .statuses(List.of(NonSeriesProductionStatus.DONE)).startDate(fixture.today).endDate(fixture.today).build(), fixture.both);
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-006")
    @Description("AC-03,04. Порожня вибірка має тільки заголовки. Після вилучення або заміни "
            + "productSearch export відразу відповідає новим умовам; без старих фільтрів.")
    public void handlesNoMatchesAndChangedFilters() throws Exception {
        verify(fixture.query(null).toBuilder().productSearch(fixture.marker + "-missing").build());
        verify(fixture.query(fixture.materialTerm).toBuilder().productSearch(fixture.decoy.getProduct()).build());
        verify(fixture.query(null).toBuilder().productSearch(fixture.alpha.getProduct()).build(), fixture.alpha);
        verify(fixture.query(null).toBuilder().productSearch(fixture.both.getProduct()).build(), fixture.both);
        verify(fixture.query(null), fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
    }

    @Test @TestCaseId("TC-NSP-XLSX-API-007")
    @Description("AC-05,07. list size=1 містить одну з п'яти операцій, export навіть з page=1&size=1 "
            + "містить усі. Дві локації перевіряються окремо й разом; записи й залишки обох локацій незмінні.")
    public void exportsAllPagesAndScopesLocationsWithoutMutations() throws Exception {
        var before = fixture.inventorySnapshot(fixture.storageId);
        var otherBefore = fixture.inventorySnapshot(fixture.otherStorageId);
        var recordsBefore = fixture.list(fixture.query(null));
        var otherQuery = fixture.query(null).toBuilder().storageId(fixture.otherStorageId).build();
        var otherRecordsBefore = fixture.list(otherQuery);
        var query = fixture.query(null).toBuilder().pageSize(1).page(1).build();
        assertThat(fixture.list(query)).hasSize(1);
        var params = new LinkedHashMap<>(query.toListQueryParams());
        var all = List.of(fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
        NonSeriesExportAssertions.assertMatches(export(params), all);
        verify(fixture.query(null).toBuilder().storageId(fixture.otherStorageId).build(), fixture.elsewhere);
        params = new LinkedHashMap<>(fixture.query(null).toTotalQueryParams());
        params.put("storageIds", List.of(fixture.storageId, fixture.otherStorageId));
        var bothLocations = new ArrayList<>(all);
        bothLocations.add(fixture.elsewhere);
        NonSeriesExportAssertions.assertMatches(export(params), bothLocations);
        fixture.expect(fixture.query(null), 28, all.toArray(NonSeriesProductionResponse[]::new));
        fixture.expect(fixture.query(null).toBuilder().storageId(fixture.otherStorageId).build(), 13, fixture.elsewhere);
        assertThat(fixture.inventorySnapshot(fixture.storageId)).isEqualTo(before);
        assertThat(fixture.inventorySnapshot(fixture.otherStorageId)).isEqualTo(otherBefore);
        assertThat(fixture.list(fixture.query(null))).containsExactlyInAnyOrderElementsOf(recordsBefore);
        assertThat(fixture.list(otherQuery)).containsExactlyInAnyOrderElementsOf(otherRecordsBefore);
    }

    private void verify(NonSeriesProductionQuery query, NonSeriesProductionResponse... expected) throws Exception {
        var list = fixture.list(query);
        assertThat(list).extracting(NonSeriesProductionResponse::getId).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(expected).map(NonSeriesProductionResponse::getId).toList());
        NonSeriesExportAssertions.assertMatches(export(query.toTotalQueryParams()), list);
    }

    private byte[] export(Map<String, Object> params) {
        var response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.NON_SERIES_PRODUCTION_EXPORT, UserRole.ADMIN, params);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header("Content-Disposition")).containsIgnoringCase("attachment").contains(".xlsx");
        byte[] bytes = response.asByteArray();
        Allure.addAttachment("Несерійне виробництво.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(bytes), ".xlsx");
        return bytes;
    }
}
