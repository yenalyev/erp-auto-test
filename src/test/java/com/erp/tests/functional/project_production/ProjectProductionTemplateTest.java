package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ProjectProductionTemplateRequest;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ProjectProductionTemplateResponse;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Project Production")
@Feature("Equipment-oriented project production templates")
public class ProjectProductionTemplateTest extends BaseFunctionalTest {

    private ProjectProductionFixture fixture;
    private Long storageId;
    private Long resourceId;
    private Long categoryId;
    private Long modelId;
    private final List<Long> createdTemplateIds = new ArrayList<>();
    private final List<Long> createdProductionIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupTemplateTest() {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();
        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID);
        modelId = testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        for (Long id : createdProductionIds) {
            try {
                ProjectProductionResponse production = fixture.getById(id, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    fixture.cancelFinishedAs(UserRole.ADMIN, id, storageId);
                }
                fixture.deleteAs(UserRole.ADMIN, id, storageId, null);
            } catch (Exception e) {
                log.warn("Could not delete project production {}: {}", id, e.getMessage());
            }
        }
        createdProductionIds.clear();

        for (Long id : createdTemplateIds) {
            try {
                apiExecutor.execute(ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_DELETE,
                        UserRole.ADMIN, null, id, storageId);
            } catch (Exception e) {
                log.warn("Could not delete template {}: {}", id, e.getMessage());
            }
        }
        createdTemplateIds.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-TPL-001")
    @Story("Template CRUD")
    public void templateCrudUsesEquipmentCategoryAndModelWithoutAffectingStock() {
        Map<Long, Double> inventoryBefore = fixture.getInventorySnapshot(storageId);
        ProjectProductionTemplateRequest request = ProjectProductionDataFactory.buildTemplateCreateRequest(
                storageId, categoryId, modelId,
                List.of(ProjectProductionDataFactory.singleResourceStage(resourceId, 3.0, 3.0)));

        ProjectProductionTemplateResponse created = fixture.createTemplate(UserRole.ADMIN, request);
        createdTemplateIds.add(created.getId());
        assertThat(created.getEquipmentCategory().getId()).isEqualTo(categoryId);
        assertThat(created.getEquipmentModel().getId()).isEqualTo(modelId);
        assertThat(created.getProjectProductionStageTemplates()).hasSize(1);

        ProjectProductionTemplateRequest update = request.toBuilder()
                .name(ProjectProductionDataFactory.uniqueTemplateName())
                .description("updated equipment template")
                .build();
        Response updateResponse = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_PUT_UPDATE,
                UserRole.ADMIN, update, created.getId());
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(updateResponse.as(ProjectProductionTemplateResponse.class).getDescription())
                .isEqualTo("updated equipment template");
        fixture.assertInventoryUnchanged(storageId, inventoryBefore);
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-TPL-002")
    @Story("Create CREATION from template")
    public void creationFromTemplateCopiesEquipmentCategoryModelAndStages() {
        ProjectProductionTemplateResponse template = fixture.createTemplate(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildTemplateCreateRequest(
                        storageId, categoryId, modelId,
                        List.of(ProjectProductionDataFactory.singleResourceStage(resourceId, 2.0, 0.0))));
        createdTemplateIds.add(template.getId());

        ProjectProductionResponse production = fixture.createProductionFromTemplate(
                UserRole.ADMIN, template.getId(), storageId);
        createdProductionIds.add(production.getId());

        assertThat(production.getEquipmentCategory().getId()).isEqualTo(categoryId);
        assertThat(production.getEquipmentModel().getId()).isEqualTo(modelId);
        assertThat(production.getEquipment()).isNull();
        assertThat(production.getProjectProductionStages()).hasSize(1);
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROJ-TPL-003")
    @Story("Save production as template")
    public void createTemplateFromExistingProductionKeepsEquipmentContract() {
        ProjectProductionRequest request = ProjectProductionDataFactory.buildCreateRequest(
                storageId, categoryId, modelId,
                ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null);
        ProjectProductionResponse production = fixture.createAs(UserRole.ADMIN, request);
        createdProductionIds.add(production.getId());

        String name = ProjectProductionDataFactory.uniqueTemplateName();
        ProjectProductionTemplateResponse template = fixture.createTemplateFromProduction(
                UserRole.ADMIN, production.getId(), storageId, name);
        createdTemplateIds.add(template.getId());

        assertThat(template.getName()).isEqualTo(name);
        assertThat(template.getEquipmentCategory().getId()).isEqualTo(categoryId);
        assertThat(template.getEquipmentModel().getId()).isEqualTo(modelId);
    }

    @Test(priority = 40)
    @TestCaseId("TC-PROJ-TPL-004")
    @Story("MODIFICATION from template requires concrete equipment before finish")
    @Description("Проєкт MODIFICATION зі шаблону без equipmentId не можна завершити")
    @Severity(SeverityLevel.BLOCKER)
    public void modificationFromTemplateCannotFinishWithoutEquipment() {
        ProjectProductionTemplateResponse template = fixture.createTemplate(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildTemplateCreateRequest(
                        storageId, categoryId, modelId, ProjectProductionType.MODIFICATION,
                        List.of(ProjectProductionDataFactory.stage(
                                "Modification", 1, ProjectProductionState.DONE, List.of()))));
        createdTemplateIds.add(template.getId());

        ProjectProductionResponse production = fixture.createProductionFromTemplate(
                UserRole.ADMIN, template.getId(), storageId);
        createdProductionIds.add(production.getId());
        assertThat(production.getType()).isEqualTo(ProjectProductionType.MODIFICATION);
        assertThat(production.getEquipment()).isNull();

        Response finish = fixture.finishRaw(UserRole.ADMIN, production.getId(), storageId);
        assertThat(finish.statusCode())
                .as("MODIFICATION without equipment must not finish; body=%s", finish.asString())
                .isBetween(400, 499);
        assertThat(fixture.getById(production.getId(), storageId).getState())
                .isNotEqualTo(ProjectProductionState.DONE);
    }

    @Test(priority = 50)
    @TestCaseId("TC-PROJ-TPL-005")
    @Story("Template stage execution percentage validation")
    @Description("Сума executionPercentage етапів шаблону не може перевищувати 100%")
    @Severity(SeverityLevel.BLOCKER)
    public void addingTemplateStageAboveHundredIsRejected() {
        ProjectProductionTemplateResponse template = fixture.createTemplate(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildTemplateCreateRequest(
                        storageId, categoryId, modelId,
                        List.of(ProjectProductionDataFactory.stage(
                                "Stage-100", 1, ProjectProductionState.CREATED, List.of()))));
        createdTemplateIds.add(template.getId());

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_STAGE_POST_ADD,
                UserRole.ADMIN,
                ProjectProductionDataFactory.stage(
                                "Stage-5", 2, ProjectProductionState.CREATED, List.of())
                        .toBuilder().executionPercentage(5).build(),
                template.getId(), storageId);

        assertThat(response.statusCode())
                .as("Template stages totaling 105%% must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }

    @Test(priority = 60)
    @TestCaseId("TC-PROJ-TPL-007")
    @Story("Modification template stage execution percentage validation")
    @Description("Сума executionPercentage етапів шаблону модифікації не може перевищувати 100%")
    @Severity(SeverityLevel.BLOCKER)
    public void addingModificationTemplateStageAboveHundredIsRejected() {
        ProjectProductionTemplateResponse template = fixture.createTemplate(
                UserRole.ADMIN,
                ProjectProductionDataFactory.buildTemplateCreateRequest(
                        storageId, categoryId, modelId, ProjectProductionType.MODIFICATION,
                        List.of(ProjectProductionDataFactory.stage(
                                "Stage-100", 1, ProjectProductionState.CREATED, List.of()))));
        createdTemplateIds.add(template.getId());

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_STAGE_POST_ADD,
                UserRole.ADMIN,
                ProjectProductionDataFactory.stage(
                                "Stage-5", 2, ProjectProductionState.CREATED, List.of())
                        .toBuilder().executionPercentage(5).build(),
                template.getId(), storageId);

        assertThat(response.statusCode())
                .as("Modification template stages totaling 105%% must be rejected; body=%s", response.asString())
                .isBetween(400, 499);
    }
}
