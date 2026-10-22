package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationEditReturnFixture;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.testng.annotations.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Epic("Relocation")
@Feature("REQ-REL-EDIT-RETURN — фільтри після редагування")
@Severity(SeverityLevel.CRITICAL)
public class RelocationEditReturnFiltersUiTest extends BaseUITest {
    private RelocationEditReturnFixture data;
    private final List<Response> lists = new ArrayList<>();

    @BeforeClass(alwaysRun = true) @Override public void baseTestClassSetup() {
        super.baseTestClassSetup();
        data = new RelocationEditReturnFixture(testContext, apiExecutor);
        data.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, data.source.getId());
    }
    @AfterClass(alwaysRun = true) public void cleanup() { if (data != null) data.cleanup(); }
    @BeforeMethod(alwaysRun = true) public void observeLists() {
        lists.clear();
        page.onResponse(r -> { if (URI.create(r.url()).getPath().endsWith("/api/v1/relocations")
                && r.request().method().equals("GET")) lists.add(r); });
    }
    @DataProvider(name = "tabs") public Object[][] tabs() { return new Object[][]{{"sent"}, {"received"}}; }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-001")
    public void combinedResourceFiltersSurviveSave(String tab) {
        var journal = scoped(tab);
        journal.category(data.marker + "-category-A", data.categoryA);
        journal.product(data.first.getName(), data.first.getId());
        journal.product(data.sameCategory.getName(), data.sameCategory.getId());
        journal.selectSupplier("МОУ");
        journal.historyStatus("Завершено");
        editAndAssert(tab, data.record(tab, false).getId(), journal, false);
    }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-002")
    public void singleProductFilterSurvivesSave(String tab) {
        var journal = scoped(tab);
        journal.product(data.first.getName(), data.first.getId());
        editAndAssert(tab, data.record(tab, false).getId(), journal, false);
    }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-003")
    public void clearedOptionalFiltersStayCleared(String tab) {
        var journal = scoped(tab);
        journal.category(data.marker + "-category-A", data.categoryA);
        journal.clearCategory();
        editAndAssert(tab, data.record(tab, false).getId(), journal, false);
    }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-004")
    public void reopeningEditorUsesLatestSnapshot(String tab) {
        var journal = scoped(tab);
        journal.category(data.marker + "-category-A", data.categoryA);
        editAndAssert(tab, data.record(tab, false).getId(), journal, false);
        journal.clearCategory();
        journal.product(data.first.getName(), data.first.getId());
        journal.dates(data.today.minusDays(7), data.today);
        editAndAssert(tab, data.record(tab, true).getId(), journal, false);
    }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-005")
    public void editedRecordOutsideDateFilterDisappears(String tab) {
        var journal = scoped(tab);
        var record = data.record(tab, false);
        try { editAndAssert(tab, record.getId(), journal, true); }
        finally {
            // Restore through the UI only if a save actually changed the record date.
            var saved = data.saved(tab, record.getId());
            if (!saved.getDate().equals(data.today)) {
                var all = scoped(tab);
                all.dates(data.today.minusDays(7), data.today);
                openEditor(tab, record.getId());
                page.locator("input[type='date']").first().fill(data.today.toString());
                submit(record.getId());
                assertThat(data.saved(tab, record.getId()).getDate()).isEqualTo(data.today);
            }
        }
    }

    @Test(dataProvider = "tabs") @TestCaseId("TC-REL-RETURN-UI-006")
    public void equipmentModeAndModelSurviveSave(String tab) {
        var journal = new RelocationSupplierFiltersPage(page).open(tab);
        journal.type("EQUIPMENT");
        journal.location(tab.equals("sent") ? "Від кого" : "Кому", data.source.getName(), data.source.getId());
        journal.equipment(tab.equals("sent") ? data.modelNameB : data.modelNameA,
                tab.equals("sent") ? data.modelB : data.modelA);
        editAndAssert(tab, tab.equals("sent") ? data.equipmentSend : data.equipmentReceipt, journal, false);
    }

    private RelocationSupplierFiltersPage scoped(String tab) {
        var journal = new RelocationSupplierFiltersPage(page).open(tab);
        journal.location("Від кого", tab.equals("sent") ? data.source.getName() : data.external.getName(),
                tab.equals("sent") ? data.source.getId() : data.external.getId());
        journal.location("Кому", tab.equals("sent") ? data.external.getName() : data.source.getName(),
                tab.equals("sent") ? data.external.getId() : data.source.getId());
        journal.dates(data.today, data.today);
        return journal;
    }

    private void editAndAssert(String tab, Long id, RelocationSupplierFiltersPage journal, boolean changeDate) {
        // Export observes the active query independently of cached table data and proves the initial fixture selection.
        final Response[] before = new Response[1];
        before[0] = page.waitForResponse(r -> URI.create(r.url()).getPath().endsWith("/relocations/export"),
                () -> journal.export());
        var query = parameters(before[0].url());
        var controls = controls(journal);
        var expectedIds = data.rows(toQuery(query)).stream().map(RelocationResponse::getId).toList();
        assertThat(expectedIds).contains(id);
        String note = data.marker + "-saved-" + UUID.randomUUID();
        openEditor(tab, id);
        page.locator("#description").fill(note);
        if (changeDate) page.locator("input[type='date']").first().fill(data.today.minusDays(1).toString());
        lists.clear();
        submit(id);
        page.getByRole(AriaRole.TABPANEL).waitFor();
        // Hidden tab requests may finish after the visible history tab. Observe its
        // states rather than treating the last response from any mounted tab as active.
        page.waitForCondition(() -> lists.stream().anyMatch(this::isHistoryList));
        var returned = lists.stream().filter(this::isHistoryList).toList().getLast();
        returned.finished();
        var saved = data.saved(tab, id);
        assertThat(saved.getDescription()).as("UI edit really persisted").isEqualTo(note);
        if (changeDate) assertThat(saved.getDate()).isEqualTo(data.today.minusDays(1));
        var actualControls = controls(journal);
        var active = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setSelected(true)).innerText();
        var returnedQuery = parameters(returned.url());
        var wanted = new ArrayList<>(expectedIds);
        if (changeDate) wanted.remove(id);
        List<Long> renderedIds = new ArrayList<>();
        {
            var response = returned;
            assertThat(response.status()).isEqualTo(200);
            try {
                var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                body.path("content").forEach(r -> renderedIds.add(r.path("id").asLong()));
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        var panel = page.getByRole(AriaRole.TABPANEL);
        var updatedRow = panel.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(note));
        page.waitForCondition(() -> updatedRow.count() == (changeDate ? 0 : 1),
                new Page.WaitForConditionOptions().setTimeout(5_000));
        var visibleRows = panel.locator("tbody tr").filter(new Locator.FilterOptions()
                .setHas(page.locator("button[title='Редагувати']"))).count();
        var tableText = panel.locator("tbody").innerText();
        attachScreenshot("Return after edit — " + tab);
        Allure.addAttachment("Filter snapshot and returned query", "before=" + query + "\nafter=" + returnedQuery
                + "\ncontrolsBefore=" + controls + "\ncontrolsAfter=" + actualControls + "\nreturnedTable=" + tableText);
        assertSoftly(soft -> {
            soft.assertThat(active).as("Original tab restored").isEqualTo(tab.equals("sent") ? "Видано" : "Отримано");
            soft.assertThat(actualControls).as("Visible filter values restored").isEqualTo(controls);
            soft.assertThat(filterParameters(returnedQuery)).as("Actual list uses the saved filter snapshot")
                    .isEqualTo(filterParameters(query));
            soft.assertThat(renderedIds).as("Returned table contains exact filtered relocation IDs")
                    .containsExactlyInAnyOrderElementsOf(wanted);
            soft.assertThat(visibleRows).as("Rendered editable fixture rows match the filtered response").isEqualTo(wanted.size());
            soft.assertThat(updatedRow.count()).as("Updated note visible exactly when the record matches filters")
                    .isEqualTo(changeDate ? 0 : 1);
        });
        // Same page object retains the expected query and verifies a real download after return.
        journal.export();
    }

    private void openEditor(String tab, Long id) {
        var record = data.saved(tab, id);
        String marker = record.getInvoiceNumber() == null || record.getInvoiceNumber().isBlank()
                ? record.getDescription() : record.getInvoiceNumber();
        assertThat(marker).as("Fixture row has an invoice or unique description").isNotBlank();
        var row = page.getByRole(AriaRole.TABPANEL).locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(marker));
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Редагувати")).click();
        if (tab.equals("sent")) new RelocationUpdateOutputPage(page).waitForLoaded().ensureIssuerFilled();
        else new RelocationUpdateInputPage(page).waitForLoaded();
    }
    private boolean isHistoryList(Response response) {
        var states = parameters(response.url()).getOrDefault("states", List.of());
        return !states.isEmpty() && Set.of("FINISHED", "AUTO_FINISHED", "RETURNED").containsAll(states);
    }
    private void submit(Long id) {
        var response = page.waitForResponse(r -> r.request().method().equals("PUT")
                && URI.create(r.url()).getPath().contains("/" + id + "/"),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Підтвердити").setExact(true)).click());
        assertThat(response.status()).as("Real edit PUT: %s", response.text()).isBetween(200, 299);
        page.waitForURL("**/relocations*");
    }
    private Map<String, String> controls(RelocationFiltersPage journal) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String label : List.of("Тип", "Від кого", "Кому", "Категорія", "Виріб", "Постачальник", "Обладнання", "Дата відправки", "Статус"))
            if (journal.hasField(label)) result.put(label, journal.field(label).innerText().replaceAll("\\s+", " ").trim());
        result.put("activeType", journal.field("Тип").locator("[data-state='on']").innerText().trim());
        return result;
    }
    private Map<String, List<String>> parameters(String url) {
        Map<String, List<String>> result = new TreeMap<>();
        String raw = URI.create(url).getRawQuery();
        if (raw != null) for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8).replace("[]", "");
            String value = URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8);
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(List.of(value.split(",")));
        }
        result.values().forEach(Collections::sort); return result;
    }
    private Map<String, List<String>> filterParameters(Map<String, List<String>> query) {
        var result = new TreeMap<>(query);
        result.keySet().retainAll(Set.of("states", "type", "fromIds", "toIds", "senderIds", "receiverIds",
                "startDate", "endDate", "category", "productIds", "suppliers", "equipmentModelIds", "isOr", "relocationAgentId"));
        return result;
    }
    private Map<String, Object> toQuery(Map<String, List<String>> query) { return new LinkedHashMap<>(query); }
}
