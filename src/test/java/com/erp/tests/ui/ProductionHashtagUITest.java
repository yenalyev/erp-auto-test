package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.models.response.ManufacturingItemResponse;
import com.erp.pages.ProductionPage;
import com.erp.utils.helpers.ProductionHashtagUiVerification;
import com.erp.utils.helpers.ProductionTagAssertions;
import io.qameta.allure.*;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Production")
@Feature("Hashtags / Notes UI")
public class ProductionHashtagUITest extends ProductionHashtagUITestBase {

    @Test(priority = 10)
    @TestCaseId("TC-UI-PROD-TAG-001")
    @Story("Happy path E2E")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: техкарта з #тегом → create production → UI: перевірка приміток,
            підсвічення тегу та фільтра badge з cross-check API.
            """)
    public void happyPathShowsInheritedTagAndFilter() {
        String tag = uniqueUiTag("e2e-happy");
        var isolated = prepareIsolatedTechMap();
        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, tag + " e2e");

        ManufacturingItemResponse production = createProduction(isolated);
        Allure.parameter("batchNumber", production.getBatchNumber());
        Allure.parameter("tag", tag);

        ProductionPage journal = Allure.step("Відкрити журнал виробництва", this::openProductionJournal);
        journal.filterByProduct(isolated.getProduct().getName());
        journal.attachScreenshot("TC-UI-PROD-TAG-001 — journal with production");

        ProductionHashtagUiVerification.assertJournalContainsBatch(journal, production.getBatchNumber());
        ProductionHashtagUiVerification.assertNotesContainTag(journal, production.getBatchNumber(), tag);
        ProductionHashtagUiVerification.assertTagFilterShowsOnlyBatches(
                journal, productionFixture, storageId, tag, isolated.getProduct().getName(),
                production.getBatchNumber());

        log.info("TC-UI-PROD-TAG-001 PASSED — batch={}, tag={}", production.getBatchNumber(), tag);
    }

    @Test(priority = 20)
    @TestCaseId("TC-UI-PROD-TAG-002")
    @Story("Edit notes without hash on UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI: редагування приміток без # — звичайний текст, тег більше не фільтрує.")
    public void editNotesWithoutHashRemovesTagFilter() {
        String tag = uniqueUiTag("ui-plain");
        var isolated = prepareIsolatedTechMap();
        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, tag);

        ManufacturingItemResponse production = createProduction(isolated);
        ProductionPage journal = openProductionJournal();
        journal.filterByProduct(isolated.getProduct().getName());

        String plainNotes = "Звичайна примітка UI " + System.currentTimeMillis();
        journal.openNotesEditorForBatch(production.getBatchNumber())
                .fillNotesDialog(plainNotes)
                .saveNotesDialog();

        int rowIndex = journal.findRowIndexByBatchNumber(production.getBatchNumber());
        assertThat(journal.getNotesTextForRow(rowIndex)).isEqualTo(plainNotes);
        assertThat(journal.getHighlightedTagsForRow(rowIndex)).isEmpty();

        journal.clearFilters().filterByProduct(isolated.getProduct().getName());
        ProductionTagAssertions.assertNotFilteredByTag(
                productionFixture, storageId, tag, production.getId());

        log.info("TC-UI-PROD-TAG-002 PASSED — batch={}", production.getBatchNumber());
    }

    @Test(priority = 30)
    @TestCaseId("TC-UI-PROD-TAG-003")
    @Story("Space inside tag on UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI: «#два слова» — підсвічується лише #два.")
    public void spaceInsideTagShowsFirstTokenOnly() {
        var isolated = prepareIsolatedTechMap();
        ManufacturingItemResponse production = createProduction(isolated);

        ProductionPage journal = openProductionJournal();
        journal.filterByProduct(isolated.getProduct().getName());
        journal.openNotesEditorForBatch(production.getBatchNumber())
                .fillNotesDialog("#два слова")
                .saveNotesDialog();

        int rowIndex = journal.findRowIndexByBatchNumber(production.getBatchNumber());
        assertThat(journal.getHighlightedTagsForRow(rowIndex)).containsExactly("#два");
        assertThat(journal.getNotesTextForRow(rowIndex)).contains("слова");

        journal.clearFilters().filterByProduct(isolated.getProduct().getName());
        ProductionTagAssertions.assertFilteredByTag(
                productionFixture, storageId, "#два", production.getId());

        log.info("TC-UI-PROD-TAG-003 PASSED — batch={}", production.getBatchNumber());
    }

    @Test(priority = 40)
    @TestCaseId("TC-UI-PROD-TAG-004")
    @Story("Manual tag on production via UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("UI: додати #ручний-ui у примітках виробництва без тегів на техкарті.")
    public void manualTagOnProductionViaUi() {
        var isolated = prepareIsolatedTechMap();
        ManufacturingItemResponse production = createProduction(isolated);
        String manualTag = uniqueUiTag("ручний-ui");

        ProductionPage journal = openProductionJournal();
        journal.filterByProduct(isolated.getProduct().getName());
        journal.openNotesEditorForBatch(production.getBatchNumber())
                .fillNotesDialog(manualTag + " через UI")
                .saveNotesDialog();

        journal.clearFilters().filterByProduct(isolated.getProduct().getName());
        ProductionHashtagUiVerification.assertNotesContainTag(journal, production.getBatchNumber(), manualTag);
        ProductionHashtagUiVerification.assertTagFilterShowsOnlyBatches(
                journal, productionFixture, storageId, manualTag, isolated.getProduct().getName(),
                production.getBatchNumber());

        ManufacturingItemResponse apiItem = productionFixture.getById(
                UserRole.OWNER_1, production.getId(), storageId);
        ProductionHashtagUiVerification.assertNotesMatchApi(journal, apiItem);

        log.info("TC-UI-PROD-TAG-004 PASSED — batch={}, tag={}", production.getBatchNumber(), manualTag);
    }

    @Test(priority = 50)
    @TestCaseId("TC-UI-PROD-TAG-005")
    @Story("Tech map tag change — existing production unchanged on UI")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            API: #старий-ui → create A, змінити техкарту на #новий-ui → create B.
            UI: фільтри показують правильні записи без перетину.
            """)
    public void techMapTagChangePreservesExistingProductionOnUi() {
        String oldTag = uniqueUiTag("старий-ui");
        String newTag = uniqueUiTag("новий-ui");
        var isolated = prepareIsolatedTechMap();

        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, oldTag);
        ManufacturingItemResponse productionA = createProduction(isolated);

        techMapFixture.updateNotes(UserRole.ADMIN, isolated.getTechMap().getId(), storageId, newTag);
        ManufacturingItemResponse productionB = createProduction(isolated);

        ProductionPage journal = openProductionJournal();
        journal.filterByProduct(isolated.getProduct().getName());

        ProductionHashtagUiVerification.assertTagFilterShowsOnlyBatches(
                journal, productionFixture, storageId, oldTag, isolated.getProduct().getName(),
                productionA.getBatchNumber());
        ProductionHashtagUiVerification.assertBatchAbsentAfterTagFilter(
                journal, oldTag, productionB.getBatchNumber());

        journal.clearFilters().filterByProduct(isolated.getProduct().getName());
        ProductionHashtagUiVerification.assertTagFilterShowsOnlyBatches(
                journal, productionFixture, storageId, newTag, isolated.getProduct().getName(),
                productionB.getBatchNumber());
        ProductionHashtagUiVerification.assertBatchAbsentAfterTagFilter(
                journal, newTag, productionA.getBatchNumber());

        log.info("TC-UI-PROD-TAG-005 PASSED — A={}, B={}", productionA.getBatchNumber(), productionB.getBatchNumber());
    }
}
