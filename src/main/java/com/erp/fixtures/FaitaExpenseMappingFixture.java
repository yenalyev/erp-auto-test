package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.enums.UserRole;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseHelper;
import io.qameta.allure.Step;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;

/** Owned aliases only; no imported FAITA events, shared mappings or stock are modified. */
public class FaitaExpenseMappingFixture implements AutoCloseable {
    public enum Kind {
        AMMO("resource_alias_with_ammo", "ammunition_name", "БЧ"),
        INITIATORS("resource_alias_with_initiators", "initiator_name", "Ініціатори");
        public final String table, sourceName, tab;
        Kind(String table, String sourceName, String tab) {
            this.table = table; this.sourceName = sourceName; this.tab = tab;
        }
    }
    public record Row(long id, String name, Long resourceId, String resourceName, BigDecimal multiplier) {}
    private final DatabaseHelper database = new DatabaseHelper();
    private final Connection connection = database.getConnection();
    private final Map<Kind, List<Row>> owned = new EnumMap<>(Kind.class);
    private final List<ResourceResponse> resources = new ArrayList<>();
    private record LifecycleSource(Kind kind, long sourceId, long ownedResourceId) {}
    private final List<LifecycleSource> lifecycleSources = new ArrayList<>();
    private ResourceFixture resourceFixture;
    public final String marker = "fmap-" + UUID.randomUUID().toString().substring(0, 8);
    public final String aliasSearch = "Ф" + marker;
    public final String resourceSearch = "ERP" + marker;

    public Connection connection() { return connection; }

    public boolean hasUpdatedBy(Kind kind) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='faita' AND table_name=? AND column_name='updated_by'
                """)) {
            statement.setString(1, kind.table);
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1) == 1; }
        }
    }

    @Step("Підготувати власні mapped/unmapped aliases із повторами ключів сортування")
    public void prepare(TestContext context, ApiExecutor executor, Kind kind) throws Exception {
        if (!"dev".equals(System.getProperty("env"))) {
            throw new IllegalStateException("Mutable mapping fixtures are restricted to env=dev");
        }
        resourceFixture = new ResourceFixture(context, executor);
        resourceFixture.fetchSharedUnit(1);
        resourceFixture.fetchSharedResourceCategory();
        ResourceResponse a = resourceFixture.createUniqueResource(resourceSearch + "-A-");
        resources.add(a);
        ResourceResponse b = resourceFixture.createUniqueResource(resourceSearch + "-B-");
        resources.add(b);
        // Deliberately insert equal names in the reverse resource order.
        insert(kind, "А " + aliasSearch, b, "2");
        insert(kind, "А " + aliasSearch, a, "1.5");
        insert(kind, "Б " + aliasSearch, b, "1");
        insert(kind, "Я " + aliasSearch, null, "3");
        insert(kind, "В " + aliasSearch, a, "0.25");
    }

    private void insert(Kind kind, String name, ResourceResponse resource, String multiplier) throws Exception {
        boolean audit = hasUpdatedBy(kind);
        String columns = "name, resource_id"
                + (kind == Kind.AMMO ? ", multiplier" : "") + (audit ? ", updated_by" : "");
        String values = "?, ?" + (kind == Kind.AMMO ? ", ?" : "") + (audit ? ", ?" : "");
        try (var statement = connection.prepareStatement("INSERT INTO faita." + kind.table
                + " (" + columns + ") VALUES (" + values + ") RETURNING id")) {
            statement.setString(1, name);
            if (resource == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, resource.getId());
            int index = 3;
            if (kind == Kind.AMMO) statement.setBigDecimal(index++, new BigDecimal(multiplier));
            if (audit) statement.setString(index, "system");
            try (var result = statement.executeQuery()) {
                result.next();
                owned.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(new Row(result.getLong(1), name,
                        resource == null ? null : resource.getId(), resource == null ? "" : resource.getName(),
                        kind == Kind.AMMO ? new BigDecimal(multiplier) : null));
            }
        }
    }

    public List<Row> rows(Kind kind) { return List.copyOf(owned.getOrDefault(kind, List.of())); }

    /** Reserve an unaliased real source; cleanup can match only this source plus an owned resource. */
    public boolean reserveUnmappedSource(Kind kind, long sourceId, String name, long resourceId) throws Exception {
        if (!"dev".equals(System.getProperty("env"))) throw new IllegalStateException("Lifecycle fixtures require dev");
        if (resources.stream().noneMatch(r -> r.getId() == resourceId))
            throw new IllegalArgumentException("Lifecycle mapping must use an owned resource");
        String sourceColumn = kind == Kind.AMMO ? "ammunition_id" : "initiator_id";
        // The create facade can take over a same-name name-only alias. Never let a test modify one.
        String takeover = kind == Kind.AMMO
                ? " OR (ammunition_id IS NULL AND lower(btrim(name))=lower(btrim(?)))" : "";
        try (var statement = connection.prepareStatement("SELECT count(*) FROM faita." + kind.table
                + " WHERE " + sourceColumn + "=?" + takeover)) {
            statement.setLong(1, sourceId);
            if (kind == Kind.AMMO) statement.setString(2, name);
            try (var result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) != 0) return false;
            }
        }
        lifecycleSources.add(new LifecycleSource(kind, sourceId, resourceId));
        return true;
    }

    @Step("Видалити лише aliases поточного fixture та деактивувати його ERP-ресурси")
    @Override public void close() throws Exception {
        Exception failure = null;
        for (LifecycleSource source : lifecycleSources) {
            String sourceColumn = source.kind() == Kind.AMMO ? "ammunition_id" : "initiator_id";
            try (var statement = connection.prepareStatement("DELETE FROM faita." + source.kind().table
                    + " WHERE " + sourceColumn + "=? AND resource_id=?")) {
                statement.setLong(1, source.sourceId()); statement.setLong(2, source.ownedResourceId());
                statement.executeUpdate();
                try (var check = connection.prepareStatement("SELECT count(*) FROM faita." + source.kind().table
                        + " WHERE " + sourceColumn + "=? AND resource_id=?")) {
                    check.setLong(1, source.sourceId()); check.setLong(2, source.ownedResourceId());
                    try (var result = check.executeQuery()) {
                        result.next();
                        if (result.getLong(1) != 0) throw new IllegalStateException("Owned lifecycle alias survived cleanup");
                    }
                }
            } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        }
        for (var entry : owned.entrySet()) {
            for (Row row : entry.getValue()) {
                try (var statement = connection.prepareStatement("DELETE FROM faita." + entry.getKey().table
                        + " WHERE id=? AND name LIKE ?")) {
                    statement.setLong(1, row.id()); statement.setString(2, "%" + marker + "%");
                    statement.executeUpdate();
                } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
            }
        }
        // Keep resources active if aliases could not be cleaned up, so failed cleanup remains recoverable.
        if (failure == null && resourceFixture != null) {
            for (ResourceResponse resource : resources) {
                try {
                    int status = resourceFixture.deactivate(UserRole.ADMIN, resource.getId()).statusCode();
                    if (status < 200 || status >= 300) throw new IllegalStateException("Resource cleanup HTTP " + status);
                } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
            }
        }
        database.closeConnection();
        if (failure != null) throw failure;
    }
}
