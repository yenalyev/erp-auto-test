package com.erp.fixtures;

import com.erp.api.endpoints.ApiEndpointDefinition;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ownership belongs to one SessionClient (one suite), never to a name or a static JVM cache. */
@Slf4j
public final class TestArtifactRegistry {
    public enum Kind { STORAGE, REGION, ORDER }

    private final Set<Long> storages = ConcurrentHashMap.newKeySet();
    private final Set<Long> regions = ConcurrentHashMap.newKeySet();
    private final Set<Long> orders = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingStorages = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingRegions = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingOrders = ConcurrentHashMap.newKeySet();

    /** Only successful create responses confer ownership; failed requests and GETs never do. */
    public void observeCreation(ApiEndpointDefinition endpoint, Response response) {
        Kind kind = switch (endpoint) {
            case STORAGE_POST_CREATE -> Kind.STORAGE;
            case STORAGE_REGION_POST_CREATE -> Kind.REGION;
            case ORDER_POST_CREATE -> Kind.ORDER;
            default -> null;
        };
        if (kind == null || response.statusCode() < 200 || response.statusCode() >= 300) return;
        try {
            Long id = response.jsonPath().getLong("id");
            if (id != null && id > 0) {
                owned(kind).add(id);
                pending(kind).add(id);
            } else {
                log.warn("Cleanup cannot track {}: successful create returned no positive id", endpoint);
            }
        } catch (RuntimeException e) {
            // Leave the original response available for the test's contract assertions.
            log.warn("Cleanup cannot track {}: successful create returned an unreadable id", endpoint);
        }
    }

    public boolean owns(Kind kind, Long id) {
        return id != null && owned(kind).contains(id);
    }

    public void observeMutation(ApiEndpointDefinition endpoint, Response response, Object... pathParams) {
        if (pathParams == null || pathParams.length != 1) return;
        Kind kind = switch (endpoint) {
            case STORAGE_DELETE_DEACTIVATE, STORAGE_PUT_UNARCHIVE -> Kind.STORAGE;
            case STORAGE_REGION_DELETE -> Kind.REGION;
            default -> null;
        };
        if (kind == null) return;
        Long id;
        try { id = Long.valueOf(String.valueOf(pathParams[0])); }
        catch (NumberFormatException e) { return; }
        if (!owns(kind, id)) return;
        int status = response.statusCode();
        if (endpoint == ApiEndpointDefinition.STORAGE_PUT_UNARCHIVE) {
            if (status >= 200 && status < 300) pending(kind).add(id);
        } else if ((status >= 200 && status < 300) || status == 404) {
            cleaned(kind, id);
        }
    }

    public List<Long> pendingIds(Kind kind) {
        // Children tend to be created later; try them before their parents.
        return pending(kind).stream().sorted(java.util.Comparator.reverseOrder()).toList();
    }

    public boolean isPending(Kind kind, Long id) {
        return id != null && pending(kind).contains(id);
    }

    public void cleaned(Kind kind, Long id) {
        pending(kind).remove(id);
    }

    private Set<Long> owned(Kind kind) {
        return switch (kind) { case STORAGE -> storages; case REGION -> regions; case ORDER -> orders; };
    }
    private Set<Long> pending(Kind kind) {
        return switch (kind) { case STORAGE -> pendingStorages; case REGION -> pendingRegions; case ORDER -> pendingOrders; };
    }
}
