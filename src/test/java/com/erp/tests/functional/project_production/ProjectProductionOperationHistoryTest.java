package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.InventoryFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.EquipmentHistoryResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ResourceHistoryResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Resource input and equipment output history")
public class ProjectProductionOperationHistoryTest extends BaseFunctionalTest {

    private ProjectProductionFixture productionFixture;
    private EquipmentFixture equipmentFixture;
    private InventoryFixture inventoryFixture;
    private long storageId;
    private long resourceId;
    private long categoryId;
    private long modelId;
    private Long createdProductionId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareHistoryTests() {
        productionFixture = new ProjectProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        inventoryFixture = new InventoryFixture(testContext, apiExecutor);
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID);
        modelId = testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupProduction() {
        if (createdProductionId == null) {
            return;
        }
        try {
            ProjectProductionResponse production = productionFixture.getById(createdProductionId, storageId);
            if (production.getState() == ProjectProductionState.DONE) {
                productionFixture.cancelFinishedAs(UserRole.ADMIN, createdProductionId, storageId);
            }
            productionFixture.deleteAs(UserRole.ADMIN, createdProductionId, storageId, null);
        } catch (Exception e) {
            log.warn("Could not clean up project production {}: {}", createdProductionId, e.getMessage());
        } finally {
            createdProductionId = null;
        }
    }

    @Test
    @TestCaseId("TC-PROJ-HIST-001")
    @Story("Consumed material is recorded as USED")
    public void addingStageRecordsUsedInputResource() {
        productionFixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        createdProductionId = createProduction().getId();
        List<ResourceHistoryResponse> before = resourceHistory();

        productionFixture.addStage(UserRole.ADMIN, createdProductionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(resourceId, 5.0, 5.0));

        assertResourceHistoryDelta(before, resourceHistory(), resourceId, "USED", 5.0);
    }

    @Test
    @TestCaseId("TC-PROJ-HIST-003")
    @Story("Material consumed by MODIFICATION is recorded as USED")
    @Description("Ресурс, використаний етапом модифікації обладнання, потрапляє в історію операцій як USED")
    @Severity(SeverityLevel.CRITICAL)
    public void addingModificationStageRecordsUsedInputResource() {
        productionFixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        EquipmentResponse equipment = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, storageId, categoryId);
        createdProductionId = createModification(equipment).getId();
        List<ResourceHistoryResponse> before = resourceHistory();

        productionFixture.addStage(UserRole.ADMIN, createdProductionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(resourceId, 4.0, 4.0));

        assertResourceHistoryDelta(before, resourceHistory(), resourceId, "USED", 4.0);
    }

    @Test
    @TestCaseId("TC-PROJ-HIST-002")
    @Story("CREATION output is recorded in equipment history")
    @Description("Finish створює equipment PRODUCED; результат не записується як resource PRODUCED")
    @Severity(SeverityLevel.CRITICAL)
    public void finishingCreationRecordsProducedEquipment() {
        ProjectProductionResponse production = createProduction();
        createdProductionId = production.getId();
        productionFixture.finishAs(UserRole.ADMIN, createdProductionId, storageId);

        ProjectProductionResponse finished = productionFixture.getById(createdProductionId, storageId);
        assertThat(finished.getEquipment()).isNotNull();
        List<EquipmentHistoryResponse> history = DatabaseIntegrityValidator.extractList(
                apiExecutor.execute(
                        ApiEndpointDefinition.EQUIPMENT_GET_UNIT_HISTORY,
                        UserRole.ADMIN,
                        null,
                        finished.getEquipment().getId()),
                EquipmentHistoryResponse.class);

        assertThat(history)
                .anySatisfy(entry -> {
                    assertThat(entry.getOperation()).isEqualTo("PRODUCED");
                    assertThat(entry.getEquipment().getId()).isEqualTo(finished.getEquipment().getId());
                    assertThat(entry.getStorage().getId()).isEqualTo(storageId);
                });
    }

    private ProjectProductionResponse createProduction() {
        return productionFixture.createAs(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                        storageId, categoryId, modelId,
                        ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null));
    }

    private ProjectProductionResponse createModification(EquipmentResponse equipment) {
        long equipmentModelId = productionFixture.findEquipmentModelId(equipment.getName());
        return productionFixture.createAs(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                                storageId, categoryId, equipmentModelId,
                                ProjectProductionState.IN_PROGRESS, ProjectProductionType.MODIFICATION, null)
                        .toBuilder()
                        .serialNumber(null)
                        .equipmentId(equipment.getId())
                        .build());
    }

    private List<ResourceHistoryResponse> resourceHistory() {
        var response = inventoryFixture.getOperationHistoryToday(storageId, UserRole.ADMIN);
        assertThat(response.statusCode()).isEqualTo(200);
        return inventoryFixture.parseOperationHistory(response).getOperationHistoryList();
    }

    private static void assertResourceHistoryDelta(List<ResourceHistoryResponse> before,
                                                   List<ResourceHistoryResponse> after,
                                                   long resourceId,
                                                   String operationType,
                                                   double expectedAmount) {
        List<ResourceHistoryResponse> previous = matchingEntries(before, resourceId, operationType);
        List<ResourceHistoryResponse> current = matchingEntries(after, resourceId, operationType);
        assertThat(current.size() - previous.size()).isEqualTo(1);
        assertThat(totalAmount(current) - totalAmount(previous))
                .isCloseTo(expectedAmount, within(0.01));
    }

    private static List<ResourceHistoryResponse> matchingEntries(List<ResourceHistoryResponse> entries,
                                                                 long resourceId,
                                                                 String operationType) {
        return entries.stream()
                .filter(entry -> entry.getResource() != null && entry.getResource().getId() != null)
                .filter(entry -> entry.getResource().getId() == resourceId)
                .filter(entry -> operationType.equals(entry.getResourceOperationType()))
                .toList();
    }

    private static double totalAmount(List<ResourceHistoryResponse> entries) {
        return entries.stream().mapToDouble(entry -> entry.getAmount() == null ? 0.0 : entry.getAmount()).sum();
    }
}
