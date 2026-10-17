package com.erp.tests.functional.storage;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaExpenseMappingFixture;
import com.erp.fixtures.FaitaExpenseMappingFixture.Kind;
import com.erp.fixtures.FaitaExpenseMappingFixture.Row;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Integration")
@Feature("Словник мапінгу Файта — Цукерка для витрат")
public class FaitaExpenseMappingApiTest extends BaseFunctionalTest {
    private FaitaExpenseMappingFixture fixture;
    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareAliases() throws Exception {
        // Verify real deployed access before creating any aliases.
        for (Kind kind : Kind.values()) {
            var response = apiExecutor.executeWithQueryParams(endpoint(kind), UserRole.ADMIN, Map.of("size", 1));
            assertThat(response.statusCode()).as("%s mapping API", kind).isEqualTo(200);
        }
        fixture = new FaitaExpenseMappingFixture();
        for (Kind kind : Kind.values()) fixture.prepare(testContext, apiExecutor, kind);
    }
    @AfterClass(alwaysRun = true)
    public void cleanupAliases() throws Exception { if (fixture != null) fixture.close(); }
    private ApiEndpointDefinition endpoint(Kind kind) {
        return kind == Kind.AMMO ? ApiEndpointDefinition.FAITA_MAPPING_AMMO_GET
                : ApiEndpointDefinition.FAITA_MAPPING_INITIATORS_GET;
    }
    private List<Map<String, Object>> list(Kind kind, Map<String, ?> filters) {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("size", 100, "faitaName", fixture.marker));
        params.putAll(filters);
        var response = apiExecutor.executeWithQueryParams(endpoint(kind), UserRole.ADMIN, params);
        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> rows = response.jsonPath().getList("content");
        assertThat(rows).isNotNull();
        assertThat(response.jsonPath().getLong("page.totalElements")).isEqualTo(rows.size());
        return rows;
    }
    private long id(Map<String, Object> row) { return ((Number) row.get("id")).longValue(); }
    private void expectIds(List<Map<String, Object>> actual, List<Row> expected) {
        assertThat(actual.stream().map(this::id).toList()).containsExactlyElementsOf(expected.stream().map(Row::id).toList());
    }
    private List<Row> sorted(Kind kind, boolean resource, boolean desc, boolean mappedOnly) {
        Comparator<Row> comparator = resource ? Comparator.comparing(Row::resourceName).thenComparing(Row::name)
                : Comparator.comparing(Row::name).thenComparing(Row::resourceName);
        if (desc) comparator = comparator.reversed();
        return fixture.rows(kind).stream().filter(r -> !mappedOnly || r.resourceId() != null).sorted(comparator).toList();
    }
    private void values(Kind kind) {
        var rows = list(kind, Map.of());
        expectIds(rows, sorted(kind, false, false, false));
        for (Row expected : fixture.rows(kind)) {
            var actual = rows.stream().filter(r -> id(r) == expected.id()).findFirst().orElseThrow();
            assertThat(actual.get("name")).isEqualTo(expected.name());
            if (expected.resourceId() == null) assertThat(actual.get("resource")).isNull();
            else {
                @SuppressWarnings("unchecked") var resource = (Map<String, Object>) actual.get("resource");
                assertThat(((Number)resource.get("id")).longValue()).isEqualTo(expected.resourceId());
                assertThat(resource.get("name")).isEqualTo(expected.resourceName());
            }
            if (kind == Kind.AMMO) assertThat(new BigDecimal(actual.get("multiplier").toString()))
                    .isEqualByComparingTo(expected.multiplier());
        }
    }
    private void filters(Kind kind) {
        Row selected = fixture.rows(kind).get(1);
        expectIds(list(kind, Map.of("faitaName", "Б " + fixture.aliasSearch)),
                fixture.rows(kind).stream().filter(r -> r.name().startsWith("Б ")).toList());
        expectIds(list(kind, Map.of("faitaName", "", "resourceName", selected.resourceName())),
                sorted(kind, false, false, true).stream().filter(r -> r.resourceId().equals(selected.resourceId())).toList());
        assertThat(list(kind, Map.of("faitaName", fixture.marker + "-missing"))).isEmpty();
        assertThat(list(kind, Map.of("faitaName", "Б " + fixture.aliasSearch, "resourceName", selected.resourceName()))).isEmpty();
        expectIds(list(kind, Map.of()), sorted(kind, false, false, false));
    }
    private void sorting(Kind kind) {
        for (boolean byResource : List.of(false, true)) {
            for (boolean descending : List.of(false, true)) {
                String sort = (byResource ? "resourceName" : "name") + (descending ? ",DESC" : ",ASC");
                expectIds(list(kind, Map.of("resourceName", fixture.resourceSearch, "sort", sort)),
                        sorted(kind, byResource, descending, true));
            }
        }
    }
    private String snapshot(Kind kind, long id) throws Exception {
        try (var statement = fixture.connection().prepareStatement("SELECT to_jsonb(t)::text FROM faita." + kind.table + " t WHERE id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); return rows.getString(1); }
        }
    }
    private void update(Kind kind) throws Exception {
        Row target = fixture.rows(kind).get(0), sibling = fixture.rows(kind).get(2), replacement = fixture.rows(kind).get(1);
        String before = snapshot(kind, sibling.id());
        Map<String, Object> body = new LinkedHashMap<>(Map.of("resourceId", replacement.resourceId()));
        // FaitaAliasDialog forces true for a name-only alias (no ammunitionId).
        if (kind == Kind.AMMO) { body.put("multiplier", new BigDecimal("7.5")); body.put("matchByName", true); }
        try {
            var response = apiExecutor.execute(kind == Kind.AMMO ? ApiEndpointDefinition.FAITA_MAPPING_AMMO_UPDATE
                    : ApiEndpointDefinition.FAITA_MAPPING_INITIATORS_UPDATE, UserRole.ADMIN, body, target.id());
            assertThat(response.statusCode()).isEqualTo(200);
            var persisted = list(kind, Map.of()).stream().filter(r -> id(r) == target.id()).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var resource = (Map<String, Object>) persisted.get("resource");
            assertThat(((Number)resource.get("id")).longValue()).isEqualTo(replacement.resourceId());
            assertThat(persisted.get("name")).isEqualTo(target.name());
            if (kind == Kind.AMMO) {
                assertThat(new BigDecimal(persisted.get("multiplier").toString())).isEqualByComparingTo("7.5");
                assertThat(persisted.get("matchByName")).as("Submitted matchByName is persisted").isEqualTo(true);
            }
            try (var statement = fixture.connection().prepareStatement("SELECT updated_by FROM faita." + kind.table + " WHERE id=?")) {
                statement.setLong(1, target.id());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(UserRole.ADMIN.getUsername());
                }
            }
            assertThat(snapshot(kind, sibling.id())).as("Neighbor mapping remains unchanged").isEqualTo(before);
        } finally {
            // Restore fixture values even if an assertion failed, so following read tests are independent.
            try (var statement = fixture.connection().prepareStatement("UPDATE faita." + kind.table
                    + " SET resource_id=?" + (kind == Kind.AMMO ? ", multiplier=?, match_by_name=false" : "") + " WHERE id=? AND name LIKE ?")) {
                int i=1; statement.setLong(i++, target.resourceId());
                if (kind == Kind.AMMO) statement.setBigDecimal(i++, target.multiplier());
                statement.setLong(i++, target.id()); statement.setString(i, "%" + fixture.marker + "%"); statement.executeUpdate();
            }
        }
    }
    @Test @TestCaseId("TC-FMAP-API-001") public void ammoValuesAndDefaultOrder() { values(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-API-002") public void initiatorValuesAndDefaultOrder() { values(Kind.INITIATORS); }
    @Test @TestCaseId("TC-FMAP-API-003") public void ammoFiltersAndReset() { filters(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-API-004") public void initiatorFiltersAndReset() { filters(Kind.INITIATORS); }
    @Test @TestCaseId("TC-FMAP-API-005") public void ammoBothSortKeysAndDirections() { sorting(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-API-006") public void initiatorBothSortKeysAndDirections() { sorting(Kind.INITIATORS); }
    @Test @TestCaseId("TC-FMAP-API-007")
    @Description("Payload tk-ui FaitaAliasDialog: PUT {resourceId,multiplier,matchByName}; GET verifies persistence, SQL author, sibling isolation.")
    public void ammoUpdatePersistsAuthorAndPreservesSibling() throws Exception { update(Kind.AMMO); }
    @Test @TestCaseId("TC-FMAP-API-008")
    @Description("Payload tk-ui FaitaAliasDialog: PUT {resourceId}; GET verifies persistence, SQL author, sibling isolation.")
    public void initiatorUpdatePersistsAuthorAndPreservesSibling() throws Exception { update(Kind.INITIATORS); }
}
