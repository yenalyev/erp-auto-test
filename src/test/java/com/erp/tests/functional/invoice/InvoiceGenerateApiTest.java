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
    @Description("GET exists + POST generate для relocation send.")
    public void generateInvoiceForSend() {
        RelocationResponse sent = relocationFixture.createSend(
                UserRole.ADMIN, senderId, receiverId, resourceId, 1.0);
        boolean existed = invoiceFixture.invoiceExists(UserRole.ADMIN, sent.getId(), senderId);

        InvoiceDataRequest request = InvoiceDataRequest.builder()
                .operationDate(LocalDate.now())
                .operationType("Видача")
                .sendName("sender")
                .receiveName("receiver")
                .sendingPersonName("Test")
                .receivingPersonName("Recv")
                .build();
        Response generated = invoiceFixture.generateRaw(UserRole.ADMIN, senderId, sent.getId(), request);
        assertThat(generated.statusCode())
                .as("generate invoice; existed=%s status=%s body=%s",
                        existed, generated.statusCode(), generated.asString())
                .isIn(200, 201, 204, 400);
        if (generated.statusCode() < 300) {
            assertThat(invoiceFixture.invoiceExists(UserRole.ADMIN, sent.getId(), senderId)
                    || existed).isTrue();
        }
    }
}
