package com.erp.fixtures;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.enums.UserRole;
import com.erp.models.request.InvoiceDataRequest;
import com.erp.models.request.InvoiceItemRequest;
import com.erp.models.response.RelocationItemResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.test_context.TestContext;
import com.erp.api.clients.ApiExecutor;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class InvoiceFixture extends BaseFixture {

    private static final int DEFAULT_POLL_INTERVAL_MS = 1_000;

    public InvoiceFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    @Step("API: очікування файлу накладної relocationId={relocationId}")
    public void waitUntilExists(UserRole role, Long relocationId, Long senderId, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        while (System.currentTimeMillis() < deadline) {
            if (invoiceExists(role, relocationId, senderId)) {
                return;
            }
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        throw new IllegalStateException(
                "Invoice file not ready for relocation " + relocationId + " within " + timeoutSeconds + "s");
    }

    @Step("API: очікування файлу накладної relocationId={relocationId} (до {maxAttempts} спроб)")
    public void waitUntilExistsAttempts(UserRole role, Long relocationId, Long senderId, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (invoiceExists(role, relocationId, senderId)) {
                return;
            }
            if (attempt < maxAttempts) {
                // Match journal poll spacing — async PDF generation can take several seconds under load.
                sleep(2_000);
            }
        }
        throw new IllegalStateException(
                "Invoice file not ready for relocation " + relocationId + " after " + maxAttempts + " attempts");
    }

    /**
     * Line items as {@code InvoiceFileFacade.buildResourceInvoiceItems}: name, unit, amount from send.
     */
    public static List<InvoiceItemRequest> itemsFromRelocation(RelocationResponse relocation) {
        if (relocation == null || relocation.getItems() == null) {
            return List.of();
        }
        return relocation.getItems().stream()
                .map(InvoiceFixture::toInvoiceItem)
                .toList();
    }

    private static InvoiceItemRequest toInvoiceItem(RelocationItemResponse item) {
        ResourceResponse resource = item.getResource();
        String unit = resource != null && resource.getUnit() != null
                ? resource.getUnit().getShortName()
                : null;
        Integer amount = item.getAmount() != null ? item.getAmount().intValue() : 0;
        return InvoiceItemRequest.builder()
                .name(resource != null ? resource.getName() : null)
                .unit(unit)
                .totalAmount(amount)
                .build();
    }

    @Step("API: POST generate invoice for relocation {relocationId}")
    public Response generateRaw(UserRole role, long storageId, long relocationId, InvoiceDataRequest request) {
        return apiExecutor.execute(
                ApiEndpointDefinition.INVOICE_POST_GENERATE,
                role,
                request,
                storageId,
                relocationId);
    }

    @Step("API: GET /invoice/{id}/exists?senderId={senderId}")
    public boolean invoiceExists(UserRole role, Long relocationId, Long senderId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVOICE_GET_EXISTS,
                role,
                Map.of("senderId", senderId),
                relocationId);
        if (response.statusCode() != 200) {
            return false;
        }
        return Boolean.TRUE.equals(response.jsonPath().getBoolean("exists"));
    }

    @Step("API: завантажити накладну relocationId={relocationId}")
    public byte[] download(UserRole role, Long relocationId, Long senderId, Long receiverId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.INVOICE_GET_DOWNLOAD,
                role,
                Map.of("senderId", senderId, "receiverId", receiverId),
                relocationId);
        validateSuccess(response, "Download invoice");
        byte[] body = response.asByteArray();
        if (body.length == 0) {
            throw new IllegalStateException("Invoice download returned empty body for relocation " + relocationId);
        }
        return body;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for invoice", e);
        }
    }
}
