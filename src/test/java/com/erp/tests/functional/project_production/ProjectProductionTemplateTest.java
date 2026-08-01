package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
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
@Feature("Project Production Templates API")
public class ProjectProductionTemplateTest extends BaseFunctionalTest {

    private ProjectProductionFixture fixture;
    private Long storageId;
    private Long resourceId;
    private Long categoryId;
    private Long productId;

    private final List<Long> createdTemplateIds = new ArrayList<>();
    private final List<Long> createdProductionIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів шаблонів проєктного виробництва")
    public void setupTemplateTest() {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_CATEGORY_ID);
        productId = testContext.get(ContextKey.PROJECT_PRODUCT_ID);
    }

    @AfterMethod(alwaysRun = true)
    @Step("Очистити створені шаблони та виробництва")
    public void cleanup() {
        for (Long id : createdProductionIds) {
            try {
                fixture.deleteAs(UserRole.PROJECT_MANAGER, id, storageId, null);
            } catch (Exception e) {
                log.warn("Could not delete project production {}: {}", id, e.getMessage());
            }
        }
        createdProductionIds.clear();

        for (Long id : createdTemplateIds) {
            try {
                Response response = apiExecutor.execute(
                        com.erp.api.endpoints.ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_DELETE,
                        UserRole.PROJECT_MANAGER, null, id, storageId);
                if (response.statusCode() >= 400) {
                    log.warn("Could not delete template {} (status={})", id, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Could not delete template {}: {}", id, e.getMessage());
            }
        }
        createdTemplateIds.clear();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-TPL-001")
    @Story("Template CRUD")
    @Description("CRUD шаблону проєктного виробництва зі стадією — залишки на складі не змінюються")
    @Severity(SeverityLevel.CRITICAL)
    public void testTemplateCrudDoesNotAffectStock() {
        Map<Long, Double> inventoryBefore = fixture.getInventorySnapshot(storageId);

        ProjectProductionTemplateRequest createRequest = ProjectProductionDataFactory.buildTemplateCreateRequest(
                storageId, categoryId, productId,
                List.of(ProjectProductionDataFactory.singleResourceStage(resourceId, 3.0, 3.0)));

        ProjectProductionTemplateResponse created = Allure.step("Створити шаблон зі стадією", () ->
                fixture.createTemplate(UserRole.PROJECT_MANAGER, createRequest));
        createdTemplateIds.add(created.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getProjectProductionStageTemplates()).hasSize(1);
        assertThat(created.getState()).isEqualTo(ProjectProductionState.CREATED);
        assertThat(created.getType()).isEqualTo(ProjectProductionType.CREATION);

        Allure.step("Оновити шаблон (нова назва)", () -> {
            ProjectProductionTemplateRequest updateRequest = createRequest.toBuilder()
                    .name(ProjectProductionDataFactory.uniqueTemplateName())
                    .description("erp-auto-test updated template")
                    .build();
            Response updateResponse = apiExecutor.execute(
                    com.erp.api.endpoints.ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_PUT_UPDATE,
                    UserRole.PROJECT_MANAGER, updateRequest, created.getId());
            assertThat(updateResponse.statusCode()).isEqualTo(200);
            ProjectProductionTemplateResponse updated = updateResponse.as(ProjectProductionTemplateResponse.class);
            assertThat(updated.getDescription()).isEqualTo("erp-auto-test updated template");
        });

        Allure.step("Перевірити GET за id", () -> {
            Response getResponse = apiExecutor.execute(
                    com.erp.api.endpoints.ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_GET_BY_ID,
                    UserRole.PROJECT_MANAGER, null, created.getId(), storageId);
            assertThat(getResponse.statusCode()).isEqualTo(200);
        });

        Allure.step("Видалити шаблон", () -> {
            Response deleteResponse = apiExecutor.execute(
                    com.erp.api.endpoints.ApiEndpointDefinition.PROJECT_PRODUCTION_TEMPLATE_DELETE,
                    UserRole.PROJECT_MANAGER, null, created.getId(), storageId);
            assertThat(deleteResponse.statusCode()).isBetween(200, 299);
        });
        createdTemplateIds.remove(created.getId());

        Allure.step("Перевірити, що залишки на складі не змінилися", () ->
                fixture.assertInventoryUnchanged(storageId, inventoryBefore));
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-TPL-002")
    @Story("Create production from template")
    @Description("Створення проєктного виробництва з шаблону копіює категорію/продукт/стадії")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateProductionFromTemplate() {
        ProjectProductionTemplateRequest templateRequest = ProjectProductionDataFactory.buildTemplateCreateRequest(
                storageId, categoryId, productId,
                List.of(ProjectProductionDataFactory.singleResourceStage(resourceId, 2.0, 0.0)));

        ProjectProductionTemplateResponse template = Allure.step("Створити шаблон", () ->
                fixture.createTemplate(UserRole.PROJECT_MANAGER, templateRequest));
        createdTemplateIds.add(template.getId());

        ProjectProductionResponse production = Allure.step("Створити виробництво з шаблону", () ->
                fixture.createProductionFromTemplate(UserRole.PROJECT_MANAGER, template.getId(), storageId));
        createdProductionIds.add(production.getId());

        assertThat(production.getId()).isNotNull();
        assertThat(production.getProjectCategory().getId()).isEqualTo(categoryId);
        assertThat(production.getProjectProduct().getId()).isEqualTo(productId);
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROJ-TPL-003")
    @Story("Save production as template")
    @Description("Збереження існуючого проєктного виробництва як нового шаблону")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateTemplateFromExistingProduction() {
        ProjectProductionRequest createRequest = ProjectProductionDataFactory.buildCreateRequest(
                storageId, categoryId, productId,
                ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION, null);
        ProjectProductionResponse production = Allure.step("Створити проєктне виробництво", () ->
                fixture.createAs(UserRole.PROJECT_MANAGER, createRequest));
        createdProductionIds.add(production.getId());

        String templateName = ProjectProductionDataFactory.uniqueTemplateName();
        ProjectProductionTemplateResponse template = Allure.step(
                "Зберегти виробництво як шаблон «" + templateName + "»", () ->
                        fixture.createTemplateFromProduction(
                                UserRole.PROJECT_MANAGER, production.getId(), storageId, templateName));
        createdTemplateIds.add(template.getId());

        assertThat(template.getId()).isNotNull();
        assertThat(template.getName()).isEqualTo(templateName);
        assertThat(template.getProjectCategory().getId()).isEqualTo(categoryId);
        assertThat(template.getProjectProduct().getId()).isEqualTo(productId);
    }
}
