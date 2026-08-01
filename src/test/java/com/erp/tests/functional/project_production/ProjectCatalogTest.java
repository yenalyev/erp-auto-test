package com.erp.tests.functional.project_production;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.project_production.ProjectProductionDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.ProjectProductionFixture;
import com.erp.models.request.ProjectCategoryRequest;
import com.erp.models.request.ProjectProductPropertyRequest;
import com.erp.models.request.ProjectProductRequest;
import com.erp.models.response.ProjectCategoryResponse;
import com.erp.models.response.ProjectProductResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Project Production")
@Feature("Project Category / Product Catalog API")
public class ProjectCatalogTest extends BaseFunctionalTest {

    private ProjectProductionFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    @Step("Підготовка середовища для тестів каталогу проєктного виробництва")
    public void setupCatalogTest() {
        fixture = new ProjectProductionFixture(testContext, apiExecutor);
        fixture.prepareContext();
    }

    @Test(priority = 10)
    @TestCaseId("TC-PROJ-CAT-001")
    @Story("Project category CRUD")
    @Description("CRUD категорії проєктного виробництва: створення, оновлення, деактивація, відновлення")
    @Severity(SeverityLevel.CRITICAL)
    public void testProjectCategoryCrudAndRestore() {
        ProjectCategoryRequest createRequest = ProjectProductionDataFactory.buildCategoryCreateRequest();

        ProjectCategoryResponse created = Allure.step("Створити категорію", () ->
                fixture.createCategory(UserRole.PROJECT_ADMIN, createRequest));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(createRequest.getName());
        assertThat(created.getActive()).isTrue();

        Allure.step("Оновити категорію", () -> {
            ProjectCategoryRequest updateRequest = createRequest.toBuilder()
                    .name(ProjectProductionDataFactory.uniqueCategoryName())
                    .description("erp-auto-test updated category")
                    .build();
            ProjectCategoryResponse updated = fixture.updateCategory(UserRole.PROJECT_ADMIN, created.getId(), updateRequest);
            assertThat(updated.getName()).isEqualTo(updateRequest.getName());
            assertThat(updated.getDescription()).isEqualTo("erp-auto-test updated category");
        });

        Allure.step("Деактивувати категорію", () -> {
            fixture.deleteCategory(UserRole.PROJECT_ADMIN, created.getId());
            ProjectCategoryResponse afterDelete = fixture.getCategoryById(created.getId());
            assertThat(afterDelete.getActive()).isFalse();
        });

        Allure.step("Відновити категорію", () -> {
            fixture.restoreCategory(UserRole.PROJECT_ADMIN, created.getId());
            ProjectCategoryResponse afterRestore = fixture.getCategoryById(created.getId());
            assertThat(afterRestore.getActive()).isTrue();
        });
    }

    @Test(priority = 20)
    @TestCaseId("TC-PROJ-CAT-002")
    @Story("Project product CRUD")
    @Description("CRUD проєктного продукту з властивостями: створення, оновлення, деактивація, відновлення")
    @Severity(SeverityLevel.CRITICAL)
    public void testProjectProductCrudWithPropertiesAndRestore() {
        ProjectCategoryResponse category = Allure.step("Створити категорію для продукту", () ->
                fixture.createCategory(UserRole.PROJECT_ADMIN, ProjectProductionDataFactory.buildCategoryCreateRequest()));

        ProjectProductRequest createRequest = ProjectProductionDataFactory.buildProductCreateRequest(category.getId());

        ProjectProductResponse created = Allure.step("Створити продукт із властивостями", () ->
                fixture.createProduct(UserRole.PROJECT_ADMIN, createRequest));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(createRequest.getName());
        assertThat(created.getProjectCategory().getId()).isEqualTo(category.getId());
        assertThat(created.getActive()).isTrue();
        assertThat(created.getProperties()).hasSize(1);
        assertThat(created.getProperties().getFirst().getName()).isEqualTo("erp-auto-test-property");

        Allure.step("Оновити продукт (нова назва + додаткова властивість)", () -> {
            ProjectProductRequest updateRequest = createRequest.toBuilder()
                    .name(ProjectProductionDataFactory.uniqueProductName())
                    .description("erp-auto-test updated product")
                    .properties(List.of(
                            ProjectProductPropertyRequest.builder()
                                    .name("erp-auto-test-property")
                                    .value("erp-auto-test-value-updated")
                                    .build(),
                            ProjectProductPropertyRequest.builder()
                                    .name("erp-auto-test-property-2")
                                    .value("erp-auto-test-value-2")
                                    .build()))
                    .build();
            ProjectProductResponse updated = fixture.updateProduct(UserRole.PROJECT_ADMIN, created.getId(), updateRequest);
            assertThat(updated.getName()).isEqualTo(updateRequest.getName());
            assertThat(updated.getProperties()).hasSize(2);
        });

        Allure.step("Деактивувати продукт", () -> {
            fixture.deleteProduct(UserRole.PROJECT_ADMIN, created.getId());
            ProjectProductResponse afterDelete = fixture.getProductById(created.getId());
            assertThat(afterDelete.getActive()).isFalse();
        });

        Allure.step("Відновити продукт", () -> {
            fixture.restoreProduct(UserRole.PROJECT_ADMIN, created.getId());
            ProjectProductResponse afterRestore = fixture.getProductById(created.getId());
            assertThat(afterRestore.getActive()).isTrue();
        });
    }
}
