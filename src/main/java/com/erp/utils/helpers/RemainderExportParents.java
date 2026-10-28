package com.erp.utils.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only configuration oracle. Never edits shared business filters or application settings. */
public final class RemainderExportParents {
    private RemainderExportParents() { }

    public static Map<String, Long> resolve(Connection connection) throws Exception {
        Map<Long, Long> parents = new LinkedHashMap<>();
        Set<Long> candidates = new LinkedHashSet<>();
        try (var statement = connection.prepareStatement("SELECT id, parent_id, is_active AS active, kind FROM storage");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong("id");
                long parent = rows.getLong("parent_id");
                parents.put(id, rows.wasNull() ? null : parent);
                if (rows.getBoolean("active") && "LOCATION".equals(rows.getString("kind"))) candidates.add(id);
            }
        }
        var pm = filter(connection, "ПМ 414", parents);
        var sbs = filter(connection, "СБС без ПМ 414", parents);
        Set<Long> cukRoots = new LinkedHashSet<>();
        try (var statement = connection.prepareStatement(
                "SELECT value::text FROM app_config WHERE name = 'cukrarnia_storage' AND username IS NULL");
             var rows = statement.executeQuery()) {
            assertThat(rows.next()).as("Налаштована Цукрарня").isTrue();
            for (var property : new ObjectMapper().readTree(rows.getString(1))) {
                for (var value : property.path("values")) cukRoots.add(Long.parseLong(value.asText().trim()));
            }
        }
        var cuk = expand(cukRoots, parents);
        assertThat(cuk).as("Непорожня область Цукрарні").isNotEmpty();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("ПМ без Цукрарня", pick(candidates, pm, cuk, Set.of(), "ПМ"));
        result.put("СБС без ПМ", pick(candidates, sbs, cuk, pm, "СБС"));
        result.put("Цукрарня", pick(candidates, cuk, Set.of(), Set.of(), "Цукрарня"));
        Set<Long> others = new LinkedHashSet<>(parents.keySet());
        others.removeAll(pm);
        others.removeAll(sbs);
        others.removeAll(cuk);
        result.put("Інші", pick(candidates, others, Set.of(), Set.of(), "Інші"));
        return result;
    }

    private static Set<Long> filter(Connection connection, String name, Map<Long, Long> parents) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT include_ids, exclude_ids, usage_type FROM business_unit_filter WHERE name = ?")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("Налаштований бізнес-фільтр %s", name).isTrue();
                assertThat(rows.getString("usage_type")).as("Фільтр охоплює нащадків нової локації")
                        .isEqualTo("CHILDREN");
                String include = rows.getString("include_ids");
                Set<Long> included = "all".equalsIgnoreCase(include.trim())
                        ? new LinkedHashSet<>(parents.keySet()) : expand(ids(include), parents);
                included.removeAll(expand(ids(rows.getString("exclude_ids")), parents));
                return included;
            }
        }
    }

    private static Set<Long> ids(String text) {
        Set<Long> ids = new LinkedHashSet<>();
        if (text != null && !text.isBlank()) for (String id : text.split(",")) ids.add(Long.parseLong(id.trim()));
        return ids;
    }

    private static Set<Long> expand(Set<Long> roots, Map<Long, Long> parents) {
        Set<Long> expanded = new LinkedHashSet<>(roots);
        boolean changed;
        do {
            changed = false;
            for (var entry : parents.entrySet()) {
                if (entry.getValue() != null && expanded.contains(entry.getValue())) changed |= expanded.add(entry.getKey());
            }
        } while (changed);
        return expanded;
    }

    private static long pick(Set<Long> candidates, Set<Long> included, Set<Long> excludedA,
                             Set<Long> excludedB, String group) {
        return candidates.stream().filter(included::contains).filter(id -> !excludedA.contains(id))
                .filter(id -> !excludedB.contains(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Немає активного parent LOCATION для " + group));
    }
}
