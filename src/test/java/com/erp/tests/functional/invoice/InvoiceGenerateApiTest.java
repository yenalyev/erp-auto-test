package com.erp.tests.functional.invoice;

import com.erp.annotations.TestCaseId;
import com.erp.enums.UserRole;
import com.erp.fixtures.InvoiceFixture;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.request.InvoiceDataRequest;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.ContextKey;
import com.erp.tests.functional.BaseFunctionalTest;
import com.erp.utils.config.ConfigProvider;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Relocation")
@Feature("Invoices")
public class InvoiceGenerateApiTest extends BaseFunctionalTest {

    private InvoiceFixture invoiceFixture;
    private RelocationFixture relocationFixture;
    private long senderId;
    private long receiverId;
    private long resourceId;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "baseTestClassSetup")
    public void setupInvoiceTests() {
        invoiceFixture = new InvoiceFixture(testContext, apiExecutor);
        relocationFixture = new RelocationFixture(testContext, apiExecutor);
        relocationFixture.prepareContext();
        senderId = ConfigProvider.getOwner1StorageId();
        receiverId = ConfigProvider.getOwner2StorageId();
        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        resourceId = resources.getFirst().getId();
        relocationFixture.ensureStock(senderId, resourceId, 20.0);
    }

    @Test(priority = 10)
    @TestCaseId("TC-INV-001")
    @Story("Invoice exists and generate")
    @Severity(SeverityLevel.CRITICAL)
    @Description("""
            Після send: GET exists, POST /invoice/generate/{storageId}/{relocationId}
            з items[] з рядків переміщення (як клієнт). Очікування: 2xx + PDF,
            далі exists=true. Без items бекенд падає NPE у populateData.
            """)
    public void generateInvoiceForSend() {
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, senderId, receiverId, resourceId, 1.0);
        assertThat(sent.getItems())
                .as("send relocation must expose line items for invoice generate")
                .isNotEmpty();

        boolean existedBefore = invoiceFixture.invoiceExists(UserRole.ADMIN, sent.getId(), senderId);
        Allure.parameter("relocationId", sent.getId());
        Allure.parameter("existedBeforeGenerate", existedBefore);

        InvoiceDataRequest request = InvoiceDataRequest.builder()
                .operationDate(LocalDate.now())
                .operationType("Видача")
                .sendName("sender")
                .receiveName("receiver")
                .sendingPersonName("Test")
                .receivingPersonName("Recv")
                .items(InvoiceFixture.itemsFromRelocation(sent))
                .build();
        assertThat(request.getItems())
                .as("invoice payload items from send rows")
                .isNotEmpty();

        Response generated = invoiceFixture.generateRaw(UserRole.ADMIN, senderId, sent.getId(), request);
        byte[] pdf = generated.asByteArray();
        assertThat(generated.statusCode())
                .as("POST generate; contentType=%s bytes=%s", generated.getContentType(), pdf.length)
                .isBetween(200, 299);
        assertThat(pdf.length)
                .as("generate має повернути PDF (не порожнє тіло)")
                .isGreaterThan(100);

        invoiceFixture.waitUntilExists(UserRole.ADMIN, sent.getId(), senderId, 30);
        assertThat(invoiceFixture.invoiceExists(UserRole.ADMIN, sent.getId(), senderId))
                .as("GET /invoice/{id}/exists після generate")
                .isTrue();
    }
}
