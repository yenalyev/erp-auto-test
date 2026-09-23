package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.EquipmentStatus;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ResourceToRollbackRequest;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Equipment creation by project production")
public class ProjectProductionTest extends BaseFunctionalTest {

    private ProjectProductionFixture fixture;
    private Long storageId;
    private Long resourceId;
    private Long categoryId;
    private Long modelId;
    private final List<Long> createdProductionIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupProjectProductionTest() {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID);
        modelId = testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedProductions() {
        List<Long> ids = new ArrayList<>(createdProductionIds);
        Collections.reverse(ids);
        for (Long id : ids) {
            try {
                ProjectProductionResponse production = fixture.getById(id, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    fixture.cancelFinishedAs(UserRole.ADMIN, id, storageId);
                }
                fixture.deleteAs(UserRole.ADMIN, id, storageId, null);
            } catch (Exception e) {
                log.warn("Could not clean up project production {}: {}", id, e.getMessage());
            }
        }
        createdProductionIds.clear();
    }

    private void track(Long id) {
        createdProductionIds.add(id);
    }

    private ProjectProductionRequest baseCreateRequest(ProjectProductionState state) {
        return ProjectProductionDataFactory.buildCreateRequest(
                storageId, categoryId, modelId, state, ProjectProductionType.CREATION, null);
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-001")
    @Story("Resources are production inputs")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateWithStageDeductsInputResourceStock() {
        fixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);

        ProjectProductionResponse production = fixture.createWithStageUsage(5.0, 5.0);
        track(production.getId());

        assertThat(production.getProjectProductionStages()).hasSize(1);
        assertThat(fixture.getResourceStock(storageId, resourceId))
                .isCloseTo(stockBefore - 5.0, within(0.01));
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-002")
    @Story("Input stock validation")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotOverConsumeInputResource() {
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        ProjectProductionResponse production = fixture.createAs(
                UserRole.ADMIN, baseCreateRequest(ProjectProductionState.IN_PROGRESS));
        track(production.getId());

        Response response = fixture.addStageRaw(
                UserRole.ADMIN,
                production.getId(),
                storageId,
                ProjectProductionDataFactory.singleResourceStage(
                        resourceId, stockBefore + 100.0, stockBefore + 100.0));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(fixture.getResourceStock(storageId, resourceId)).isCloseTo(stockBefore, within(0.01));
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROJ-003")
    @Story("CREATION produces equipment")
    @Description("Finish створює одну одиницю обладнання, а не resource batch")
    @Severity(SeverityLevel.CRITICAL)
    public void testFinishCreatesAvailableEquipment() {
        ProjectProductionResponse production = fixture.createWithStageUsage(1.0, 1.0);
        track(production.getId());

        fixture.finishAs(UserRole.ADMIN, production.getId(), storageId);
        ProjectProductionResponse finished = fixture.getById(production.getId(), storageId);

        assertThat(finished.getState()).isEqualTo(ProjectProductionState.DONE);
        assertThat(finished.getEquipment()).isNotNull();
        assertThat(finished.getEquipment().getSerialNumber()).isEqualTo(production.getSerialNumber());
        assertThat(finished.getEquipment().getInventoryNumber()).startsWith("EQ-");
        assertThat(finished.getEquipmentCategory().getId()).isEqualTo(categoryId);
        assertThat(finished.getEquipmentModel().getId()).isEqualTo(modelId);

        Response equipmentResponse = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_GET_BY_ID,
                UserRole.ADMIN,
                null,
                finished.getEquipment().getId());
        assertThat(equipmentResponse.statusCode()).isEqualTo(200);
        assertThat(equipmentResponse.as(EquipmentResponse.class).getStatus())
                .isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test(priority = 40)
    @TestCaseId("TC-PROJ-004")
    @Story("Cancel CREATION finish removes produced equipment")
    @Severity(SeverityLevel.CRITICAL)
    public void testCancelFinishRemovesProducedEquipment() {
        ProjectProductionResponse production = fixture.createWithStageUsage(1.0, 1.0);
        track(production.getId());
        fixture.finishAs(UserRole.ADMIN, production.getId(), storageId);
        Long equipmentId = fixture.getById(production.getId(), storageId).getEquipment().getId();

        fixture.cancelFinishedAs(UserRole.ADMIN, production.getId(), storageId);

        ProjectProductionResponse cancelled = fixture.getById(production.getId(), storageId);
        assertThat(cancelled.getState()).isEqualTo(ProjectProductionState.IN_PROGRESS);
        assertThat(cancelled.getEquipment()).isNull();
        Response equipmentResponse = apiExecutor.execute(
                ApiEndpointDefinition.EQUIPMENT_GET_BY_ID, UserRole.ADMIN, null, equipmentId);
        assertThat(equipmentResponse.statusCode()).isBetween(400, 499);
    }

    @Test(priority = 50)
    @TestCaseId("TC-PROJ-005")
    @Story("Delete restores used resources")
    public void testDeleteFullRollbackRestoresStock() {
        fixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        ProjectProductionResponse production = fixture.createWithStageUsage(5.0, 5.0);
        track(production.getId());

        fixture.deleteAs(UserRole.ADMIN, production.getId(), storageId, null);
        createdProductionIds.remove(production.getId());

        assertThat(fixture.getResourceStock(storageId, resourceId)).isCloseTo(stockBefore, within(0.01));
    }

    @Test(priority = 60)
    @TestCaseId("TC-PROJ-006")
    @Story("Partial resource rollback")
    public void testDeletePartialRollback() {
        fixture.ensureStockAtLeast(storageId, resourceId, 10.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        ProjectProductionResponse production = fixture.createWithStageUsage(5.0, 5.0);
        track(production.getId());
        Long stageId = production.getProjectProductionStages().getFirst().getId();

        List<ResourceToRollbackRequest> rollback = List.of(
                ProjectProductionDataFactory.rollback(stageId, resourceId, 3.0));
        fixture.deleteAs(UserRole.ADMIN, production.getId(), storageId, rollback);
        createdProductionIds.remove(production.getId());

        assertThat(fixture.getResourceStock(storageId, resourceId))
                .isCloseTo(stockBefore - 2.0, within(0.01));
    }

    @Test(priority = 70)
    @TestCaseId("TC-PROJ-007")
    @Story("Creation serial validation")
    @Severity(SeverityLevel.CRITICAL)
    public void testFinishRequiresUniqueSerialNumber() {
        ProjectProductionResponse blank = fixture.createAs(
                UserRole.ADMIN,
                baseCreateRequest(ProjectProductionState.IN_PROGRESS).toBuilder().serialNumber("").build());
        track(blank.getId());
        assertThat(fixture.finishRaw(UserRole.ADMIN, blank.getId(), storageId).statusCode())
                .isBetween(400, 499);

        String serial = ProjectProductionDataFactory.uniqueSerialNumber();
        ProjectProductionResponse first = fixture.createAs(
                UserRole.ADMIN,
                baseCreateRequest(ProjectProductionState.IN_PROGRESS).toBuilder().serialNumber(serial).build());
        track(first.getId());
        fixture.finishAs(UserRole.ADMIN, first.getId(), storageId);

        Response duplicate = fixture.createRaw(
                UserRole.ADMIN,
                baseCreateRequest(ProjectProductionState.IN_PROGRESS).toBuilder().serialNumber(serial).build());
        assertThat(duplicate.statusCode()).isBetween(400, 499);
    }

    @Test(priority = 80)
    @TestCaseId("TC-PROJ-009")
    @Story("DONE project cannot be deleted")
    public void testCannotDeleteDoneProduction() {
        ProjectProductionResponse production = fixture.createWithStageUsage(1.0, 1.0);
        track(production.getId());
        fixture.finishAs(UserRole.ADMIN, production.getId(), storageId);

        assertThat(fixture.deleteRaw(UserRole.ADMIN, production.getId(), storageId, null).statusCode())
                .isBetween(400, 499);
    }

    @Test(priority = 90)
    @TestCaseId("TC-PROJ-VAL-001")
    @Story("Stage execution percentage validation")
    @Description("Сума executionPercentage етапів проєкту не може перевищувати 100%")
    @Severity(SeverityLevel.BLOCKER)
    public void addingStageAboveHundredIsRejected() {
        ProjectProductionResponse production = fixture.createAs(
                UserRole.ADMIN,
                baseCreateRequest(ProjectProductionState.IN_PROGRESS).toBuilder()
                        .projectProductionStages(List.of(ProjectProductionDataFactory.stage(
                                "Stage-100", 1, ProjectProductionState.CREATED, List.of())))
                        .build());
        track(production.getId());

        Response response = fixture.addStageRaw(
                UserRole.ADMIN,
                production.getId(),
                storageId,
                ProjectProductionDataFactory.stage(
                                "Stage-5", 2, ProjectProductionState.CREATED, List.of())
                        .toBuilder().executionPercentage(5).build());

        assertThat(response.statusCode())
                .as("Project stages totaling 105%% must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }
}
