package com.erp.tests.functional.relocation;

import com.erp.annotations.TestCaseId;
import com.erp.fixtures.CommercialDocumentFixture;
import com.erp.tests.functional.BaseFunctionalTest;
import io.qameta.allure.*;
import org.testng.annotations.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Документообіг переміщень")
@Feature("Новий комерційний документ — обов'язкові поля")
@Severity(SeverityLevel.CRITICAL)
public class CommercialDocumentRequiredFieldsApiTest extends BaseFunctionalTest {
    private CommercialDocumentFixture fixture;

    @BeforeClass(alwaysRun = true)
    public void prepareLedger() {
        fixture = new CommercialDocumentFixture(testContext, apiExecutor);
        fixture.prepare();
    }

    @AfterClass(alwaysRun = true)
    public void cleanupLedger() { if (fixture != null) fixture.cleanup(); }

    @AfterMethod(alwaysRun = true)
    public void releaseReceipts() { if (fixture != null) fixture.cleanupDocuments(); }

    @DataProvider
    public Object[][] headerCases() {
        return new Object[][] {
                {"number", "absent"}, {"number", "null"}, {"number", "empty"}, {"number", "blank"},
                {"documentDate", "absent"}, {"documentDate", "null"},
                {"receiptIds", "absent"}, {"receiptIds", "null"}, {"receiptIds", "empty"}
        };
    }

    @Test(dataProvider = "headerCases")
    @TestCaseId("TC-CD-REQ-API-001")
    @Story("Номер, дата й надходження обов'язкові")
    @Description("Multipart request + files як у handleSave; змінюється лише одне поле. "
            + "HTTP 400, помилка відповідного поля, документи, надходження та залишки обліку без змін.")
    public void rejectMissingHeader(String field, String variant) {
        var body = fixture.request();
        mutate(body, field, variant);
        assertRejected(body, CommercialDocumentFixture.file(), field);
    }

    @DataProvider
    public Object[][] lineCases() {
        return new Object[][] {
                {"lines", "absent"}, {"lines", "null"}, {"lines", "empty"},
                {"total", "absent"}, {"total", "null"},
                {"documentResourceName", "absent"}, {"documentResourceName", "null"},
                {"documentResourceName", "empty"}, {"documentResourceName", "blank"}
        };
    }

    @Test(dataProvider = "lineCases")
    @TestCaseId("TC-CD-REQ-API-002")
    @Story("Кожен ресурс потребує суми й назви за документом")
    @Description("Два вибрані надходження різних ресурсів; перший рядок валідний, "
            + "у другому відсутня сума або назва. Запит не має створювати частковий документ.")
    public void rejectMissingLineFields(String field, String variant) {
        var body = fixture.request();
        body.put("receiptIds", List.of(fixture.receiptId(0), fixture.receiptId(1)));
        var second = fixture.line(1);
        body.put("lines", List.of(fixture.line(0), second));
        mutate(field.equals("lines") ? body : second, field, variant);
        // Empty lines pass basic validation but fail the resource/line correspondence check.
        assertRejected(body, CommercialDocumentFixture.file(),
                field.equals("lines") && variant.equals("empty") ? null : "lines");
    }

    @DataProvider
    public Object[][] missingFiles() { return new Object[][] {{false}, {true}}; }

    @Test(dataProvider = "missingFiles")
    @TestCaseId("TC-CD-REQ-API-003")
    @Story("Потрібен хоча б один непорожній файл")
    public void rejectAbsentOrEmptyFile(boolean emptyPart) {
        assertRejected(fixture.request(), emptyPart ? new byte[0] : null, "files");
    }

    @Test
    @TestCaseId("TC-CD-REQ-API-004")
    @Story("Нульова сума та порожні необов'язкові поля дозволені")
    @Description("Повний клієнтський multipart: номер, дата, надходження, lines(total=0, "
            + "documentResourceName), files. Коментар, код номенклатури та сорт відсутні. "
            + "Збереження підтверджується окремим GET; сусіднє надходження та облік не змінюються.")
    public void createWithOnlyRequiredFields() {
        var beforeLedger = fixture.ledger();
        var sibling = fixture.uncovered().stream()
                .filter(r -> ((Number) r.get("id")).longValue() == fixture.receiptId(1)).findFirst().orElseThrow();
        var beforeDocs = fixture.documents();
        var body = fixture.request();
        var created = fixture.create(body, CommercialDocumentFixture.file());
        assertThat(created.statusCode()).as(created.asString()).isEqualTo(200);
        Long id = created.jsonPath().getLong("id");
        var saved = fixture.document(id);
        assertThat(saved).containsEntry("number", body.get("number"))
                .containsEntry("documentDate", fixture.date).containsEntry("status", "NEW")
                .containsEntry("comment", null);
        assertThat((List<?>) saved.get("files")).hasSize(1);
        var lines = (List<Map<String, Object>>) saved.get("resources");
        assertThat(lines).hasSize(1);
        assertThat(((Number) lines.getFirst().get("total")).doubleValue()).isZero();
        assertThat(lines.getFirst()).containsEntry("documentResourceName", "Ресурс за документом 0")
                .containsEntry("nomenclatureCode", null).containsEntry("grade", null);
        var receipts = (List<Map<String, Object>>) saved.get("receipts");
        assertThat(receipts).extracting(r -> ((Number) r.get("id")).longValue())
                .containsExactly(fixture.receiptId(0));
        assertThat(fixture.documents()).hasSize(beforeDocs.size() + 1).containsAll(beforeDocs);
        assertThat(fixture.uncovered()).containsExactly(sibling);
        // lastCommercialDocumentDate is expected to change for the touched resource only.
        var afterLedger = fixture.ledger();
        assertThat(afterLedger.stream().filter(r -> ((Number) r.get("resourceId")).longValue()
                == fixture.resourceId(1)).toList()).isEqualTo(beforeLedger.stream()
                .filter(r -> ((Number) r.get("resourceId")).longValue() == fixture.resourceId(1)).toList());
        assertThat(afterLedger).extracting(r -> r.get("receivedAmount"))
                .containsExactlyElementsOf(beforeLedger.stream().map(r -> r.get("receivedAmount")).toList());
    }

    private void assertRejected(Map<String, Object> body, byte[] file, String field) {
        var beforeDocs = fixture.documents();
        var beforeReceipts = fixture.uncovered();
        var beforeLedger = fixture.ledger();
        var response = fixture.create(body, file);
        var errors = new org.assertj.core.api.SoftAssertions();
        errors.assertThat(response.statusCode()).as(response.asString()).isEqualTo(400);
        if (field != null) errors.assertThat(response.asString()).contains("\"" + field + "\"");
        errors.assertThat(fixture.documents()).as("No partial document").isEqualTo(beforeDocs);
        errors.assertThat(fixture.uncovered()).as("Receipts stay uncovered").isEqualTo(beforeReceipts);
        errors.assertThat(fixture.ledger()).as("Ledger unchanged").isEqualTo(beforeLedger);
        errors.assertAll();
    }

    private static void mutate(Map<String, Object> target, String field, String variant) {
        switch (variant) {
            case "absent" -> target.remove(field);
            case "null" -> target.put(field, null);
            case "blank" -> target.put(field, "   ");
            case "empty" -> target.put(field, Set.of("lines", "receiptIds").contains(field) ? List.of() : "");
            default -> throw new IllegalArgumentException(variant);
        }
    }
}
