package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.notification.NotificationDataFactory;
import com.erp.enums.UserRole;
import com.erp.models.request.NotificationRecipientRequest;
import com.erp.models.request.NotificationSubscriptionRequest;
import com.erp.models.request.RemoveNotificationSubscriptionRequest;
import com.erp.models.response.NotificationLogResponse;
import com.erp.models.response.NotificationRecipientResponse;
import com.erp.models.response.NotificationSubscriptionResponse;
import com.erp.models.response.NotificationTemplateResponse;
import com.erp.models.response.PagedNotificationResponse;
import com.erp.models.response.PagedNotificationSubscriptionResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.ApiResponseHelper;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class NotificationFixture extends BaseFixture {

    private final List<Integer> trackedRecipientIds = new ArrayList<>();

    public NotificationFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
    }

    public void prepareContext() {
        Long owner1 = ConfigProvider.getOwner1StorageId();
        Long owner2 = ConfigProvider.getOwner2StorageId();
        testContext.set(ContextKey.OWNER_1_STORAGE_ID, owner1);
        testContext.set(ContextKey.OWNER_2_STORAGE_ID, owner2);
    }

    @Step("API: створити notification recipient")
    public NotificationRecipientResponse createRecipient(UserRole role, NotificationRecipientRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_CREATE, role, request);
        validateSuccess(response, "Create notification recipient");
        NotificationRecipientResponse created = response.as(NotificationRecipientResponse.class);
        trackRecipient(created.getId());
        testContext.set(ContextKey.NOTIFICATION_RECIPIENT_ID, created.getId());
        return created;
    }

    @Step("API: створити ACTIVE recipient (factory)")
    public NotificationRecipientResponse createActiveRecipient(UserRole role) {
        return createRecipient(role, NotificationDataFactory.newActiveRecipient());
    }

    @Step("API: GET recipient {id}")
    public NotificationRecipientResponse getRecipientById(UserRole role, Integer id) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_BY_ID, role, String.valueOf(id));
        validateSuccess(response, "GET notification recipient " + id);
        return response.as(NotificationRecipientResponse.class);
    }

    @Step("API: GET all recipients")
    public List<NotificationRecipientResponse> getAllRecipients(UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_GET_ALL, role);
        validateSuccess(response, "GET notification recipients");
        List<NotificationRecipientResponse> list = ApiResponseHelper.parseList(
                response, NotificationRecipientResponse.class, "GET recipients");
        return list != null ? list : List.of();
    }

    @Step("API: update recipient {id}")
    public NotificationRecipientResponse updateRecipient(
            UserRole role, Integer id, NotificationRecipientRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_RECIPIENT_UPDATE, role, request, id);
        validateSuccess(response, "Update notification recipient " + id);
        return response.as(NotificationRecipientResponse.class);
    }

    @Step("API: disable recipient {id}")
    public void disableRecipient(UserRole role, Integer id) {
        NotificationRecipientResponse current = getRecipientById(role, id);
        NotificationRecipientRequest disable = NotificationRecipientRequest.builder()
                .id(id)
                .caption(current.getCaption())
                .addressInfo(current.getAddressInfo())
                .state(NotificationDataFactory.STATE_DISABLED)
                .build();
        updateRecipient(role, id, disable);
    }

    @Step("API: GET templates")
    public List<NotificationTemplateResponse> getTemplates(UserRole role) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.NOTIFICATION_TEMPLATE_GET_ALL, role);
        validateSuccess(response, "GET notification templates");
        List<NotificationTemplateResponse> list = ApiResponseHelper.parseList(
                response, NotificationTemplateResponse.class, "GET templates");
        return list != null ? list : List.of();
    }

    @Step("API: save subscription")
    public Response saveSubscriptionRaw(UserRole role, NotificationSubscriptionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_SAVE, role, request);
    }

    @Step("API: save subscription")
    public void saveSubscription(UserRole role, NotificationSubscriptionRequest request) {
        Response response = saveSubscriptionRaw(role, request);
        validateSuccess(response, "Save notification subscription");
    }

    @Step("API: delete subscription")
    public Response deleteSubscriptionRaw(UserRole role, RemoveNotificationSubscriptionRequest request) {
        return apiExecutor.execute(ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_DELETE, role, request);
    }

    @Step("API: delete subscription")
    public void deleteSubscription(UserRole role, Integer recipientId, String templateCode) {
        Response response = deleteSubscriptionRaw(
                role, NotificationDataFactory.removeSubscription(recipientId, templateCode));
        validateSuccess(response, "Delete notification subscription");
    }

    @Step("API: list subscriptions")
    public List<NotificationSubscriptionResponse> listSubscriptions(UserRole role) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_SUBSCRIPTION_GET_PAGE,
                role,
                java.util.Map.of("page", 0, "size", 200));
        validateSuccess(response, "GET notification subscriptions");
        PagedNotificationSubscriptionResponse page = response.as(PagedNotificationSubscriptionResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }

    @Step("API: find subscription for recipient {recipientId} / {templateCode}")
    public NotificationSubscriptionResponse findSubscription(
            UserRole role, Integer recipientId, String templateCode) {
        return listSubscriptions(role).stream()
                .filter(s -> Objects.equals(s.getRecipientId(), recipientId)
                        && Objects.equals(s.getTemplateCode(), templateCode))
                .findFirst()
                .orElse(null);
    }

    @Step("API: GET notifications journal")
    public List<NotificationLogResponse> listNotifications(UserRole role) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.NOTIFICATION_GET_PAGE,
                role,
                java.util.Map.of("page", 0, "size", 50, "sort", "createdAt,desc"));
        validateSuccess(response, "GET notifications journal");
        PagedNotificationResponse page = response.as(PagedNotificationResponse.class);
        return page.getContent() != null ? page.getContent() : List.of();
    }

    @Step("API: знайти stock_red у журналі для складу id={storageId}")
    public NotificationLogResponse findStockRedForStorage(UserRole role, Long storageId) {
        return listNotifications(role).stream()
                .filter(n -> NotificationDataFactory.TEMPLATE_STOCK_RED.equals(n.getTemplateCode()))
                .filter(n -> n.getStorage() != null && Objects.equals(storageId, n.getStorage().getId()))
                .findFirst()
                .orElse(null);
    }

    @Step("Await: stock_red у журналі для складу id={storageId}")
    public NotificationLogResponse awaitStockRedForStorage(UserRole role, Long storageId, long timeoutMs) {
        return com.erp.utils.helpers.PollUtils.waitUntil(
                () -> findStockRedForStorage(role, storageId),
                Objects::nonNull,
                timeoutMs,
                "stock_red notification for storageId=" + storageId);
    }

    public void trackRecipient(Integer id) {
        if (id != null && !trackedRecipientIds.contains(id)) {
            trackedRecipientIds.add(id);
        }
    }

    @Step("Cleanup: disable tracked notification recipients")
    public void disableTrackedRecipients(UserRole role) {
        for (Integer id : new ArrayList<>(trackedRecipientIds)) {
            try {
                disableRecipient(role, id);
            } catch (Exception e) {
                log.warn("Failed to disable notification recipient {}: {}", id, e.getMessage());
            }
        }
        trackedRecipientIds.clear();
    }

    public List<Integer> getTrackedRecipientIds() {
        return List.copyOf(trackedRecipientIds);
    }
}
