package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationSupplierFiltersFixture;
import com.erp.models.response.ResourceResponse;
import com.erp.pages.RelocationSupplierFiltersPage;
import com.erp.utils.assertions.RelocationEquipmentExportAssertions;
import com.erp.utils.helpers.XlsxWorkbookReader;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;

import static com.erp.fixtures.RelocationSupplierFiltersFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Relocation")
@Feature("REQ-REL-SUPPLIER — Постачальник у Видати/Отримати")
@Severity(SeverityLevel.CRITICAL)
public class RelocationSupplierFiltersUiTest extends BaseUITest {
    private RelocationSupplierFiltersFixture data;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        data = new RelocationSupplierFiltersFixture(testContext, apiExecutor);
        data.prepare();
        data.prepareEquipment();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, data.source.getId());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupSupplierData() { if (data != null) data.cleanup(); }

    @DataProvider(name = "tabs")
    public Object[][] tabs() { return new Object[][] {{"active"}, {"sent"}, {"received"}, {"lost"}}; }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-UI-001")
    public void optionsSearchAndSingleSupplierFilter(String tab) {
        RelocationSupplierFiltersPage journal = scoped(tab);
        assertThat(journal.hasField("Постачальник")).isTrue();
        assertThat(journal.chips()).isEmpty();
        assertThat(journal.searchSuppliers("")).contains(MOU, OTHER).doesNotHaveDuplicates();
        assertThat(journal.searchSuppliers("МО")).contains(MOU).doesNotContain(OTHER);
        assertThat(journal.searchSuppliers(data.marker)).isEmpty();
        assertThat(journal.searchSuppliers("")).contains(MOU, OTHER);
        journal.closeSuppliers();
        journal.selectSupplier(MOU);
        cargo(journal, tab, true, false, false);
        journal.attachScreenshot("TC-REL-SUP-UI-001 — " + tab + " single supplier");
        data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), data.expectedIds(tab).size(), data.first);
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-UI-002")
    public void multipleSuppliersAndSameItemIntersection(String tab) {
        RelocationSupplierFiltersPage journal = scoped(tab);
        journal.selectSupplier(MOU);
        journal.selectSupplier(OTHER);
        cargo(journal, tab, true, true, false);
        data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), data.expectedIds(tab).size(), data.first, data.sameCategory);
        journal.removeSupplier(OTHER);
        journal.category(data.marker + "-category-A", data.categoryA);
        journal.product(data.sameCategory.getName(), data.sameCategory.getId());
        cargo(journal, tab, false, false, false);
        journal.attachScreenshot("TC-REL-SUP-UI-002 — " + tab + " empty same-item intersection");
        data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), 0);
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-UI-003")
    public void removingChipsRestoresAllCargoAndReopeningClearsSelection(String tab) {
        RelocationSupplierFiltersPage journal = scoped(tab);
        journal.selectSupplier(MOU);
        journal.selectSupplier(OTHER);
        journal.removeSupplier(MOU);
        cargo(journal, tab, false, true, false);
        journal.removeSupplier(OTHER);
        cargo(journal, tab, true, true, true);
        data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), data.expectedIds(tab).size(),
                data.first, data.sameCategory, data.otherCategory);
        journal.selectSupplier(MOU);
        journal = scoped(tab);
        assertThat(journal.chips()).isEmpty();
        cargo(journal, tab, true, true, true);
        journal.attachScreenshot("TC-REL-SUP-UI-003 — " + tab + " reopened without supplier selection");
    }

    @Test(dataProvider = "tabs")
    @TestCaseId("TC-REL-SUP-UI-004")
    @Description("Select supplier, switch to equipment: field hidden and suppliers absent from actual list/totals/export. "
            + "Switch back: previous selection restored and applied. Equipment rows and real download checked.")
    public void equipmentOmitsSupplierAndResourceModeRestoresIt(String tab) {
        RelocationSupplierFiltersPage journal = scoped(tab);
        journal.selectSupplier(MOU);
        journal.type("EQUIPMENT");
        assertThat(journal.hasField("Постачальник")).isFalse();
        journal.equipment(data.modelNameA, data.modelA);
        int count = data.expectedEquipmentIds(tab).size();
        journal.assertCargo(data.marker, Map.of(data.modelNameA, count, data.modelNameB, 0,
                data.first.getName(), 0, data.sameCategory.getName(), 0, data.otherCategory.getName(), 0), count);
        Map<String, Object> query = data.query(tab);
        query.put("type", "EQUIPMENT");
        query.put("equipmentModelIds", List.of(data.modelA));
        RelocationEquipmentExportAssertions.assertMatches(XlsxWorkbookReader.firstSheet(journal.export()), data.rows(query));
        journal.type("RESOURCE");
        assertThat(journal.hasField("Постачальник")).isTrue();
        assertThat(journal.chips()).containsExactly(MOU);
        cargo(journal, tab, true, false, false);
        journal.attachScreenshot("TC-REL-SUP-UI-004 — " + tab + " supplier restored");
        data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), data.expectedIds(tab).size(), data.first);
    }

    @Test
    @TestCaseId("TC-REL-SUP-UI-005")
    public void reopeningUsesUpdatedSupplierOfExistingHistory() {
        RelocationSupplierFiltersPage journal = scoped("sent");
        journal.selectSupplier(MOU);
        cargo(journal, "sent", true, false, false);
        try {
            data.supplier(data.first, OTHER);
            journal = scoped("sent");
            journal.selectSupplier(MOU);
            cargo(journal, "sent", false, false, false);
            journal.removeSupplier(MOU);
            journal.selectSupplier(OTHER);
            cargo(journal, "sent", true, true, false);
            journal.attachScreenshot("TC-REL-SUP-UI-005 — existing history uses updated property");
            data.assertExcel(XlsxWorkbookReader.firstSheet(journal.export()), data.expectedIds("sent").size(),
                    data.first, data.sameCategory);
            assertThat(data.supplierValue(data.sameCategory)).isEqualTo(OTHER);
        } finally { data.supplier(data.first, MOU); }
    }

    private RelocationSupplierFiltersPage scoped(String tab) {
        RelocationSupplierFiltersPage journal = new RelocationSupplierFiltersPage(page).open(tab);
        journal.location("Від кого", data.fromParent.getName(), data.fromParent.getId());
        journal.location("Кому", data.destination.getName(), data.destination.getId());
        journal.dates(data.today, data.today);
        return journal;
    }

    private void cargo(RelocationSupplierFiltersPage journal, String tab, boolean first, boolean same, boolean other) {
        int count = data.expectedIds(tab).size();
        journal.assertCargo(data.marker, Map.of(data.first.getName(), first ? count : 0,
                data.sameCategory.getName(), same ? count : 0, data.otherCategory.getName(), other ? count : 0),
                first || same || other ? count : 0);
        journal.assertVisibleTotal(data.first.getUnit().getShortName(),
                count * ((first ? 2 : 0) + (same ? 3 : 0) + (other ? 5 : 0)));
    }
}
