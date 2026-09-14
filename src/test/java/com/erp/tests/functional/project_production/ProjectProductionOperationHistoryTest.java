package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.response.ProjectProductInstanceResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ResourceHistoryResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Operation History")
public class ProjectProductionOperationHistoryTest extends BaseFunctionalTest {

    private ProjectProductionFixture productionFixture;
    private InventoryFixture inventoryFixture;
    private long storageId;
    private long resourceId;
    private long categoryId;
    private long productId;
    private String productName;
    private Long createdProductionId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareHistoryTests() {
        productionFixture = new ProjectProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_CATEGORY_ID);
        productId = testContext.get(ContextKey.PROJECT_PRODUCT_ID);
        productName = testContext.get(ContextKey.PROJECT_PRODUCT_NAME);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupProduction() {
        if (createdProductionId == null) {
            return;
        }
        try {
            ProjectProductionResponse production = productionFixture.getById(createdProductionId, storageId);
            if (production.getState() == ProjectProductionState.DONE) {
                productionFixture.cancelFinishedAs(UserRole.PROJECT_MANAGER, createdProductionId, storageId);
            }
            productionFixture.deleteAs(UserRole.PROJECT_MANAGER, createdProductionId, storageId, null);
        } catch (Exception e) {
            log.warn("Could not clean up project production {}: {}", createdProductionId, e.getMessage());
        } finally {
            createdProductionId = null;
        }
    }

    @TestCaseId("TC-PROJ-HIST-001")
    @Test
    @Story("Stage resource usage appears in operation history")
    @Description("Додавання стадії зі списанням ресурсу створює окремий запис USED у журналі операцій складу")
    @Severity(SeverityLevel.CRITICAL)
    public void addingStageRecordsUsedResource() {
        productionFixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        createdProductionId = createProduction().getId();
        List<ResourceHistoryResponse> before = history();

        productionFixture.addStage(UserRole.PROJECT_MANAGER, createdProductionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(resourceId, 5.0, 5.0));

        List<ResourceHistoryResponse> after = history();
        assertHistoryDelta(before, after, resourceId, "USED", 5.0);
    }

    @TestCaseId("TC-PROJ-HIST-002")
    @Test
    @Story("Finished product appears in operation history")
    @Description("Завершення проєктного виробництва створює запис PRODUCED для готового продукту")
    @Severity(SeverityLevel.CRITICAL)
    public void finishingProductionRecordsProducedResource() {
        productionFixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        ProjectProductionResponse production = createProduction();
        createdProductionId = production.getId();
        productionFixture.addStage(UserRole.PROJECT_MANAGER, createdProductionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(resourceId, 1.0, 1.0));
        List<ResourceHistoryResponse> before = history();

        productionFixture.finishAs(UserRole.PROJECT_MANAGER, createdProductionId, storageId);
        long finishedResourceId = productionFixture.getProducts(storageId, productName).stream()
                .filter(instance -> production.getSerialNumber().equals(instance.getSerialNumber()))
                .map(ProjectProductInstanceResponse::getResourceId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Finished product resource is absent for SN "
                        + production.getSerialNumber()));

        List<ResourceHistoryResponse> after = history();
        assertHistoryDelta(before, after, finishedResourceId, "PRODUCED", 1.0);
    }

    private ProjectProductionResponse createProduction() {
        return productionFixture.createAs(UserRole.PROJECT_MANAGER,
                ProjectProductionDataFactory.buildCreateRequest(storageId, categoryId, productId,
                        ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null));
    }

    private List<ResourceHistoryResponse> history() {
        var response = inventoryFixture.getOperationHistoryToday(storageId, UserRole.OWNER_1);
        assertThat(response.statusCode()).as("GET resource-operation-history").isEqualTo(200);
        return inventoryFixture.parseOperationHistory(response).getOperationHistoryList();
    }

    private static void assertHistoryDelta(List<ResourceHistoryResponse> before,
                                           List<ResourceHistoryResponse> after,
                                           long resourceId,
                                           String operationType,
                                           double expectedAmount) {
        List<ResourceHistoryResponse> previous = matchingEntries(before, resourceId, operationType);
        List<ResourceHistoryResponse> current = matchingEntries(after, resourceId, operationType);
        assertThat(current.size() - previous.size())
                .as("Нові записи %s для resourceId=%s", operationType, resourceId)
                .isEqualTo(1);
        assertThat(totalAmount(current) - totalAmount(previous))
                .as("Кількість у новому записі %s для resourceId=%s", operationType, resourceId)
                .isCloseTo(expectedAmount, within(0.01));
    }

    private static List<ResourceHistoryResponse> matchingEntries(List<ResourceHistoryResponse> entries,
                                                                 long resourceId,
                                                                 String operationType) {
        return entries.stream()
                .filter(entry -> entry.getResource() != null
                        && entry.getResource().getId() != null
                        && entry.getResource().getId() == resourceId)
                .filter(entry -> operationType.equals(entry.getResourceOperationType()))
                .toList();
    }

    private static double totalAmount(List<ResourceHistoryResponse> entries) {
        return entries.stream().mapToDouble(entry -> entry.getAmount() == null ? 0.0 : entry.getAmount()).sum();
    }
}
