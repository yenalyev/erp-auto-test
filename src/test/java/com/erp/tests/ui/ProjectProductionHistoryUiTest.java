package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.BusinessRole;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.EquipmentFixture;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.EquipmentResponse;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProjectProductionListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Response;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.assertj.core.api.SoftAssertions;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Operation History UI")
public class ProjectProductionHistoryUiTest extends BaseUITest {

    private ProjectProductionFixture productionFixture;
    private EquipmentFixture equipmentFixture;
    private ResourceFixture resourceFixture;
    private UserFixture userFixture;
    private long storageId;
    private Long productionId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProjectProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
        equipmentFixture = new EquipmentFixture(testContext, apiExecutor);
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(5);
        resourceFixture.fetchSharedResourceCategory();
        storageId = ConfigProvider.getOwner1StorageId();

        userFixture = new UserFixture(testContext, apiExecutor);
        StorageFixture storageFixture = new StorageFixture(testContext, apiExecutor);
        var ownerStorage = storageFixture.getById(UserRole.ADMIN, storageId);
        var actor = userFixture.createBusinessActor(getPlaywrightSessionProvider(),
                BusinessRole.BUSINESS_UNIT_AND_PROJECT_OWNER, List.of(ownerStorage));
        injectSessionCookies(getPlaywrightSessionProvider().getSession(actor.username(), actor.password()),
                sessionCookieDomain());
        browserContext.addInitScript("localStorage.setItem('selectedStorageId', '" + storageId + "');");
    }

    @AfterClass(alwaysRun = true)
    public void cleanupBusinessActor() {
        if (userFixture != null) {
            userFixture.deactivateTrackedUsers();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupProduction() {
        if (productionId != null) {
            try {
                ProjectProductionResponse production = productionFixture.getById(productionId, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    productionFixture.cancelFinishedAs(UserRole.ADMIN, productionId, storageId);
                }
                productionFixture.deleteAs(UserRole.ADMIN, productionId, storageId, null);
            } catch (Exception e) {
                log.warn("Could not clean up project production {}: {}", productionId, e.getMessage());
            } finally {
                productionId = null;
            }
        }
    }

    @Test
    @TestCaseId(value = "TC-UI-PROJ-HIST-001", roles = BusinessRole.BUSINESS_UNIT_AND_PROJECT_OWNER)
    @Story("Resource input and equipment output in operation history")
    @Description("Сировина відображається як USED, а створене обладнання — у картці "
            + "«Обладнання (виготовлено)» та equipment-таблиці як PRODUCED")
    @Severity(SeverityLevel.CRITICAL)
    public void usedResourceAndProducedEquipmentAppearInHistory() {
        ResourceResponse input = resourceFixture.createUniqueResource("PP-HIST-INPUT");
        RelocationStockSeeder.receiveFromSupplier(apiExecutor, UserRole.ADMIN, storageId,
                Map.of(input.getId(), 10.0));

        ProjectProductionResponse production = productionFixture.createAs(UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                        storageId,
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID),
                        testContext.get(ContextKey.PROJECT_EQUIPMENT_MODEL_ID),
                        ProjectProductionState.IN_PROGRESS,
                        ProjectProductionType.CREATION,
                        null));
        productionId = production.getId();
        productionFixture.addStage(UserRole.ADMIN, productionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(input.getId(), 2.0, 2.0));
        productionFixture.finishAs(UserRole.ADMIN, productionId, storageId);
        ProjectProductionResponse finished = productionFixture.getById(productionId, storageId);

        ProjectProductionListPage projectList = new ProjectProductionListPage(page).open()
                .clearPeriodFilter()
                .waitForRowWithSerial(production.getSerialNumber());
        assertThat(projectList.hasRowWithSerialNumber(production.getSerialNumber()))
                .as("Користувач із комбінованою роллю бачить проєкт у журналі виробництва")
                .isTrue();

        OperationHistoryPage history = new OperationHistoryPage(page);
        Response equipmentHistoryResponse = page.waitForResponse(
                response -> response.url().contains("/api/v1/equipment/history")
                        && "GET".equals(response.request().method())
                        && response.status() == 200,
                history::open);
        String equipmentHistoryPayload = equipmentHistoryResponse.text();
        io.qameta.allure.Allure.addAttachment(
                "GET equipment history — project production output",
                "application/json",
                equipmentHistoryPayload);
        assertThat(history.isLoaded()).isTrue();
        assertThat(new AppSidebarPage(page).isNavItemVisible(AppSidebarPage.GROUP_PROJECT_PRODUCTION))
                .as("Користувач із комбінованою роллю бачить проєктне виробництво в меню")
                .isTrue();
        assertThat(history.getSummaryCardAmountForResource("Використано", input.getName()))
                .as("Картка «Використано» містить списану сировину")
                .isCloseTo(2.0, within(0.01));
        assertThat(history.tableHasResourceOperation(input.getName(), "Використано"))
                .as("Таблиця містить рядок «Використано» для сировини")
                .isTrue();
        String equipmentInventoryNumber = finished.getEquipment().getInventoryNumber();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(history.isEquipmentSummaryCardVisible(
                            OperationHistoryPage.EQUIPMENT_PRODUCED_CARD))
                    .as("Картка «Обладнання (виготовлено)» має бути видима")
                    .isTrue();
            softly.assertThat(equipmentHistoryPayload)
                    .as("GET /equipment/history містить створене проєктом обладнання")
                    .contains(equipmentInventoryNumber);
            softly.assertThat(history.equipmentTableContains(equipmentInventoryNumber))
                    .as("Equipment-таблиця містить саме створену проєктом одиницю")
                    .isTrue();
            softly.assertThat(history.equipmentTableHasOperation(
                            equipmentInventoryNumber, OperationHistoryPage.EQUIPMENT_OP_PRODUCED))
                    .as("Створене проєктом обладнання має UI-операцію «Виготовлено»")
                    .isTrue();
        });

        history.filterBySummaryCard("Використано", input.getName());
        assertThat(history.tableHasResourceOperation(input.getName(), "Використано")).isTrue();
        history.attachScreenshot("TC-UI-PROJ-HIST-001 — картки та таблиця");
    }

    @Test
    @TestCaseId(value = "TC-UI-PROJ-HIST-002", roles = BusinessRole.BUSINESS_UNIT_AND_PROJECT_OWNER)
    @Story("Resource used by equipment modification in operation history")
    @Description("Ресурс із MODIFICATION відображається в картці та таблиці «Використано»")
    @Severity(SeverityLevel.CRITICAL)
    public void resourceUsedByModificationAppearsInHistory() {
        ResourceResponse input = resourceFixture.createUniqueResource("PP-MOD-HIST-INPUT");
        RelocationStockSeeder.receiveFromSupplier(apiExecutor, UserRole.ADMIN, storageId,
                Map.of(input.getId(), 10.0));
        long categoryId = testContext.get(ContextKey.PROJECT_EQUIPMENT_CATEGORY_ID);
        EquipmentResponse equipment = equipmentFixture.createEquipmentOnStorage(
                UserRole.ADMIN, storageId, categoryId);
        long equipmentModelId = productionFixture.findEquipmentModelId(equipment.getName());

        ProjectProductionResponse modification = productionFixture.createAs(UserRole.ADMIN,
                ProjectProductionDataFactory.buildCreateRequest(
                                storageId,
                                categoryId,
                                equipmentModelId,
                                ProjectProductionState.IN_PROGRESS,
                                ProjectProductionType.MODIFICATION,
                                null)
                        .toBuilder()
                        .serialNumber(null)
                        .equipmentId(equipment.getId())
                        .build());
        productionId = modification.getId();
        productionFixture.addStage(UserRole.ADMIN, productionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(input.getId(), 3.0, 3.0));

        OperationHistoryPage history = new OperationHistoryPage(page).open();
        assertThat(history.getSummaryCardAmountForResource("Використано", input.getName()))
                .as("Картка «Використано» містить ресурс модифікації")
                .isCloseTo(3.0, within(0.01));
        assertThat(history.tableHasResourceOperation(input.getName(), "Використано"))
                .as("Таблиця містить USED для ресурсу модифікації")
                .isTrue();

        history.filterBySummaryCard("Використано", input.getName());
        assertThat(history.tableHasResourceOperation(input.getName(), "Використано")).isTrue();
        history.attachScreenshot("TC-UI-PROJ-HIST-002 — ресурс модифікації використано");
    }
}
