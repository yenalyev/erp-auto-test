package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.data.factories.defect.DefectDataFactory;
import com.erp.enums.UserRole;
import com.erp.fixtures.DefectFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.DefectResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.pages.AppSidebarPage;
import com.erp.pages.RelocationPage;
import com.erp.utils.helpers.RelocationBatchAssertions;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Checks the real admin form after a supplier-linked relocation defect. */
public class RelocationSupplierDefectEditUiTest extends BaseUITest {
    private DefectFixture fixture;
    private Long storageId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void baseTestClassSetup() {
        super.baseTestClassSetup();
        fixture = new DefectFixture(testContext, apiExecutor);
        fixture.prepareIsolatedContext(getPlaywrightSessionProvider());
        storageId = fixture.getStorageId();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
    }

    @AfterClass(alwaysRun = true)
    public void cleanUpScenario() {
        if (fixture != null) {
            fixture.cleanupIsolatedContext();
        }
    }

    @Test
    @TestCaseId("TC-UI-REL-USED-008")
    @Story("Admin form accepts 4 after supplier defect 8, server rejects save")
    public void adminFormAfterSupplierDefectEight() {
        Long resourceId = fixture.createFreshResource();
        String batchNumber = "ui-supplier-defect-" + UUID.randomUUID();
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, batchNumber);
        DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(
                        storageId, resourceId, receipt.getId(), 8.0, LocalDate.now()));
        assertThat(fixture.getById(UserRole.OWNER_1, defect.getId()).getAmount().doubleValue())
                .isCloseTo(8.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, batchNumber, false, "before admin UI edit").amount())
                .isCloseTo(2.0, within(0.01));

        String storageName = fixture.getIsolatedStorageFixture()
                .getById(UserRole.ADMIN, storageId).getName();
        RelocationPage journal = new RelocationPage(page).open();
        new AppSidebarPage(page).selectWorkspaceByName(storageName);
        journal.openReceivedHistoryTab();
        String invoice = receipt.getInvoiceNumber();
        Locator row = page.locator("tbody tr")
                .filter(new Locator.FilterOptions().setHasText(invoice));
        row.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE));
        assertThat(journal.isEditButtonVisibleInRow(invoice)).isTrue();
        journal.clickEditInRow(invoice);

        assertThat(page.getByRole(AriaRole.HEADING,
                new com.microsoft.playwright.Page.GetByRoleOptions()
                        .setName("Редагування отримання")).isVisible()).isTrue();
        Locator quantity = page.getByPlaceholder("Кількість");
        assertThat(quantity.inputValue()).isEqualTo("10");
        quantity.fill("4");
        assertThat(quantity.inputValue()).isEqualTo("4");
        Locator confirm = page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Підтвердити"));
        assertThat(confirm.isEnabled()).as("admin form accepts quantity 4 after defect 8").isTrue();
        attachScreenshot("Supplier defect 8 — admin edit form filled with 4");

        com.microsoft.playwright.Response uiResponse = page.waitForResponse(
                response -> response.request().method().equals("PUT")
                        && response.url().contains("/relocations/")
                        && response.url().contains("/receive"),
                new com.microsoft.playwright.Page.WaitForResponseOptions().setTimeout(15_000),
                confirm::click);
        assertThat(uiResponse.status()).isEqualTo(400);
        Locator error = page.getByText("Недостатньо залишку ресурсу").first();
        error.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10_000));
        assertThat(error.isVisible()).isTrue();
        assertThat(quantity.inputValue()).isEqualTo("4");
        attachScreenshot("Supplier defect 8 — save rejected with visible error");
        RelocationResponse savedReceipt = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.ADMIN).stream()
                .filter(item -> receipt.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        DefectResponse savedDefect = fixture.getById(UserRole.OWNER_1, defect.getId());
        double stock = fixture.resourceStock(resourceId);
        double batchStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, batchNumber, false, "after admin UI edit").amount();
        assertThat(savedReceipt.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(10.0, within(0.01));
        assertThat(savedDefect.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(stock).isCloseTo(2.0, within(0.01));
        assertThat(batchStock).isCloseTo(2.0, within(0.01));
    }

    @Test
    @TestCaseId("TC-UI-REL-USED-009")
    @Story("Admin must not save receipt 4 after linked supplier defect 8 when another batch exists")
    public void adminFormMustRejectLinkedDefectDespiteOtherBatchStock() {
        Long resourceId = fixture.createFreshResource();
        String receiptBatch = "ui-linked-source-" + UUID.randomUUID();
        String otherBatch = "ui-other-source-" + UUID.randomUUID();
        RelocationResponse receipt = fixture.createExternalReceipt(resourceId, 10.0, receiptBatch);
        fixture.createExternalReceipt(resourceId, 10.0, otherBatch);
        DefectResponse defect = fixture.createAs(UserRole.OWNER_1,
                DefectDataFactory.buildRelocationDefect(
                        storageId, resourceId, receipt.getId(), 8.0, LocalDate.now()));
        DefectResponse savedDefect = fixture.getById(UserRole.OWNER_1, defect.getId());
        assertThat(savedDefect.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(savedDefect.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(fixture.resourceStock(resourceId)).isCloseTo(12.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, receiptBatch, false, "linked batch before UI edit").amount())
                .isCloseTo(2.0, within(0.01));
        assertThat(RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, otherBatch, false, "other batch before UI edit").amount())
                .isCloseTo(10.0, within(0.01));

        String storageName = fixture.getIsolatedStorageFixture()
                .getById(UserRole.ADMIN, storageId).getName();
        RelocationPage journal = new RelocationPage(page).open();
        new AppSidebarPage(page).selectWorkspaceByName(storageName);
        journal.openReceivedHistoryTab();
        String invoice = receipt.getInvoiceNumber();
        page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(invoice))
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        journal.clickEditInRow(invoice);
        Locator quantity = page.getByPlaceholder("Кількість");
        assertThat(quantity.inputValue()).isEqualTo("10");
        quantity.fill("4");
        Locator confirm = page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Підтвердити"));
        assertThat(confirm.isEnabled()).isTrue();
        attachScreenshot("Other batch present — admin enters 4 after linked defect 8");

        com.microsoft.playwright.Response uiResponse = page.waitForResponse(
                response -> response.request().method().equals("PUT")
                        && response.url().contains("/relocations/")
                        && response.url().contains("/receive"),
                new com.microsoft.playwright.Page.WaitForResponseOptions().setTimeout(15_000),
                confirm::click);
        RelocationResponse savedReceipt = fixture.getRelocationFixture().getJournalPage(
                        RelocationJournalQuery.receivedHistoryUi(storageId).toBuilder()
                                .pageSize(100).build(), UserRole.ADMIN).stream()
                .filter(item -> receipt.getId().equals(item.getId()))
                .findFirst().orElseThrow();
        DefectResponse defectAfterEdit = fixture.getById(UserRole.OWNER_1, defect.getId());
        double stock = fixture.resourceStock(resourceId);
        double linkedStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, receiptBatch, false, "linked batch after UI edit").amount();
        double otherStock = RelocationBatchAssertions.captureBatch(apiExecutor, storageId,
                UserRole.OWNER_1, resourceId, otherBatch, false, "other batch after UI edit").amount();
        attachScreenshot("Other batch present — result of admin save attempt");
        assertThat(uiResponse.status())
                .as("admin UI save: receipt=%s, defect=%s, stock=%s, linked batch=%s, other batch=%s",
                        savedReceipt.getItems().getFirst().getAmount(), defectAfterEdit.getAmount(),
                        stock, linkedStock, otherStock)
                .isBetween(400, 499);
        assertThat(savedReceipt.getItems().getFirst().getAmount().doubleValue())
                .isCloseTo(10.0, within(0.01));
        assertThat(defectAfterEdit.getRelocationId()).isEqualTo(receipt.getId());
        assertThat(defectAfterEdit.getAmount().doubleValue()).isCloseTo(8.0, within(0.01));
        assertThat(stock).isCloseTo(12.0, within(0.01));
        assertThat(linkedStock).isCloseTo(2.0, within(0.01));
        assertThat(otherStock).isCloseTo(10.0, within(0.01));
    }
}
