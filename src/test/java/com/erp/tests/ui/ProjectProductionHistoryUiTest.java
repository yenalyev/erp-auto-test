package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.data.factories.relocation.RelocationStockSeeder;
import com.erp.enums.BusinessRole;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.ProjectProductionType;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.OperationHistoryPage;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProjectProductionListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
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

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Slf4j
@Epic("Project Production")
@Feature("Operation History UI")
public class ProjectProductionHistoryUiTest extends BaseUITest {

    private ProjectProductionFixture productionFixture;
    private ResourceFixture resourceFixture;
    private UserFixture userFixture;
    private long storageId;
    private Long productionId;
    private Long productId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        productionFixture = new ProjectProductionFixture(testContext, apiExecutor);
        productionFixture.prepareContext();
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
    public void cleanupProductionAndProduct() {
        if (productionId != null) {
            try {
                ProjectProductionResponse production = productionFixture.getById(productionId, storageId);
                if (production.getState() == ProjectProductionState.DONE) {
                    productionFixture.cancelFinishedAs(UserRole.PROJECT_MANAGER, productionId, storageId);
                }
                productionFixture.deleteAs(UserRole.PROJECT_MANAGER, productionId, storageId, null);
            } catch (Exception e) {
                log.warn("Could not clean up project production {}: {}", productionId, e.getMessage());
            } finally {
                productionId = null;
            }
        }
        if (productId != null) {
            try {
                productionFixture.deleteProduct(UserRole.PROJECT_ADMIN, productId);
            } catch (Exception e) {
                log.warn("Could not deactivate project product {}: {}", productId, e.getMessage());
            } finally {
                productId = null;
            }
        }
    }

    @Test
    @TestCaseId(value = "TC-UI-PROJ-HIST-001", roles = BusinessRole.BUSINESS_UNIT_AND_PROJECT_OWNER)
    @Story("Project production usage and output in history card and table")
    @Description("Після списання сировини та завершення виробництва кожен ресурс видно у своїй картці й рядку таблиці історії операцій")
    @Severity(SeverityLevel.CRITICAL)
    public void usedAndProducedResourcesAppearInCardsAndTable() {
        ResourceResponse input = resourceFixture.createUniqueResource("PP-HIST-INPUT");
        RelocationStockSeeder.receiveFromSupplier(apiExecutor, UserRole.OWNER_1, storageId,
                Map.of(input.getId(), 10.0));

        var product = productionFixture.createProduct(UserRole.PROJECT_ADMIN,
                ProjectProductionDataFactory.buildProductCreateRequest(
                        testContext.get(ContextKey.PROJECT_CATEGORY_ID)));
        productId = product.getId();

        ProjectProductionResponse production = productionFixture.createAs(UserRole.PROJECT_MANAGER,
                ProjectProductionDataFactory.buildCreateRequest(
                        storageId,
                        testContext.get(ContextKey.PROJECT_CATEGORY_ID),
                        productId,
                        ProjectProductionState.IN_PROGRESS,
                        ProjectProductionType.CREATION,
                        null));
        productionId = production.getId();
        productionFixture.addStage(UserRole.PROJECT_MANAGER, productionId, storageId,
                ProjectProductionDataFactory.singleResourceStage(input.getId(), 2.0, 2.0));
        productionFixture.finishAs(UserRole.PROJECT_MANAGER, productionId, storageId);

        ProjectProductionListPage projectList = new ProjectProductionListPage(page).open()
                .clearPeriodFilter()
                .waitForRowWithSerial(production.getSerialNumber());
        assertThat(projectList.hasRowWithSerialNumber(production.getSerialNumber()))
                .as("Користувач із комбінованою роллю бачить проєкт у журналі виробництва")
                .isTrue();

        OperationHistoryPage history = new OperationHistoryPage(page).open();
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
        assertThat(history.getSummaryCardAmountForResource("Вироблено", product.getName()))
                .as("Картка «Вироблено» містить готовий продукт")
                .isCloseTo(1.0, within(0.01));
        assertThat(history.tableHasResourceOperation(product.getName(), "Вироблено"))
                .as("Таблиця містить рядок «Вироблено» для готового продукту")
                .isTrue();

        history.filterBySummaryCard("Використано", input.getName());
        assertThat(history.tableHasResourceOperation(input.getName(), "Використано")).isTrue();
        history.filterBySummaryCard("Вироблено", product.getName());
        assertThat(history.tableHasResourceOperation(product.getName(), "Вироблено")).isTrue();
        history.attachScreenshot("TC-UI-PROJ-HIST-001 — картки та таблиця");
    }
}
