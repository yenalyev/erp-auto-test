package com.erp.utils.helpers;

import io.restassured.builder.ResponseBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

public class InventorySnapshotContractTest {
    @Test
    public void deletedInventoryRowUsesVersionedTombstoneWithoutChangingSibling() throws Exception {
        var removed = com.erp.models.response.StorageItemResponse.builder()
                .resource(com.erp.models.response.ResourceResponse.builder().id(11L).build())
                .amount(10.0).version(7L).build();
        var sibling = com.erp.models.response.StorageItemResponse.builder()
                .resource(com.erp.models.response.ResourceResponse.builder().id(12L).build())
                .amount(4.0).version(8L).build();
        var request = com.erp.data.factories.inventory.InventoryDataFactory.copyExcept(
                java.util.List.of(removed, sibling), 11L);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(request);
        assertThat(json.path("knownResourceIds").toString()).isEqualTo("[11,12]");
        assertThat(json.path("resources")).hasSize(2);
        var deleted = json.path("resources").get(0);
        assertThat(deleted.path("resourceId").asLong()).isEqualTo(11L);
        assertThat(deleted.path("version").asLong()).isEqualTo(7L);
        assertThat(deleted.path("amount").asDouble()).isZero();
        assertThat(deleted.path("removed").asBoolean()).isTrue();
        var retained = json.path("resources").get(1);
        assertThat(retained.path("resourceId").asLong()).isEqualTo(12L);
        assertThat(retained.path("version").asLong()).isEqualTo(8L);
        assertThat(retained.path("amount").asDouble()).isEqualTo(4.0);
        assertThat(retained.has("removed")).isFalse();
    }

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
