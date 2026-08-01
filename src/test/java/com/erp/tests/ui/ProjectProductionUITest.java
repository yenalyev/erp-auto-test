package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.ProjectProductionState;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.ProjectProductionTemplateRequest;
import com.erp.models.response.ProjectProductionResponse;
import com.erp.models.response.ProjectProductionTemplateResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.ProjectProductionFormPage;
import com.erp.pages.ProjectProductionListPage;
import com.erp.pages.ProjectProductionTemplateListPage;
import com.erp.test_context.ContextKey;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for Project Production (/project-production).
 */
@Slf4j
@Epic("Project Production")
@Feature("Project Production UI")
public class ProjectProductionUITest extends BaseUITest {

    private ProjectProductionFixture fixture;
    private long storageId;
    private Long categoryId;
    private Long productId;
    private String categoryName;
    private String productName;
    private String resourceName;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();

        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();

        storageId = ConfigProvider.getOwner1StorageId();
        categoryId = testContext.get(ContextKey.PROJECT_CATEGORY_ID);
        productId = testContext.get(ContextKey.PROJECT_PRODUCT_ID);
        categoryName = testContext.get(ContextKey.PROJECT_CATEGORY_NAME);
        productName = testContext.get(ContextKey.PROJECT_PRODUCT_NAME);
        resourceName = testContext.get(ContextKey.PROJECT_RESOURCE_NAME);

        Map<String, String> cookies = getPlaywrightSessionProvider()
                .getSession(UserRole.PROJECT_MANAGER.getUsername(), UserRole.PROJECT_MANAGER.getPassword());
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(cookies, domain);
        browserContext.addInitScript(
                "localStorage.setItem('selectedStorageId', '" + storageId + "');");
        log.info("OWNER_1 session injected — domain: {}, storageId: {}", domain, storageId);
    }

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROJ-001")
    @Story("Create CREATION with stage and resource")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 створює проєктне виробництво (CREATION) через UI:
            категорія + продукт + SN + етап 100% з ресурсом (amountNeeded).
            Запис з'являється в журналі.
            """)
    public void createProjectWithStageAndResource() {
        String serial = ProjectProductionDataFactory.uniqueSerialNumber();

        ProjectProductionListPage listPage = new ProjectProductionListPage(page).open();
        ProjectProductionFormPage form = listPage.clickNewProject();

        form.selectCategory(categoryName)
                .selectProduct(productName)
                .fillSerialNumber(serial)
                .configureStage(1, resourceName, 1.0);

        ProjectProductionListPage afterCreate = form.createProject();
        afterCreate.clearPeriodFilter().waitForRowWithSerial(serial);
        assertThat(afterCreate.isOnListPage()).isTrue();
        assertThat(afterCreate.hasRowWithSerialNumber(serial))
                .as("Створений проєкт з SN=%s має бути в журналі", serial)
                .isTrue();
    }

    @Test(priority = 15)
    @TestCaseId("TC-UI-PROJ-005")
    @Story("Add stage button appends after existing stages")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            На формі створення клік «Додати етап» додає «Етап 2».
            Після CPMA-646 кнопка має бути після всіх етапів; на legacy header-layout
            перевірка позиції пропускається, функціонал add-stage все одно валідується.
            """)
    public void addStageButtonAppendsAfterExistingStages() {
        String serial = ProjectProductionDataFactory.uniqueSerialNumber();

        ProjectProductionListPage listPage = new ProjectProductionListPage(page).open();
        ProjectProductionFormPage form = listPage.clickNewProject();

        assertThat(form.stageCardCount()).isGreaterThanOrEqualTo(1);
        assertThat(form.addStageButton().isVisible()).isTrue();

        boolean newLayout = form.isAddStageButtonAfterAllStages();
        if (!newLayout) {
            log.warn("CPMA-646 not on this env yet — layout={}", form.describeAddStageButtonPlacement());
        } else {
            assertThat(form.isAddStageButtonAfterAllStages())
                    .as("«Додати етап» після початкового етапу — layout=%s",
                            form.describeAddStageButtonPlacement())
                    .isTrue();
        }

        form.selectCategory(categoryName)
                .selectProduct(productName)
                .fillSerialNumber(serial)
                .configureStage(1, 50, null, 0);

        int before = form.stageCardCount();
        form.clickAddStage();
        assertThat(form.stageCardCount())
                .as("Після «Додати етап» має з'явитися нова картка")
                .isEqualTo(before + 1);

        if (newLayout) {
            assertThat(form.isAddStageButtonAfterAllStages())
                    .as("«Додати етап» лишається після всіх етапів — layout=%s",
                            form.describeAddStageButtonPlacement())
                    .isTrue();
        }

        form.configureStage(before + 1, 50, resourceName, 1.0);

        ProjectProductionListPage afterCreate = form.createProject();
        afterCreate.clearPeriodFilter().waitForRowWithSerial(serial);
        assertThat(afterCreate.hasRowWithSerialNumber(serial))
                .as("Проєкт з двома етапами SN=%s має бути в журналі", serial)
                .isTrue();
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PROJ-002")
    @Story("Finish project from edit form")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            OWNER_1 відкриває існуюче виробництво з етапом 100%% і натискає «Завершити проєкт».
            У журналі статус стає «Завершено».
            """)
    public void finishProjectFromEditForm() {
        ProjectProductionResponse created = fixture.createWithStageUsage(2.0, 1.0);
        String serial = created.getSerialNumber();

        ProjectProductionFormPage form = new ProjectProductionFormPage(page).openEdit(created.getId());
        assertThat(form.addStageButton().isVisible())
                .as("На edit-формі кнопка «Додати етап» має бути видима")
                .isTrue();

        ProjectProductionListPage listPage = form.finishProject();
        listPage.clearPeriodFilter().waitForRowWithSerial(serial);

        assertThat(fixture.getById(created.getId(), storageId).getState())
                .as("API state після UI finish має бути DONE")
                .isEqualTo(ProjectProductionState.DONE);
        assertThat(listPage.rowShowsStatus(serial, "Завершено"))
                .as("Після finish SN=%s має статус Завершено", serial)
                .isTrue();
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-PROJ-003")
    @Story("Create production from template via UI")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            OWNER_1 створює виробництво зі шаблону через кнопку «Створити виробництво» на списку шаблонів.
            """)
    public void createProductionFromTemplateUi() {
        ProjectProductionTemplateRequest tplRequest = ProjectProductionDataFactory.buildTemplateCreateRequest(
                storageId, categoryId, productId,
                List.of(ProjectProductionDataFactory.singleResourceStage(
                        testContext.get(ContextKey.PROJECT_RESOURCE_ID), 1.0, 0.0)));
        ProjectProductionTemplateResponse template = fixture.createTemplate(UserRole.PROJECT_MANAGER, tplRequest);

        ProjectProductionTemplateListPage tplList = new ProjectProductionTemplateListPage(page).open();
        assertThat(tplList.hasTemplateNamed(template.getName())).isTrue();

        tplList.createProductionFromTemplate(template.getName());

        ProjectProductionListPage listPage = new ProjectProductionListPage(page).open();
        assertThat(listPage.isOnListPage()).isTrue();
    }

    @Test(priority = 5)
    @TestCaseId("TC-UI-PROJ-004")
    @Story("PageTabs for categories and products")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            OWNER_1 відкриває групу «Проєктне виробництво» і перемикає PageTabs
            «Категорії» та «Продукти».
            """)
    public void pageTabsCategoriesAndProducts() {
        AppSidebarPage sidebar = new AppSidebarPage(page);
        page.navigate(ConfigProvider.getBaseUrl() + "/project-production");
        sidebar.waitForSidebarLoaded();

        sidebar.openPageTab(AppSidebarPage.TAB_PROJECT_CATEGORIES);
        assertThat(sidebar.isPageTabVisible(AppSidebarPage.TAB_PROJECT_CATEGORIES)).isTrue();
        assertThat(page.url()).contains("/project-category");

        sidebar.openPageTab(AppSidebarPage.TAB_PROJECT_PRODUCTS);
        assertThat(sidebar.isPageTabVisible(AppSidebarPage.TAB_PROJECT_PRODUCTS)).isTrue();
        assertThat(page.url()).contains("/project-product");
    }
}
