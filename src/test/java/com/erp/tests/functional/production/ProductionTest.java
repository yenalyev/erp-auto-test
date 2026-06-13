package com.erp.tests.functional.production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.production.ProductionDataFactory;
import com.erp.data.factories.tech_map.TechnologicalMapDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProductionFixture;
import com.erp.models.request.ManufacturingListRequest;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.models.response.StorageItemBatchResponse;
import com.erp.models.response.TechnologicalMapResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.AllureHelper;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production")
@Feature("Manufacturing")
public class ProductionTest extends BaseFunctionalTest {

    private ProductionFixture productionFixture;
    private Long storageId;
    private TechnologicalMapResponse techMap;
    private Long inputResourceId1;
    private Long inputResourceId2;
    private Long outputResourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів виробництва")
    public void setupProductionTest() {
        productionFixture = new ProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        techMap = testContext.get(ContextKey.PRODUCTION_TECH_MAP);
        if (techMap == null) {
            throw new IllegalStateException("PRODUCTION_TECH_MAP missing after fixture setup");
        }
        List<Long> inputIds = testContext.get(ContextKey.PRODUCTION_INPUT_RESOURCE_IDS);
        if (inputIds == null || inputIds.size() < 2) {
            throw new IllegalStateException("PRODUCTION_INPUT_RESOURCE_IDS missing after fixture setup");
        }
        inputResourceId1 = inputIds.get(0);
        inputResourceId2 = inputIds.get(1);
        outputResourceId = testContext.get(ContextKey.PRODUCTION_OUTPUT_RESOURCE_ID);

        SchemaRegistry.logSchemaCoverage();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PRD-001")
    @Story("Create production")
    @Description("Успішне створення виробництва, перевірка відповіді, журналу та залишків")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProduction() {
        double productionAmount = 5.0;
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                techMap, productionAmount, java.time.LocalDate.now(), batchNumber);

        Allure.step(String.format(
                "Параметри виробництва: %.0f од. продукції, партія %s, техкарта id=%d (%s), склад id=%d",
                productionAmount, batchNumber, techMap.getId(),
                TechnologicalMapDataFactory.formatCoefficientsPerOutputUnit(techMap),
                storageId));

        Set<Long> trackedResources = Set.of(inputResourceId1, inputResourceId2, outputResourceId);
        ProductionStockAssertions.StockSnapshot stockBefore = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, trackedResources, "до виробництва");

        Response response = Allure.step("POST create production", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                        UserRole.OWNER_1,
                        request,
                        String.valueOf(storageId))
        );

        Allure.step("Validate status and schema", () -> {
            assertThat(response.statusCode()).isEqualTo(200);
            AllureHelper.attachSchemaValidationInfo(ApiEndpointDefinition.PRODUCTION_POST_CREATE, response);
            SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.PRODUCTION_POST_CREATE);
        });

        List<ManufacturingItemResponse> created = response.jsonPath()
                .getList("", ManufacturingItemResponse.class);
        assertThat(created).isNotNull().hasSize(1);
        ManufacturingItemResponse item = created.getFirst();

        assertManufacturingCreated(item, techMap, productionAmount, batchNumber);

        Allure.step("Verify production appears in list", () -> {
            Response listResponse = apiExecutor.execute(
                    ApiEndpointDefinition.PRODUCTION_GET_ALL_BY_STORE_ID,
                    UserRole.OWNER_1,
                    String.valueOf(storageId));
            List<ManufacturingItemResponse> list = DatabaseIntegrityValidator.extractList(
                    listResponse, ManufacturingItemResponse.class);
            assertThat(list).anyMatch(p -> item.getId().equals(p.getId()));
        });

        Allure.step("Verify GET by id", () -> {
            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    item.getId(),
                    storageId);
            assertThat(getResponse.statusCode()).isEqualTo(200);
            ManufacturingItemResponse byId = getResponse.as(ManufacturingItemResponse.class);
            assertThat(byId.getId()).isEqualTo(item.getId());
            assertThat(byId.getBatchNumber()).isEqualTo(batchNumber);
        });

        ProductionStockAssertions.StockSnapshot stockAfter = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, trackedResources, "після виробництва");

        double inputCoef1 = techMap.getInput().get(0).getAmount();
        double inputCoef2 = techMap.getInput().get(1).getAmount();
        double outputCoef = techMap.getOutput().getFirst().getAmount();

        ProductionStockAssertions.assertDelta(stockBefore, stockAfter, Map.of(
                inputResourceId1, -productionAmount * inputCoef1,
                inputResourceId2, -productionAmount * inputCoef2,
                outputResourceId, productionAmount * outputCoef
        ), outputResourceId);
    }

    @Test(priority = 20)
    @TestCaseId("TC-PRD-002")
    @Story("Create production")
    @Description("Перевірка збільшення кількості записів у журналі після створення")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateProductionIncreasesCount() {
        long countBefore = getProductionListSize();

        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(techMap, 3.0);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                UserRole.OWNER_1,
                request,
                String.valueOf(storageId));
        assertThat(response.statusCode()).isEqualTo(200);

        long countAfter = getProductionListSize();
        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    @DataProvider(name = "invalidProductionProvider")
    public Object[][] invalidProductionData() {
        TechnologicalMapResponse map = testContext != null
                ? testContext.get(ContextKey.PRODUCTION_TECH_MAP)
                : null;
        if (map == null) {
            return new Object[0][];
        }
        return new Object[][]{
                {ProductionDataFactory.emptyItemRequest(), "Empty item"},
                {ProductionDataFactory.negativeAmountRequest(map), "Negative amount"},
                {ProductionDataFactory.invalidTechMapRequest(map), "Invalid tech map"},
                {ProductionDataFactory.missingBatchRequest(map), "Missing batch"},
                {ProductionDataFactory.missingDateRequest(map), "Missing date"},
        };
    }

    @Test(priority = 30, dataProvider = "invalidProductionProvider")
    @TestCaseId("TC-PRD-003")
    @Story("Create production validation")
    @Description("Негативні сценарії створення виробництва")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateProductionValidation(ManufacturingListRequest request, String scenario) {
        long countBefore = getProductionListSize();

        Response response = Allure.step("POST invalid: " + scenario, () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                        UserRole.OWNER_1,
                        request,
                        String.valueOf(storageId))
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(getProductionListSize()).isEqualTo(countBefore);
    }

    @Test(priority = 40)
    @TestCaseId("TC-PRD-004")
    @Story("Production batches")
    @Description("Після створення виробництва з'являється produced batch з очікуваним номером")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateProductionProducesBatch() {
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        ManufacturingListRequest request = ProductionDataFactory.buildCreateRequest(
                techMap, 2.0, java.time.LocalDate.now(), batchNumber);

        Response createResponse = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_POST_CREATE,
                UserRole.OWNER_1,
                request,
                String.valueOf(storageId));
        assertThat(createResponse.statusCode()).isEqualTo(200);

        Long resolvedItemId = testContext.get(ContextKey.PRODUCTION_OUTPUT_STORAGE_ITEM_ID);
        if (resolvedItemId == null) {
            resolvedItemId = ProductionStockAssertions.findStorageItemId(
                    apiExecutor, storageId, UserRole.OWNER_1, outputResourceId);
        }
        final Long outputStorageItemId = resolvedItemId;
        assertThat(outputStorageItemId).isNotNull();

        Response batchesResponse = Allure.step("GET produced batches", () ->
                apiExecutor.execute(
                        ApiEndpointDefinition.STORAGE_INVENTORY_BATCHES_GET,
                        UserRole.OWNER_1,
                        null,
                        storageId,
                        outputStorageItemId)
        );

        assertThat(batchesResponse.statusCode()).isEqualTo(200);
        List<StorageItemBatchResponse> batches = batchesResponse.jsonPath()
                .getList("", StorageItemBatchResponse.class);
        assertThat(batches).isNotNull();
        assertThat(batches).anyMatch(b -> batchNumber.equals(b.getBatchNumber()));
    }

    private long getProductionListSize() {
        Response listResponse = apiExecutor.execute(
                ApiEndpointDefinition.PRODUCTION_GET_PAGE_BY_STORE_ID,
                UserRole.OWNER_1,
                String.valueOf(storageId));
        return DatabaseIntegrityValidator.extractPageTotalElements(listResponse);
    }

    private void assertManufacturingCreated(ManufacturingItemResponse item,
                                            TechnologicalMapResponse techMap,
                                            double expectedAmount,
                                            String expectedBatch) {
        Allure.step("Assert manufacturing response fields", () -> {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getAmount()).isEqualTo(expectedAmount);
            assertThat(item.getBatchNumber()).isEqualTo(expectedBatch);
            assertThat(item.getTechnologicalMap().getId()).isEqualTo(techMap.getId());
            assertThat(item.getProduct().getId()).isEqualTo(techMap.getOutput().getFirst().getResource().getId());
        });
    }
}
