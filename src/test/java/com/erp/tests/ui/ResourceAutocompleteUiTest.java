package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.models.response.ResourceCategoryResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.ResourceRelocationViewerPage;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI smoke for resource autocomplete category filter on «Відстеження ресурсів».
 * Data is prepared via API; UI verifies autocomplete options respect selected categories.
 */
@Slf4j
@Epic("Master Data")
@Feature("Resources")
@Story("Autocomplete UI")
public class ResourceAutocompleteUiTest extends BaseUITest {

    private static final String SEARCH_PREFIX = "ui_ac_cat_";

    private ResourceFixture resourceFixture;
    private String categoryAName;
    private ResourceResponse resourceInCategoryA;
    private ResourceResponse resourceInCategoryB;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.fetchSharedUnit(3);

        List<ResourceCategoryResponse> categories = apiExecutor
                .execute(ApiEndpointDefinition.RESOURCE_CATEGORY_GET_ALL, UserRole.ADMIN)
                .jsonPath()
                .getList("", ResourceCategoryResponse.class);
        Set<Long> trackingCategoryIds = loadResourceTrackingCategoryIds();

        CategoryPair pair = resolveCategoryPair(categories, trackingCategoryIds);
        categoryAName = pair.categoryA().getName();

        resourceInCategoryA = resourceFixture.createUniqueResource(SEARCH_PREFIX, pair.categoryA().getId());
        resourceInCategoryB = resourceFixture.createUniqueResource(SEARCH_PREFIX, pair.categoryB().getId());

        log.info("Injecting RESOURCE_VIEWER session for autocomplete UI test");
        String domain = ConfigProvider.getBaseUrl()
                .replaceFirst("https?://", "")
                .split("/")[0];
        injectSessionCookies(
                getPlaywrightSessionProvider().getSession(
                        UserRole.RESOURCE_VIEWER.getUsername(),
                        UserRole.RESOURCE_VIEWER.getPassword()),
                domain);
        browserContext.addInitScript("localStorage.removeItem('resourceRelocationFilters');");
    }

    @Test
    @TestCaseId("TC-UI-RES-AC-001")
    @Severity(SeverityLevel.NORMAL)
    @Description("""
            RESOURCE_VIEWER відкриває «Відстеження ресурсів» (/resources-viewer/relocation).
            У фільтрі «Категорії» обирає категорію A — autocomplete «Ресурси для відстеження»
            показує ресурс A і не показує ресурс B. Після «Очистити» autocomplete знову
            показує обидва ресурси за спільним search-префіксом.
            """)
    public void resourceAutocompleteRespectsCategoryFilterOnRelocationViewer() {
        String resourceAName = resourceInCategoryA.getName();
        String resourceBName = resourceInCategoryB.getName();

        Allure.parameter("categoryA", categoryAName);
        Allure.parameter("resourceA", resourceAName);
        Allure.parameter("resourceB", resourceBName);

        ResourceRelocationViewerPage viewerPage = Allure.step(
                "Відкрити «Відстеження ресурсів»",
                () -> new ResourceRelocationViewerPage(page).open());

        assertThat(viewerPage.isLoaded()).isTrue();
        viewerPage.clearFilters();
        viewerPage.attachScreenshot("TC-UI-RES-AC-001 — page loaded");

        Allure.step("Фільтр категорії A: autocomplete показує лише ресурс A", () -> {
            viewerPage.selectCategory(categoryAName);
            List<String> filteredOptions = viewerPage.searchResourcesAndCollectOptionNames(SEARCH_PREFIX);
            viewerPage.attachScreenshot("TC-UI-RES-AC-001 — category A filter");

            assertThat(filteredOptions)
                    .anyMatch(option -> option.contains(resourceAName))
                    .noneMatch(option -> option.contains(resourceBName));
            viewerPage.closeResourceAutocomplete();
        });

        Allure.step("Без фільтра категорій: autocomplete показує обидва ресурси", () -> {
            viewerPage.clearFilters();
            List<String> unfilteredOptions = viewerPage.searchResourcesAndCollectOptionNames(SEARCH_PREFIX);
            viewerPage.attachScreenshot("TC-UI-RES-AC-001 — filters cleared");

            assertThat(unfilteredOptions)
                    .anyMatch(option -> option.contains(resourceAName))
                    .anyMatch(option -> option.contains(resourceBName));
        });
    }

    private Set<Long> loadResourceTrackingCategoryIds() {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.APP_CONFIG_GET_ALL, UserRole.ADMIN);
        List<Map<String, Object>> entries = response.jsonPath().getList("");
        Set<Long> ids = new HashSet<>();
        if (entries == null) {
            return ids;
        }
        for (Map<String, Object> entry : entries) {
            if (!"resource_tracking_categories".equals(entry.get("name"))) {
                continue;
            }
            Object value = entry.get("value");
            if (!(value instanceof List<?> options)) {
                continue;
            }
            for (Object option : options) {
                if (!(option instanceof Map<?, ?> optionMap)) {
                    continue;
                }
                Object values = optionMap.get("values");
                if (!(values instanceof List<?> rawValues)) {
                    continue;
                }
                for (Object rawId : rawValues) {
                    if (rawId == null) {
                        continue;
                    }
                    try {
                        ids.add(Long.parseLong(String.valueOf(rawId)));
                    } catch (NumberFormatException ignored) {
                        log.warn("Skipping non-numeric resource_tracking_categories id: {}", rawId);
                    }
                }
            }
        }
        return ids;
    }

    private static CategoryPair resolveCategoryPair(
            List<ResourceCategoryResponse> categories,
            Set<Long> trackingCategoryIds) {
        if (categories == null || categories.size() < 2) {
            throw new SkipException(
                    "Потрібно щонайменше 2 категорії ресурсів на env для UI autocomplete categoryIds");
        }

        List<ResourceCategoryResponse> eligible = new ArrayList<>();
        for (ResourceCategoryResponse category : categories) {
            if (category.getId() == null || category.getName() == null) {
                continue;
            }
            if (trackingCategoryIds.isEmpty() || trackingCategoryIds.contains(category.getId())) {
                eligible.add(category);
            }
        }

        if (eligible.size() < 2) {
            throw new SkipException(
                    "Потрібно щонайменше 2 категорії з resource_tracking_categories для UI тесту autocomplete");
        }

        return new CategoryPair(eligible.get(0), eligible.get(1));
    }

    private record CategoryPair(ResourceCategoryResponse categoryA, ResourceCategoryResponse categoryB) {}
}
