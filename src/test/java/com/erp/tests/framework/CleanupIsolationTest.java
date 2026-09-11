package com.erp.tests.framework;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.clients.SessionClient;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.StorageRegionFixture;
import com.erp.fixtures.OrderFixture;
import com.erp.fixtures.TestArtifactRegistry;
import com.erp.models.response.StorageRegionResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.StorageLocationLinkResponse;
import com.erp.test_context.GlobalTestContext;
import io.restassured.builder.ResponseBuilder;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.erp.api.endpoints.ApiEndpointDefinition.*;
import static com.erp.fixtures.TestArtifactRegistry.Kind.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses the real executor registration and fixture cleanup paths with an in-memory HTTP transport. */
public class CleanupIsolationTest {
    @Test
    public void onlySuccessfulCreatesConferOwnershipAcrossExecutors() {
        MemoryClient client = new MemoryClient();
        ApiExecutor first = executor(client);
        ApiExecutor sameSuite = executor(client);
        ApiExecutor otherSuite = executor(new MemoryClient());
        first.execute(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of("name", "same-loc_1234567890123_T1"));
        assertThat(sameSuite.getArtifactRegistry().owns(STORAGE, 71L)).isTrue();
        assertThat(otherSuite.getArtifactRegistry().owns(STORAGE, 71L)).isFalse();
        client.next = response(500, "{\"id\":72}");
        first.execute(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of());
        client.next = response(200, "{\"id\":73}");
        first.execute(STORAGE_GET_BY_ID, UserRole.ADMIN, "73");
        assertThat(first.getArtifactRegistry().pendingIds(STORAGE)).containsExactly(71L);
        assertThat(first.getArtifactRegistry().owns(REGION, 71L)).isFalse();
    }

    @Test
    public void cleanupCannotClaimAnExistingStorageOrTouchItsStock() {
        MemoryClient client = new MemoryClient();
        StorageFixture fixture = new StorageFixture(new GlobalTestContext(), executor(client));
        fixture.trackForCleanup(999L);
        fixture.deactivateTrackedStorages(UserRole.ADMIN);
        assertThat(fixture.archiveStorage(UserRole.ADMIN, 999L)).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    public void regionCleanupRetainsFailuresAndDoesNotNeedCatalogSearch() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.execute(STORAGE_REGION_POST_CREATE, UserRole.ADMIN, Map.of());
        StorageRegionFixture fixture = new StorageRegionFixture(new GlobalTestContext(), api);
        fixture.trackForCleanup(71L);
        client.next = response(500, "{}");
        fixture.deleteTrackedRegions(UserRole.ADMIN);
        assertThat(api.getArtifactRegistry().pendingIds(REGION)).containsExactly(71L);
        client.next = response(200, "{}");
        fixture.deleteTrackedRegions(UserRole.ADMIN);
        assertThat(api.getArtifactRegistry().pendingIds(REGION)).isEmpty();

        // Even an untracked direct API create is available to the final suite sweep.
        client.next = response(200, "{\"id\":72}");
        api.execute(STORAGE_REGION_POST_CREATE, UserRole.ADMIN, Map.of());
        client.next = response(404, "{}");
        assertThat(fixture.purgeAutotestNamedRegions(UserRole.ADMIN)).isEqualTo(1);
        assertThat(api.getArtifactRegistry().pendingIds(REGION)).isEmpty();
        assertThat(client.calls).noneMatch(call -> call.startsWith("GET"));
    }

    @Test
    public void prefixPurgeCannotDeleteOtherRunsIdenticallyNamedRegions() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.execute(STORAGE_REGION_POST_CREATE, UserRole.ADMIN, Map.of());
        StorageRegionResponse own = response(200, "{\"id\":71,\"name\":\"test-loc_same\"}")
                .as(StorageRegionResponse.class);
        StorageRegionResponse foreign = response(200, "{\"id\":72,\"name\":\"test-loc_same\"}")
                .as(StorageRegionResponse.class);
        StorageRegionFixture fixture = new StorageRegionFixture(new GlobalTestContext(), api) {
            @Override public List<StorageRegionResponse> findRegions(UserRole role, String filter) {
                return api.getArtifactRegistry().pendingIds(REGION).isEmpty() ? List.of(foreign) : List.of(own, foreign);
            }
        };
        fixture.purgeRegionsByNamePrefixes(UserRole.ADMIN, "test-");
        assertThat(client.calls.stream().filter(call -> call.startsWith("DELETE"))).hasSize(1)
                .allMatch(call -> call.endsWith("/71"));
    }

    @Test
    public void rawDeleteAndUnarchiveUpdatePendingCleanupWithoutLosingOwnership() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.execute(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of());
        api.execute(STORAGE_DELETE_DEACTIVATE, UserRole.ADMIN, "71");
        assertThat(api.getArtifactRegistry().pendingIds(STORAGE)).isEmpty();
        assertThat(api.getArtifactRegistry().owns(STORAGE, 71L)).isTrue();
        int callsBeforeCleanup = client.calls.size();
        assertThat(new StorageFixture(new GlobalTestContext(), api).archiveStorage(UserRole.ADMIN, 71L)).isTrue();
        assertThat(client.calls).hasSize(callsBeforeCleanup); // already archived; do not clear stock again
        api.execute(STORAGE_PUT_UNARCHIVE, UserRole.ADMIN, "71");
        assertThat(api.getArtifactRegistry().pendingIds(STORAGE)).containsExactly(71L);
    }

    @Test
    public void visibilityPurgeLeavesForeignMembershipsAndGrantsUntouched() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.execute(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of());
        api.execute(STORAGE_REGION_POST_CREATE, UserRole.ADMIN, Map.of());
        List<Long> removedRegions = new ArrayList<>();
        List<Long> revokedStorages = new ArrayList<>();
        List<Long> probedStorages = new ArrayList<>();
        StorageFixture storages = new StorageFixture(new GlobalTestContext(), api) {
            @Override public List<StorageResponse> getNames(UserRole role, Boolean active, String name) {
                return List.of(response(200, "{\"id\":71,\"name\":\"same-loc_name\"}").as(StorageResponse.class),
                        response(200, "{\"id\":72,\"name\":\"same-loc_name\"}").as(StorageResponse.class));
            }
        };
        StorageRegionFixture regions = new StorageRegionFixture(new GlobalTestContext(), api) {
            @Override public List<StorageLocationLinkResponse> getStorageLocationLinks(UserRole role, Long id) {
                probedStorages.add(id);
                if (id == 999L) return List.of(
                        response(200, "{\"regionId\":71}").as(StorageLocationLinkResponse.class),
                        response(200, "{\"regionId\":72}").as(StorageLocationLinkResponse.class));
                return List.of(response(200, "{\"locationId\":999}").as(StorageLocationLinkResponse.class));
            }
            @Override public StorageRegionResponse removeRegionMembers(Long id, Long... members) {
                removedRegions.add(id);
                return null;
            }
            @Override public Response removeExplicitLocations(Long id, Long... viewers) {
                revokedStorages.add(id);
                return response(200, "{}");
            }
        };
        regions.purgeViewerVisibilityScope(UserRole.ADMIN, 999L, storages);
        assertThat(removedRegions).containsExactly(71L);
        assertThat(revokedStorages).containsExactly(71L);
        assertThat(probedStorages).containsExactly(999L, 71L);
    }

    @Test
    public void storageSweepUsesRegisteredIdsAndRetriesFailures() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.execute(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of());
        List<Long> attempted = new ArrayList<>();
        StorageFixture fixture = new StorageFixture(new GlobalTestContext(), api) {
            @Override public boolean archiveStorage(UserRole role, Long id) {
                attempted.add(id);
                if (attempted.size() == 1) return false;
                api.getArtifactRegistry().cleaned(STORAGE, id);
                return true;
            }
        };
        assertThat(fixture.deactivateAutotestStorages(UserRole.ADMIN)).isZero();
        assertThat(fixture.deactivateAutotestStorages(UserRole.ADMIN)).isEqualTo(1);
        assertThat(fixture.deactivateAutotestStorages(UserRole.ADMIN)).isZero();
        assertThat(attempted).containsExactly(71L, 71L);
        assertThat(client.calls).hasSize(1); // creation only; no global catalog scan
    }

    @Test
    public void concurrentRegistrationsAreRetainedAndMalformedCreatesCannotClaimOwnership() {
        TestArtifactRegistry registry = new TestArtifactRegistry();
        java.util.stream.LongStream.rangeClosed(1, 100).parallel().forEach(id ->
                registry.observeCreation(STORAGE_POST_CREATE, response(200, "{\"id\":" + id + "}")));
        registry.observeCreation(STORAGE_POST_CREATE, response(200, "<html>login</html>"));
        registry.observeCreation(STORAGE_POST_CREATE, response(200, "{}"));
        assertThat(registry.pendingIds(STORAGE)).hasSize(100);
    }

    @Test
    public void holdCleanupSkipsForeignOrdersAndFindsOwnedOrdersBeyondFirstPage() {
        List<Integer> pages = new ArrayList<>();
        MemoryClient client = new MemoryClient() {
            @Override public Response executeWithCookies(Method method, String path, Object body,
                                                         Map<String, String> cookies, Map<String, ?> query) {
                calls.add(method + " " + path);
                int page = ((Number) query.get("page")).intValue();
                pages.add(page);
                String content = page == 0 ? java.util.stream.LongStream.range(1000, 1100)
                        .mapToObj(id -> "{\"id\":" + id + "}").collect(java.util.stream.Collectors.joining(","))
                        : "{\"id\":71},{\"id\":72}";
                return response(200, "{\"content\":[" + content + "]}");
            }
            @Override public Response executeWithCookies(Method method, String path, Object body, Map<String, String> cookies) {
                calls.add(method + " " + path);
                return method == Method.GET ? response(200, "[]") : next;
            }
        };
        ApiExecutor api = executor(client);
        api.execute(ORDER_POST_CREATE, UserRole.ADMIN, Map.of());
        new OrderFixture(new GlobalTestContext(), api).clearInProgressOrders(UserRole.ADMIN, 999L);
        assertThat(pages).containsExactly(0, 1);
        assertThat(client.calls.stream().filter(call -> call.startsWith("PUT")))
                .containsExactly("PUT " + ORDER_PUT_CANCEL.getPath(71L, 999L));
        assertThat(client.calls).contains("GET " + ORDER_GET_BOOKINGS.getPath(71L))
                .doesNotContain("GET " + ORDER_GET_BOOKINGS.getPath(72L));
    }

    @Test
    public void queryParameterExecutionAlsoTracksSuccessfulCreates() {
        MemoryClient client = new MemoryClient();
        ApiExecutor api = executor(client);
        api.executeWithQueryParams(STORAGE_POST_CREATE, UserRole.ADMIN, Map.of());
        assertThat(api.getArtifactRegistry().owns(STORAGE, 71L)).isTrue();
    }

    private static ApiExecutor executor(MemoryClient client) {
        return new ApiExecutor(client, null) {
            @Override protected Map<String, String> getSessionForRole(UserRole role) { return Map.of(); }
        };
    }

    private static Response response(int status, String body) {
        return new ResponseBuilder().setStatusCode(status).setContentType("application/json").setBody(body).build();
    }

    private static class MemoryClient extends SessionClient {
        Response next = response(200, "{\"id\":71}");
        final List<String> calls = new ArrayList<>();
        @Override public Response executeWithCookies(Method method, String path, Object body, Map<String, String> cookies) {
            calls.add(method + " " + path);
            return next;
        }
        @Override public Response executeWithCookies(Method method, String path, Object body,
                                                     Map<String, String> cookies, Map<String, ?> query) {
            return executeWithCookies(method, path, body, cookies);
        }
    }
}
