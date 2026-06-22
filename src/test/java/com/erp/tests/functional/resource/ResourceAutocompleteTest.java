package com.erp.tests.functional.resource;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Master Data")
@Feature("Resources")
@Story("Autocomplete")
public class ResourceAutocompleteTest extends BaseFunctionalTest {

    private static final String SEARCH_PREFIX = "ac_cat_";

    private ResourceFixture resourceFixture;
    private Long categoryAId;
    private Long categoryBId;
    private String searchToken;
    private ResourceResponse resourceInCategoryA;
    private ResourceResponse resourceInCategoryB;

    @BeforeClass(alwaysRun = true)
    @Step("Setup environment for Resource Autocomplete tests")
    public void setupAutocompleteTest() {
        if (testContext == null) {
            baseTestClassSetup();
        }
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);

        Response categoriesResponse = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN);
        List<ResourceCategoryResponse> categories = categoriesResponse.jsonPath()
                .getList("", ResourceCategoryResponse.class);
        if (categories == null || categories.size() < 2) {
            throw new SkipException(
                    "Потрібно щонайменше 2 категорії ресурсів на env для тестів autocomplete categoryIds");
        }

        categoryAId = categories.get(0).getId();
        categoryBId = categories.stream()
                .map(ResourceCategoryResponse::getId)
                .filter(id -> !id.equals(categoryAId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No second category found"));

        resourceInCategoryA = resourceFixture.createUniqueResource(SEARCH_PREFIX, categoryAId);
        resourceInCategoryB = resourceFixture.createUniqueResource(SEARCH_PREFIX, categoryBId);
        searchToken = SEARCH_PREFIX;
    }

    @Test(priority = 10)
    @TestCaseId("TC-RES-020")
    @Description("GET /resources/autocomplete?categoryIds=... повертає лише ресурси обраної категорії")
    @Severity(SeverityLevel.NORMAL)
    public void testAutocompleteFiltersByCategoryIds() {
        List<ResourceResponse> filtered = Allure.step(
                "Act: autocomplete з categoryIds=" + categoryAId,
                () -> resourceFixture.autocomplete(UserRole.ADMIN, searchToken, false, List.of(categoryAId)));

        Allure.step("Assert: ресурс категорії A присутній, ресурс категорії B відсутній", () -> {
            assertThat(filtered).extracting(ResourceResponse::getId)
                    .contains(resourceInCategoryA.getId())
                    .doesNotContain(resourceInCategoryB.getId());
        });
    }

    @Test(priority = 11)
    @TestCaseId("TC-RES-021")
    @Description("GET /resources/autocomplete без categoryIds повертає ресурси всіх категорій за search")
    @Severity(SeverityLevel.NORMAL)
    public void testAutocompleteWithoutCategoryIdsReturnsAllMatching() {
        List<ResourceResponse> unfiltered = Allure.step(
                "Act: autocomplete без categoryIds",
                () -> resourceFixture.autocomplete(UserRole.ADMIN, searchToken, false));

        Allure.step("Assert: обидва тестові ресурси присутні", () -> {
            assertThat(unfiltered).extracting(ResourceResponse::getId)
                    .contains(resourceInCategoryA.getId(), resourceInCategoryB.getId());
        });
    }
}
