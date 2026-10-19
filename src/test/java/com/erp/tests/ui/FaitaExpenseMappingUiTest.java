package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.FaitaExpenseMappingFixture;
import com.erp.fixtures.FaitaExpenseMappingFixture.Kind;
import com.erp.fixtures.FaitaExpenseMappingFixture.Row;
import com.erp.pages.FaitaExpenseMappingPage;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Integration")
@Feature("Словник мапінгу Файта — Цукерка для витрат")
public class FaitaExpenseMappingUiTest extends BaseUITest {
    private FaitaExpenseMappingFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void authenticateAdmin() {
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        browserContext.addInitScript("localStorage.removeItem('sorting_faita-mapping-ammunition-mapped');"
                + "localStorage.removeItem('sorting_faita-mapping-initiators-mapped');");
    }
    @AfterMethod(alwaysRun = true)
    public void cleanupMappings() throws Exception {
        if (fixture != null) { try { fixture.close(); } finally { fixture = null; } }
    }
    private FaitaExpenseMappingPage withData(Kind kind) throws Exception {
        // A missing release feature fails in the test body, before any mutable fixtures are created.
        var dictionary = new FaitaExpenseMappingPage(page).open().tab(kind);
        fixture = new FaitaExpenseMappingFixture();
        fixture.prepare(testContext, apiExecutor, kind);
        page.reload();
        return dictionary.tab(kind).filterAlias(fixture.marker);
    }
    private List<Row> sorted(Kind kind, boolean byResource, boolean descending, boolean mappedOnly) {
        Comparator<Row> comparator = byResource
                ? Comparator.comparing(Row::resourceName).thenComparing(Row::name)
                : Comparator.comparing(Row::name).thenComparing(Row::resourceName);
        if (descending) comparator = comparator.reversed();
        return fixture.rows(kind).stream().filter(r -> !mappedOnly || r.resourceId() != null).sorted(comparator).toList();
    }
    private void columns(Kind kind, String id) throws Exception {
        var dictionary = withData(kind);
        dictionary.expectHeaders(kind);
        dictionary.expectRows(sorted(kind, false, false, false));
        if (kind == Kind.AMMO) {
            var expected = sorted(kind, false, false, false);
            for (int i = 0; i < expected.size(); i++) {
                String text = dictionary.dataRows().nth(i).locator("td").nth(2).innerText().trim().replace(',', '.');
                assertThat(new java.math.BigDecimal(text)).isEqualByComparingTo(expected.get(i).multiplier());
            }
        }
        dictionary.attachScreenshot(id + " — дані та колонки");
    }
    private void searchAlias(Kind kind, String id) throws Exception {
        var dictionary = withData(kind);
        dictionary.filterAlias("Б " + fixture.aliasSearch);
        dictionary.expectRows(fixture.rows(kind).stream().filter(r -> r.name().startsWith("Б ")).toList());
        dictionary.filterAlias(fixture.marker + "-missing").expectRows(List.of());
        dictionary.filterAlias(fixture.aliasSearch).expectRows(sorted(kind, false, false, false));
        dictionary.filterResource(fixture.resourceSearch).expectRows(sorted(kind, false, false, true));
        // Clearing the alias field retains the independent resource filter.
        dictionary.filterAlias("").expectRows(sorted(kind, false, false, true));
        dictionary.filterAlias(fixture.marker).filterResource("").expectRows(sorted(kind, false, false, false));
        dictionary.attachScreenshot(id + " — пошук назви та скидання");
    }
    private void searchResource(Kind kind, String id) throws Exception {
        var dictionary = withData(kind);
        Row selected = fixture.rows(kind).get(1);
        dictionary.filterResource(selected.resourceName());
        dictionary.expectRows(sorted(kind, false, false, true).stream()
                .filter(r -> r.resourceId().equals(selected.resourceId())).toList());
        dictionary.filterResource(fixture.resourceSearch).expectRows(sorted(kind, false, false, true));
        dictionary.filterAlias("Б " + fixture.aliasSearch).filterResource(selected.resourceName()).expectRows(List.of());
        dictionary.filterAlias(fixture.marker).filterResource(fixture.marker + "-missing").expectRows(List.of());
        dictionary.filterResource("").expectRows(sorted(kind, false, false, false));
        dictionary.attachScreenshot(id + " — пошук за ERP-ресурсом");
    }
    private void unmatched(Kind kind, String id) throws Exception {
        var dictionary = withData(kind);
        dictionary.expectRows(sorted(kind, false, false, false));
        dictionary.filterAlias("");
        String endpoint = "/api/v1/faita-mapping/"
                + (kind == Kind.AMMO ? "ammunition" : "initiators") + "/unmapped";
        var response = page.waitForResponse(r -> r.url().split("\\?")[0].endsWith(endpoint)
                && r.status() == 200, () -> dictionary.unmatched(true));
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(body.path("content").size()).as("Existing unmapped source data required").isPositive();
        String sourceName = body.path("content").get(0).path("name").asText();
        assertThat(sourceName).isNotBlank();
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                page.getByPlaceholder("Ресурс", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true)))
                .hasCount(0);
        dictionary.expectUnmatchedHeaders(kind);
        dictionary.filterAlias(sourceName);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                dictionary.dataRows().first().locator("td").first()).containsText(sourceName);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                dictionary.dataRows().first().getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                        new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Співставити").setExact(true))).isVisible();
        dictionary.filterAlias(fixture.marker + "-missing").expectRows(List.of());
        dictionary.filterAlias("");
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                dictionary.dataRows().first().locator("td").first()).containsText(sourceName);
        dictionary.attachScreenshot(id + " — Неспівставлені: пошук і скидання");
        dictionary.unmatched(false).filterAlias(fixture.marker).expectRows(sorted(kind, false, false, false));
        dictionary.expectHeaders(kind);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
                page.getByPlaceholder("Ресурс", new com.microsoft.playwright.Page.GetByPlaceholderOptions().setExact(true))).isVisible();
        dictionary.attachScreenshot(id + " — повернення до Співставлені");
    }
    private void sort(Kind kind, boolean byResource, String id) throws Exception {
        var dictionary = withData(kind);
        // Exclude NULL resource names: the requirement does not define NULLS FIRST/LAST.
        dictionary.filterResource(fixture.resourceSearch).expectRows(sorted(kind, false, false, true));
        String column = byResource ? "Ресурс" : "Назва Файти";
        if (byResource) {
            dictionary.sortAndExpect(column, sorted(kind, true, false, true));
            dictionary.sortAndExpect(column, sorted(kind, true, true, true));
        } else {
            dictionary.sortAndExpect(column, sorted(kind, false, true, true));
            dictionary.sortAndExpect(column, sorted(kind, false, false, true));
        }
        dictionary.attachScreenshot(id + " — два ключі ASC/DESC");
    }
    private void edit(Kind kind, String id) throws Exception {
        var dictionary = withData(kind);
        dictionary.expectRows(sorted(kind, false, false, false));
        dictionary.openEdit(0);
        dictionary.expectEditTarget(sorted(kind, false, false, false).getFirst(), kind);
        dictionary.attachScreenshot(id + " — форма редагування");
        page.keyboard().press("Escape");
        dictionary.expectRows(sorted(kind, false, false, false));
    }

    private record MappingTarget(long sourceId, String name, Row resource) {}
    private ApiEndpointDefinition aliasesEndpoint(Kind kind) {
        return kind == Kind.AMMO ? ApiEndpointDefinition.FAITA_MAPPING_AMMO_GET : ApiEndpointDefinition.FAITA_MAPPING_INITIATORS_GET;
    }
    private ApiEndpointDefinition unmappedEndpoint(Kind kind) {
        return kind == Kind.AMMO ? ApiEndpointDefinition.FAITA_MAPPING_AMMO_UNMAPPED_GET : ApiEndpointDefinition.FAITA_MAPPING_INITIATORS_UNMAPPED_GET;
    }
    private String sourceKey(Kind kind) { return kind == Kind.AMMO ? "ammunitionId" : "initiatorId"; }
    private String sourceColumn(Kind kind) { return kind == Kind.AMMO ? "ammunition_id" : "initiator_id"; }
    private String mappingPath(Kind kind) { return "/api/v1/faita-mapping/" + (kind == Kind.AMMO ? "ammunition" : "initiators"); }
    private List<Map<String, Object>> unmappedSources(Kind kind, String name) {
        Map<String, Object> parameters = new LinkedHashMap<>(Map.of("page", 0, "size", 100));
        if (kind == Kind.AMMO) parameters.put("byName", false);
        if (name != null) parameters.put("faitaName", name);
        var response = apiExecutor.executeWithQueryParams(unmappedEndpoint(kind), UserRole.ADMIN, parameters);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.jsonPath().getList("content");
    }
    private MappingTarget reserveTarget(Kind kind) throws Exception {
        Row resource = fixture.rows(kind).get(1);
        for (var source : unmappedSources(kind, null)) {
            if (source.get(sourceKey(kind)) instanceof Number number && number.longValue() > 0
                    && source.get("name") instanceof String name && !name.isBlank()
                    && fixture.reserveUnmappedSource(kind, number.longValue(), name, resource.resourceId()))
                return new MappingTarget(number.longValue(), name, resource);
        }
        throw new AssertionError("Need an existing unmapped FAITA ID without an alias or same-name takeover risk");
    }
    private List<Map<String, Object>> targetAliases(Kind kind, MappingTarget target) {
        var response = apiExecutor.executeWithQueryParams(aliasesEndpoint(kind), UserRole.ADMIN,
                Map.of("resourceName", target.resource().resourceName(), "page", 0, "size", 100));
        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> rows = response.jsonPath().getList("content");
        String relation = kind == Kind.AMMO ? "faitaAmmunition" : "faitaInitiator";
        return rows.stream().filter(row -> row.get(relation) instanceof Map<?, ?> source
                && source.get("id") instanceof Number number && number.longValue() == target.sourceId()).toList();
    }
    private String snapshotAlias(Kind kind, long id) throws Exception {
        try (var statement = fixture.connection().prepareStatement("SELECT to_jsonb(a)::text FROM faita." + kind.table + " a WHERE id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); return rows.getString(1); }
        }
    }
    private void expectSourceAliasCount(Kind kind, MappingTarget target, long expected) throws Exception {
        try (var statement = fixture.connection().prepareStatement("SELECT count(*) FROM faita." + kind.table
                + " WHERE " + sourceColumn(kind) + "=?")) {
            statement.setLong(1, target.sourceId());
            try (var rows = statement.executeQuery()) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(expected); }
        }
    }
    private void expectPersistedCreation(Kind kind, MappingTarget target, long aliasId) throws Exception {
        var aliases = targetAliases(kind, target);
        assertThat(aliases).hasSize(1);
        var alias = aliases.getFirst();
        assertThat(((Number)alias.get("id")).longValue()).isEqualTo(aliasId);
        assertThat(alias.get("name")).isEqualTo(target.name());
        @SuppressWarnings("unchecked") var resource = (Map<String, Object>)alias.get("resource");
        assertThat(((Number)resource.get("id")).longValue()).isEqualTo(target.resource().resourceId());
        if (kind == Kind.AMMO) {
            assertThat(new java.math.BigDecimal(alias.get("multiplier").toString())).isEqualByComparingTo("1.75");
            assertThat(alias.get("matchByName")).isEqualTo(false);
        }
        try (var statement = fixture.connection().prepareStatement("SELECT resource_id," + sourceColumn(kind)
                + ",created_by,updated_by FROM faita." + kind.table + " WHERE id=?")) {
            statement.setLong(1, aliasId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("resource_id")).isEqualTo(target.resource().resourceId());
                assertThat(rows.getLong(sourceColumn(kind))).isEqualTo(target.sourceId());
                assertThat(rows.getString("created_by")).isEqualTo(UserRole.ADMIN.getUsername());
                assertThat(rows.getString("updated_by")).isEqualTo(UserRole.ADMIN.getUsername());
            }
        }
        expectSourceAliasCount(kind, target, 1);
    }
    private void createMapping(Kind kind, String testId) throws Exception {
        var dictionary = withData(kind);
        MappingTarget target = reserveTarget(kind);
        Row sibling = fixture.rows(kind).getFirst();
        String siblingBefore = snapshotAlias(kind, sibling.id());
        dictionary.filterAlias("").unmatched(true).filterAlias(target.name());
        dictionary.openCreate(target.sourceId(), target.name(), kind);
        dictionary.fillCreationResource(target.resource().resourceName(), kind);
        dictionary.cancelCreation();
        expectSourceAliasCount(kind, target, 0);
        dictionary.openCreate(target.sourceId(), target.name(), kind);
        dictionary.fillCreationResource(target.resource().resourceName(), kind);
        var response = page.waitForResponse(r -> r.request().method().equals("POST")
                && java.net.URI.create(r.url()).getPath().endsWith(mappingPath(kind)), dictionary::saveCreation);
        assertThat(response.status()).isEqualTo(200);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        long aliasId = mapper.readTree(response.body()).path("id").asLong();
        assertThat(aliasId).isPositive();
        var sent = mapper.readTree(response.request().postData());
        assertThat(sent.path(sourceKey(kind)).asLong()).isEqualTo(target.sourceId());
        assertThat(sent.path("resourceId").asLong()).isEqualTo(target.resource().resourceId());
        expectPersistedCreation(kind, target, aliasId);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(dictionary.sourceRow(target.sourceId())).hasCount(0);
        dictionary.unmatched(false);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(dictionary.sourceRow(target.sourceId()).locator("td").nth(1))
                .hasText(target.resource().resourceName());
        page.reload();
        dictionary.tab(kind).filterAlias(target.name());
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(dictionary.sourceRow(target.sourceId()).locator("td").nth(1))
                .hasText(target.resource().resourceName());
        assertThat(snapshotAlias(kind, sibling.id())).isEqualTo(siblingBefore);
        dictionary.attachScreenshot(testId + " — нове зіставлення після reload");
        Allure.addAttachment("Created mapping evidence", "application/json", mapper.writeValueAsString(Map.of(
                "kind", kind.name(), "sourceId", target.sourceId(), "aliasId", aliasId,
                "ownedResourceId", target.resource().resourceId(), "cancelCreatedNothing", true, "siblingUnchanged", true)));
    }
    private void deleteMapping(Kind kind, String testId) throws Exception {
        var dictionary = withData(kind);
        MappingTarget target = reserveTarget(kind);
        Row sibling = fixture.rows(kind).getFirst();
        String siblingBefore = snapshotAlias(kind, sibling.id());
        Map<String, Object> body = new LinkedHashMap<>(Map.of(sourceKey(kind), target.sourceId(), "resourceId", target.resource().resourceId()));
        if (kind == Kind.AMMO) { body.put("multiplier", new java.math.BigDecimal("1.75")); body.put("matchByName", false); }
        var created = apiExecutor.execute(kind == Kind.AMMO ? ApiEndpointDefinition.FAITA_MAPPING_AMMO_CREATE
                : ApiEndpointDefinition.FAITA_MAPPING_INITIATORS_CREATE, UserRole.ADMIN, body);
        assertThat(created.statusCode()).isEqualTo(200);
        long aliasId = created.jsonPath().getLong("id");
        expectPersistedCreation(kind, target, aliasId);
        String targetBefore = snapshotAlias(kind, aliasId);
        page.reload();
        dictionary.tab(kind).filterAlias(target.name());
        dictionary.openDelete(target.sourceId(), target.name(), target.resource().resourceName());
        dictionary.deleteDecision(false);
        assertThat(snapshotAlias(kind, aliasId)).isEqualTo(targetBefore);
        assertThat(targetAliases(kind, target)).hasSize(1);
        dictionary.openDelete(target.sourceId(), target.name(), target.resource().resourceName());
        var response = page.waitForResponse(r -> r.request().method().equals("DELETE")
                && java.net.URI.create(r.url()).getPath().endsWith(mappingPath(kind) + "/" + aliasId),
                () -> dictionary.deleteDecision(true));
        assertThat(response.status()).isEqualTo(200);
        response.finished();
        assertThat(targetAliases(kind, target)).isEmpty();
        expectSourceAliasCount(kind, target, 0);
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(dictionary.sourceRow(target.sourceId())).hasCount(0);
        page.reload();
        dictionary.tab(kind).filterAlias(target.name());
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(dictionary.sourceRow(target.sourceId())).hasCount(0);
        assertThat(snapshotAlias(kind, sibling.id())).isEqualTo(siblingBefore);
        dictionary.attachScreenshot(testId + " — видалення підтверджено API/SQL та reload");
        Allure.addAttachment("Deleted mapping evidence", "application/json", new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("kind", kind.name(), "sourceId", target.sourceId(), "aliasId", aliasId,
                        "cancelPreservedMapping", true, "deletedInApiAndDatabase", true, "siblingUnchanged", true)));
    }

    @Test @TestCaseId("TC-FMAP-UI-001")
    public void sidebarAndBothTabs() {
        var dictionary = new FaitaExpenseMappingPage(page).openFromSidebar();
        dictionary.attachScreenshot("TC-FMAP-UI-001 — вкладки");
        for (String label : List.of("Амуніція", "Ініціатори")) {
            com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.getByRole(
                    com.microsoft.playwright.options.AriaRole.TAB,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile(
                            "^" + label + "(?:\\s*\\d+)?$")))).isVisible();
        }
    }
    @Test @TestCaseId("TC-FMAP-UI-002") public void ammoColumnsAndData() throws Exception { columns(Kind.AMMO, "TC-FMAP-UI-002"); }
    @Test @TestCaseId("TC-FMAP-UI-003") public void initiatorColumnsAndData() throws Exception { columns(Kind.INITIATORS, "TC-FMAP-UI-003"); }
    @Test @TestCaseId("TC-FMAP-UI-004") public void ammoSearchByAlias() throws Exception { searchAlias(Kind.AMMO, "TC-FMAP-UI-004"); }
    @Test @TestCaseId("TC-FMAP-UI-005") public void initiatorSearchByAlias() throws Exception { searchAlias(Kind.INITIATORS, "TC-FMAP-UI-005"); }
    @Test @TestCaseId("TC-FMAP-UI-006") public void ammoSearchByResource() throws Exception { searchResource(Kind.AMMO, "TC-FMAP-UI-006"); }
    @Test @TestCaseId("TC-FMAP-UI-007") public void initiatorSearchByResource() throws Exception { searchResource(Kind.INITIATORS, "TC-FMAP-UI-007"); }
    @Test @TestCaseId("TC-FMAP-UI-008") public void ammoUnmatchedAndSearch() throws Exception { unmatched(Kind.AMMO, "TC-FMAP-UI-008"); }
    @Test @TestCaseId("TC-FMAP-UI-009") public void initiatorUnmatchedAndSearch() throws Exception { unmatched(Kind.INITIATORS, "TC-FMAP-UI-009"); }
    @Test @TestCaseId("TC-FMAP-UI-010") public void ammoSortByAliasWithResourceTieBreaker() throws Exception { sort(Kind.AMMO, false, "TC-FMAP-UI-010"); }
    @Test @TestCaseId("TC-FMAP-UI-011") public void initiatorSortByAliasWithResourceTieBreaker() throws Exception { sort(Kind.INITIATORS, false, "TC-FMAP-UI-011"); }
    @Test @TestCaseId("TC-FMAP-UI-012") public void ammoSortByResourceWithAliasTieBreaker() throws Exception { sort(Kind.AMMO, true, "TC-FMAP-UI-012"); }
    @Test @TestCaseId("TC-FMAP-UI-013") public void initiatorSortByResourceWithAliasTieBreaker() throws Exception { sort(Kind.INITIATORS, true, "TC-FMAP-UI-013"); }
    @Test @TestCaseId("TC-FMAP-UI-014")
    @Description("PencilIcon відкриває форму, Escape закриває її без змін. Поля і save-контракт ще не задані вимогою.")
    public void ammoEditPencilOpensForm() throws Exception { edit(Kind.AMMO, "TC-FMAP-UI-014"); }
    @Test @TestCaseId("TC-FMAP-UI-015")
    @Description("PencilIcon відкриває форму, Escape закриває її без змін. Поля і save-контракт ще не задані вимогою.")
    public void initiatorEditPencilOpensForm() throws Exception { edit(Kind.INITIATORS, "TC-FMAP-UI-015"); }
    @Test @TestCaseId("TC-FMAP-UI-016")
    @Description("UI POST ammunition {ammunitionId,resourceId,multiplier=1.75,matchByName=false}; cancel/save, GET/SQL audit, reload; only owned mapping/resource cleanup.")
    public void ammoCreateMapping() throws Exception { createMapping(Kind.AMMO, "TC-FMAP-UI-016"); }
    @Test @TestCaseId("TC-FMAP-UI-017")
    @Description("UI POST initiators {initiatorId,resourceId}; cancel/save, GET/SQL audit and reload; source events and dictionaries unchanged.")
    public void initiatorCreateMapping() throws Exception { createMapping(Kind.INITIATORS, "TC-FMAP-UI-017"); }
    @Test @TestCaseId("TC-FMAP-UI-018")
    @Description("API creates an owned valid ammunition mapping; UI confirmation cancel then DELETE /ammunition/{id}; verify GET/SQL absence, reload and sibling preservation.")
    public void ammoDeleteMapping() throws Exception { deleteMapping(Kind.AMMO, "TC-FMAP-UI-018"); }
    @Test @TestCaseId("TC-FMAP-UI-019")
    @Description("API creates an owned valid initiator mapping; UI confirmation cancel then DELETE /initiators/{id}; verify GET/SQL absence, reload and sibling preservation.")
    public void initiatorDeleteMapping() throws Exception { deleteMapping(Kind.INITIATORS, "TC-FMAP-UI-019"); }
}
