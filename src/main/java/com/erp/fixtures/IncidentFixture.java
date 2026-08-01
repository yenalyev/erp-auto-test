package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.incident.IncidentDataFactory;
import com.erp.enums.RelocationState;
import com.erp.enums.UserRole;
import com.erp.models.request.RelocationIncidentRequest;
import com.erp.models.response.RelocationIncidentResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.helpers.DatabaseIntegrityValidator;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

public class IncidentFixture extends BaseFixture {

    private final RelocationFixture relocationFixture;

    public IncidentFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.relocationFixture = new RelocationFixture(testContext, apiExecutor);
    }

    public RelocationFixture relocation() {
        return relocationFixture;
    }

    @Step("Prepare shared context for incident tests")
    public void prepareContext() {
        relocationFixture.prepareContext();
    }

    @Step("API: створити надзвичайну подію на relocation {relocation.id}")
    public void createIncident(UserRole role, RelocationResponse relocation, String description) {
        Response response = createIncidentRaw(
                role, IncidentDataFactory.buildFullCargoLoss(relocation, description));
        validateSuccess(response, "Create relocation incident");
    }

    @Step("API: POST incident (raw response, без assert success)")
    public Response createIncidentRaw(UserRole role, RelocationIncidentRequest request) {
        return apiExecutor.executeIncidentCreate(request, role);
    }

    @Step("API: створити надзвичайну подію «часткова доставка» на relocation {relocation.id}")
    public void createPartialDeliveryIncident(UserRole role,
                                              RelocationResponse relocation,
                                              Long deliveryStorageId,
                                              Long resourceId,
                                              double deliveredAmount,
                                              String description) {
        RelocationIncidentRequest request = IncidentDataFactory.buildPartialDelivery(
                relocation, deliveryStorageId, resourceId, deliveredAmount, description);
        Response response = apiExecutor.executeIncidentCreate(request, role);
        validateSuccess(response, "Create partial-delivery relocation incident");
    }

    @Step("API: створити надзвичайну подію «часткова доставка» (кілька ресурсів)")
    public void createPartialDeliveryIncident(UserRole role,
                                              RelocationResponse relocation,
                                              Long deliveryStorageId,
                                              Map<Long, Double> deliveredByResourceId,
                                              String description) {
        Response response = createPartialDeliveryIncidentRaw(
                role, relocation, deliveryStorageId, deliveredByResourceId, description);
        validateSuccess(response, "Create multi-item partial-delivery incident");
    }

    @Step("API: POST partial delivery (raw response, без assert success)")
    public Response createPartialDeliveryIncidentRaw(UserRole role,
                                                     RelocationResponse relocation,
                                                     Long deliveryStorageId,
                                                     Long resourceId,
                                                     double deliveredAmount,
                                                     String description) {
        return createPartialDeliveryIncidentRaw(
                role, relocation, deliveryStorageId, Map.of(resourceId, deliveredAmount), description);
    }

    @Step("API: POST partial delivery multi (raw response, без assert success)")
    public Response createPartialDeliveryIncidentRaw(UserRole role,
                                                     RelocationResponse relocation,
                                                     Long deliveryStorageId,
                                                     Map<Long, Double> deliveredByResourceId,
                                                     String description) {
        RelocationIncidentRequest request = IncidentDataFactory.buildPartialDelivery(
                relocation, deliveryStorageId, deliveredByResourceId, description);
        return apiExecutor.executeIncidentCreate(request, role);
    }

    @Step("API: GET інцидент для relocation {relocationId}")
    public RelocationIncidentResponse getIncident(UserRole role, Long relocationId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.INCIDENT_GET_BY_RELOCATION, role, null, String.valueOf(relocationId));
        validateSuccess(response, "Get relocation incident");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.INCIDENT_GET_BY_RELOCATION);
        return response.as(RelocationIncidentResponse.class);
    }

    @Step("API: DELETE інцидент для relocation {relocationId}")
    public void deleteIncident(UserRole role, Long relocationId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.INCIDENT_DELETE_BY_RELOCATION, role, null, String.valueOf(relocationId));
        validateSuccess(response, "Delete relocation incident");
    }

    @Step("API: знайти LOST relocation за маркером у примітках")
    public RelocationResponse findLostByDescription(UserRole role, Long storageId, String description) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.RELOCATION_GET_PAGE,
                role,
                Map.of(
                        "page", 0,
                        "size", 100,
                        "senderIds", storageId,
                        "receiverIds", storageId,
                        "isOr", true,
                        "states", List.of(RelocationState.LOST.name())));
        validateSuccess(response, "Get LOST relocations");
        List<RelocationResponse> page = DatabaseIntegrityValidator.extractList(response, RelocationResponse.class);
        return page.stream()
                .filter(r -> r.getDescription() != null && r.getDescription().contains(description))
                .findFirst()
                .orElse(null);
    }

    @Step("API: send + create incident (повна втрата)")
    public RelocationResponse sendAndCreateIncident(UserRole role,
                                                    Long senderId,
                                                    Long recipientId,
                                                    Long resourceId,
                                                    double amount,
                                                    String description) {
        RelocationResponse sent = relocationFixture.createSendWithDescription(
                role, senderId, recipientId, resourceId, amount, description);
        createIncident(role, sent, description);
        testContext.set(ContextKey.RELOCATION_WITH_INCIDENT_ID, sent.getId());
        return sent;
    }

    @Step("Seed RBAC context for incident endpoints")
    public void seedRbacIncidentContext() {
        if (testContext.get(ContextKey.RELOCATION_WITH_INCIDENT_ID) != null) {
            return;
        }
        if (testContext.get(ContextKey.RELOCATION_RESOURCE_ID) == null) {
            prepareContext();
        }
        Long owner1 = testContext.get(ContextKey.OWNER_1_STORAGE_ID);
        Long owner2 = testContext.get(ContextKey.OWNER_2_STORAGE_ID);
        Long resourceId = testContext.get(ContextKey.RELOCATION_RESOURCE_ID);
        if (owner1 == null) {
            owner1 = com.erp.utils.config.ConfigProvider.getOwner1StorageId();
        }
        if (owner2 == null) {
            owner2 = com.erp.utils.config.ConfigProvider.getOwner2StorageId();
        }

        RelocationResponse forCreate = relocationFixture.createSend(
                UserRole.OWNER_1, owner1, owner2, resourceId, 1.0);
        testContext.set(ContextKey.RELOCATION_FOR_INCIDENT_ID, forCreate.getId());

        String marker = IncidentDataFactory.uniqueDescription();
        RelocationResponse withIncident = sendAndCreateIncident(
                UserRole.OWNER_1, owner1, owner2, resourceId, 1.0, marker);
        testContext.set(ContextKey.RELOCATION_WITH_INCIDENT_ID, withIncident.getId());
    }
}
