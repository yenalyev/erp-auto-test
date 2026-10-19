package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.NonSeriesProductionStatus;
import com.erp.enums.UserRole;
import com.erp.fixtures.NonSeriesResourceFilterFixture;
import com.erp.models.response.NonSeriesProductionResponse;
import com.erp.pages.NonSeriesExcelExportPage;
import com.erp.utils.assertions.NonSeriesExportAssertions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Non-Series Production")
@Feature("Несерійне виробництво — експорт в Excel")
@Severity(SeverityLevel.CRITICAL)
public class NonSeriesExcelExportUiTest extends BaseUITest {
    private NonSeriesResourceFilterFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareExportData() {
        fixture = new NonSeriesResourceFilterFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId);
    }

    @AfterClass(alwaysRun = true)
    public void cleanupExportData() { if (fixture != null) fixture.cleanup(); }

    private NonSeriesExcelExportPage openClean() {
        var journal = new NonSeriesExcelExportPage(page);
        journal.open().filterByResource(fixture.materialTerm).clearAllFilters(fixture.materialTerm)
                .expectProducts(fixture.allProducts()).waitForDisplayedTotalVolume(BigDecimal.valueOf(28));
        return journal;
    }

    private Map<String, Object> filters() { return new LinkedHashMap<>(Map.of("storageIds", fixture.storageId)); }

    private void verify(NonSeriesExcelExportPage journal, Map<String, Object> params, String label,
                        NonSeriesProductionResponse... expected) throws Exception {
        journal.expectProducts(Arrays.stream(expected).map(NonSeriesProductionResponse::getProduct).toList());
        journal.waitForDisplayedTotalVolume(Arrays.stream(expected).map(NonSeriesProductionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        journal.attachScreenshot(label);
        NonSeriesExportAssertions.assertMatches(journal.download(params), List.of(expected));
        assertThat(page.getByTestId("non-series-production-export")).isEnabled();
    }

    @Test @TestCaseId("TC-NSP-XLSX-UI-001")
    @Description("AC-01,06. Реальні UI-фільтри назви, статусу, періоду й матеріалу окремо. "
            + "Кожний download перевіряє точний query, рядки/об'єм журналу та всі поля XLSX. Без mocks.")
    public void exportsEachActiveFilter() throws Exception {
        var journal = openClean();
        var params = filters();
        params.put("productSearch", fixture.alpha.getProduct());
        journal.changeFilters(params, () -> journal.product(fixture.alpha.getProduct()));
        verify(journal, params, "TC-NSP-XLSX-UI-001 — назва виробу", fixture.alpha);
        journal.changeFilters(filters(), journal::clear);
        params = filters(); params.put("statuses", "DONE");
        journal.changeFilters(params, () -> journal.status("Завершено"));
        verify(journal, params, "TC-NSP-XLSX-UI-001 — завершені", fixture.both);
        journal.changeFilters(filters(), journal::clear);
        params = filters(); params.put("startDate", fixture.today); params.put("endDate", fixture.today);
        journal.changeFilters(params, () -> journal.dates(fixture.today, fixture.today));
        verify(journal, params, "TC-NSP-XLSX-UI-001 — один день", fixture.alpha, fixture.both, fixture.decoy, fixture.noResources);
        journal.changeFilters(filters(), journal::clear);
        params = filters(); params.put("resourceSearch", fixture.materialTerm);
        journal.changeFilters(params, () -> journal.resourceSearchInput().fill(fixture.materialTerm));
        verify(journal, params, "TC-NSP-XLSX-UI-001 — матеріал", fixture.alpha, fixture.both, fixture.older);
    }

    @Test @TestCaseId("TC-NSP-XLSX-UI-002")
    @Description("AC-02,03. AND назви, матеріалу, дат і статусу в UI/export. Зміна статусу й назви, "
            + "видалення лише назви та загальне очищення формують нові файли без застарілих умов.")
    public void exportsCombinedChangedAndClearedFilters() throws Exception {
        var journal = openClean();
        var params = filters(); params.put("resourceSearch", fixture.materialTerm);
        journal.changeFilters(params, () -> journal.resourceSearchInput().fill(fixture.materialTerm));
        params.put("startDate", fixture.today); params.put("endDate", fixture.today);
        journal.changeFilters(params, () -> journal.dates(fixture.today, fixture.today));
        params.put("statuses", "DONE");
        journal.changeFilters(params, () -> journal.status("Завершено"));
        params.put("productSearch", fixture.both.getProduct());
        journal.changeFilters(params, () -> journal.product(fixture.both.getProduct()));
        verify(journal, params, "TC-NSP-XLSX-UI-002 — усі фільтри", fixture.both);
        params.remove("productSearch");
        journal.changeFilters(params, () -> journal.product(""));
        params.put("statuses", "IN_PROGRESS");
        journal.changeFilters(params, () -> journal.status("В роботі"));
        verify(journal, params, "TC-NSP-XLSX-UI-002 — змінено статус і очищено назву", fixture.alpha);
        journal.changeFilters(filters(), journal::clear);
        verify(journal, filters(), "TC-NSP-XLSX-UI-002 — очищено всі фільтри",
                fixture.alpha, fixture.both, fixture.decoy, fixture.noResources, fixture.older);
        assertThat(page.getByPlaceholder("Назва продукту...")).hasValue("");
        assertThat(journal.resourceSearchInput()).hasValue("");
    }

    @Test @TestCaseId("TC-NSP-XLSX-UI-003")
    @Description("AC-04. Немає збігів: порожній журнал, об'єм 0, успішне завантаження XLSX тільки із заголовками.")
    public void exportsEmptySelection() throws Exception {
        var journal = openClean();
        var params = filters(); params.put("productSearch", fixture.marker + "-missing");
        journal.changeFilters(params, () -> journal.product(fixture.marker + "-missing"));
        verify(journal, params, "TC-NSP-XLSX-UI-003 — порожня вибірка");
    }

    @Test(priority = 100) @TestCaseId("TC-NSP-XLSX-UI-004")
    @Description("AC-05,07. 26 власних записів, size=25, друга сторінка UI. Excel містить усі 26, "
            + "query не містить page/size. Записи іншої локації виключено; залишки обох локацій незмінні.")
    public void exportsEveryPageFromSecondUiPage() throws Exception {
        var journal = openClean();
        List<NonSeriesProductionResponse> expected = new ArrayList<>(List.of(fixture.alpha, fixture.both,
                fixture.decoy, fixture.noResources, fixture.older));
        for (int i = 0; i < 21; i++) expected.add(fixture.create(fixture.storageId, "Excel " + fixture.marker + " " + i,
                1, NonSeriesProductionStatus.IN_PROGRESS, fixture.today));
        var before = fixture.inventorySnapshot(fixture.storageId);
        var otherBefore = fixture.inventorySnapshot(fixture.otherStorageId);
        // Reopening restores the UI default period; clear it again before testing unfiltered pagination.
        journal.open().filterByResource(fixture.materialTerm).clearAllFilters(fixture.materialTerm);
        page.getByRole(AriaRole.COMBOBOX).last().click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("25").setExact(true)).click();
        assertThat(page.getByText("Сторінка 1 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Далі").setExact(true)).click();
        assertThat(page.getByText("Сторінка 2 з 2", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.locator("tbody tr")).hasCount(1);
        journal.waitForDisplayedTotalVolume(BigDecimal.valueOf(49));
        journal.attachScreenshot("TC-NSP-XLSX-UI-004 — друга сторінка з 26 записів");
        NonSeriesExportAssertions.assertMatches(journal.download(filters()), expected);
        assertThat(fixture.inventorySnapshot(fixture.storageId)).isEqualTo(before);
        assertThat(fixture.inventorySnapshot(fixture.otherStorageId)).isEqualTo(otherBefore);
    }
}
