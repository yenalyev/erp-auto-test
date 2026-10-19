package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ResourceTrackerActorScope;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.ResourceRelocationViewerPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Resource Viewer")
@Feature("Іменовані списки ресурсів відстеження")
public class ResourceViewerNamedListsUiTest extends BaseUITest {

    private static final String PREFIX = "RVW-UI-LIST-";
    private ResourceFixture resourceFixture;
    private ResourceResponse first;
    private ResourceResponse second;
    private final List<String> createdNames = new ArrayList<>();
    private ResourceTrackerActorScope trackerActorScope;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        first = resourceFixture.createUniqueResource("RVW-UI-LIST-A-");
        second = resourceFixture.createUniqueResource("RVW-UI-LIST-B-");
        trackerActorScope = new ResourceTrackerActorScope(
                testContext, apiExecutor, getPlaywrightSessionProvider());
        var actor = trackerActorScope.acquire();
        injectSessionCookies(
                authService.getSessionForUser(actor.username(), actor.password()),
                sessionCookieDomain());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupListsAndResources() {
        try {
            if (apiExecutor != null) {
                Response names = apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_VIEWER_LIST_NAMES_GET, UserRole.ADMIN);
                if (names.statusCode() == 200) {
                    for (Object raw : names.jsonPath().getList("")) {
                        if (raw instanceof Map<?, ?> item
                                && createdNames.stream().anyMatch(name ->
                                name.equals(item.get("name")) || (name + "-EDITED").equals(item.get("name")))) {
                            apiExecutor.execute(ApiEndpointDefinition.RESOURCE_VIEWER_LIST_DELETE,
                                    UserRole.ADMIN, null, item.get("id"));
                        }
                    }
                }
            }
            if (resourceFixture != null) {
                if (first != null) resourceFixture.deactivate(UserRole.ADMIN, first.getId());
                if (second != null) resourceFixture.deactivate(UserRole.ADMIN, second.getId());
            }
        } finally {
            if (trackerActorScope != null) trackerActorScope.release();
        }
    }

    @TestCaseId("TC-UI-RVW-014")
    @Story("Створити, застосувати, змінити й видалити список")
    @Test
    public void manageAndApplyNamedResourceList() {
        String name = PREFIX + System.currentTimeMillis();
        createdNames.add(name);
        ResourceRelocationViewerPage viewer = new ResourceRelocationViewerPage(page).open();
        Locator listFilter = page.getByText("Список ресурсів",
                new Page.GetByTextOptions().setExact(true)).locator("..");

        listFilter.getByRole(AriaRole.BUTTON).last().click();
        page.getByTestId("resource-list-create").click();
        page.getByTestId("resource-list-name").fill(name);
        page.getByTestId("resource-picker-name-filter").fill(first.getName());
        Response catalogue = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_GET_PAGE,
                UserRole.RESOURCE_VIEWER,
                Map.of("name", first.getName(), "isActive", true), 25);
        assertThat(catalogue.statusCode())
                .as("каталог ресурсів доступний для UI fixture")
                .isEqualTo(200);
        page.getByTestId("resource-picker-row-" + first.getId()).click();
        page.getByTestId("resource-list-save").click();

        Response names = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_NAMES_GET, UserRole.RESOURCE_VIEWER);
        assertThat(names.statusCode()).isEqualTo(200);
        Long id = names.jsonPath().getList("", Map.class).stream()
                .filter(item -> name.equals(item.get("name")))
                .map(item -> Long.valueOf(String.valueOf(item.get("id"))))
                .findFirst().orElseThrow();
        page.getByTestId("resource-list-row-" + id).waitFor();
        page.keyboard().press("Escape");

        listFilter.getByRole(AriaRole.COMBOBOX).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(name)).click();
        waitForSelectedResources(first.getId());
        assertThat(selectedResourceIds()).containsExactly(first.getId().intValue());
        assertThat(selectedCategoryIds()).isEmpty();
        viewer.search();

        listFilter.getByRole(AriaRole.BUTTON).last().click();
        page.getByTestId("resource-list-row-" + id + "-edit").click();
        page.getByTestId("resource-list-name").fill(name + "-EDITED");
        page.getByTestId("resource-list-selected-chip-" + first.getId() + "-remove").click();
        page.getByTestId("resource-picker-name-filter").fill(second.getName());
        page.getByTestId("resource-picker-row-" + second.getId()).click();
        page.getByTestId("resource-list-save").click();
        page.getByTestId("resource-list-row-" + id).waitFor();
        page.keyboard().press("Escape");

        listFilter.getByRole(AriaRole.COMBOBOX).click();
        page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(name + "-EDITED")).click();
        waitForSelectedResources(second.getId());
        assertThat(selectedResourceIds()).containsExactly(second.getId().intValue());

        listFilter.getByRole(AriaRole.BUTTON).last().click();
        page.getByTestId("resource-list-row-" + id + "-delete").click();
        page.getByRole(AriaRole.ALERTDIALOG).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Видалити")).click();
        assertThat(apiExecutor.execute(ApiEndpointDefinition.RESOURCE_VIEWER_LIST_NAMES_GET,
                UserRole.RESOURCE_VIEWER).jsonPath().getList("id", Long.class)).doesNotContain(id);
    }

    @TestCaseId("TC-UI-RVW-015")
    @Story("Застосувати збережений список до фільтрів")
    @Test
    public void savedListPopulatesTrackingFilters() {
        String name = PREFIX + System.currentTimeMillis();
        createdNames.add(name);
        Response created = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_CREATE,
                UserRole.RESOURCE_VIEWER,
                Map.of("name", name, "resourceIds", List.of(first.getId(), second.getId())));
        assertThat(created.statusCode()).isEqualTo(200);

        new ResourceRelocationViewerPage(page).open();
        Locator listFilter = page.getByText("Список ресурсів",
                new Page.GetByTextOptions().setExact(true)).locator("..");
        listFilter.getByRole(AriaRole.COMBOBOX).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(name)).click();
        waitForSelectedResources(first.getId(), second.getId());
        assertThat(selectedResourceIds())
                .containsExactlyInAnyOrder(first.getId().intValue(), second.getId().intValue());
        assertThat(selectedCategoryIds()).isEmpty();
    }

    @TestCaseId("TC-UI-RVW-017")
    @Story("Системна роль resource-tracker читає каталог для списку")
    @Test
    public void systemResourceTrackerCanPickResource() {
        Response catalogue = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RESOURCE_GET_PAGE,
                UserRole.RESOURCE_VIEWER,
                Map.of("name", first.getName(), "isActive", true), 25);
        assertThat(catalogue.statusCode())
                .as("resource-tracker читає каталог ресурсів")
                .isEqualTo(200);
        new ResourceRelocationViewerPage(page).open();
        Locator listFilter = page.getByText("Список ресурсів",
                new Page.GetByTextOptions().setExact(true)).locator("..");
        listFilter.getByRole(AriaRole.BUTTON).last().click();
        page.getByTestId("resource-list-create").click();
        page.getByTestId("resource-picker-name-filter").fill(first.getName());
        page.getByTestId("resource-picker-row-" + first.getId()).waitFor();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> selectedResourceIds() {
        return (List<Integer>) page.evaluate("() => JSON.parse(localStorage.getItem('resourceRelocationFilters')).selectedResources.map(r => r.id)");
    }

    private void waitForSelectedResources(Long... ids) {
        String expected = Arrays.stream(ids).sorted().map(String::valueOf)
                .collect(Collectors.joining(","));
        page.waitForFunction("() => { const f = JSON.parse(localStorage.getItem('resourceRelocationFilters') || 'null');"
                + " return f && f.selectedResources.map(r => r.id).sort((a,b) => a-b).join(',') === '"
                + expected + "'; }");
    }

    @SuppressWarnings("unchecked")
    private List<Integer> selectedCategoryIds() {
        return (List<Integer>) page.evaluate("() => JSON.parse(localStorage.getItem('resourceRelocationFilters')).selectedCategories");
    }
}
