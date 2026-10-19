package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.relocation.RelocationDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.GlobalBusinessActorScope;
import com.erp.fixtures.RelocationFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.TestArtifactCleanup;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.ApiResponseHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** API contract behind «Всього» on /logistics and /relocations. */
@Slf4j
@Epic("Relocation")
@Feature("Filtered relocation totals")
public class RelocationTotalsApiTest extends BaseFunctionalTest {

    private final List<Long> relocationIds = new ArrayList<>();
    private final List<Long> resourceIds = new ArrayList<>();

    private GlobalBusinessActorScope logistScope;
    private StorageFixture storages;
    private StorageRegionFixture regions;
    private ResourceFixture resources;
    private RelocationFixture relocations;
    private StorageResponse source;
    private StorageResponse destination;
    private ResourceResponse first;
    private ResourceResponse second;
    private ResourceResponse third;
    private String firstUnit;
    private String secondUnit;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareTotalsData() {
        storages = new StorageFixture(testContext, apiExecutor);
        regions = new StorageRegionFixture(testContext, apiExecutor);
        resources = new ResourceFixture(testContext, apiExecutor);
        relocations = new RelocationFixture(testContext, apiExecutor);

        resources.fetchSharedResourceCategory();
        Response unitsResponse = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL, UserRole.ADMIN);
        assertThat(unitsResponse.statusCode()).isEqualTo(200);
        List<MeasurementUnitResponse> units = ApiResponseHelper.parseList(
                unitsResponse, MeasurementUnitResponse.class, "GET measurement units for relocation totals");
        assertThat(units).isNotNull();
        List<MeasurementUnitResponse> distinctUnits = units.stream()
                .filter(unit -> unit.getShortName() != null && !unit.getShortName().isBlank())
                .filter(unit -> unit.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        MeasurementUnitResponse::getShortName,
                        unit -> unit,
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream().limit(2).toList();
        assertThat(distinctUnits).as("потрібні дві різні одиниці виміру").hasSize(2);
        firstUnit = distinctUnits.get(0).getShortName();
        secondUnit = distinctUnits.get(1).getShortName();
        Long categoryId = testContext.get(ContextKey.SHARED_RESOURCE_CATEGORY_ID);

        source = storages.createChildStorage("rel-totals-src-");
        destination = storages.createChildStorage("rel-totals-dst-");
        first = createResource("rel-totals-first-", distinctUnits.get(0).getId(), categoryId);
        second = createResource("rel-totals-second-", distinctUnits.get(0).getId(), categoryId);
        third = createResource("rel-totals-third-", distinctUnits.get(1).getId(), categoryId);

        Long supplierId = RelocationStockSeeder.resolveSupplierStorageId(apiExecutor, UserRole.ADMIN);
        receive(supplierId, first, 8);
        receive(supplierId, second, 2);
        receive(supplierId, third, 4);
        relocationIds.add(relocations.createSendWithDescription(
                UserRole.ADMIN, source.getId(), destination.getId(), first.getId(), 4.0,
                "rel-totals-active-first").getId());
        relocationIds.add(relocations.createSendWithDescription(
                UserRole.ADMIN, source.getId(), destination.getId(), third.getId(), 1,
                "rel-totals-active-third").getId());

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        startDate = today.minusDays(2);
        endDate = today.plusDays(2);

        logistScope = new GlobalBusinessActorScope(
                testContext, apiExecutor, getPlaywrightSessionProvider());
        logistScope.acquireWithSystemRoles(UserRole.DYNAMIC_GLOBAL_ACTOR, BusinessRole.LOGIST);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupTotalsData() {
        for (int i = relocationIds.size() - 1; i >= 0; i--) {
            Long id = relocationIds.get(i);
            try {
                Response deleted = relocations.deleteRelocationRaw(UserRole.ADMIN, id, source.getId());
                if (deleted.statusCode() < 200 || deleted.statusCode() >= 300) {
                    log.warn("Cleanup relocation {} returned HTTP {}", id, deleted.statusCode());
                }
            } catch (Exception error) {
                log.warn("Cleanup relocation {} failed: {}", id, error.getMessage());
            }
        }
        if (resources != null) {
            for (Long id : resourceIds) {
                try {
                    resources.deactivate(UserRole.ADMIN, id);
                } catch (Exception error) {
                    log.warn("Cleanup resource {} failed: {}", id, error.getMessage());
                }
            }
        }
        TestArtifactCleanup.cleanupRegionsAndStorages(regions, storages);
        if (logistScope != null) {
            logistScope.release();
        }
    }

    @Test
    @TestCaseId("TC-REL-TOTAL-API-001")
    @Story("History totals by measurement unit")
    @Severity(SeverityLevel.CRITICAL)
    @Description("History totals use states=AUTO_FINISHED, toIds, dates and isOr=false. " +
            "Three isolated received relocations total 10 in one unit and 4 in another; " +
            "productIds/productName narrow the total to the matching resource item.")
    public void historyTotalsGroupUnitsAndRespectProductFilters() {
        Map<String, Object> filter = historyFilter();
        assertTotals(totals(UserRole.ADMIN, filter), Map.of(firstUnit, "10", secondUnit, "4"));

        filter.put("productIds", List.of(first.getId()));
        assertTotals(totals(UserRole.ADMIN, filter), Map.of(firstUnit, "8"));

        filter.remove("productIds");
        filter.put("productName", first.getName());
        assertTotals(totals(UserRole.ADMIN, filter), Map.of(firstUnit, "8"));

        filter.put("productName", "rel-totals-no-matching-resource");
        assertTotals(totals(UserRole.ADMIN, filter), Map.of());
    }

    @Test
    @TestCaseId("TC-REL-TOTAL-API-002")
    @Story("Active totals and complete filtered set")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Active totals use CREATED,CANCELLED with fromIds + toIds + isOr=false. " +
            "Totals cover both isolated sends even when the table page size is one; " +
            "the sibling received relocations are excluded.")
    public void activeTotalsCoverAllPagesWithoutHistory() {
        Map<String, Object> filter = baseFilter(List.of("CREATED", "CANCELLED"));
        filter.put("fromIds", List.of(source.getId()));
        filter.put("toIds", List.of(destination.getId()));
        assertTotals(totals(UserRole.ADMIN, filter), Map.of(firstUnit, "4", secondUnit, "1"));

        Map<String, Object> pageFilter = new LinkedHashMap<>(filter);
        pageFilter.put("page", 0);
        pageFilter.put("size", 1);
        Response page = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE, UserRole.ADMIN, pageFilter);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.jsonPath().getList("content")).hasSize(1);
        assertThat(page.jsonPath().getInt("page.totalElements")).isEqualTo(2);
        assertTotals(totals(UserRole.ADMIN, filter), Map.of(firstUnit, "4", secondUnit, "1"));
    }

    @Test
    @TestCaseId("TC-REL-TOTAL-API-003")
    @Story("Totals access control")
    @Severity(SeverityLevel.CRITICAL)
    @Description("A fresh LOGIST business actor with the global system role Логістика: загальний перегляд " +
            "can read global totals; an ordinary location owner " +
            "cannot read global totals without senderIds or receiverIds scope.")
    public void totalsRequireTheSameReadScopeAsJournal() {
        Map<String, Object> filter = baseFilter(List.of("CREATED", "CANCELLED"));
        Map<String, Object> pageFilter = new LinkedHashMap<>(filter);
        pageFilter.put("page", 0);
        pageFilter.put("size", 1);
        assertThat(apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE, UserRole.DYNAMIC_GLOBAL_ACTOR, pageFilter).statusCode())
                .as("logistician journal access").isEqualTo(200);
        assertThat(totals(UserRole.DYNAMIC_GLOBAL_ACTOR, filter).statusCode()).isEqualTo(200);
        assertThat(totals(UserRole.OWNER_1, filter).statusCode()).isEqualTo(403);
    }

    private ResourceResponse createResource(String prefix, Long unitId, Long categoryId) {
        ResourceResponse created = resources.createUniqueResource(prefix, unitId, categoryId);
        resourceIds.add(created.getId());
        return created;
    }

    private void receive(Long supplierId, ResourceResponse resource, double amount) {
        Response response = apiExecutor.executeRelocationReceive(
                RelocationDataFactory.buildReceiveRequest(
                        supplierId, source.getId(), resource.getId(), amount,
                        RelocationDataFactory.uniqueBatchNumber()), UserRole.ADMIN);
        assertThat(response.statusCode())
                .as("receive %s: %s", resource.getName(), response.asString())
                .isBetween(200, 299);
        RelocationResponse created = response.as(RelocationResponse.class);
        assertThat(created.getState().name()).isEqualTo("AUTO_FINISHED");
        relocationIds.add(created.getId());
    }

    private Map<String, Object> historyFilter() {
        Map<String, Object> filter = baseFilter(List.of("AUTO_FINISHED"));
        filter.put("toIds", List.of(source.getId()));
        return filter;
    }

    private Map<String, Object> baseFilter(List<String> states) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("states", states);
        filter.put("isOr", false);
        filter.put("startDate", startDate.toString());
        filter.put("endDate", endDate.toString());
        return filter;
    }

    private Response totals(UserRole role, Map<String, Object> filter) {
        return apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RELOCATION_GET_TOTALS, role, filter);
    }

    private void assertTotals(Response response, Map<String, String> expected) {
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_GET_TOTALS);
        List<Map<String, Object>> units = response.jsonPath().getList("units");
        assertThat(units).hasSize(expected.size());
        for (Map<String, Object> unit : units) {
            String name = (String) unit.get("unit");
            assertThat(expected).as("unexpected unit %s", name).containsKey(name);
            assertThat(new BigDecimal(String.valueOf(unit.get("amount"))))
                    .as("total for %s", name)
                    .isEqualByComparingTo(new BigDecimal(expected.get(name)));
        }
        assertThat(response.jsonPath().getLong("equipmentCount")).isZero();
    }
}
