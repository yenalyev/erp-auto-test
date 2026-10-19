package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InventoryConcurrentIssueFixture;
import com.erp.pages.*;
import com.erp.utils.config.ConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.annotations.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Inventory")
@Feature("REQ-WMS-003 Concurrent issue")
public class InventoryConcurrentIssueUiTest extends BaseUITest {
    private InventoryConcurrentIssueFixture fixture;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void prepareConcurrentInventory() {
        fixture = new InventoryConcurrentIssueFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @BeforeMethod(alwaysRun = true)
    public void resetOwnedStock() {
        fixture.reset();
        injectSessionCookies(getPlaywrightSessionProvider().getSession(
                UserRole.ADMIN.getUsername(), UserRole.ADMIN.getPassword()), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.source.getId());
    }

    @AfterMethod(alwaysRun = true)
    public void closeOwnedSession() { if (fixture != null) fixture.closeSession(); }

    @AfterClass(alwaysRun = true)
    public void cleanupOwnedData() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-UI-001")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Дві реальні вкладки: змінити A, видати B частково й C повністю, отримати 412, прийняти актуальні, перевірити залишки й запис інвентаризації.")
    public void parallelMixedIssueTakeAllCurrentAmounts() throws Exception {
        verifyTwoTabScenario(false, false);
    }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-UI-002")
    @Severity(SeverityLevel.CRITICAL)
    public void conflictsCanBeResolvedIndividuallyWithoutLosingDraft() throws Exception {
        verifyTwoTabScenario(true, false);
    }

    @Test
    @TestCaseId("TC-INV-CONCURRENT-UI-003")
    @Severity(SeverityLevel.CRITICAL)
    public void keepingOwnEditedAmountRequiresExplicitChoiceAndUsesCurrentDelta() throws Exception {
        verifyTwoTabScenario(false, true);
    }

    private void verifyTwoTabScenario(boolean individual, boolean sameResource) throws Exception {
        UnitManagementPage stock = new UnitManagementPage(page).openForStorage(fixture.source.getId());
        stock.waitForConductButtonEnabled().clickConductInventory();
        InventoryEditPage form = new InventoryEditPage(page).waitForLoaded();
        String comment = fixture.marker + " UI completion";
        form.updateAmountForResource(fixture.edited.getName(), "15").fillComment(comment);
        var historyBefore = fixture.inventoryHistory();

        try (Page issuePage = browserContext.newPage()) {
            issuePage.navigate(ConfigProvider.getBaseUrl() + RelocationCreateOutputPage.PATH);
            RelocationCreateOutputPage issue = new RelocationCreateOutputPage(issuePage).waitForLoaded()
                    .selectRecipientByLabel(fixture.recipient.getName());
            if (sameResource) {
                issue.selectOutputResourceByName(fixture.edited.getName()).fillOutputQuantity("4");
            } else {
                issue.selectOutputResourceByName(fixture.partial.getName()).fillOutputQuantity("7")
                        .clickAddPosition().selectOutputResourceByName(1, fixture.exhausted.getName())
                        .fillOutputQuantity(1, "30");
            }
            issue.fillDescription(fixture.marker + " second tab").fillInvoiceIssuerDefaults();
            var sent = issuePage.waitForResponse(r -> r.url().contains("/relocations/send")
                    && "POST".equals(r.request().method()), issue::submitSendExpectSuccess);
            fixture.trackIssue(new ObjectMapper().readTree(sent.text()).path("id").asLong());
            issue.attachScreenshot("Concurrent issue — second tab submitted");
        }
        fixture.assertStock(sameResource ? 6 : 10, sameResource ? 20 : 13, sameResource ? 30 : 0);
        // Keep the first tab untouched so that it submits the actual stale snapshot.
        assertThat(form.getResourceAmountInputValue(fixture.edited.getName())).isEqualTo("15");
        var rejected = form.saveChangesRaw();
        assertThat(rejected.status()).as("Stale browser form must be rejected").isEqualTo(412);
        var payload = new ObjectMapper().readTree(rejected.request().postData());
        assertThat(payload.path("knownResourceIds").size()).isEqualTo(3);
        payload.path("resources").forEach(row -> assertThat(row.path("version").isIntegralNumber()).isTrue());
        form.waitForConflicts();
        assertThat(form.isSaveEnabled()).as("Unresolved conflicts block submit").isFalse();
        assertThat(form.getCommentFieldValue()).isEqualTo(comment);
        assertThat(fixture.currentProcess().getState()).isEqualTo("OPEN");
        assertThat(fixture.inventoryHistory()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(historyBefore);
        if (sameResource) {
            assertThat(form.conflictCount()).isEqualTo(1);
            assertThat(form.conflictText(fixture.edited.getId())).contains("було 10", "зараз 6", "ваше 15");
            form.attachScreenshot("Concurrent inventory — explicit keep own amount");
            form.keepOwnAmount(fixture.edited.getId());
        } else {
            assertThat(form.conflictCount()).isEqualTo(2);
            assertThat(form.conflictText(fixture.partial.getId())).contains("було 20", "зараз 13", "ваше 20");
            assertThat(form.conflictText(fixture.exhausted.getId())).contains("було 30", "зараз 0", "ваше 30");
            form.attachScreenshot("Concurrent inventory — partial and full issue conflicts");
            if (individual) {
                form.takeCurrentAmount(fixture.partial.getId());
                assertThat(form.isSaveEnabled()).isFalse();
                form.takeCurrentAmount(fixture.exhausted.getId());
            } else form.takeAllCurrentAmounts();
            assertThat(form.getResourceAmountInputValue(fixture.partial.getName())).isEqualTo("13");
            assertThat(form.getResourceAmountInputValue(fixture.exhausted.getName())).isEqualTo("0");
        }
        assertThat(form.getResourceAmountInputValue(fixture.edited.getName())).isEqualTo("15");
        assertThat(form.isSaveEnabled()).isTrue();
        form.saveChanges();
        fixture.assertStock(15, sameResource ? 20 : 13, sameResource ? 30 : 0);
        fixture.assertOnlyEditedDiff(sameResource ? 9 : 5, comment);
        new UnitManagementPage(page).openForStorage(fixture.source.getId())
                .assertResourceAmountVisible(fixture.edited.getName(), 15)
                .assertResourceAmountVisible(fixture.partial.getName(), sameResource ? 20 : 13);
        InventoryProcessesPage processes = new InventoryProcessesPage(page).open().showClosed();
        assertThat(processes.resultCellText(fixture.process.getId()))
                .contains(fixture.edited.getName(), sameResource ? "+9" : "+5")
                .doesNotContain(fixture.partial.getName(), fixture.exhausted.getName());
        assertThat(processes.completionCellText(fixture.process.getId())).contains(comment);
        processes.attachScreenshot("Concurrent inventory — closed record has only intentional delta");
        page.reload();
        assertThat(new InventoryProcessesPage(page).waitForLoaded().showClosed()
                .resultCellText(fixture.process.getId()))
                .contains(fixture.edited.getName()).doesNotContain(fixture.partial.getName(), fixture.exhausted.getName());
    }
}
