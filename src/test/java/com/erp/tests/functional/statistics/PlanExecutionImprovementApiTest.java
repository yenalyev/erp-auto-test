package com.erp.tests.functional.statistics;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.PlanExecutionFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.TechnologicalMapFixture;
import com.erp.models.request.ExecutionFilterRequest;
import com.erp.models.request.ResourceRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.MeasurementUnitResponse;
import com.erp.models.response.PlanExecutionResponse;
import com.erp.models.response.PlanExecutionRowResponse;
import com.erp.models.response.PlanResponse;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.StorageResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.XlsxWorkbookReader;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Plans")
@Feature("Plan Execution improvement API")
public class PlanExecutionImprovementApiTest extends BaseFunctionalTest {
    private PlanExecutionFixture fixture;
    private ProductionFixture productionFixture;
    private ResourceFixture resourceFixture;
    private StorageFixture storageFixture;
    private InventoryFixture inventoryFixture;
    private final List<ManufacturingItemResponse> productions = new ArrayList<>();
    private final List<TechnologicalMapFixture.IsolatedTechMapContext> contexts = new ArrayList<>();
    private PlanResponse plan;
    private Long storageId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setup() {
        fixture = new PlanExecutionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        storageFixture = new StorageFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (fixture == null || storageFixture == null) {
            productions.clear();
            contexts.clear();
            storageId = null;
            return;
        }
        if (plan != null) {
            try { fixture.cleanupPlan(plan); } catch (Exception e) { log.warn("Plan cleanup: {}", e.getMessage()); }
            plan = null;
        }
        for (ManufacturingItemResponse production : productions.reversed()) {
            try { fixture.cleanupProduction(production, storageId); } catch (Exception e) { log.warn("Production cleanup: {}", e.getMessage()); }
        }
        productions.clear();
        for (var context : contexts.reversed()) {
            try { fixture.cleanupTechMap(context.getTechMap(), storageId); } catch (Exception e) { log.warn("Tech map cleanup: {}", e.getMessage()); }
        }
        contexts.clear();
        if (storageId != null) {
            try { inventoryFixture.clearStock(storageId); } catch (Exception e) { log.warn("Stock cleanup: {}", e.getMessage()); }
        }
        storageFixture.deactivateTrackedStorages(UserRole.ADMIN);
        storageId = null;
    }

    @Test(priority = 10)
    @TestCaseId("TC-API-PLANEXEC-001")
    @Story("Category filtering uses the resource's latest category and aggregates name changes")
    public void categoryFilterUsesLatestResourceCategory() {
        List<ResourceCategoryResponse> categories = categories();
        assertThat(categories).as("At least two categories are required for the category-history scenario")
                .hasSizeGreaterThanOrEqualTo(2);
        MeasurementUnitResponse unit = units().getFirst();
        storageId = isolatedStorage().getId();

        var context = fixture.createIsolatedProduct(storageId, unit.getId(), categories.get(0).getId());
        contexts.add(context);
        productions.add(fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 2));

        ResourceResponse product = context.getProduct();
        String latestName = product.getName() + "-renamed";
        resourceFixture.update(UserRole.ADMIN, product.getId(), ResourceRequest.builder()
                .name(latestName)
                .measurementUnitId(unit.getId())
                .categoryId(categories.get(1).getId())
                .build());
        productions.add(fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 3));

        PlanExecutionResponse oldCategory = execution(List.of(categories.get(0).getId()), null);
        assertThat(rowsFor(oldCategory, product.getId())).as("Historical category must not match").isEmpty();

        PlanExecutionResponse latestCategory = execution(List.of(categories.get(1).getId()), null);
        List<PlanExecutionRowResponse> rows = rowsFor(latestCategory, product.getId());
        assertThat(rows).as("Name/category-only changes do not split a row").hasSize(1);
        assertThat(rows.getFirst().getResource().getName()).isEqualTo(latestName);
        assertThat(rows.getFirst().getResourceCategory().getId()).isEqualTo(categories.get(1).getId());
        assertThat(rows.getFirst().getTotalProduced()).isEqualTo(5.0);
    }

    @Test(priority = 20)
    @TestCaseId("TC-API-PLANEXEC-002")
    @Story("Excel export contains the filtered rows, unit and current stock date")
    public void exportHasRequiredColumnsAndFilteredRows() {
        ResourceCategoryResponse category = categories().getFirst();
        MeasurementUnitResponse unit = units().getFirst();
        storageId = isolatedStorage().getId();
        var context = fixture.createIsolatedProduct(storageId, unit.getId(), category.getId());
        contexts.add(context);
        productions.add(fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 4));

        byte[] bytes = fixture.exportExecution(storageId, filter(List.of(category.getId()), null));
        assertThat(bytes).startsWith((byte) 'P', (byte) 'K');
        Map<String, List<List<String>>> sheets = XlsxWorkbookReader.sheets(bytes);
        log.info("Plan execution XLSX structure: {}", sheets.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue().isEmpty() ? List.of() : e.getValue().getFirst()))
                .toList());
        assertThat(sheets).isNotEmpty();
        List<String> plannedHeaders = List.of(
                "Продукт", "Категорія", "Ціль", "Од. вимір", "Зроблено",
                "На складі (" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        List<String> outsidePlanHeaders = List.of(
                "Продукт", "Категорія", "Од. виміру", "Зроблено",
                "На складі (" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
        assertThat(sheets).containsKeys("За планом", "Поза планом", "Розбір");
        assertThat(sheets.get("За планом")).isNotEmpty();
        assertThat(sheets.get("За планом").getFirst()).containsExactlyElementsOf(plannedHeaders);
        assertThat(sheets.get("Поза планом")).isNotEmpty();
        assertThat(sheets.get("Поза планом").getFirst()).containsExactlyElementsOf(outsidePlanHeaders);
        assertThat(sheets.values().stream().flatMap(List::stream).flatMap(List::stream).toList())
                .contains(context.getProduct().getName(), unit.getShortName());
    }

    @Test(priority = 30)
    @TestCaseId("TC-API-PLANEXEC-003")
    @Story("Final production remains in execution after technological-map archival")
    public void archivedTechMapDoesNotRemoveProductionHistory() {
        storageId = isolatedStorage().getId();
        var context = fixture.createIsolatedProduct(storageId);
        contexts.add(context);
        ManufacturingItemResponse production = fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 6);
        productions.add(production);

        Response archive = new TechnologicalMapFixture(testContext, apiExecutor)
                .deactivateTechMap(UserRole.ADMIN, context.getTechMap().getId(), storageId);
        assertThat(archive.statusCode()).isBetween(200, 299);

        List<PlanExecutionRowResponse> rows = rowsFor(execution(null, null), context.getProduct().getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getTotalProduced()).isEqualTo(6.0);
        contexts.clear(); // already archived
    }

    @Test(priority = 40)
    @TestCaseId("TC-API-PLANEXEC-004")
    @Story("Execution uses the final updated/deleted state of production records")
    public void executionUsesFinalProductionState() {
        storageId = isolatedStorage().getId();
        var context = fixture.createIsolatedProduct(storageId);
        contexts.add(context);
        ManufacturingItemResponse created = fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 5);
        productions.add(created);
        assertProduced(context.getProduct().getId(), 5);

        ManufacturingItemResponse updated = productionFixture.updateAs(
                UserRole.ADMIN, created.getId(), storageId, context.getTechMap(), 7, created.getBatchNumber());
        productions.set(0, updated);
        assertProduced(context.getProduct().getId(), 7);

        productionFixture.deleteAs(UserRole.ADMIN, updated.getId(), storageId);
        productions.clear();
        assertThat(rowsFor(execution(null, null), context.getProduct().getId())).isEmpty();
    }

    @Test(priority = 50)
    @TestCaseId("TC-API-RES-007")
    @Story("ADMIN can change a resource unit; production is split by unit and old unit does not satisfy new target")
    public void unitChangeSplitsExecutionRows() {
        List<MeasurementUnitResponse> units = units();
        assertThat(units).as("At least two units are required for mixed-unit aggregation").hasSizeGreaterThanOrEqualTo(2);
        ResourceCategoryResponse category = categories().getFirst();
        storageId = isolatedStorage().getId();
        var context = fixture.createIsolatedProduct(storageId, units.get(0).getId(), category.getId());
        contexts.add(context);
        productions.add(fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 2));

        ResourceResponse changed = resourceFixture.update(UserRole.ADMIN, context.getProduct().getId(), ResourceRequest.builder()
                .name(context.getProduct().getName())
                .measurementUnitId(units.get(1).getId())
                .categoryId(category.getId())
                .build());
        assertThat(changed.getUnit().getId()).isEqualTo(units.get(1).getId());
        productions.add(fixture.createCurrentMonthProduction(storageId, context.getTechMap(), 3));
        plan = fixture.createCurrentMonthPlan(storageId, context.getProduct().getId(), 10);

        List<PlanExecutionRowResponse> rows = rowsFor(execution(null, null), context.getProduct().getId());
        log.info("Mixed-unit execution rows: {}", rows.stream()
                .map(row -> Map.of(
                        "unitId", row.getUnit().getId(),
                        "unit", row.getUnit().getName(),
                        "produced", row.getTotalProduced(),
                        "goal", row.getPlanGoal()))
                .toList());
        assertThat(rows).extracting(r -> r.getUnit().getId())
                .containsExactlyInAnyOrder(units.get(0).getId(), units.get(1).getId());
        PlanExecutionRowResponse oldUnit = rows.stream()
                .filter(r -> Objects.equals(r.getUnit().getId(), units.get(0).getId())).findFirst().orElseThrow();
        PlanExecutionRowResponse newUnit = rows.stream()
                .filter(r -> Objects.equals(r.getUnit().getId(), units.get(1).getId())).findFirst().orElseThrow();
        assertThat(oldUnit.getPlanGoal()).isZero();
        assertThat(newUnit.getPlanGoal()).isEqualTo(10.0);
    }

    private StorageResponse isolatedStorage() {
        return storageFixture.createProductionStorage(
                com.erp.utils.config.ConfigProvider.getOwner1StorageId(), "plan-exec-improvement-");
    }

    private PlanExecutionResponse execution(List<Long> categoryIds, List<Long> resourceIds) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.STATISTIC_POST_EXECUTION, UserRole.ADMIN,
                filter(categoryIds, resourceIds), storageId);
        assertThat(response.statusCode()).isEqualTo(200);
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.STATISTIC_POST_EXECUTION);
        return response.as(PlanExecutionResponse.class);
    }

    private ExecutionFilterRequest filter(List<Long> categoryIds, List<Long> resourceIds) {
        YearMonth now = YearMonth.now();
        return ExecutionFilterRequest.builder().month(now.getMonthValue()).year(now.getYear())
                .categoryIds(categoryIds).resourceIds(resourceIds).build();
    }

    private List<ResourceCategoryResponse> categories() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        return DatabaseIntegrityValidator.extractList(response, ResourceCategoryResponse.class);
    }

    private List<MeasurementUnitResponse> units() {
        Response response = apiExecutor.execute(ApiEndpointDefinition.MEASUREMENT_UNIT_GET_ALL, UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        return DatabaseIntegrityValidator.extractList(response, MeasurementUnitResponse.class);
    }

    private static List<PlanExecutionRowResponse> rowsFor(PlanExecutionResponse execution, Long resourceId) {
        if (execution.getResourcePlanExecutionList() == null) return List.of();
        return execution.getResourcePlanExecutionList().stream()
                .filter(r -> r.getResource() != null && Objects.equals(r.getResource().getId(), resourceId))
                .toList();
    }

    private void assertProduced(Long resourceId, double expected) {
        assertThat(rowsFor(execution(null, null), resourceId)).singleElement()
                .extracting(PlanExecutionRowResponse::getTotalProduced).isEqualTo(expected);
    }
}
