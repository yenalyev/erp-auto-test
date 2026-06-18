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
import com.erp.utils.helpers.ProductionBatchAssertions;
import com.erp.utils.helpers.ProductionStockAssertions;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Production")
@Feature("Manufacturing")
public class ProductionTest extends BaseFunctionalTest {

    private static final double MIN_INPUT_STOCK_PER_TEST = 500.0;

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

    @BeforeMethod(alwaysRun = true)
    @Step("Поповнити запас сировини перед тестом (ізоляція на staging)")
    public void ensureInputStockBeforeTest() {
        productionFixture.ensureInputStockAtLeast(
                storageId, inputResourceId1, inputResourceId2, MIN_INPUT_STOCK_PER_TEST);
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

    @Test(priority = 50)
    @TestCaseId("TC-PRD-005")
    @Story("Production batches — merge")
    @Description("""
            Створено виробництво з номером партії → з'явилась produced-партія.
            Друге виробництво з тим самим номером партії → розмір існуючої партії збільшується,
            а не створюється друга партія з тим самим номером.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testSecondProductionWithSameBatchNumberIncreasesBatchSize() {
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        double firstAmount = 3.0;
        double secondAmount = 4.0;
        double inputCoef1 = inputCoef(0);
        double inputCoef2 = inputCoef(1);
        double outputCoef = outputCoef();

        Allure.step(String.format(
                "Сценарій: двічі POST /productions з партією «%s» — спочатку %.0f од., потім +%.0f од.",
                batchNumber, firstAmount, secondAmount), () -> {
            Allure.parameter("batchNumber", batchNumber);
            Allure.parameter("techMapId", techMap.getId());
            Allure.parameter("inputCoef1", inputCoef1);
            Allure.parameter("inputCoef2", inputCoef2);
            Allure.parameter("outputCoef", outputCoef);
        });

        Set<Long> tracked = trackedResources();
        ProductionStockAssertions.StockSnapshot stockBaseline = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "базовий знімок перед сценарієм");

        ManufacturingItemResponse first = Allure.step(String.format(
                "Крок 1: створити виробництво %.0f од. → партія «%s»", firstAmount, batchNumber), () -> {
            ManufacturingItemResponse created = productionFixture.createAs(
                    UserRole.OWNER_1, storageId, techMap, firstAmount, batchNumber);
            Allure.parameter("productionId1", created.getId());
            Allure.step(String.format(
                    "Списання сировини: компонент id=%d × %.0f × %.1f = %.0f од.; "
                            + "компонент id=%d × %.0f × %.1f = %.0f од.",
                    inputResourceId1, firstAmount, inputCoef1, firstAmount * inputCoef1,
                    inputResourceId2, firstAmount, inputCoef2, firstAmount * inputCoef2));
            Allure.step(String.format(
                    "Нарахування продукції id=%d: %.0f од. × %.1f = %.0f од. у партію «%s»",
                    outputResourceId, firstAmount, outputCoef, firstAmount * outputCoef, batchNumber));
            return created;
        });

        ProductionBatchAssertions.BatchSnapshot batchAfterFirst = ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після 1-го виробництва");
        ProductionBatchAssertions.assertProducedBatchAmount(
                new ProductionBatchAssertions.BatchSnapshot(batchNumber, 0, outputResourceId, null),
                batchAfterFirst,
                firstAmount * outputCoef,
                "після першого запису журналу");

        ProductionStockAssertions.StockSnapshot stockAfterFirst = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після 1-го виробництва");
        ProductionStockAssertions.assertDelta(stockBaseline, stockAfterFirst, Map.of(
                inputResourceId1, -firstAmount * inputCoef1,
                inputResourceId2, -firstAmount * inputCoef2,
                outputResourceId, firstAmount * outputCoef
        ), outputResourceId);

        ManufacturingItemResponse second = Allure.step(String.format(
                "Крок 2: створити друге виробництво %.0f од. з тією ж партією «%s»", secondAmount, batchNumber),
                () -> {
                    ManufacturingItemResponse created = productionFixture.createAs(
                            UserRole.OWNER_1, storageId, techMap, secondAmount, batchNumber);
                    Allure.parameter("productionId2", created.getId());
                    Allure.step(String.format(
                            "Додаткове списання: компонент id=%d −%.0f од., компонент id=%d −%.0f од.",
                            inputResourceId1, secondAmount * inputCoef1,
                            inputResourceId2, secondAmount * inputCoef2));
                    Allure.step(String.format(
                            "Додаткове нарахування продукції id=%d +%.0f од. у ту саму партію «%s»",
                            outputResourceId, secondAmount * outputCoef, batchNumber));
                    return created;
                });

        ProductionBatchAssertions.BatchSnapshot batchAfterSecond = ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після 2-го виробництва");

        ProductionBatchAssertions.assertProducedBatchIncreasedBy(
                batchAfterFirst, batchAfterSecond, secondAmount * outputCoef,
                "другий запис журналу додає обсяг до існуючої партії, а не створює нову");

        ProductionStockAssertions.StockSnapshot stockAfterSecond = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після 2-го виробництва");
        ProductionStockAssertions.assertDelta(stockAfterFirst, stockAfterSecond, Map.of(
                inputResourceId1, -secondAmount * inputCoef1,
                inputResourceId2, -secondAmount * inputCoef2,
                outputResourceId, secondAmount * outputCoef
        ), outputResourceId);

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test(priority = 60)
    @TestCaseId("TC-PRD-006")
    @Story("Update production amount")
    @Description("""
            Керівник виробництва (OWNER_1) змінює обсяг існуючого запису:
            спочатку збільшення 3→5 од., потім зменшення 5→2 од.
            Перевіряється корекція produced-партії та залишків сировини/продукції.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testUpdateProductionAmountAdjustsBatchAndStock() {
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        double createAmount = 3.0;
        double increaseTo = 5.0;
        double decreaseTo = 2.0;
        double inputCoef1 = inputCoef(0);
        double inputCoef2 = inputCoef(1);
        double outputCoef = outputCoef();

        Set<Long> tracked = trackedResources();
        ProductionStockAssertions.StockSnapshot stockBefore = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "до створення");

        ManufacturingItemResponse created = Allure.step(String.format(
                "Крок 1: створити виробництво %.0f од., партія «%s»", createAmount, batchNumber), () ->
                productionFixture.createAs(UserRole.OWNER_1, storageId, techMap, createAmount, batchNumber));

        ProductionStockAssertions.StockSnapshot stockAfterCreate = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після створення (3 од.)");
        ProductionStockAssertions.assertDelta(stockBefore, stockAfterCreate, Map.of(
                inputResourceId1, -createAmount * inputCoef1,
                inputResourceId2, -createAmount * inputCoef2,
                outputResourceId, createAmount * outputCoef
        ), outputResourceId);

        ProductionBatchAssertions.BatchSnapshot batchAfterCreate = ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після створення");

        ManufacturingItemResponse increased = Allure.step(String.format(
                "Крок 2: PUT збільшити обсяг %.0f → %.0f од. (дельта +%.0f)", createAmount, increaseTo,
                increaseTo - createAmount), () -> {
            Allure.step(String.format(
                    "Очікувана додаткова витрата: компонент id=%d −%.0f од., компонент id=%d −%.0f од.",
                    inputResourceId1, (increaseTo - createAmount) * inputCoef1,
                    inputResourceId2, (increaseTo - createAmount) * inputCoef2));
            Allure.step(String.format(
                    "Очікуване додаткове нарахування продукції id=%d +%.0f од. у партію «%s»",
                    outputResourceId, (increaseTo - createAmount) * outputCoef, batchNumber));
            return productionFixture.updateAs(
                    UserRole.OWNER_1, created.getId(), storageId, techMap, increaseTo, batchNumber);
        });

        assertThat(increased.getAmount()).isEqualTo(increaseTo);

        ProductionStockAssertions.StockSnapshot stockAfterIncrease = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після збільшення до 5 од.");
        ProductionStockAssertions.assertDelta(stockAfterCreate, stockAfterIncrease, Map.of(
                inputResourceId1, -(increaseTo - createAmount) * inputCoef1,
                inputResourceId2, -(increaseTo - createAmount) * inputCoef2,
                outputResourceId, (increaseTo - createAmount) * outputCoef
        ), outputResourceId);

        ProductionBatchAssertions.BatchSnapshot batchAfterIncrease = ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після збільшення");
        ProductionBatchAssertions.assertProducedBatchAmount(
                batchAfterCreate, batchAfterIncrease, increaseTo * outputCoef,
                "партія має відображати новий загальний обсяг виробництва");

        ManufacturingItemResponse decreased = Allure.step(String.format(
                "Крок 3: PUT зменшити обсяг %.0f → %.0f од. (дельта −%.0f)", increaseTo, decreaseTo,
                increaseTo - decreaseTo), () -> {
            Allure.step(String.format(
                    "Очікуване повернення сировини: компонент id=%d +%.0f од., компонент id=%d +%.0f од.",
                    inputResourceId1, (increaseTo - decreaseTo) * inputCoef1,
                    inputResourceId2, (increaseTo - decreaseTo) * inputCoef2));
            Allure.step(String.format(
                    "Очікуване зменшення продукції id=%d −%.0f од. у партії «%s»",
                    outputResourceId, (increaseTo - decreaseTo) * outputCoef, batchNumber));
            return productionFixture.updateAs(
                    UserRole.OWNER_1, created.getId(), storageId, techMap, decreaseTo, batchNumber);
        });

        assertThat(decreased.getAmount()).isEqualTo(decreaseTo);

        ProductionStockAssertions.StockSnapshot stockAfterDecrease = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після зменшення до 2 од.");
        ProductionStockAssertions.assertDelta(stockAfterIncrease, stockAfterDecrease, Map.of(
                inputResourceId1, (increaseTo - decreaseTo) * inputCoef1,
                inputResourceId2, (increaseTo - decreaseTo) * inputCoef2,
                outputResourceId, -(increaseTo - decreaseTo) * outputCoef
        ), outputResourceId);

        ProductionBatchAssertions.BatchSnapshot batchAfterDecrease = ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після зменшення");
        ProductionBatchAssertions.assertProducedBatchAmount(
                batchAfterIncrease, batchAfterDecrease, decreaseTo * outputCoef,
                "партія зменшилась разом із обсягом запису журналу");
    }

    @Test(priority = 70)
    @TestCaseId("TC-PRD-007")
    @Story("Delete production")
    @Description("""
            Створено виробництво з унікальним номером партії.
            OWNER_1 видаляє запис → produced-партія зникає,
            сировина повертається на склад, готова продукція списується.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteProductionRestoresInputsAndRemovesBatch() {
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        double amount = 4.0;
        double inputCoef1 = inputCoef(0);
        double inputCoef2 = inputCoef(1);
        double outputCoef = outputCoef();

        Set<Long> tracked = trackedResources();
        ProductionStockAssertions.StockSnapshot stockBefore = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "до створення");

        ManufacturingItemResponse created = Allure.step(String.format(
                "Крок 1: створити виробництво %.0f од., унікальна партія «%s»", amount, batchNumber), () ->
                productionFixture.createAs(UserRole.OWNER_1, storageId, techMap, amount, batchNumber));

        ProductionBatchAssertions.captureProducedBatch(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                "після створення — партія існує");

        ProductionStockAssertions.StockSnapshot stockAfterCreate = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після створення");
        ProductionStockAssertions.assertDelta(stockBefore, stockAfterCreate, Map.of(
                inputResourceId1, -amount * inputCoef1,
                inputResourceId2, -amount * inputCoef2,
                outputResourceId, amount * outputCoef
        ), outputResourceId);

        long journalBeforeDelete = getProductionListSize();

        Allure.step(String.format(
                "Крок 2: DELETE виробництво id=%d — очікується повернення "
                        + "компонент id=%d +%.0f од., компонент id=%d +%.0f од., "
                        + "продукція id=%d −%.0f од., видалення партії «%s»",
                created.getId(),
                inputResourceId1, amount * inputCoef1,
                inputResourceId2, amount * inputCoef2,
                outputResourceId, amount * outputCoef,
                batchNumber), () ->
                productionFixture.deleteAs(UserRole.OWNER_1, created.getId(), storageId));

        ProductionStockAssertions.StockSnapshot stockAfterDelete = ProductionStockAssertions.capture(
                apiExecutor, storageId, UserRole.OWNER_1, tracked, "після видалення");
        ProductionStockAssertions.assertDelta(stockAfterCreate, stockAfterDelete, Map.of(
                inputResourceId1, amount * inputCoef1,
                inputResourceId2, amount * inputCoef2,
                outputResourceId, -amount * outputCoef
        ), outputResourceId);

        ProductionBatchAssertions.assertProducedBatchAbsent(
                apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber);

        Allure.step("Перевірити, що запис видалено з журналу", () -> {
            long journalAfterDelete = getProductionListSize();
            assertThat(journalAfterDelete)
                    .as("Кількість записів у журналі має зменшитись на 1")
                    .isEqualTo(journalBeforeDelete - 1);

            Response getResponse = apiExecutor.execute(
                    ApiEndpointDefinition.PRODUCTION_GET_BY_ID,
                    UserRole.OWNER_1,
                    null,
                    created.getId(),
                    storageId);
            assertThat(getResponse.statusCode())
                    .as("GET by id для видаленого запису не повинен повертати 200")
                    .isNotEqualTo(200);
            Allure.parameter("getByIdStatusAfterDelete", getResponse.statusCode());
        });
    }

    @Test(priority = 80)
    @TestCaseId("TC-PRD-008")
    @Story("Production batches — sum")
    @Description("""
            Два окремі записи журналу з однаковим номером партії.
            Розмір produced-партії дорівнює сумі обсягів обох записів (× коефіцієнт виходу техкарти).
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testProducedBatchAmountEqualsSumOfTwoProductionRecords() {
        String batchNumber = ProductionDataFactory.uniqueBatchNumber();
        double amountA = 2.0;
        double amountB = 3.0;
        double expectedBatchTotal = (amountA + amountB) * outputCoef();

        ManufacturingItemResponse recordA = Allure.step(String.format(
                "Крок 1: запис A — %.0f од., партія «%s»", amountA, batchNumber), () ->
                productionFixture.createAs(UserRole.OWNER_1, storageId, techMap, amountA, batchNumber));

        ManufacturingItemResponse recordB = Allure.step(String.format(
                "Крок 2: запис B — %.0f од., та сама партія «%s»", amountB, batchNumber), () ->
                productionFixture.createAs(UserRole.OWNER_1, storageId, techMap, amountB, batchNumber));

        Allure.step(String.format(
                "Крок 3: перевірка — партія «%s» = %.0f (A) + %.0f (B) = %.0f од. продукції id=%d",
                batchNumber, amountA, amountB, expectedBatchTotal, outputResourceId), () -> {
            Allure.parameter("productionIdA", recordA.getId());
            Allure.parameter("productionIdB", recordB.getId());
            Allure.parameter("amountA", amountA);
            Allure.parameter("amountB", amountB);
            Allure.parameter("expectedBatchTotal", expectedBatchTotal);

            ProductionBatchAssertions.BatchSnapshot batch = ProductionBatchAssertions.captureProducedBatch(
                    apiExecutor, storageId, UserRole.OWNER_1, outputResourceId, batchNumber,
                    "після двох записів");

            assertThat(batch.amount())
                    .as("Розмір партії «%s» = сума обсягів записів A і B", batchNumber)
                    .isCloseTo(expectedBatchTotal, within(0.01));

            Allure.step(String.format(
                    "Підтверджено: партія «%s» містить %.0f од. (= %.0f + %.0f)",
                    batchNumber, batch.amount(), amountA, amountB));
        });
    }

    private Set<Long> trackedResources() {
        return Set.of(inputResourceId1, inputResourceId2, outputResourceId);
    }

    private double inputCoef(int index) {
        return techMap.getInput().get(index).getAmount();
    }

    private double outputCoef() {
        return techMap.getOutput().getFirst().getAmount();
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
