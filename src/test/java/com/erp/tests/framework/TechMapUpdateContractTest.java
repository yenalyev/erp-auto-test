package com.erp.tests.framework;

import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.fixtures.TechMapCukrarniaEditFixture;
import com.erp.models.request.*;
import com.erp.validators.SchemaRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Local contract regression: mutation references differ from populated read resources. */
public class TechMapUpdateContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String BODY = """
            {"id":9,"name":"map","type":"PRODUCTION","version":2,
             "groupId":"e614e4ed-3826-4e31-8931-a0c8682acd6a","dateTime":"2026-10-27T08:00:00Z",
             "storages":[{"id":7,"name":"location"}],
             "input":[{"resource":{"id":1,"name":null,"unit":null},"amount":1}],
             "output":[{"resource":{"id":2,"name":null,"unit":null},"amount":1}],
             "groups":[{"id":4,"name":"alternatives","alternativeResources":[
               {"id":5,"resource":{"id":3,"name":null,"unit":null},"amount":2,"isDefault":true}]}]}
            """;

    @Test
    public void updateAcceptsResourceReferences() {
        SchemaRegistry.validateIfSuccess(response(BODY), ApiEndpointDefinition.TECH_MAP_UPDATE_NAME);
        assertThat(ApiEndpointDefinition.TECH_MAP_UPDATE_NAME.getSchemaPath())
                .endsWith("technological-map-update-response-schema.json");
    }

    @Test
    public void readsStillRequirePopulatedMetadata() {
        assertThatThrownBy(() -> SchemaRegistry.validateIfSuccess(response(BODY), ApiEndpointDefinition.TECH_MAP_GET_BY_ID))
                .isInstanceOf(AssertionError.class);
        assertThat(ApiEndpointDefinition.TECH_MAP_GET_BY_ID.getSchemaPath())
                .endsWith("technological-map-response-schema.json");
    }

    @DataProvider(name = "invalidMutations")
    public Object[][] invalidMutations() {
        return new Object[][]{{"resourceId"}, {"amount"}, {"defaultFlag"}, {"version"}};
    }

    @Test(dataProvider = "invalidMutations")
    public void mutationStillRejectsBrokenBusinessFields(String field) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(BODY);
        switch (field) {
            case "resourceId" -> ((ObjectNode) body.at("/groups/0/alternativeResources/0/resource")).putNull("id");
            case "amount" -> ((ObjectNode) body.at("/output/0")).put("amount", "broken");
            case "defaultFlag" -> ((ObjectNode) body.at("/groups/0/alternativeResources/0")).remove("isDefault");
            case "version" -> body.put("version", 0);
            default -> throw new IllegalArgumentException(field);
        }
        assertThatThrownBy(() -> SchemaRegistry.validateIfSuccess(response(body.toString()), ApiEndpointDefinition.TECH_MAP_UPDATE_NAME))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    public void formPayloadOmitsDatabaseRowIdsAndKeepsBusinessValues() {
        var request = TechnologicalMapRequest.builder().name("map").type("PRODUCTION").storageIds(Set.of(7L))
                .input(List.of(new ResourceUsageRequest(1L, 1.0))).output(List.of(new ResourceUsageRequest(2L, 1.0)))
                .groups(List.of(TechnologicalMapAlternativeGroupRequest.builder().id(99L).name("alternatives")
                        .alternativeResources(List.of(TechnologicalMapAlternativeGroupResourceRequest.builder()
                                .id(98L).resourceId(3L).amount(2.0).isDefault(true).build())).build())).build();
        var body = mapper.valueToTree(TechMapCukrarniaEditFixture.formPayload(request));
        assertThat(body.at("/groups/0").has("id")).isFalse();
        assertThat(body.at("/groups/0/alternativeResources/0").has("id")).isFalse();
        assertThat(body.at("/groups/0/alternativeResources/0/resourceId").asLong()).isEqualTo(3);
        assertThat(body.at("/groups/0/alternativeResources/0/amount").asDouble()).isEqualTo(2);
        assertThat(body.at("/groups/0/alternativeResources/0/isDefault").asBoolean()).isTrue();
        assertThat(body.at("/input/0").size()).isEqualTo(2);
        assertThat(body.at("/storageIds/0").asLong()).isEqualTo(7);
    }

    private Response response(String body) {
        return new ResponseBuilder().setStatusCode(200).setContentType("application/json").setBody(body).build();
    }
}
