package com.erp.tests.ui;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.CommercialDocumentFixture;
import com.erp.pages.CommercialDocumentPage;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Epic("Документообіг переміщень")
@Feature("Новий комерційний документ — обов'язкові поля")
@Severity(SeverityLevel.CRITICAL)
public class CommercialDocumentRequiredFieldsUiTest extends BaseUITest {
    private CommercialDocumentFixture fixture;
    private CommercialDocumentPage form;

    @BeforeClass(alwaysRun = true)
    public void prepareLedger() {
        fixture = new CommercialDocumentFixture(testContext, apiExecutor);
        fixture.prepare();
        injectSessionCookies(cachedSessionCookies(UserRole.ADMIN), sessionCookieDomain());
        injectWorkspaceView(UserRole.ADMIN, fixture.storageId);
    }

    @BeforeMethod(alwaysRun = true)
    public void openForm() {
        form = new CommercialDocumentPage(page);
        form.open();
    }

    @AfterMethod(alwaysRun = true)
    public void releaseReceipts() { if (fixture != null) fixture.cleanupDocuments(); }

    @AfterClass(alwaysRun = true)
    public void cleanupLedger() { if (fixture != null) fixture.cleanup(); }

    @Test
    @TestCaseId("TC-CD-REQ-UI-001")
    @Story("Обов'язкові поля позначені до введення даних")
    public void requiredMarkersAppearOnLabelsAndResourceColumns() {
        form.attachScreenshot("TC-CD-REQ-UI-001 — initial required markers");
        form.assertRequired(form.label("Номер"));
        form.assertRequired(form.label("Дата документа"));
        form.assertRequired(form.filesButton());
        assertThat(form.label("Коментар")).not().containsText("*");
        form.selectReceipt(fixture.resourceName(0));
        form.attachScreenshot("TC-CD-REQ-UI-001 — resource required markers");
        form.assertRequired(form.header("Сума за все, грн"));
        form.assertRequired(form.header("Назва ресурсу за документом"));
        assertThat(form.header("Код номенклатури")).not().containsText("*");
        assertThat(form.header("Категорія (сорт)")).not().containsText("*");
        assertThat(form.save()).isDisabled();
    }

    @Test
    @TestCaseId("TC-CD-REQ-UI-002")
    @Story("Номер, файл та надходження керують доступністю збереження")
    @Description("Кожне поле очищається окремо на повністю валідній формі. "
            + "Необов'язкові поля лишаються порожніми, total=0 дозволений. "
            + "Заблоковані та скасовані форми не надсилають POST.")
    public void saveRequiresNumberFileAndReceiptAndCancelResetsForm() {
        AtomicInteger posts = new AtomicInteger();
        page.onRequest(r -> { if (r.method().equals("POST") && r.url().endsWith("/commercial-documents"))
            posts.incrementAndGet(); });
        var before = fixture.uncovered();
        fillValidForm();
        assertThat(form.save()).isEnabled();
        form.number().fill("");
        assertThat(form.save()).isDisabled();
        form.number().fill("   ");
        assertThat(form.save()).isDisabled();
        form.number().fill("AUTO-UI-" + UUID.randomUUID());
        assertThat(form.save()).isEnabled();
        form.removeFile();
        assertThat(form.save()).isDisabled();
        form.addFile(CommercialDocumentFixture.file());
        assertThat(form.save()).isEnabled();
        form.selectReceipt(fixture.resourceName(0));
        assertThat(form.save()).isDisabled();
        form.selectReceipt(fixture.resourceName(0));
        assertThat(form.save()).isEnabled();
        form.attachScreenshot("TC-CD-REQ-UI-002 — complete required fields with zero total");
        form.cancel();
        form.newDocument();
        assertThat(form.number()).isEmpty();
        assertThat(form.dialog().getByTitle("Прибрати")).hasCount(0);
        assertThat(form.dialog().locator("[role=checkbox][data-state=checked]")).hasCount(0);
        assertThat(form.save()).isDisabled();
        org.assertj.core.api.Assertions.assertThat(posts.get()).isZero();
        org.assertj.core.api.Assertions.assertThat(fixture.documents()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(fixture.uncovered()).isEqualTo(before);
    }

    @Test
    @TestCaseId("TC-CD-REQ-UI-003")
    @Story("Сума й назва потрібні для кожного вибраного ресурсу")
    public void eachResourceRequiresTotalAndNonblankDocumentName() {
        fillValidForm();
        String second = fixture.resourceName(1);
        form.selectReceipt(second);
        assertThat(form.save()).isDisabled();
        form.total(second).fill("0");
        assertThat(form.save()).isDisabled();
        form.documentName(second).fill("   ");
        assertThat(form.save()).isDisabled();
        form.documentName(second).fill("Друга назва за документом");
        assertThat(form.save()).isEnabled();
        for (int i = 0; i < 2; i++) {
            String resource = fixture.resourceName(i);
            form.total(resource).fill("");
            assertThat(form.save()).isDisabled();
            form.total(resource).fill("0");
            assertThat(form.save()).isEnabled();
            form.documentName(resource).fill("");
            assertThat(form.save()).isDisabled();
            form.documentName(resource).fill("Назва " + i);
            assertThat(form.save()).isEnabled();
        }
        form.attachScreenshot("TC-CD-REQ-UI-003 — both resource lines valid");
        org.assertj.core.api.Assertions.assertThat(fixture.documents()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(fixture.uncovered()).hasSize(2);
    }

    @Test
    @TestCaseId("TC-CD-REQ-UI-004")
    @Story("Збереження й повторне відкриття з лише обов'язковими полями")
    public void saveAndReopenRequiredFieldsOnly() {
        var beforeLedger = fixture.ledger();
        fillValidForm();
        String number = form.number().inputValue();
        String dateText = form.date().innerText().trim();
        String dateIso = java.time.LocalDate.parse(dateText,
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString();
        form.attachScreenshot("TC-CD-REQ-UI-004 — ready to save");
        var response = form.submit(fixture.storageId);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        Long id;
        try { id = json.readTree(response.body()).get("id").asLong(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        fixture.trackDocument(id);
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(200);
        assertThat(form.dialog()).hasCount(0);
        var saved = fixture.document(id);
        org.assertj.core.api.Assertions.assertThat(saved).containsEntry("number", number)
                .containsEntry("documentDate", dateIso)
                .containsEntry("comment", null).containsEntry("status", "NEW");
        var lines = (List<Map<String, Object>>) saved.get("resources");
        org.assertj.core.api.Assertions.assertThat(lines).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(lines.getFirst()).containsEntry("documentResourceName", "Назва з UI")
                .containsEntry("nomenclatureCode", null).containsEntry("grade", null);
        org.assertj.core.api.Assertions.assertThat(((Number) lines.getFirst().get("total")).doubleValue()).isZero();
        org.assertj.core.api.Assertions.assertThat((List<?>) saved.get("files")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(fixture.uncovered()).hasSize(1)
                .allSatisfy(r -> org.assertj.core.api.Assertions.assertThat(((Number) r.get("id")).longValue())
                        .isEqualTo(fixture.receiptId(1)));
        org.assertj.core.api.Assertions.assertThat(fixture.ledger().stream()
                .filter(r -> ((Number) r.get("resourceId")).longValue() == fixture.resourceId(1)).toList())
                .isEqualTo(beforeLedger.stream()
                        .filter(r -> ((Number) r.get("resourceId")).longValue() == fixture.resourceId(1)).toList());
        form.reopen(number);
        form.attachScreenshot("TC-CD-REQ-UI-004 — persisted fields reopened");
        assertThat(form.number()).hasValue(number);
        assertThat(form.total(fixture.resourceName(0))).hasValue("0");
        assertThat(form.documentName(fixture.resourceName(0))).hasValue("Назва з UI");
        assertThat(form.comment()).isEmpty();
        assertThat(form.date()).hasText(dateText);
        assertThat(form.save()).isEnabled();
    }

    private void fillValidForm() {
        form.number().fill("AUTO-UI-" + UUID.randomUUID());
        form.addFile(CommercialDocumentFixture.file());
        form.selectReceipt(fixture.resourceName(0));
        form.total(fixture.resourceName(0)).fill("0");
        form.documentName(fixture.resourceName(0)).fill("Назва з UI");
    }
}
