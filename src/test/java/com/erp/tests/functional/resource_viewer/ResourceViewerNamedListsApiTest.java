package com.erp.tests.functional.resource_viewer;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.ResourceFixture;
import com.erp.fixtures.ResourceTrackerActorScope;
import com.erp.models.response.ResourceResponse;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Resource Viewer")
@Feature("Іменовані списки ресурсів відстеження")
public class ResourceViewerNamedListsApiTest extends BaseFunctionalTest {

    private final List<Long> listIds = new ArrayList<>();
    private ResourceFixture resourceFixture;
    private ResourceResponse first;
    private ResourceResponse second;
    private ResourceTrackerActorScope trackerActorScope;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareResources() {
        resourceFixture = new ResourceFixture(testContext, apiExecutor);
        resourceFixture.prepareContext();
        first = resourceFixture.createUniqueResource("RVW-LIST-A-");
        second = resourceFixture.createUniqueResource("RVW-LIST-B-");
        trackerActorScope = new ResourceTrackerActorScope(
                testContext, apiExecutor, getPlaywrightSessionProvider());
        trackerActorScope.acquire();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        try {
            for (Long id : listIds) {
                Response response = apiExecutor.execute(
                        ApiEndpointDefinition.RESOURCE_VIEWER_LIST_DELETE,
                        UserRole.ADMIN, null, id);
                assertThat(response.statusCode()).isBetween(200, 299);
            }
            if (resourceFixture != null) {
                if (first != null) resourceFixture.deactivate(UserRole.ADMIN, first.getId());
                if (second != null) resourceFixture.deactivate(UserRole.ADMIN, second.getId());
            }
        } finally {
            if (trackerActorScope != null) trackerActorScope.release();
        }
    }

    @TestCaseId("TC-RVW-LIST-API-001")
    @Story("Створення, читання, оновлення та видалення спільного списку")
    @Test
    public void viewerCanManageSharedList() {
        String name = "RVW-LIST-" + System.currentTimeMillis();
        Response created = create(name, List.of(first.getId(), second.getId()));
        assertThat(created.statusCode()).isEqualTo(200);
        Long id = created.jsonPath().getLong("id");
        listIds.add(id);
        assertList(created, id, name, List.of(first.getId(), second.getId()));

        Response namesAsAdmin = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_NAMES_GET, UserRole.ADMIN);
        assertThat(namesAsAdmin.statusCode()).isEqualTo(200);
        assertThat(namesAsAdmin.jsonPath().getList("id", Long.class)).contains(id);

        Response loaded = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_GET,
                UserRole.RESOURCE_VIEWER, null, id);
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertList(loaded, id, name, List.of(first.getId(), second.getId()));

        String renamed = name + "-UPDATED";
        Response updated = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_UPDATE,
                UserRole.RESOURCE_VIEWER,
                Map.of("name", renamed, "resourceIds", List.of(second.getId())), id);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertList(updated, id, renamed, List.of(second.getId()));
        Response afterUpdate = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_GET, UserRole.ADMIN, null, id);
        assertList(afterUpdate, id, renamed, List.of(second.getId()));

        Response deleted = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_DELETE,
                UserRole.RESOURCE_VIEWER, null, id);
        assertThat(deleted.statusCode()).isBetween(200, 299);
        listIds.remove(id);
        Response namesAfterDelete = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_NAMES_GET, UserRole.ADMIN);
        assertThat(namesAfterDelete.jsonPath().getList("id", Long.class)).doesNotContain(id);
    }

    @TestCaseId("TC-RVW-LIST-API-002")
    @Story("Не можна створити порожній список або повторити назву")
    @Test
    public void validationRejectsInvalidListsWithoutChangingSavedList() {
        String name = "RVW-LIST-VALID-" + System.currentTimeMillis();
        Response created = create(name, List.of(first.getId()));
        assertThat(created.statusCode()).isEqualTo(200);
        Long id = created.jsonPath().getLong("id");
        listIds.add(id);

        assertThat(create("  ", List.of(first.getId())).statusCode()).isEqualTo(400);
        assertThat(create(name, List.of(second.getId())).statusCode()).isEqualTo(400);
        assertThat(create(name + "-EMPTY", List.of()).statusCode()).isEqualTo(400);
        assertThat(create(name + "-MISSING", List.of(Long.MAX_VALUE)).statusCode()).isEqualTo(400);

        Response unchanged = apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_GET,
                UserRole.RESOURCE_VIEWER, null, id);
        assertList(unchanged, id, name, List.of(first.getId()));
    }

    private Response create(String name, List<Long> resourceIds) {
        return apiExecutor.execute(
                ApiEndpointDefinition.RESOURCE_VIEWER_LIST_CREATE,
                UserRole.RESOURCE_VIEWER,
                Map.of("name", name, "resourceIds", resourceIds));
    }

    private void assertList(Response response, Long id, String name, List<Long> resourceIds) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getLong("id")).isEqualTo(id);
        assertThat(response.jsonPath().getString("name")).isEqualTo(name);
        assertThat(response.jsonPath().getList("resources.id", Long.class))
                .containsExactlyInAnyOrderElementsOf(resourceIds);
    }
}
