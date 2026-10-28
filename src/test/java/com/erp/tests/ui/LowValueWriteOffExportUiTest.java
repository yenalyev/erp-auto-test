package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.fixtures.*;
import com.erp.pages.LowValueWriteOffExportPage;
import com.erp.pages.LowValueWriteOffPage;
import com.erp.utils.assertions.LowValueWriteOffExportAssertions;
import com.erp.utils.config.ConfigProvider;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Inventory")
@Feature("REQ-MSHP-EXCEL — Списання МШП, Excel")
@Severity(SeverityLevel.CRITICAL)
public class LowValueWriteOffExportUiTest extends BaseUITest {
    private static final UserRole SLOT = UserRole.DYNAMIC_GLOBAL_ACTOR;
    private static final String CONTRACT = "Реальні UI-фільтри: resourceName, categoryIds, storageIds, fromDate/toDate, parentStorageId workspace. "
            + "Deployed dev контракт: GET low-value-resources/export, доступна кнопка та валідний XLSX. ";
    private LowValueWriteOffExportFixture fixture;
    private UserFixture users;
    private UserFixture.BusinessActor accountant, denied;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareExportData() {
        fixture = new LowValueWriteOffExportFixture(testContext, apiExecutor);
        fixture.prepareExport();
        users = new UserFixture(testContext, apiExecutor);
        accountant = users.createPermissionActor(getPlaywrightSessionProvider(), List.of("Бухгалтер"), List.of());
        denied = users.createPermissionActor(getPlaywrightSessionProvider(), List.of(), List.of("location.read", "inventory.read", "resource.read"));
        var access = new AccessFixture(testContext, apiExecutor);
        assertThat(access.hasEffectivePermission(accountant.userId(), "inventory.low-value-write-off.read", fixture.a.getId())).isTrue();
        assertThat(access.hasEffectivePermission(denied.userId(), "inventory.low-value-write-off.read", fixture.a.getId())).isFalse();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupExportData() {
        apiExecutor.restoreDefaultSessionForRole(SLOT);
        try { if (fixture != null) fixture.cleanup(); }
        finally {
            if (users != null) {
                users.deactivateTrackedUsers();
                for (var actor : java.util.stream.Stream.of(accountant, denied).filter(Objects::nonNull).toList())
                    assertThat(users.getUser(UserRole.ADMIN, actor.userId()).isEnabled()).isFalse();
            }
        }
    }

    private LowValueWriteOffExportPage journal(Object storage) {
        login(null, storage);
        return new LowValueWriteOffExportPage(page).open();
    }

    private void login(UserFixture.BusinessActor actor, Object storage) {
        browserContext.clearCookies();
        injectSessionCookies(actor == null ? cachedSessionCookies(UserRole.ADMIN)
                : authService.getSessionForUser(actor.username(), actor.password()), sessionCookieDomain());
        page.navigate(ConfigProvider.getBaseUrl());
        page.evaluate("localStorage.clear()");
        page.evaluate("([u,s]) => {localStorage.setItem('selectedStorageId',s);localStorage.setItem('selectedStorageId:'+u,s)}",
                List.of(actor == null ? UserRole.ADMIN.getUsername() : actor.username(), String.valueOf(storage)));
    }

    private List<Map<String, Object>> a() { return fixture.records(fixture.a.getId()); }
    private List<Map<String, Object>> b() { return fixture.records(fixture.b.getId()); }
    private List<Map<String, Object>> one(long id, long storage) { return List.of(fixture.record(id, storage)); }
    private List<Map<String, Object>> both() { var rows = new ArrayList<>(a()); rows.addAll(b()); return rows; }

    private void verify(LowValueWriteOffExportPage journal, String label, List<Map<String, Object>> expected) throws Exception {
        journal.expectRows(expected);
        journal.attachScreenshot(label);
        LowValueWriteOffExportAssertions.assertMatches(journal.download(), expected);
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-001")
    @Description(CONTRACT + "Excel поточної локації: точні дані та дробова кількість 1.25, незмінні операції/залишки A і B.")
    public void exportsCurrentHistory() throws Exception {
        var before = fixture.snapshot();
        try { verify(journal(fixture.a.getId()), "TC-MSHP-XLSX-UI-001 — журнал і кнопка експорту", a()); }
        finally { assertThat(fixture.snapshot()).as("Export leaves both locations unchanged").isEqualTo(before); }
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-002")
    @Description(CONTRACT + "Пошук cloth і наступний пошук gloves через UI дають різний точний склад Excel.")
    public void exportsResourceSearch() throws Exception {
        var journal = journal(fixture.a.getId()).resource(fixture.second.getName());
        verify(journal, "TC-MSHP-XLSX-UI-002 — пошук cloth", one(fixture.clothOperation, fixture.a.getId()));
        journal.resource(fixture.low.getName());
        // Matching operations include all their assets, including the mixed operation.
        verify(journal, "TC-MSHP-XLSX-UI-002 — змінено на gloves", a());
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-003")
    @Description(CONTRACT + "Категорії з власними ресурсами; A та A+B через UI multiselect; порожня контрольна категорія.")
    public void exportsCategoriesAndLocations() throws Exception {
        var journal = journal("all").resource(fixture.marker).toggleCategory(fixture.categoryId).toggleLocation(fixture.a.getName());
        verify(journal, "TC-MSHP-XLSX-UI-003 — категорія та A", a());
        journal.toggleLocation(fixture.b.getName());
        verify(journal, "TC-MSHP-XLSX-UI-003 — A OR B", both());
        journal.toggleCategory(fixture.categoryId).toggleCategory(fixture.controlCategoryId);
        verify(journal, "TC-MSHP-XLSX-UI-003 — контрольна категорія", List.of());
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-004")
    @Description(CONTRACT + "Діапазон сьогодні–сьогодні включає обидві межі; вчора–вчора не включає сьогоднішні операції.")
    public void exportsPeriod() throws Exception {
        LocalDate today = LocalDate.parse(LowValueWriteOffFixture.today());
        var journal = journal(fixture.a.getId()).dates(today, today);
        verify(journal, "TC-MSHP-XLSX-UI-004 — один день, обидві межі", a());
        journal.dates(today.minusDays(1), today.minusDays(1));
        verify(journal, "TC-MSHP-XLSX-UI-004 — вчора без операцій", List.of());
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-005")
    @Description(CONTRACT + "Ресурс AND категорія AND локація AND період; справжній reset повертає повну історію workspace A.")
    public void exportsCombinedAndResetFilters() throws Exception {
        var today = LocalDate.parse(LowValueWriteOffFixture.today());
        var journal = journal(fixture.a.getId()).resource(fixture.second.getName()).toggleCategory(fixture.categoryId)
                .toggleLocation(fixture.a.getName()).dates(today, today);
        verify(journal, "TC-MSHP-XLSX-UI-005 — перетин фільтрів", one(fixture.clothOperation, fixture.a.getId()));
        journal.reset();
        verify(journal, "TC-MSHP-XLSX-UI-005 — скинуто фільтри", a());
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-006")
    @Description(CONTRACT + "Пошук без збігів експортує заголовки без даних попередньої чи чужої вибірки.")
    public void exportsEmptySearch() throws Exception {
        verify(journal(fixture.a.getId()).resource(fixture.marker + "-missing"), "TC-MSHP-XLSX-UI-006 — порожній пошук", List.of());
    }

    @Test(priority = 100) @TestCaseId("TC-MSHP-XLSX-UI-007")
    @Description(CONTRACT + "26 операцій, друга сторінка size=25: файл містить усі 26; після тесту зайві списання скасовано.")
    public void exportsAllPages() throws Exception {
        List<Long> added = new ArrayList<>();
        try {
            for (int i = 0; i < 24; i++) added.add(fixture.createOperation(fixture.a.getId(), fixture.low.getId(), .1, "сторінка " + i));
            var expected = a();
            assertThat(expected).hasSize(26);
            var journal = journal(fixture.a.getId()).secondPage();
            journal.attachScreenshot("TC-MSHP-XLSX-UI-007 — друга сторінка, 26 операцій");
            var before = fixture.snapshot();
            try { LowValueWriteOffExportAssertions.assertMatches(journal.download(), expected); }
            finally { assertThat(fixture.snapshot()).isEqualTo(before); }
        } finally { for (long id : added.reversed()) fixture.remove(id, fixture.a.getId()); }
    }

    @Test @TestCaseId("TC-MSHP-XLSX-UI-008")
    @Description(CONTRACT + "Без права читання API журналу, прямий export API та маршрут заборонено; Бухгалтер може завантажити XLSX.")
    public void enforcesHistoryReadPermission() throws Exception {
        apiExecutor.setSessionForRole(SLOT, denied.username(), denied.password());
        try {
            assertThat(fixture.list(SLOT, Map.of("parentStorageId", fixture.a.getId())).statusCode()).isEqualTo(403);
            assertThat(apiExecutor.executeWithQueryParams(ApiEndpointDefinition.LOW_VALUE_WRITE_OFF_EXPORT, SLOT,
                    Map.of("parentStorageId", fixture.a.getId())).statusCode()).isEqualTo(403);
        } finally { apiExecutor.restoreDefaultSessionForRole(SLOT); }
        login(denied, fixture.a.getId());
        page.navigate(ConfigProvider.getBaseUrl() + LowValueWriteOffPage.HISTORY);
        assertThat(page.getByText("403", new Page.GetByTextOptions().setExact(true))).isVisible();
        var journal = new LowValueWriteOffExportPage(page);
        assertThat(journal.exportButton()).hasCount(0);
        journal.attachScreenshot("TC-MSHP-XLSX-UI-008 — користувачу без права заборонено");
        login(accountant, fixture.a.getId());
        journal.open();
        verify(journal, "TC-MSHP-XLSX-UI-008 — Бухгалтер читає історію", a());
    }
}
