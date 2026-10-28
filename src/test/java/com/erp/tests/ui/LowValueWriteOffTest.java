package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.*;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.LowValueWriteOffPage;
import com.erp.pages.ResourceEditPage;
import com.erp.pages.ResourcesListPage;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.time.LocalDate;
import java.util.*;

import static com.erp.fixtures.LowValueWriteOffFixture.asset;
import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Inventory")
@Feature("REQ-MSHP — Списання ресурсів МШП")
public class LowValueWriteOffTest extends BaseUITest {
    private static final UserRole SLOT = UserRole.DYNAMIC_GLOBAL_ACTOR;
    private LowValueWriteOffFixture fixture;
    private UserFixture users;
    private UserFixture.BusinessActor accountant, denied;
    private LowValueWriteOffPage form;

    @BeforeClass(alwaysRun = true)
    @Override public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new LowValueWriteOffFixture(testContext, apiExecutor);
        fixture.prepare();
        users = new UserFixture(testContext, apiExecutor);
        accountant = users.createPermissionActor(getPlaywrightSessionProvider(), List.of("Бухгалтер"), List.of());
        denied = users.createPermissionActor(getPlaywrightSessionProvider(), List.of(),
                List.of("location.read", "inventory.read", "resource.read"));
        var access = new AccessFixture(testContext, apiExecutor);
        assertThat(access.hasEffectivePermission(accountant.userId(), "inventory.low-value-write-off.read", fixture.a.getId())).isTrue();
        assertThat(access.hasEffectivePermission(denied.userId(), "inventory.low-value-write-off.read", fixture.a.getId())).isFalse();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        apiExecutor.restoreDefaultSessionForRole(SLOT);
        try { if (fixture != null) fixture.cleanup(); }
        finally {
            if (users != null) {
                users.deactivateTrackedUsers();
                for (var actor : java.util.stream.Stream.of(accountant, denied).filter(Objects::nonNull).toList()) {
                    assertThat(users.getUser(UserRole.ADMIN, actor.userId()).isEnabled()).isFalse();
                }
            }
        }
    }

    @Test @TestCaseId("TC-MSHP-API-001")
    @Description("Property round trip REGULAR → LOW_VALUE_ITEM → REGULAR on own resource, including dictionary query.")
    public void categoryPersistsAndDictionaryFilters() {
        fixture.setCategory(fixture.absent, "REGULAR");
        try {
            assertDictionary(List.of(fixture.low.getId(), fixture.second.getId()));
            fixture.setCategory(fixture.absent, "LOW_VALUE_ITEM");
            assertDictionary(List.of(fixture.low.getId(), fixture.second.getId(), fixture.absent.getId()));
        } finally { fixture.setCategory(fixture.absent, "LOW_VALUE_ITEM"); }
    }
    private void assertDictionary(List<Long> ids) {
        Response response = apiExecutor.executeWithQueryParams(ApiEndpointDefinition.RESOURCE_GET_ALL, UserRole.ADMIN,
                Map.of("assetCategory", "LOW_VALUE_ITEM", "name", fixture.marker, "size", 100, "isActive", true));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("content.id", Long.class)).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(response.jsonPath().getList("content.assetCategory", String.class)).containsOnly("LOW_VALUE_ITEM");
    }

    @Test @TestCaseId("TC-MSHP-API-002")
    @Description("Only LOW_VALUE_ITEM resources from selected location are offered, REGULAR and no-stock excluded.")
    public void availableResourcesAreEligibleAndLocal() {
        Response response = fixture.available(UserRole.ADMIN, fixture.a.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("resource.id", Long.class))
                .containsExactlyInAnyOrder(fixture.low.getId(), fixture.second.getId());
        assertThat(response.jsonPath().getList("amount", Double.class)).allSatisfy(x -> assertThat(x).isPositive());
        assertThat(fixture.available(UserRole.ADMIN, fixture.b.getId()).jsonPath().getList("resource.id", Long.class))
                .containsExactly(fixture.low.getId());
    }

    @Test @TestCaseId("TC-MSHP-API-003")
    @Description("Multi-resource fractional write-off persists actors/comment and exact stock deltas; cancellation restores both.")
    public void multiResourceWriteOffDebitsExactAmounts() {
        double beforeLow = fixture.stock(fixture.a.getId(), fixture.low.getId());
        double beforeSecond = fixture.stock(fixture.a.getId(), fixture.second.getId());
        Map<String, Object> body = fixture.body(fixture.a.getId(), List.of(asset(fixture.low.getId(), 2.5), asset(fixture.second.getId(), 1.25)));
        long id = fixture.create(UserRole.ADMIN, body);
        try {
            Response saved = fixture.get(UserRole.ADMIN, id, fixture.a.getId());
            assertThat(saved.statusCode()).isEqualTo(200);
            assertThat(saved.jsonPath().getString("operationType")).isEqualTo("WRITE_OFF");
            assertThat(saved.jsonPath().getString("date")).isEqualTo(LowValueWriteOffFixture.today());
            assertThat(saved.jsonPath().getMap("actors")).isEqualTo(body.get("actors"));
            assertThat(saved.jsonPath().getString("comment")).isEqualTo(body.get("comment"));
            assertThat(saved.jsonPath().getString("createdBy")).isNotBlank();
            assertThat(saved.jsonPath().getList("assets.resource.id", Long.class)).containsExactlyInAnyOrder(fixture.low.getId(), fixture.second.getId());
            fixture.assertStock(fixture.a.getId(), fixture.low.getId(), beforeLow - 2.5);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), beforeSecond - 1.25);
        } finally { fixture.remove(id, fixture.a.getId()); }
        fixture.assertStock(fixture.a.getId(), fixture.low.getId(), beforeLow);
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), beforeSecond);
    }

    @DataProvider public Object[][] invalidRequests() {
        return new Object[][]{{"empty"}, {"zero"}, {"negative"}, {"missingAmount"}, {"overStock"},
                {"regular"}, {"absent"}, {"duplicate"}, {"unknownBatch"}, {"external"}, {"yesterday"}, {"tomorrow"}, {"longComment"}};
    }
    @Test(dataProvider = "invalidRequests") @TestCaseId("TC-MSHP-API-004")
    @Description("Invalid requests return 400/409 with validation errors and preserve exact stocks, batches and operation IDs atomically.")
    public void invalidWriteOffIsAtomic(String variant) {
        var first = asset(fixture.low.getId(), 1);
        var second = asset(fixture.second.getId(), 1);
        var assets = new ArrayList<>(List.of(first, second));
        Map<String, Object> body = fixture.body(fixture.a.getId(), assets);
        switch (variant) {
            case "empty" -> body.put("assets", List.of());
            case "zero" -> second.put("amount", 0);
            case "negative" -> second.put("amount", -1);
            case "missingAmount" -> second.remove("amount");
            case "overStock" -> second.put("amount", 999999);
            case "regular" -> second.put("resourceId", fixture.regular.getId());
            case "absent" -> second.put("resourceId", fixture.absent.getId());
            case "duplicate" -> assets.add(asset(fixture.low.getId(), 1));
            case "unknownBatch" -> second.put("batchId", UUID.randomUUID().toString());
            case "external" -> body.put("storageId", fixture.external.getId());
            case "yesterday" -> body.put("date", LocalDate.parse(LowValueWriteOffFixture.today()).minusDays(1).toString());
            case "tomorrow" -> body.put("date", LocalDate.parse(LowValueWriteOffFixture.today()).plusDays(1).toString());
            case "longComment" -> body.put("comment", "x".repeat(10001));
            default -> throw new AssertionError(variant);
        }
        double beforeLow = fixture.stock(fixture.a.getId(), fixture.low.getId());
        double beforeSecond = fixture.stock(fixture.a.getId(), fixture.second.getId());
        var beforeBatches = fixture.batches(fixture.a.getId(), fixture.low.getId());
        var ids = fixture.ids(Map.of("resourceName", fixture.marker));
        Response response = fixture.post(UserRole.ADMIN, body);
        assertThat(response.statusCode()).as("%s: %s", variant, response.asString()).isIn(400, 409);
        assertThat(response.jsonPath().getList("errors")).isNotEmpty();
        fixture.assertStock(fixture.a.getId(), fixture.low.getId(), beforeLow);
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), beforeSecond);
        assertThat(fixture.batches(fixture.a.getId(), fixture.low.getId())).isEqualTo(beforeBatches);
        assertThat(fixture.ids(Map.of("resourceName", fixture.marker))).containsExactlyElementsOf(ids);
    }

    @Test @TestCaseId("TC-MSHP-API-005")
    @Description("Explicit batch debit changes only selected batch; deletion returns exact original batch amounts.")
    public void explicitBatchWriteOffAndCancellation() {
        var before = fixture.batches(fixture.a.getId(), fixture.low.getId());
        var chosen = before.getLast();
        var item = asset(fixture.low.getId(), 1.5);
        item.put("batchId", chosen.getBatchUuid().toString());
        long id = fixture.create(UserRole.ADMIN, fixture.body(fixture.a.getId(), List.of(item)));
        try {
            var after = fixture.batches(fixture.a.getId(), fixture.low.getId());
            for (var batch : before) {
                double expected = batch.getAmount() - (batch.getBatchUuid().equals(chosen.getBatchUuid()) ? 1.5 : 0);
                assertThat(after.stream().filter(x -> x.getBatchUuid().equals(batch.getBatchUuid())).findFirst().orElseThrow().getAmount()).isEqualTo(expected);
            }
        } finally { fixture.remove(id, fixture.a.getId()); }
        assertThat(fixture.batches(fixture.a.getId(), fixture.low.getId())).isEqualTo(before);
    }

    @Test @TestCaseId("TC-MSHP-API-006")
    @Description("Operation history records LOW_VALUE_WRITE_OFF and its own total, exact resource/amount/comment/location.")
    public void writeOffAppearsInOperationHistory() {
        long id = fixture.create(UserRole.ADMIN, fixture.body(fixture.a.getId(), List.of(asset(fixture.second.getId(), 3))));
        try {
            Response history = fixture.history(fixture.a.getId(), fixture.second.getName());
            assertThat(history.statusCode()).isEqualTo(200);
            List<Map<String, Object>> rows = history.jsonPath().getList("operationHistoryList");
            var writeOffs = rows.stream().filter(x -> "LOW_VALUE_WRITE_OFF".equals(x.get("resourceOperationType"))).toList();
            assertThat(writeOffs).hasSize(1);
            assertThat(((Number)writeOffs.getFirst().get("amount")).doubleValue()).isEqualTo(3);
            assertThat(writeOffs.getFirst().get("comment")).isEqualTo("Списання малоцінних ресурсів #" + id);
            assertThat(((Map<?, ?>)writeOffs.getFirst().get("resource")).get("id"))
                    .isInstanceOf(Number.class);
            assertThat(((Number)((Map<?, ?>)writeOffs.getFirst().get("resource")).get("id")).longValue()).isEqualTo(fixture.second.getId());
            assertThat(history.jsonPath().getDouble("totalByOperations.lowValueWriteOff")).isEqualTo(3);
            assertThat(history.jsonPath().getList("totalLowValueWriteOffResources.resource.id", Long.class)).containsExactly(fixture.second.getId());
        } finally { fixture.remove(id, fixture.a.getId()); }
    }

    @Test @TestCaseId("TC-MSHP-API-007")
    @Description("Resource-name search, multi-location OR, period boundaries, combined filters and pagination return exact own IDs.")
    public void journalFiltersAndPagination() {
        assertThat(fixture.ids(Map.of("resourceName", fixture.marker))).containsExactlyInAnyOrder(fixture.journalA, fixture.journalB);
        assertThat(fixture.ids(Map.of("resourceName", fixture.second.getName()))).isEmpty();
        assertThat(fixture.ids(Map.of("storageIds", fixture.a.getId(), "resourceName", fixture.low.getName()))).containsExactly(fixture.journalA);
        assertThat(fixture.ids(Map.of("storageIds", List.of(fixture.a.getId(), fixture.b.getId()), "resourceName", fixture.low.getName(),
                "fromDate", LowValueWriteOffFixture.today(), "toDate", LowValueWriteOffFixture.today())))
                .containsExactlyInAnyOrder(fixture.journalA, fixture.journalB);
        assertThat(fixture.ids(Map.of("resourceName", fixture.marker, "toDate", LocalDate.parse(LowValueWriteOffFixture.today()).minusDays(1).toString()))).isEmpty();
        List<Long> paged = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            Response response = fixture.list(UserRole.ADMIN, Map.of("resourceName", fixture.marker, "page", index, "size", 1));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getInt("page.totalElements")).isEqualTo(2);
            paged.addAll(response.jsonPath().getList("content.id", Long.class));
        }
        assertThat(paged).containsExactly(fixture.journalA, fixture.journalB).doesNotHaveDuplicates();
    }

    @Test @TestCaseId("TC-MSHP-API-008")
    @Description("Accountant sees journal and details; actor lacking write-off.read gets 403 for list/details/create. Stock unchanged.")
    public void journalRbac() {
        bind(accountant);
        assertThat(fixture.list(SLOT, Map.of("parentStorageId", fixture.a.getId())).statusCode()).isEqualTo(200);
        assertThat(fixture.get(SLOT, fixture.journalA, fixture.a.getId()).statusCode()).isEqualTo(200);
        bind(denied);
        assertThat(fixture.list(SLOT, Map.of("parentStorageId", fixture.a.getId())).statusCode()).isEqualTo(403);
        assertThat(fixture.get(SLOT, fixture.journalA, fixture.a.getId()).statusCode()).isEqualTo(403);
        assertThat(fixture.post(SLOT, fixture.body(fixture.a.getId(), List.of(asset(fixture.low.getId(), 1)))).statusCode()).isEqualTo(403);
        fixture.assertStock(fixture.a.getId(), fixture.low.getId(), 29);
    }

    @Test @TestCaseId("TC-MSHP-API-009")
    @Description("Same-day update replaces old debit; invalid update is atomic; mismatched storage cannot read/delete an operation.")
    public void sameDayEditAndLocationIsolation() {
        long id = fixture.create(UserRole.ADMIN, fixture.body(fixture.a.getId(), List.of(asset(fixture.second.getId(), 2))));
        try {
            Response response = fixture.update(UserRole.ADMIN, id, fixture.body(fixture.a.getId(), List.of(asset(fixture.second.getId(), 4))));
            assertThat(response.statusCode()).isEqualTo(200);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 11);
            assertThat(fixture.update(UserRole.ADMIN, id, fixture.body(fixture.a.getId(), List.of(asset(fixture.second.getId(), 1000)))).statusCode()).isIn(400, 409);
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 11);
            assertThat(fixture.get(UserRole.ADMIN, id, fixture.b.getId()).statusCode()).isEqualTo(404);
            assertThat(fixture.delete(UserRole.ADMIN, id, fixture.b.getId()).statusCode()).isEqualTo(404);
            // Wrong-location 404 must not untrack the actual operation.
            fixture.track(id, fixture.a.getId());
        } finally { fixture.remove(id, fixture.a.getId()); }
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 15);
    }

    @Test @TestCaseId("TC-MSHP-UI-001")
    @Description("Edit resource accounting category, save through UI and reload. Current UI uses a select with the misspelled МПШ label.")
    public void resourceCategoryCanBeEdited() {
        login(null, fixture.a.getId());
        try {
            for (String value : List.of("REGULAR", "LOW_VALUE_ITEM")) {
                var editor = new ResourceEditPage(page).open(fixture.absent.getId(), fixture.a.getId());
                page.getByTestId("resource-form-asset-category").click();
                page.getByTestId("resource-form-asset-category-option-" + value).click();
                editor.fillEmptyRequiredSelects();
                editor.saveAndReturnToList();
                assertThat(fixture.resource(fixture.absent.getId()).jsonPath().getString("assetCategory")).isEqualTo(value);
            }
        } finally { fixture.setCategory(fixture.absent, "LOW_VALUE_ITEM"); }
    }

    @Test @TestCaseId("TC-MSHP-UI-002")
    @Description("Accepted 2026-10-26: full hint on resource editing is optional. Verify the accounting category control and the explanation provided on the write-off form.")
    public void writeOffFormExplainsMshp() {
        login(null, fixture.a.getId());
        new ResourceEditPage(page).open(fixture.absent.getId(), fixture.a.getId());
        assertThat(page.getByTestId("resource-form-asset-category")).isVisible();
        form.open(LowValueWriteOffPage.FORM);
        Locator explanation = page.getByTestId("low-value-write-off-info");
        assertThat(explanation).isVisible();
        assertThat(explanation).containsText("МШП (малоцінні та швидкозношувані предмети)");
        assertThat(explanation).containsText("використовують у процесі виробництва продукції");
    }

    @Test @TestCaseId("TC-MSHP-UI-003")
    @Description("Actual dictionary select requests assetCategory=LOW_VALUE_ITEM, excludes REGULAR; reset restores every own resource.")
    public void dictionaryFilterWorksAndResets() {
        login(null, fixture.a.getId());
        form.open("/resources");
        var dictionary = new ResourcesListPage(page).waitForLoaded().searchAndWait(fixture.marker);
        assertThat(dictionary.getVisibleResourceNames()).hasSize(4);
        page.getByTestId("resources-batch-filter").click();
        var response = page.waitForResponse(r -> r.url().contains("/api/v1/resources?") && r.url().contains("assetCategory=LOW_VALUE_ITEM"),
                () -> page.getByTestId("resources-batch-filter-option-low-value-item").click());
        assertThat(response.status()).isEqualTo(200);
        page.waitForCondition(() -> dictionary.getVisibleResourceNames().size() == 3);
        assertThat(dictionary.getVisibleResourceNames()).containsExactlyInAnyOrder(fixture.low.getName(), fixture.second.getName(), fixture.absent.getName());
        page.getByTestId("resources-batch-filter").click();
        page.getByTestId("resources-batch-filter-option-all").click();
        page.waitForCondition(() -> dictionary.getVisibleResourceNames().size() == 4);
    }

    @Test @TestCaseId("TC-MSHP-UI-004")
    @Description("Accepted 2026-10-26: dictionary uses select instead of checkbox. Verify both all-resources and LOW_VALUE_ITEM options and the selected label after applying the filter.")
    public void dictionaryUsesAcceptedMshpSelect() {
        login(null, fixture.a.getId());
        form.open("/resources");
        new ResourcesListPage(page).waitForLoaded();
        Locator control = page.getByTestId("resources-batch-filter");
        assertThat(control).hasAttribute("role", "combobox");
        assertThat(control).containsText("Усі способи обліку");
        control.click();
        assertThat(page.getByTestId("resources-batch-filter-option-all")).isVisible();
        Locator option = page.getByTestId("resources-batch-filter-option-low-value-item");
        assertThat(option).isVisible();
        String label = option.innerText().trim();
        var response = page.waitForResponse(r -> r.url().contains("/api/v1/resources?")
                        && r.url().contains("assetCategory=LOW_VALUE_ITEM"), option::click);
        assertThat(response.status()).isEqualTo(200);
        assertThat(control).containsText(label);
    }

    @Test @TestCaseId("TC-MSHP-UI-005")
    @Description("Real inventory button opens shared resource-selection form; empty/zero/negative/over-stock blocked; positive fractional accepted; cancel unchanged.")
    public void inventoryButtonAndFormValidation() {
        login(null, fixture.a.getId());
        form.open("/inventory");
        page.getByTestId("inventory-low-value-write-off").click();
        assertThat(form.submit()).isDisabled();
        form.selectResource(fixture.second.getName());
        // Wait for asynchronous batch lookup before editing quantities.
        assertThat(page.getByPlaceholder("Кількість").last()).isEnabled();
        for (String amount : List.of("0", "-1", "999999")) {
            form.amount(amount);
            assertThat(form.submit()).isDisabled();
        }
        form.amount("1.5");
        assertThat(form.submit()).isEnabled();
        page.getByTestId("low-value-write-off-cancel").click();
        page.waitForURL("**/inventory");
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 15);
    }

    @Test @TestCaseId("TC-MSHP-UI-006")
    @Description("Resource dropdown excludes regular and absent stock; selected resource unavailable for a duplicate line.")
    public void formOffersOnlyEligibleResources() {
        login(null, fixture.a.getId());
        form.open(LowValueWriteOffPage.FORM);
        Locator input = page.getByPlaceholder("Оберіть ресурс...").first();
        input.fill(fixture.regular.getName());
        assertThat(page.getByRole(AriaRole.OPTION)).hasCount(0);
        input.fill(fixture.absent.getName());
        assertThat(page.getByRole(AriaRole.OPTION)).hasCount(0);
        form.selectResource(fixture.second.getName());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Додати позицію")).click();
        page.getByPlaceholder("Оберіть ресурс...").last().fill(fixture.second.getName());
        assertThat(page.getByRole(AriaRole.OPTION)).hasCount(0);
    }

    @Test @TestCaseId("TC-MSHP-UI-007")
    @Description("Submit actual UI POST, exact stock debit, journal row, then cancel own operation and assert stock restoration.")
    public void createWriteOffThroughUi() {
        login(null, fixture.a.getId());
        form.open(LowValueWriteOffPage.FORM);
        form.selectResource(fixture.second.getName());
        form.amount("1.5");
        page.getByTestId("low-value-write-off-description").fill(fixture.marker + " UI");
        long id = form.save(fixture.a.getId(), fixture);
        try {
            fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 13.5);
            form.open(LowValueWriteOffPage.HISTORY);
            assertThat(form.row(id)).containsText(fixture.second.getName());
            assertThat(form.row(id)).containsText("1.5");
            assertThat(form.row(id)).containsText(fixture.marker + " UI");
        } finally { fixture.remove(id, fixture.a.getId()); }
        fixture.assertStock(fixture.a.getId(), fixture.second.getId(), 15);
    }

    @DataProvider public Object[][] journalReaders() { return new Object[][]{{"admin"}, {"accountant"}}; }
    @Test(dataProvider = "journalReaders") @TestCaseId("TC-MSHP-UI-008")
    @Description("Administrator and Accountant enter journal using real sidebar; row displays location/date/author/resources; UI search excludes other resources and reset restores.")
    public void journalSidebarColumnsAndSearch(String role) {
        login("accountant".equals(role) ? accountant : null, fixture.a.getId());
        form.open("/inventory");
        page.locator("a[href='" + LowValueWriteOffPage.HISTORY + "']").click();
        assertThat(form.row(fixture.journalA)).containsText(fixture.a.getName());
        assertThat(form.row(fixture.journalA)).containsText(fixture.low.getName());
        String author = fixture.get(UserRole.ADMIN, fixture.journalA, fixture.a.getId()).jsonPath().getString("createdBy");
        assertThat(form.row(fixture.journalA)).containsText(author);
        for (String column : List.of("Дата", "Локація", "Ресурси")) assertThat(page.locator("thead")).containsText(column);
        form.search(fixture.second.getName());
        assertThat(form.row(fixture.journalA)).hasCount(0);
        page.getByTestId("low-value-write-off-filter-reset").click();
        assertThat(form.search()).hasValue("");
        assertThat(form.row(fixture.journalA)).isVisible();
        if ("accountant".equals(role)) assertThat(page.getByTestId("low-value-write-off-create")).hasCount(0);
    }

    @Test @TestCaseId("TC-MSHP-UI-009")
    @Description("User without read permission has no sidebar item, direct history and create routes denied; no data changed.")
    public void unauthorizedUserCannotOpenWriteOffPages() {
        login(denied, fixture.a.getId());
        form.open("/inventory");
        assertThat(page.locator("a[href='" + LowValueWriteOffPage.HISTORY + "']")).hasCount(0);
        for (String path : List.of(LowValueWriteOffPage.HISTORY, LowValueWriteOffPage.FORM)) {
            form.open(path);
            assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("403").setExact(true))).isVisible();
            assertThat(form.row(fixture.journalA)).hasCount(0);
            assertThat(form.submit()).hasCount(0);
        }
    }

    @Test @TestCaseId("TC-MSHP-UI-010")
    @Description("Real multi-location selector OR, one-day period and resource search issue correct filters and show exact fixture rows; reset restores both.")
    public void journalLocationAndPeriodFilters() {
        login(null, "all");
        form.open(LowValueWriteOffPage.HISTORY);
        form.search(fixture.low.getName());
        assertThat(form.row(fixture.journalA)).isVisible();
        assertThat(form.row(fixture.journalB)).isVisible();
        form.selectLocations(List.of(fixture.a.getName()));
        assertThat(form.row(fixture.journalA)).isVisible();
        assertThat(form.row(fixture.journalB)).hasCount(0);
        form.selectLocations(List.of(fixture.b.getName()));
        assertThat(form.row(fixture.journalA)).isVisible();
        assertThat(form.row(fixture.journalB)).isVisible();
        page.getByTestId("low-value-write-off-filter-dates").click();
        var response = page.waitForResponse(r -> LowValueWriteOffPage.listResponse(r)
                        && r.url().contains("fromDate=" + LowValueWriteOffFixture.today())
                        && r.url().contains("toDate=" + LowValueWriteOffFixture.today()),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("1 день").setExact(true)).click());
        assertThat(response.status()).isEqualTo(200);
        assertThat(form.row(fixture.journalA)).isVisible();
        assertThat(form.row(fixture.journalB)).isVisible();
        page.getByTestId("low-value-write-off-filter-reset").click();
        assertThat(form.search()).hasValue("");
        assertThat(form.row(fixture.journalA)).isVisible();
        assertThat(form.row(fixture.journalB)).isVisible();
    }

    private void bind(UserFixture.BusinessActor actor) { apiExecutor.setSessionForRole(SLOT, actor.username(), actor.password()); }
    private void login(UserFixture.BusinessActor actor, Object storage) {
        browserContext.clearCookies();
        injectSessionCookies(actor == null ? cachedSessionCookies(UserRole.ADMIN)
                : authService.getSessionForUser(actor.username(), actor.password()), sessionCookieDomain());
        String username = actor == null ? UserRole.ADMIN.getUsername() : actor.username();
        // Clear once per method, preserving subsequent navigation and actual filter state.
        page.navigate(ConfigProvider.getBaseUrl());
        page.evaluate("localStorage.clear()");
        page.evaluate("([u,s]) => {localStorage.setItem('selectedStorageId',s);localStorage.setItem('selectedStorageId:'+u,s)}",
                List.of(username, String.valueOf(storage)));
        form = new LowValueWriteOffPage(page);
    }
}
