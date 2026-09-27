package com.erp.utils.helpers;

import io.restassured.builder.ResponseBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

public class InventorySnapshotContractTest {
    @DataProvider
    public Object[][] failedResponses() {
        return new Object[][] {
                {403, "text/plain", "Forbidden"},
                {404, "application/json", "{\"content\":[]}"},
                {500, "text/html", "<html>Unavailable</html>"},
                {500, "application/json", "{\"content\":[]}"},
                {200, "text/html", "<html>Login</html>"},
                {200, "application/json", "{}"},
                {200, "application/json", "{\"content\":null}"}
        };
    }

    @Test(dataProvider = "failedResponses")
    public void failuresCannotBecomeZeroStock(int status, String type, String body) {
        var response = new ResponseBuilder().setStatusCode(status).setContentType(type).setBody(body).build();
        assertThatThrownBy(() -> ProductionStockAssertions.parseMultiInventoryContent(response))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void validEmptyPageRepresentsZeroStock() {
        var response = new ResponseBuilder().setStatusCode(200).setContentType("application/json; charset=UTF-8")
                .setBody("{\"content\":[]}").build();
        assertThat(ProductionStockAssertions.parseMultiInventoryContent(response)).isEmpty();
    }

    @Test
    public void missingResponseIsAnError() {
        assertThatThrownBy(() -> ProductionStockAssertions.parseMultiInventoryContent(null))
                .isInstanceOf(AssertionError.class);
    }
}
