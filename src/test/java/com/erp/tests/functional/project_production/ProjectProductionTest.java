package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.ProjectProductionRequest;
import com.erp.models.request.ResourceToRollbackRequest;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.StorageItemBatchResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Project Production API")
public class ProjectProductionTest extends BaseFunctionalTest {

    private ProjectProductionFixture fixture;
    private Long storageId;
    private Long resourceId;
    private Long categoryId;
    private Long productId;
    private String projectProductName;

    private final List<Long> createdProductionIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів проєктного виробництва")
    public void setupProjectProductionTest() {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        resourceId = testContext.get(ContextKey.PROJECT_RESOURCE_ID);
        categoryId = testContext.get(ContextKey.PROJECT_CATEGORY_ID);
        productId = testContext.get(ContextKey.PROJECT_PRODUCT_ID);
        projectProductName = fixture.getProductById(productId).getName();
    }

    @AfterMethod(alwaysRun = true)
    @Step("Очистити створені проєктні виробництва")
    public void cleanupCreatedProductions() {
        List<Long> ids = new ArrayList<>(createdProductionIds);
        Collections.reverse(ids);
        for (Long id : ids) {
            try {
                ProjectProductionResponse production = fixture.getById(id, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    try {
                        fixture.cancelFinishedAs(UserRole.PROJECT_MANAGER, id, storageId);
                    } catch (Exception e) {
                        log.warn("Could not cancel DONE production {} during cleanup: {}", id, e.getMessage());
                        continue;
                    }
                }
                fixture.deleteAs(UserRole.PROJECT_MANAGER, id, storageId, null);
            } catch (Exception e) {
                log.warn("Could not clean up project production {}: {}", id, e.getMessage());
            }
        }
        createdProductionIds.clear();
    }

    private void track(Long id) {
        createdProductionIds.add(id);
    }

    private ProjectProductionRequest baseCreateRequest(ProjectProductionState state, ProjectProductionType type) {
        return ProjectProductionDataFactory.buildCreateRequest(storageId, categoryId, productId, state, type, null);
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-001")
    @Story("Stage resource usage deducts stock")
    @Description("Додавання стадії з amountUsed списує вказану кількість сировини зі складу")
    @Severity(SeverityLevel.CRITICAL)
    public void testCreateWithStageDeductsStock() {
        double amountNeeded = 5.0;
        double amountUsed = 5.0;
        fixture.ensureStockAtLeast(storageId, resourceId, amountUsed + 5.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);

        ProjectProductionResponse production = Allure.step(
                "Створити проєктне виробництво і додати стадію зі списанням сировини", () ->
                        fixture.createWithStageUsage(amountNeeded, amountUsed));
        track(production.getId());

        assertThat(production.getProjectProductionStages()).hasSize(1);
        assertThat(production.getProjectProductionStages().getFirst()
                .getProjectProductionStageResourceUsages()).hasSize(1);

        Allure.step("Перевірити списання сировини зі складу", () -> {
            double stockAfter = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfter).isCloseTo(stockBefore - amountUsed, within(0.01));
            Allure.parameter("stockBefore", stockBefore);
            Allure.parameter("stockAfter", stockAfter);
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-002")
    @Story("Stock validation")
    @Description("Неможливо списати більше сировини, ніж є на складі при додаванні стадії")
    @Severity(SeverityLevel.CRITICAL)
    public void testCannotOverConsumeStock() {
        double stockBefore = fixture.getResourceStock(storageId, resourceId);
        assertThat(stockBefore).isGreaterThan(0.0);
        double excessiveAmount = stockBefore + 100.0;

        ProjectProductionResponse production = Allure.step("Створити проєктне виробництво без стадій", () ->
                fixture.createAs(UserRole.PROJECT_MANAGER, baseCreateRequest(
                        ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION)));
        track(production.getId());

        Response addStageResponse = Allure.step("Спроба додати стадію з надмірним списанням", () ->
                fixture.addStageRaw(UserRole.PROJECT_MANAGER, production.getId(), storageId,
                        ProjectProductionDataFactory.singleResourceStage(resourceId, excessiveAmount, excessiveAmount)));

        Allure.step("Перевірити відмову (4xx) і незмінність залишків", () -> {
            assertThat(addStageResponse.statusCode()).isEqualTo(400);
            double stockAfter = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfter).isCloseTo(stockBefore, within(0.01));
        });
    }

    @Test(priority = 30)
    @TestCaseId("TC-PROJ-003")
    @Story("Finish project production")
    @Description("Завершення проєктного виробництва створює партію готової продукції із серійним номером")
    @Severity(SeverityLevel.CRITICAL)
    public void testFinishCreatesBatchWithSerialNumber() {
        ProjectProductionResponse production = Allure.step(
                "Створити проєктне виробництво зі стадією", () ->
                        fixture.createWithStageUsage(1.0, 1.0));
        track(production.getId());

        Allure.step("Завершити виробництво", () ->
                fixture.finishAs(UserRole.PROJECT_MANAGER, production.getId(), storageId));

        ProjectProductionResponse finished = fixture.getById(production.getId(), storageId);
        assertThat(finished.getState()).isEqualTo(ProjectProductionState.DONE);

        Allure.step("Перевірити наявність партії готової продукції з серійним номером", () -> {
            assertThat(fixture.hasFinishedBatch(storageId, projectProductName, production.getSerialNumber()))
                    .as("Партія з серійним номером %s має існувати після завершення", production.getSerialNumber())
                    .isTrue();

            Optional<StorageItemBatchResponse> batch = fixture.findFinishedBatch(
                    storageId, projectProductName, production.getSerialNumber());
            assertThat(batch).isPresent();
            assertThat(batch.get().getAmount()).isCloseTo(1.0, within(0.001));
            assertThat(batch.get().getIsProduced()).isTrue();
        });
    }

    @Test(priority = 40)
    @TestCaseId("TC-PROJ-004")
    @Story("Cancel finished project production")
    @Description("Скасування завершення повертає виробництво у роботу і видаляє партію готової продукції")
    @Severity(SeverityLevel.CRITICAL)
    public void testCancelFinishRemovesBatch() {
        ProjectProductionResponse production = Allure.step(
                "Створити та завершити проєктне виробництво", () -> {
                    ProjectProductionResponse created = fixture.createWithStageUsage(1.0, 1.0);
                    fixture.finishAs(UserRole.PROJECT_MANAGER, created.getId(), storageId);
                    return created;
                });
        track(production.getId());

        assertThat(fixture.hasFinishedBatch(storageId, projectProductName, production.getSerialNumber())).isTrue();

        Allure.step("Скасувати завершення виробництва", () ->
                fixture.cancelFinishedAs(UserRole.PROJECT_MANAGER, production.getId(), storageId));

        Allure.step("Перевірити стан IN_PROGRESS і відсутність партії", () -> {
            ProjectProductionResponse afterCancel = fixture.getById(production.getId(), storageId);
            assertThat(afterCancel.getState()).isEqualTo(ProjectProductionState.IN_PROGRESS);
            assertThat(fixture.hasFinishedBatch(storageId, projectProductName, production.getSerialNumber()))
                    .as("Партія має зникнути після скасування завершення")
                    .isFalse();
        });
    }

    @Test(priority = 50)
    @TestCaseId("TC-PROJ-005")
    @Story("Delete project production")
    @Description("Видалення з порожнім тілом (null) повністю повертає списану сировину на склад")
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteFullRollbackRestoresStock() {
        double amount = 5.0;
        fixture.ensureStockAtLeast(storageId, resourceId, amount + 5.0);
        double stockBefore = fixture.getResourceStock(storageId, resourceId);

        ProjectProductionResponse production = Allure.step(
                "Створити проєктне виробництво зі стадією", () ->
                        fixture.createWithStageUsage(amount, amount));
        track(production.getId());

        double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
        assertThat(stockAfterCreate).isCloseTo(stockBefore - amount, within(0.01));

        Allure.step("Видалити з null тілом (повний rollback)", () ->
                fixture.deleteAs(UserRole.PROJECT_MANAGER, production.getId(), storageId, null));
        createdProductionIds.remove(production.getId());

        Allure.step("Перевірити повне повернення сировини на склад", () -> {
            double stockAfterDelete = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterDelete).isCloseTo(stockBefore, within(0.01));
        });

        Allure.step("Перевірити, що запис видалено", () -> {
            Response getResponse = fixture.deleteRaw(UserRole.PROJECT_MANAGER, production.getId(), storageId, null);
            assertThat(getResponse.statusCode()).isBetween(400, 499);
        });
    }

    @Test(priority = 60)
    @TestCaseId("TC-PROJ-006")
    @Story("Delete project production")
    @Description("""
            Часткове повернення сировини на склад через resourcesToRollback (наприклад 3 з 5 списаних)
            і нульовий rollback (amount=0) залишають частину/усю списану сировину невідновленою.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testDeletePartialAndZeroRollback() {
        Allure.step("Частковий rollback: повертається 3 з 5 списаних", () -> {
            double amountUsed = 5.0;
            double amountToRollback = 3.0;
            fixture.ensureStockAtLeast(storageId, resourceId, amountUsed + 5.0);
            double stockBefore = fixture.getResourceStock(storageId, resourceId);

            ProjectProductionResponse production = fixture.createWithStageUsage(amountUsed, amountUsed);
            track(production.getId());
            Long stageId = production.getProjectProductionStages().getFirst().getId();

            double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterCreate).isCloseTo(stockBefore - amountUsed, within(0.01));

            List<ResourceToRollbackRequest> rollbackBody = List.of(
                    ProjectProductionDataFactory.rollback(stageId, resourceId, amountToRollback));
            fixture.deleteAs(UserRole.PROJECT_MANAGER, production.getId(), storageId, rollbackBody);
            createdProductionIds.remove(production.getId());

            double stockAfterDelete = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterDelete)
                    .as("Має повернутись лише %s з %s списаних", amountToRollback, amountUsed)
                    .isCloseTo(stockAfterCreate + amountToRollback, within(0.01));
        });

        Allure.step("Нульовий rollback (amount=0): сировина залишається списаною", () -> {
            double amountUsed = 4.0;
            fixture.ensureStockAtLeast(storageId, resourceId, amountUsed + 5.0);
            double stockBefore = fixture.getResourceStock(storageId, resourceId);

            ProjectProductionResponse production = fixture.createWithStageUsage(amountUsed, amountUsed);
            track(production.getId());
            Long stageId = production.getProjectProductionStages().getFirst().getId();

            double stockAfterCreate = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterCreate).isCloseTo(stockBefore - amountUsed, within(0.01));

            List<ResourceToRollbackRequest> rollbackBody = List.of(
                    ProjectProductionDataFactory.rollback(stageId, resourceId, 0.0));
            fixture.deleteAs(UserRole.PROJECT_MANAGER, production.getId(), storageId, rollbackBody);
            createdProductionIds.remove(production.getId());

            double stockAfterDelete = fixture.getResourceStock(storageId, resourceId);
            assertThat(stockAfterDelete)
                    .as("Залишок не має змінитись при amount=0 у rollback")
                    .isCloseTo(stockAfterCreate, within(0.01));
        });
    }

    @Test(priority = 70)
    @TestCaseId("TC-PROJ-007")
    @Story("Finish validation")
    @Description("""
            Завершення блокується при порожньому серійному номері.
            Унікальність SN у межах категорії перевіряється вже на create (після finish першого
            CREATION з тим же SN) — публічний API не дозволяє створити дублікат для finish.
            """)
    @Severity(SeverityLevel.CRITICAL)
    public void testFinishBlankAndDuplicateSerialNumber() {
        Allure.step("Порожній серійний номер → 4xx при завершенні", () -> {
            ProjectProductionRequest request = baseCreateRequest(
                            ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION)
                    .toBuilder().serialNumber("").build();
            ProjectProductionResponse production = fixture.createAs(UserRole.PROJECT_MANAGER, request);
            track(production.getId());

            Response finishResponse = fixture.finishRaw(UserRole.PROJECT_MANAGER, production.getId(), storageId);
            assertThat(finishResponse.statusCode()).isBetween(400, 499);
        });

        Allure.step("Дублікат SN у межах категорії → 4xx на create після finish першого", () -> {
            String sharedSerialNumber = ProjectProductionDataFactory.uniqueSerialNumber();

            ProjectProductionRequest firstRequest = baseCreateRequest(
                            ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION)
                    .toBuilder().serialNumber(sharedSerialNumber).build();
            ProjectProductionResponse first = fixture.createAs(UserRole.PROJECT_MANAGER, firstRequest);
            track(first.getId());
            fixture.finishAs(UserRole.PROJECT_MANAGER, first.getId(), storageId);

            ProjectProductionRequest secondRequest = baseCreateRequest(
                            ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION)
                    .toBuilder().serialNumber(sharedSerialNumber).build();
            Response secondCreate = fixture.createRaw(UserRole.PROJECT_MANAGER, secondRequest);
            assertThat(secondCreate.statusCode())
                    .as("Другий CREATION з тим же SN має бути відхилений на create")
                    .isBetween(400, 499);
        });
    }

    @Test(priority = 80)
    @TestCaseId("TC-PROJ-008")
    @Story("Cancel validation")
    @Description("""
            Скасування завершення блокується, якщо для того ж проєктного продукту та серійного номера
            існує модифікація (ProjectProductionType.MODIFICATION).
            """)
    @Severity(SeverityLevel.NORMAL)
    public void testCancelBlockedByModification() {
        String serialNumber = ProjectProductionDataFactory.uniqueSerialNumber();

        ProjectProductionResponse creation = Allure.step(
                "Створити та завершити CREATION виробництво", () -> {
                    ProjectProductionRequest request = baseCreateRequest(
                                    ProjectProductionState.IN_PROGRESS, ProjectProductionType.CREATION)
                            .toBuilder().serialNumber(serialNumber).build();
                    ProjectProductionResponse production = fixture.createAs(UserRole.PROJECT_MANAGER, request);
                    fixture.finishAs(UserRole.PROJECT_MANAGER, production.getId(), storageId);
                    return production;
                });
        track(creation.getId());

        ProjectProductionResponse modification = Allure.step(
                "Створити MODIFICATION з тим же серійним номером", () -> {
                    ProjectProductionRequest request = baseCreateRequest(
                                    ProjectProductionState.IN_PROGRESS, ProjectProductionType.MODIFICATION)
                            .toBuilder().serialNumber(serialNumber).build();
                    return fixture.createAs(UserRole.PROJECT_MANAGER, request);
                });
        track(modification.getId());

        Allure.step("Спроба скасувати завершення CREATION → 4xx через наявну модифікацію", () -> {
            Response cancelResponse = fixture.cancelFinishedRaw(UserRole.PROJECT_MANAGER, creation.getId(), storageId);
            assertThat(cancelResponse.statusCode()).isBetween(400, 499);
        });
    }

    @Test(priority = 90)
    @TestCaseId("TC-PROJ-009")
    @Story("Delete validation")
    @Description("Видалити завершене (DONE) проєктне виробництво неможливо")
    @Severity(SeverityLevel.NORMAL)
    public void testCannotDeleteDoneProduction() {
        ProjectProductionResponse production = Allure.step(
                "Створити та завершити виробництво", () -> {
                    ProjectProductionResponse created = fixture.createWithStageUsage(1.0, 1.0);
                    fixture.finishAs(UserRole.PROJECT_MANAGER, created.getId(), storageId);
                    return created;
                });
        track(production.getId());

        Allure.step("Спроба видалити DONE запис → 4xx", () -> {
            Response deleteResponse = fixture.deleteRaw(UserRole.PROJECT_MANAGER, production.getId(), storageId, null);
            assertThat(deleteResponse.statusCode()).isBetween(400, 499);
        });
    }
}
